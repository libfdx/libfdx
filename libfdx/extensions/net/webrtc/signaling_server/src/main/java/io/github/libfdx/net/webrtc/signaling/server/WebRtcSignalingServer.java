package io.github.libfdx.net.webrtc.signaling.server;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingCodec;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessage;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessageType;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;

/**
 * Room-scoped WebSocket signaling server for libFDX WebRTC transports.
 *
 * @author xpenatan
 */
public final class WebRtcSignalingServer implements Disposable {
    private static final String SERVER_PEER_ID = "server";
    private static final int MAX_ROOM_ID_UTF8_BYTES = 128;

    private final WebRtcSignalingServerConfig config;
    private final WebRtcSignalingProcessingConfig processingConfig;
    private final WebRtcSignalingCodec codec = new WebRtcSignalingCodec();
    private final SignalingEventQueue events;
    private final HashMap<WebSocket, Peer> peersBySocket = new HashMap<WebSocket, Peer>();
    private final HashMap<String, Room> rooms = new HashMap<String, Room>();
    private final LinkedHashMap<String, RegisteredRoom> registeredRooms =
            new LinkedHashMap<String, RegisteredRoom>();
    private final ArrayList<Peer> scratchPeers = new ArrayList<Peer>();
    private final ArrayList<String> scratchRoomIds = new ArrayList<String>();
    private final SocketServer server;
    private float accumulatedTime;
    private boolean disposed;

    public WebRtcSignalingServer(WebRtcSignalingServerConfig config) {
        if (config == null) {
            throw new FdxException("WebRTC signaling server config cannot be null");
        }
        this.config = config;
        processingConfig = config.processing();
        events = new SignalingEventQueue(processingConfig);
        server = new SocketServer(new InetSocketAddress(config.bindHost(), config.port()));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        try {
            server.stop();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FdxException("Interrupted while stopping WebRTC signaling server", exception);
        }
    }

    /**
     * Processes queued signaling events on the caller thread.
     *
     * @param deltaTime the frame delta time in seconds
     */
    public void process(float deltaTime) {
        if (disposed) {
            return;
        }
        int ticks = beginFrame(deltaTime);
        for (int tick = 0; tick < ticks; tick++) {
            processTick();
            cleanupIdlePeers();
        }
    }

    public int queuedEventCount() {
        return events.size();
    }

    public int eventCapacity() {
        return events.capacity();
    }

    public void cleanupIdlePeers() {
        if (config.idleTimeoutMillis() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        scratchPeers.clear();
        synchronized (this) {
            for (Peer peer : peersBySocket.values()) {
                if (now - peer.lastSeenMillis > config.idleTimeoutMillis()) {
                    scratchPeers.add(peer);
                }
            }
        }
        for (int i = 0; i < scratchPeers.size(); i++) {
            scratchPeers.get(i).socket.close(1001, "idle timeout");
        }
        scratchPeers.clear();
    }

    public synchronized int roomCount() {
        return rooms.size();
    }

    /**
     * Returns a stable snapshot of the currently active room IDs.
     */
    public synchronized List<String> roomIds() {
        ArrayList<String> roomIds = new ArrayList<String>(rooms.keySet());
        Collections.sort(roomIds);
        return roomIds;
    }

    public synchronized int peerCount(String roomId) {
        Room room = rooms.get(roomId);
        return room != null ? room.peers.size() : 0;
    }

    public synchronized int registeredRoomCount() {
        return registeredRooms.size();
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        stop();
        events.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private int beginFrame(float deltaTime) {
        if (deltaTime > 0.0f && Float.isFinite(deltaTime)) {
            accumulatedTime += deltaTime;
        }
        float tickSeconds = 1.0f / processingConfig.tickRate();
        int ticks = 0;
        while (accumulatedTime >= tickSeconds && ticks < processingConfig.maxTicksPerFrame()) {
            accumulatedTime -= tickSeconds;
            ticks++;
        }
        if (ticks == processingConfig.maxTicksPerFrame()) {
            float maxCarry = tickSeconds * processingConfig.maxTicksPerFrame();
            if (accumulatedTime > maxCarry) {
                accumulatedTime = maxCarry;
            }
        }
        return ticks;
    }

    private void processTick() {
        int eventsThisTick = 0;
        int bytesThisTick = 0;
        while (events.size() > 0 && canProcessMore(eventsThisTick)) {
            SignalingEvent event = events.poll(bytesThisTick, processingConfig.maxBytesPerTick());
            if (event == null) {
                return;
            }
            int eventBytes = event.byteLength;
            try {
                processEvent(event);
            }
            finally {
                events.release(event);
            }
            eventsThisTick++;
            bytesThisTick += eventBytes;
        }
    }

    private boolean canProcessMore(int eventsThisTick) {
        int maxEvents = processingConfig.maxEventsPerTick();
        return maxEvents == 0 || eventsThisTick < maxEvents;
    }

    private void processEvent(SignalingEvent event) {
        if (event.type == SignalingEvent.OPEN) {
            opened(event.socket, event.resource, event.headers, event.remoteAddress);
        }
        else if (event.type == SignalingEvent.MESSAGE) {
            message(event.socket, event.text);
        }
        else if (event.type == SignalingEvent.CLOSE) {
            closed(event.socket);
        }
    }

    private synchronized void opened(WebSocket socket, String resource, Map<String, String> headers,
            InetSocketAddress remoteAddress) {
        cleanupIdlePeers();
        Map<String, String> query;
        try {
            query = parseQuery(resource);
        }
        catch(RuntimeException error) {
            error(socket, null, "malformed query");
            socket.close(1008, "malformed query");
            return;
        }
        String roomId = query.get("room");
        String requestedPeerId = query.get("peerId");
        String token = query.get("token");
        if (roomId == null || roomId.trim().isEmpty()) {
            error(socket, null, "missing room");
            socket.close(1008, "missing room");
            return;
        }
        if (!isSafeRoomId(roomId)) {
            error(socket, null, "invalid room");
            socket.close(1008, "invalid room");
            return;
        }
        WebRtcSignalingJoinRequest request = new WebRtcSignalingJoinRequest(roomId, requestedPeerId, token,
                resource, path(resource), query, headers, remoteAddress);
        WebRtcSignalingAuthResult authResult = config.auth().authenticate(request);
        if (authResult == null || !authResult.isAccepted()) {
            String reason = authResult != null ? reason(authResult.rejectionReason(), "auth rejected")
                    : "auth rejected";
            error(socket, roomId, reason);
            socket.close(1008, reason);
            return;
        }
        Room room = rooms.get(roomId);
        int peerCount = room != null ? room.peers.size() : 0;
        if (!config.roomPolicy().allowJoin(roomId, requestedPeerId, peerCount)) {
            error(socket, roomId, "room policy rejected");
            socket.close(1008, "room policy rejected");
            return;
        }
        if (peerCount >= config.maxPeersPerRoom()) {
            error(socket, roomId, "room is full");
            socket.close(1008, "room is full");
            return;
        }
        String peerId = uniquePeerId(room, config.peerIdGenerator().generatePeerId(roomId, requestedPeerId,
                peerCount));
        WebRtcSignalingAccessDecision joinDecision = config.joinPolicy().allowJoin(
                new WebRtcSignalingJoinContext(request, peerId, peerCount, authResult.session()));
        if (joinDecision == null || !joinDecision.allowed()) {
            String reason = joinDecision != null ? reason(joinDecision.rejectionReason(), "join policy rejected")
                    : "join policy rejected";
            error(socket, roomId, reason);
            socket.close(1008, reason);
            return;
        }
        if (room == null) {
            room = room(roomId);
        }
        Peer peer = new Peer(socket, roomId, peerId, request, authResult.session());
        scratchPeers.clear();
        scratchPeers.addAll(room.peers.values());
        room.peers.put(peerId, peer);
        peersBySocket.put(socket, peer);
        send(socket, WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.WELCOME)
                .roomId(roomId)
                .sourcePeerId(SERVER_PEER_ID)
                .targetPeerId(peerId)
                .payload(JsonValue.object().put("peerId", peerId))
                .build());
        for (int i = 0; i < scratchPeers.size(); i++) {
            Peer existing = scratchPeers.get(i);
            send(existing.socket, joinMessage(roomId, peerId, existing.peerId));
            send(peer.socket, joinMessage(roomId, existing.peerId, peerId));
        }
        scratchPeers.clear();
        config.logger().info("WebRTC signaling peer joined room " + roomId + ": " + peerId);
        broadcastDirectoryForRegisteredRoom(roomId);
    }

    private synchronized void closed(WebSocket socket) {
        Peer peer = peersBySocket.remove(socket);
        if (peer == null) {
            return;
        }
        boolean removedOwnedRoom = unregisterOwnedRooms(peer);
        Room room = rooms.get(peer.roomId);
        if (room != null) {
            room.peers.remove(peer.peerId);
            broadcast(room, WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.PEER_LEFT)
                    .roomId(peer.roomId)
                    .sourcePeerId(peer.peerId)
                    .payload(JsonValue.object().put("peerId", peer.peerId))
                    .build());
            if (room.peers.isEmpty()) {
                rooms.remove(peer.roomId);
            }
        }
        if (removedOwnedRoom) {
            broadcastDirectory(peer.roomId);
        }
        broadcastDirectoryForRegisteredRoom(peer.roomId);
        config.logger().info("WebRTC signaling peer left room " + peer.roomId + ": " + peer.peerId);
    }

    private synchronized void message(WebSocket socket, String text) {
        cleanupIdlePeers();
        Peer peer = peersBySocket.get(socket);
        if (peer == null) {
            error(socket, null, "peer is not joined");
            return;
        }
        peer.lastSeenMillis = System.currentTimeMillis();
        WebRtcSignalingMessage message;
        try {
            message = codec.decode(text);
        }
        catch (RuntimeException exception) {
            error(socket, peer.roomId, "malformed signaling message");
            return;
        }
        Room room = rooms.get(peer.roomId);
        int peersInRoom = room != null ? room.peers.size() : 0;
        WebRtcSignalingAccessDecision messageDecision = config.messagePolicy().allowMessage(
                new WebRtcSignalingMessageContext(message, peer.joinRequest, peer.roomId, peer.peerId,
                        message.targetPeerId(), peersInRoom, peer.session));
        if (messageDecision == null || !messageDecision.allowed()) {
            String reason = messageDecision != null ? reason(messageDecision.rejectionReason(), "message rejected")
                    : "message rejected";
            error(socket, peer.roomId, reason);
            return;
        }
        if (message.type() == WebRtcSignalingMessageType.PING) {
            send(socket, WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.PONG)
                    .roomId(peer.roomId)
                    .sourcePeerId(SERVER_PEER_ID)
                    .targetPeerId(peer.peerId)
                    .payload(JsonValue.object())
                    .build());
            return;
        }
        if (handleDirectoryMessage(peer, message)) {
            return;
        }
        if (!isRelayType(message.type())) {
            error(socket, peer.roomId, "server cannot relay message type " + message.type().wireName());
            return;
        }
        String targetPeerId = message.targetPeerId();
        Peer target = room != null && targetPeerId != null ? room.peers.get(targetPeerId) : null;
        if (target == null) {
            error(socket, peer.roomId, "target peer is not in room");
            return;
        }
        send(target.socket, WebRtcSignalingMessage.builder(message.type())
                .roomId(peer.roomId)
                .sourcePeerId(peer.peerId)
                .targetPeerId(target.peerId)
                .payload(message.payload())
                .build());
    }

    private synchronized void error(WebSocket socket, String roomId, String text) {
        send(socket, WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ERROR)
                .roomId(roomId)
                .sourcePeerId(SERVER_PEER_ID)
                .payload(JsonValue.object().put("message", text))
                .build());
    }

    private synchronized Room room(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            room = new Room();
            rooms.put(roomId, room);
        }
        return room;
    }

    private synchronized void broadcast(Room room, WebRtcSignalingMessage message) {
        for (Peer target : room.peers.values()) {
            send(target.socket, WebRtcSignalingMessage.builder(message.type())
                    .roomId(message.roomId())
                    .sourcePeerId(message.sourcePeerId())
                    .targetPeerId(target.peerId)
                    .payload(message.payload())
                    .build());
        }
    }

    private void send(WebSocket socket, WebRtcSignalingMessage message) {
        if (socket != null && socket.isOpen()) {
            socket.send(codec.encode(message));
        }
    }

    private WebRtcSignalingMessage joinMessage(String roomId, String joinedPeerId, String targetPeerId) {
        return WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.PEER_JOINED)
                .roomId(roomId)
                .sourcePeerId(joinedPeerId)
                .targetPeerId(targetPeerId)
                .payload(JsonValue.object().put("peerId", joinedPeerId))
                .build();
    }

    private synchronized boolean handleDirectoryMessage(Peer peer, WebRtcSignalingMessage message) {
        if (message.type() == WebRtcSignalingMessageType.ROOM_REGISTER) {
            registerRoom(peer, message.payload());
            return true;
        }
        if (message.type() == WebRtcSignalingMessageType.ROOM_UNREGISTER) {
            unregisterRoom(peer, message.payload());
            return true;
        }
        if (message.type() == WebRtcSignalingMessageType.ROOM_LIST) {
            send(peer.socket, directoryMessage(WebRtcSignalingMessageType.ROOM_LIST, peer.roomId, peer.peerId));
            return true;
        }
        return false;
    }

    private void registerRoom(Peer owner, JsonValue payload) {
        String roomId = payload.stringValue("roomId", null);
        if (roomId == null || roomId.trim().isEmpty()) {
            error(owner.socket, owner.roomId, "room registration missing roomId");
            return;
        }
        if (!isSafeRoomId(roomId)) {
            error(owner.socket, owner.roomId, "room registration invalid roomId");
            return;
        }
        String name = payload.stringValue("name", roomId);
        String hostPeerId = payload.stringValue("hostPeerId", owner.peerId);
        int players = Math.max(1, payload.intValue("players", 1));
        int maxPeers = Math.max(1, payload.intValue("maxPeers", config.maxPeersPerRoom()));
        registeredRooms.put(roomId, new RegisteredRoom(roomId, name, owner.roomId, owner.peerId, hostPeerId,
                players, maxPeers));
        send(owner.socket, directoryMessage(WebRtcSignalingMessageType.ROOM_LIST, owner.roomId, owner.peerId));
        broadcastDirectory(owner.roomId);
    }

    private void unregisterRoom(Peer owner, JsonValue payload) {
        String roomId = payload.stringValue("roomId", null);
        if (roomId == null || roomId.trim().isEmpty()) {
            error(owner.socket, owner.roomId, "room unregister missing roomId");
            return;
        }
        if (!isSafeRoomId(roomId)) {
            error(owner.socket, owner.roomId, "room unregister invalid roomId");
            return;
        }
        RegisteredRoom room = registeredRooms.get(roomId);
        if (room != null && !owner.peerId.equals(room.ownerPeerId)) {
            error(owner.socket, owner.roomId, "only the room owner can unregister room " + roomId);
            return;
        }
        if (room != null) {
            registeredRooms.remove(roomId);
            broadcastDirectory(room.directoryRoomId);
        }
    }

    private boolean unregisterOwnedRooms(Peer owner) {
        scratchRoomIds.clear();
        for (RegisteredRoom room : registeredRooms.values()) {
            if (owner.peerId.equals(room.ownerPeerId) && owner.roomId.equals(room.directoryRoomId)) {
                scratchRoomIds.add(room.roomId);
            }
        }
        for (int i = 0; i < scratchRoomIds.size(); i++) {
            registeredRooms.remove(scratchRoomIds.get(i));
        }
        boolean removedAny = !scratchRoomIds.isEmpty();
        scratchRoomIds.clear();
        return removedAny;
    }

    private void broadcastDirectoryForRegisteredRoom(String roomId) {
        RegisteredRoom registered = registeredRooms.get(roomId);
        if (registered != null) {
            broadcastDirectory(registered.directoryRoomId);
        }
    }

    private void broadcastDirectory(String directoryRoomId) {
        Room directory = rooms.get(directoryRoomId);
        if (directory != null) {
            broadcast(directory, directoryMessage(WebRtcSignalingMessageType.ROOM_LIST_CHANGED, directoryRoomId,
                    null));
        }
    }

    private WebRtcSignalingMessage directoryMessage(WebRtcSignalingMessageType type, String directoryRoomId,
            String targetPeerId) {
        return WebRtcSignalingMessage.builder(type)
                .roomId(directoryRoomId)
                .sourcePeerId(SERVER_PEER_ID)
                .targetPeerId(targetPeerId)
                .payload(directoryPayload(directoryRoomId))
                .build();
    }

    private JsonValue directoryPayload(String directoryRoomId) {
        JsonValue roomsJson = JsonValue.array();
        for (RegisteredRoom room : registeredRooms.values()) {
            if (room.directoryRoomId.equals(directoryRoomId)) {
                roomsJson.add(room.toJson(peerCount(room.roomId)));
            }
        }
        return JsonValue.object().put("rooms", roomsJson);
    }

    private String uniquePeerId(Room room, String generated) {
        String base = generated != null && !generated.trim().isEmpty() ? generated : "peer";
        String candidate = base;
        int suffix = 2;
        while (room != null && room.peers.containsKey(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static boolean isRelayType(WebRtcSignalingMessageType type) {
        return type == WebRtcSignalingMessageType.OFFER
                || type == WebRtcSignalingMessageType.ANSWER
                || type == WebRtcSignalingMessageType.ICE
                || type == WebRtcSignalingMessageType.CONNECT_REQUEST;
    }

    private static boolean isSafeRoomId(String roomId) {
        if (roomId == null || roomId.length() == 0 || utf8Length(roomId) > MAX_ROOM_ID_UTF8_BYTES) {
            return false;
        }
        for (int i = 0; i < roomId.length(); i++) {
            char character = roomId.charAt(i);
            if (character == '|' || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> parseQuery(String resource) {
        LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
        if (resource == null) {
            return values;
        }
        int queryStart = resource.indexOf('?');
        if (queryStart < 0 || queryStart == resource.length() - 1) {
            return values;
        }
        String query = resource.substring(queryStart + 1);
        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            int separator = pair.indexOf('=');
            String name = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            values.put(decode(name), decode(value));
        }
        return values;
    }

    private static Map<String, String> headers(ClientHandshake handshake) {
        LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
        if (handshake == null) {
            return values;
        }
        Iterator<String> fields = handshake.iterateHttpFields();
        while (fields.hasNext()) {
            String name = fields.next();
            values.put(name, handshake.getFieldValue(name));
        }
        return values;
    }

    private static String path(String resource) {
        if (resource == null) {
            return "";
        }
        int queryStart = resource.indexOf('?');
        return queryStart >= 0 ? resource.substring(0, queryStart) : resource;
    }

    private static String reason(String reason, String fallback) {
        return reason != null && !reason.trim().isEmpty() ? reason : fallback;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        }
        catch(IllegalArgumentException exception) {
            throw new FdxException("Malformed URL-encoded query value", exception);
        }
        catch (UnsupportedEncodingException exception) {
            throw new FdxException("UTF-8 is not supported", exception);
        }
    }

    private void enqueueOpen(WebSocket socket, ClientHandshake handshake) {
        if (disposed) {
            closeOverflow(socket);
            return;
        }
        String resource = handshake != null ? handshake.getResourceDescriptor() : null;
        Map<String, String> headers = headers(handshake);
        InetSocketAddress remoteAddress = socket != null ? socket.getRemoteSocketAddress() : null;
        int byteLength = utf8Length(resource) + headersByteLength(headers);
        if (!events.enqueueOpen(socket, resource, headers, remoteAddress, byteLength)) {
            closeOverflow(socket);
        }
    }

    private void enqueueMessage(WebSocket socket, String message) {
        if (disposed) {
            closeOverflow(socket);
            return;
        }
        if (!events.enqueueMessage(socket, message, utf8Length(message))) {
            closeOverflow(socket);
        }
    }

    private void enqueueClose(WebSocket socket) {
        if (disposed) {
            return;
        }
        events.dropQueuedEvents(socket);
        if (!events.enqueueClose(socket)) {
            closed(socket);
        }
    }

    private void closeOverflow(WebSocket socket) {
        if (socket != null) {
            socket.close(1013, "signaling queue overflow");
        }
    }

    private static int headersByteLength(Map<String, String> headers) {
        int length = 0;
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                length += utf8Length(entry.getKey()) + utf8Length(entry.getValue());
            }
        }
        return length;
    }

    private static int utf8Length(String value) {
        if (value == null) {
            return 0;
        }
        int length = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch <= 0x7F) {
                length++;
            }
            else if (ch <= 0x7FF) {
                length += 2;
            }
            else if (Character.isHighSurrogate(ch) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                length += 4;
                i++;
            }
            else {
                length += 3;
            }
        }
        return length;
    }

    private final class SocketServer extends WebSocketServer {
        SocketServer(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            enqueueOpen(conn, handshake);
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            enqueueClose(conn);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            enqueueMessage(conn, message);
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            config.logger().error("WebRTC signaling socket error", ex);
        }

        @Override
        public void onStart() {
            config.logger().info("WebRTC signaling server started on " + config.bindHost() + ":" + config.port());
        }
    }

    private static final class SignalingEventQueue {
        private final int maxEvents;
        private SignalingEvent head;
        private SignalingEvent tail;
        private SignalingEvent freeHead;
        private int size;
        private int capacity;

        SignalingEventQueue(WebRtcSignalingProcessingConfig config) {
            maxEvents = config.maxQueuedEvents();
            for (int i = 0; i < config.initialEvents(); i++) {
                SignalingEvent event = new SignalingEvent();
                event.next = freeHead;
                freeHead = event;
                capacity++;
            }
        }

        synchronized boolean enqueueOpen(WebSocket socket, String resource, Map<String, String> headers,
                InetSocketAddress remoteAddress, int byteLength) {
            SignalingEvent event = acquire();
            if (event == null) {
                return false;
            }
            event.type = SignalingEvent.OPEN;
            event.socket = socket;
            event.resource = resource;
            event.headers = headers;
            event.remoteAddress = remoteAddress;
            event.byteLength = byteLength;
            enqueue(event);
            return true;
        }

        synchronized boolean enqueueMessage(WebSocket socket, String text, int byteLength) {
            SignalingEvent event = acquire();
            if (event == null) {
                return false;
            }
            event.type = SignalingEvent.MESSAGE;
            event.socket = socket;
            event.text = text;
            event.byteLength = byteLength;
            enqueue(event);
            return true;
        }

        synchronized boolean enqueueClose(WebSocket socket) {
            SignalingEvent event = acquire();
            if (event == null) {
                return false;
            }
            event.type = SignalingEvent.CLOSE;
            event.socket = socket;
            enqueue(event);
            return true;
        }

        synchronized SignalingEvent poll(int bytesThisTick, int maxBytesPerTick) {
            SignalingEvent event = head;
            if (event == null) {
                return null;
            }
            if (bytesThisTick > 0 && maxBytesPerTick > 0 && bytesThisTick + event.byteLength > maxBytesPerTick) {
                return null;
            }
            head = event.next;
            if (head == null) {
                tail = null;
            }
            event.next = null;
            size--;
            return event;
        }

        synchronized void release(SignalingEvent event) {
            event.clear();
            event.next = freeHead;
            freeHead = event;
        }

        synchronized int dropQueuedEvents(WebSocket socket) {
            int dropped = 0;
            SignalingEvent previous = null;
            SignalingEvent current = head;
            while (current != null) {
                SignalingEvent next = current.next;
                if (current.socket == socket) {
                    if (previous == null) {
                        head = next;
                    }
                    else {
                        previous.next = next;
                    }
                    if (tail == current) {
                        tail = previous;
                    }
                    size--;
                    current.next = null;
                    release(current);
                    dropped++;
                }
                else {
                    previous = current;
                }
                current = next;
            }
            return dropped;
        }

        synchronized int size() {
            return size;
        }

        synchronized int capacity() {
            return capacity;
        }

        synchronized void clear() {
            while (head != null) {
                SignalingEvent event = poll(0, 0);
                release(event);
            }
        }

        private SignalingEvent acquire() {
            if (size >= maxEvents) {
                return null;
            }
            if (freeHead != null) {
                SignalingEvent event = freeHead;
                freeHead = event.next;
                event.next = null;
                return event;
            }
            if (capacity >= maxEvents) {
                return null;
            }
            capacity++;
            return new SignalingEvent();
        }

        private void enqueue(SignalingEvent event) {
            if (tail == null) {
                head = event;
                tail = event;
            }
            else {
                tail.next = event;
                tail = event;
            }
            size++;
        }
    }

    private static final class SignalingEvent {
        private static final int OPEN = 1;
        private static final int MESSAGE = 2;
        private static final int CLOSE = 3;

        private int type;
        private WebSocket socket;
        private String resource;
        private Map<String, String> headers;
        private InetSocketAddress remoteAddress;
        private String text;
        private int byteLength;
        private SignalingEvent next;

        void clear() {
            type = 0;
            socket = null;
            resource = null;
            headers = null;
            remoteAddress = null;
            text = null;
            byteLength = 0;
            next = null;
        }
    }

    private static final class Room {
        private final LinkedHashMap<String, Peer> peers = new LinkedHashMap<String, Peer>();
    }

    private static final class RegisteredRoom {
        private final String roomId;
        private final String name;
        private final String directoryRoomId;
        private final String ownerPeerId;
        private final String hostPeerId;
        private final int players;
        private final int maxPeers;

        RegisteredRoom(String roomId, String name, String directoryRoomId, String ownerPeerId, String hostPeerId,
                int players, int maxPeers) {
            this.roomId = roomId;
            this.name = name;
            this.directoryRoomId = directoryRoomId;
            this.ownerPeerId = ownerPeerId;
            this.hostPeerId = hostPeerId;
            this.players = players;
            this.maxPeers = maxPeers;
        }

        JsonValue toJson(int actualPlayers) {
            int playerCount = Math.max(players, actualPlayers);
            return JsonValue.object()
                    .put("roomId", roomId)
                    .put("name", name)
                    .put("hostPeerId", hostPeerId)
                    .put("players", playerCount)
                    .put("maxPeers", maxPeers);
        }
    }

    private static final class Peer {
        private final WebSocket socket;
        private final String roomId;
        private final String peerId;
        private final WebRtcSignalingJoinRequest joinRequest;
        private final Object session;
        private long lastSeenMillis;

        Peer(WebSocket socket, String roomId, String peerId, WebRtcSignalingJoinRequest joinRequest, Object session) {
            this.socket = socket;
            this.roomId = roomId;
            this.peerId = peerId;
            this.joinRequest = joinRequest;
            this.session = session;
            lastSeenMillis = System.currentTimeMillis();
        }
    }
}
