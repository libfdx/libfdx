package io.github.libfdx.net.webrtc.signaling.server;

import io.github.libfdx.json.JsonValue;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescription;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingCodec;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessage;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessageType;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;





final class WebRtcSignalingServerTest {
    private static final float TICK_DELTA = 1.0f / 30.0f;
    private final WebRtcSignalingCodec codec = new WebRtcSignalingCodec();

    @Test
    void roomsAreIsolatedAndPeersReceiveSameRoomJoinEvents() throws Exception {
        int port = freePort();
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .build());
        server.start();
        try {
            TestSocket a = connect(port, "room-a", "a");
            TestSocket b = connect(port, "room-a", "b");
            TestSocket c = connect(port, "room-b", "c");

            assertTrue(awaitMessages(server, a, 2));
            assertTrue(awaitMessages(server, b, 2));
            assertTrue(awaitMessages(server, c, 1));

            assertEquals(1, countType(a.messages(), WebRtcSignalingMessageType.PEER_JOINED));
            assertEquals(1, countType(b.messages(), WebRtcSignalingMessageType.PEER_JOINED));
            assertEquals(0, countType(c.messages(), WebRtcSignalingMessageType.PEER_JOINED));

            a.closeBlocking();
            b.closeBlocking();
            c.closeBlocking();
        }
        finally {
            server.dispose();
        }
    }

    @Test
    void roomIdsReturnsSortedDefensiveSnapshot() throws Exception {
        int port = freePort();
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .build());
        server.start();
        try {
            TestSocket roomB = connect(port, "room-b", "b");
            TestSocket roomA = connect(port, "room-a", "a");
            assertTrue(awaitMessages(server, roomB, 1));
            assertTrue(awaitMessages(server, roomA, 1));

            List<String> roomIds = server.roomIds();
            assertEquals(Arrays.asList("room-a", "room-b"), roomIds);

            roomIds.clear();
            assertEquals(Arrays.asList("room-a", "room-b"), server.roomIds());

            roomA.closeBlocking();
            roomB.closeBlocking();
        }
        finally {
            server.dispose();
        }
    }

    @Test
    void malformedPercentEscapeDoesNotEscapeTheProcessingLoop() throws Exception {
        int port = freePort();
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .build());
        server.start();
        try (Socket malformed = new Socket("127.0.0.1", port)) {
            String request = "GET /?room=%ZZ&peerId=bad HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + port + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n";
            malformed.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            malformed.getOutputStream().flush();
            for (int i = 0; i < 20; i++) {
                server.process(0.01f);
                Thread.sleep(5L);
            }

            TestSocket accepted = connect(port, "healthy-room", "healthy-peer");
            assertTrue(awaitMessages(server, accepted, 1));
            assertEquals(WebRtcSignalingMessageType.WELCOME,
                    codec.decode(accepted.messages().get(0)).type());
            assertEquals(1, server.roomCount());
            accepted.closeBlocking();
        }
        finally {
            server.dispose();
        }
    }

    @Test
    void rejectsUnsafeAndOversizedEncodedRoomIdsBeforeJoin() throws Exception {
        int port = freePort();
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .build());
        server.start();
        try {
            String[] encodedRoomIds = {
                    "line%0Abreak",
                    "line%0Dbreak",
                    "pipe%7Cbreak",
                    "control%1Fbreak",
                    repeated('x', 129)
            };
            for (int i = 0; i < encodedRoomIds.length; i++) {
                TestSocket socket = rawConnect("ws://127.0.0.1:" + port
                        + "/?room=" + encodedRoomIds[i] + "&peerId=peer");
                assertTrue(awaitMessages(server, socket, 1));
                WebRtcSignalingMessage error = lastType(socket.messages(), WebRtcSignalingMessageType.ERROR);
                assertEquals("invalid room", error.payload().requireString("message"));
                assertEquals(0, server.roomCount());
                socket.closeBlocking();
            }
        }
        finally {
            server.dispose();
        }
    }

    @Test
    void relaysOfferOnlyToTargetInSameRoom() throws Exception {
        int port = freePort();
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .build());
        server.start();
        try {
            TestSocket a = connect(port, "room", "a");
            TestSocket b = connect(port, "room", "b");
            assertTrue(awaitMessages(server, a, 2));
            assertTrue(awaitMessages(server, b, 2));

            a.send(codec.encode(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.OFFER)
                    .targetPeerId("b")
                    .payload(codec.writeSessionDescription(new io.github.libfdx.net.webrtc.platform.WebRtcSessionDescription(
                            io.github.libfdx.net.webrtc.platform.WebRtcSessionDescription.Type.OFFER, "offer")))
                    .build()));

            assertTrue(awaitMessages(server, b, 3));
            WebRtcSignalingMessage offer = lastType(b.messages(), WebRtcSignalingMessageType.OFFER);
            assertEquals("a", offer.sourcePeerId());
            assertEquals("b", offer.targetPeerId());

            a.closeBlocking();
            b.closeBlocking();
        }
        finally {
            server.dispose();
        }
    }

    @Test
    void authRejectsAndMalformedMessagesReturnError() throws Exception {
        int port = freePort();
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .auth(new WebRtcSignalingAuth() {
                    @Override
                    public boolean allow(WebRtcSignalingJoinRequest request) {
                        return "ok".equals(request.token());
                    }
                })
                .build());
            server.start();
        try {
            TestSocket rejected = rawConnect("ws://127.0.0.1:" + port + "/?room=room&peerId=bad");
            assertTrue(awaitMessages(server, rejected, 1));
            assertEquals(WebRtcSignalingMessageType.ERROR, codec.decode(rejected.messages().get(0)).type());
            assertEquals(0, server.roomCount());

            TestSocket accepted = rawConnect("ws://127.0.0.1:" + port + "/?room=room&peerId=ok&token=ok");
            assertTrue(awaitMessages(server, accepted, 1));
            accepted.send("not-json");
            assertTrue(awaitMessages(server, accepted, 2));
            assertEquals(WebRtcSignalingMessageType.ERROR, codec.decode(accepted.messages().get(1)).type());

            rejected.closeBlocking();
            accepted.closeBlocking();
        }
        finally {
            server.dispose();
        }
    }

    @Test
    void authenticationSessionCanDriveJoinAndMessagePolicies() throws Exception {
        int port = freePort();
        final ArrayList<String> joinedSessions = new ArrayList<String>();
        final ArrayList<String> messageSessions = new ArrayList<String>();
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .auth(new WebRtcSignalingAuth() {
                    @Override
                    public boolean allow(WebRtcSignalingJoinRequest request) {
                        return "ok".equals(request.token());
                    }

                    @Override
                    public WebRtcSignalingAuthResult authenticate(WebRtcSignalingJoinRequest request) {
                        if (!allow(request)) {
                            return WebRtcSignalingAuthResult.rejected("bad token");
                        }
                        assertEquals("/secure", request.path());
                        assertEquals("ok", request.query("token"));
                        assertEquals("Bearer accepted", request.header("Authorization"));
                        assertTrue(request.remoteAddress() != null);
                        return WebRtcSignalingAuthResult.accepted("session:" + request.requestedPeerId());
                    }
                })
                .joinPolicy(new WebRtcSignalingJoinPolicy() {
                    @Override
                    public WebRtcSignalingAccessDecision allowJoin(WebRtcSignalingJoinContext context) {
                        joinedSessions.add(String.valueOf(context.session()));
                        assertEquals(context.requestedPeerId(), context.peerId());
                        return WebRtcSignalingAccessDecision.allow();
                    }
                })
                .messagePolicy(new WebRtcSignalingMessagePolicy() {
                    @Override
                    public WebRtcSignalingAccessDecision allowMessage(WebRtcSignalingMessageContext context) {
                        messageSessions.add(String.valueOf(context.session()));
                        if (context.message().type() == WebRtcSignalingMessageType.ROOM_REGISTER
                                && !"session:host".equals(context.session())) {
                            return WebRtcSignalingAccessDecision.reject("only hosts can register rooms");
                        }
                        return WebRtcSignalingAccessDecision.allow();
                    }
                })
                .build());
        server.start();
        try {
            HashMap<String, String> headers = new HashMap<String, String>();
            headers.put("Authorization", "Bearer accepted");
            TestSocket host = rawConnect("ws://127.0.0.1:" + port
                    + "/secure?room=lobby&peerId=host&token=ok", headers);
            TestSocket guest = rawConnect("ws://127.0.0.1:" + port
                    + "/secure?room=lobby&peerId=guest&token=ok", headers);
            assertTrue(awaitMessages(server, host, 2));
            assertTrue(awaitMessages(server, guest, 2));
            assertTrue(joinedSessions.contains("session:host"));
            assertTrue(joinedSessions.contains("session:guest"));

            int beforeRejectedRegister = guest.messages().size();
            guest.send(codec.encode(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_REGISTER)
                    .payload(JsonValue.object().put("roomId", "secure-room"))
                    .build()));
            WebRtcSignalingMessage rejectedRegister = awaitTypeAfter(server, guest, WebRtcSignalingMessageType.ERROR,
                    beforeRejectedRegister);
            assertEquals("only hosts can register rooms", rejectedRegister.payload().requireString("message"));

            int beforeAcceptedRegister = guest.messages().size();
            host.send(codec.encode(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_REGISTER)
                    .payload(JsonValue.object().put("roomId", "secure-room"))
                    .build()));
            WebRtcSignalingMessage update = awaitTypeAfter(server, guest, WebRtcSignalingMessageType.ROOM_LIST_CHANGED,
                    beforeAcceptedRegister);
            assertEquals("secure-room", update.payload().require("rooms").require(0).requireString("roomId"));
            assertTrue(messageSessions.contains("session:host"));
            assertTrue(messageSessions.contains("session:guest"));

            host.closeBlocking();
            guest.closeBlocking();
        }
        finally {
            server.dispose();
        }
    }

    @Test
    void lobbyPeersCanRegisterListAndUnregisterRooms() throws Exception {
        int port = freePort();
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .build());
        server.start();
        try {
            TestSocket host = connect(port, "lobby", "host-lobby");
            TestSocket browser = connect(port, "lobby", "browser-lobby");
            assertTrue(awaitMessages(server, host, 2));
            assertTrue(awaitMessages(server, browser, 2));

            int beforeRegister = browser.messages().size();
            host.send(codec.encode(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_REGISTER)
                    .payload(JsonValue.object()
                            .put("roomId", "arena-1")
                            .put("name", "Arena 1")
                            .put("hostPeerId", "host")
                            .put("players", 1)
                            .put("maxPeers", 4))
                    .build()));

            WebRtcSignalingMessage update = awaitTypeAfter(server, browser,
                    WebRtcSignalingMessageType.ROOM_LIST_CHANGED,
                    beforeRegister);
            JsonValue room = update.payload().require("rooms").require(0);
            assertEquals("arena-1", room.requireString("roomId"));
            assertEquals("Arena 1", room.requireString("name"));
            assertEquals("host", room.requireString("hostPeerId"));
            assertEquals(1, room.intValue("players", 0));
            assertEquals(4, room.intValue("maxPeers", 0));
            assertEquals(1, server.registeredRoomCount());

            int beforeList = browser.messages().size();
            browser.send(codec.encode(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_LIST)
                    .payload(JsonValue.object())
                    .build()));
            WebRtcSignalingMessage list = awaitTypeAfter(server, browser, WebRtcSignalingMessageType.ROOM_LIST,
                    beforeList);
            assertEquals("arena-1", list.payload().require("rooms").require(0).requireString("roomId"));

            int beforeUnregister = browser.messages().size();
            host.send(codec.encode(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_UNREGISTER)
                    .payload(JsonValue.object().put("roomId", "arena-1"))
                    .build()));
            WebRtcSignalingMessage emptyUpdate = awaitTypeAfter(server, browser,
                    WebRtcSignalingMessageType.ROOM_LIST_CHANGED,
                    beforeUnregister);
            assertEquals(0, emptyUpdate.payload().require("rooms").size());
            assertEquals(0, server.registeredRoomCount());

            host.closeBlocking();
            browser.closeBlocking();
        }
        finally {
            server.dispose();
        }
    }

    @Test
    void rejectsUnsafeAndOversizedRegisteredRoomIds() throws Exception {
        int port = freePort();
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .build());
        server.start();
        try {
            TestSocket host = connect(port, "lobby", "host-lobby");
            assertTrue(awaitMessages(server, host, 1));
            String[] invalidRoomIds = {
                    "line\nbreak",
                    "line\rbreak",
                    "pipe|break",
                    "control\u001Fbreak",
                    repeated('x', 129)
            };

            for (int i = 0; i < invalidRoomIds.length; i++) {
                int beforeRegister = host.messageCount();
                host.send(codec.encode(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_REGISTER)
                        .payload(JsonValue.object().put("roomId", invalidRoomIds[i]))
                        .build()));
                WebRtcSignalingMessage error = awaitTypeAfter(server, host, WebRtcSignalingMessageType.ERROR,
                        beforeRegister);
                assertEquals("room registration invalid roomId", error.payload().requireString("message"));
                assertEquals(0, server.registeredRoomCount());
            }

            int beforeValidRegister = host.messageCount();
            host.send(codec.encode(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_REGISTER)
                    .payload(JsonValue.object().put("roomId", "ordinary-room_42"))
                    .build()));
            awaitTypeAfter(server, host, WebRtcSignalingMessageType.ROOM_LIST, beforeValidRegister);
            assertEquals(1, server.registeredRoomCount());

            for (int i = 0; i < invalidRoomIds.length; i++) {
                int beforeUnregister = host.messageCount();
                host.send(codec.encode(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_UNREGISTER)
                        .payload(JsonValue.object().put("roomId", invalidRoomIds[i]))
                        .build()));
                WebRtcSignalingMessage error = awaitTypeAfter(server, host, WebRtcSignalingMessageType.ERROR,
                        beforeUnregister);
                assertEquals("room unregister invalid roomId", error.payload().requireString("message"));
                assertEquals(1, server.registeredRoomCount());
            }

            int beforeValidUnregister = host.messageCount();
            host.send(codec.encode(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_UNREGISTER)
                    .payload(JsonValue.object().put("roomId", "ordinary-room_42"))
                    .build()));
            awaitTypeAfter(server, host, WebRtcSignalingMessageType.ROOM_LIST_CHANGED, beforeValidUnregister);
            assertEquals(0, server.registeredRoomCount());
            host.closeBlocking();
        }
        finally {
            server.dispose();
        }
    }

    @Test
    void registeredRoomIsRemovedWhenOwnerDisconnects() throws Exception {
        int port = freePort();
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .build());
        server.start();
        try {
            TestSocket host = connect(port, "lobby", "host-lobby");
            TestSocket browser = connect(port, "lobby", "browser-lobby");
            assertTrue(awaitMessages(server, host, 2));
            assertTrue(awaitMessages(server, browser, 2));

            int beforeRegister = browser.messages().size();
            host.send(codec.encode(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.ROOM_REGISTER)
                    .payload(JsonValue.object().put("roomId", "arena-2").put("name", "Arena 2"))
                    .build()));
            assertEquals("arena-2", awaitTypeAfter(server, browser,
                    WebRtcSignalingMessageType.ROOM_LIST_CHANGED,
                    beforeRegister)
                    .payload().require("rooms").require(0).requireString("roomId"));

            int beforeClose = browser.messages().size();
            host.closeBlocking();
            WebRtcSignalingMessage emptyUpdate = awaitTypeAfter(server, browser,
                    WebRtcSignalingMessageType.ROOM_LIST_CHANGED,
                    beforeClose);
            assertEquals(0, emptyUpdate.payload().require("rooms").size());
            assertEquals(0, server.registeredRoomCount());

            browser.closeBlocking();
        }
        finally {
            server.dispose();
        }
    }

    @Test
    void callbacksOnlyRunDuringProcess() throws Exception {
        int port = freePort();
        final int[] authCalls = new int[1];
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .auth(new WebRtcSignalingAuth() {
                    @Override
                    public boolean allow(WebRtcSignalingJoinRequest request) {
                        authCalls[0]++;
                        return true;
                    }
                })
                .build());
        server.start();
        try {
            TestSocket socket = connect(port, "room", "peer");
            assertTrue(awaitQueuedEvents(server, 1));
            Thread.sleep(100);
            assertEquals(0, authCalls[0]);
            assertEquals(0, socket.messageCount());

            server.process(TICK_DELTA);
            assertTrue(socket.awaitMessages(1));
            assertEquals(1, authCalls[0]);

            socket.closeBlocking();
        }
        finally {
            server.dispose();
        }
    }

    @Test
    void processingBudgetLimitsEventsPerTickAndReusesEventStorage() throws Exception {
        int port = freePort();
        final int[] authCalls = new int[1];
        WebRtcSignalingProcessingConfig processing = WebRtcSignalingProcessingConfig.builder()
                .tickRate(30)
                .maxTicksPerFrame(1)
                .maxEventsPerTick(1)
                .initialEvents(2)
                .maxQueuedEvents(2)
                .build();
        WebRtcSignalingServer server = new WebRtcSignalingServer(WebRtcSignalingServerConfig.builder(port)
                .bindHost("127.0.0.1")
                .processing(processing)
                .auth(new WebRtcSignalingAuth() {
                    @Override
                    public boolean allow(WebRtcSignalingJoinRequest request) {
                        authCalls[0]++;
                        return true;
                    }
                })
                .build());
        server.start();
        try {
            TestSocket a = connect(port, "room", "a");
            TestSocket b = connect(port, "room", "b");
            assertTrue(awaitQueuedEvents(server, 2));
            assertEquals(2, server.eventCapacity());

            server.process(TICK_DELTA);
            assertEquals(1, authCalls[0]);
            assertEquals(1, server.queuedEventCount());

            server.process(TICK_DELTA);
            assertEquals(2, authCalls[0]);
            assertEquals(0, server.queuedEventCount());
            assertEquals(2, server.eventCapacity());
            assertTrue(awaitMessages(server, a, 2));
            assertTrue(awaitMessages(server, b, 2));

            a.closeBlocking();
            b.closeBlocking();
        }
        finally {
            server.dispose();
        }
    }

    private TestSocket connect(int port, String roomId, String peerId) throws Exception {
        return rawConnect("ws://127.0.0.1:" + port + "/?room=" + roomId + "&peerId=" + peerId);
    }

    private TestSocket rawConnect(String url) throws Exception {
        return rawConnect(url, Collections.<String, String>emptyMap());
    }

    private TestSocket rawConnect(String url, Map<String, String> headers) throws Exception {
        TestSocket socket = new TestSocket(URI.create(url));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            socket.addHeader(entry.getKey(), entry.getValue());
        }
        socket.connectBlocking(5, TimeUnit.SECONDS);
        return socket;
    }

    private int countType(List<String> messages, WebRtcSignalingMessageType type) {
        int count = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (codec.decode(messages.get(i)).type() == type) {
                count++;
            }
        }
        return count;
    }

    private WebRtcSignalingMessage lastType(List<String> messages, WebRtcSignalingMessageType type) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            WebRtcSignalingMessage message = codec.decode(messages.get(i));
            if (message.type() == type) {
                return message;
            }
        }
        throw new AssertionError("Missing message type " + type);
    }

    private WebRtcSignalingMessage awaitTypeAfter(WebRtcSignalingServer server, TestSocket socket,
            WebRtcSignalingMessageType type, int afterCount) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            server.process(TICK_DELTA);
            WebRtcSignalingMessage message = lastTypeOrNull(socket.messagesAfter(afterCount), type);
            if (message != null) {
                return message;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Missing message type " + type);
    }

    private WebRtcSignalingMessage lastTypeOrNull(List<String> messages, WebRtcSignalingMessageType type) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            WebRtcSignalingMessage message = codec.decode(messages.get(i));
            if (message.type() == type) {
                return message;
            }
        }
        return null;
    }

    private boolean awaitMessages(WebRtcSignalingServer server, TestSocket socket, int count)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            server.process(TICK_DELTA);
            if (socket.messageCount() >= count) {
                return true;
            }
            Thread.sleep(25);
        }
        return socket.messageCount() >= count;
    }

    private boolean awaitQueuedEvents(WebRtcSignalingServer server, int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (server.queuedEventCount() >= count) {
                return true;
            }
            Thread.sleep(25);
        }
        return server.queuedEventCount() >= count;
    }

    private static int freePort() throws Exception {
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }

    private static String repeated(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }

    private static final class TestSocket extends org.java_websocket.client.WebSocketClient {
        private final ArrayList<String> messages = new ArrayList<String>();
        private final CountDownLatch open = new CountDownLatch(1);

        TestSocket(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            open.countDown();
        }

        @Override
        public void onMessage(String message) {
            synchronized (messages) {
                messages.add(message);
                messages.notifyAll();
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
        }

        @Override
        public void onError(Exception ex) {
            throw new AssertionError(ex);
        }

        boolean awaitMessages(int count) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5000;
            synchronized (messages) {
                while (messages.size() < count && System.currentTimeMillis() < deadline) {
                    messages.wait(50);
                }
                return messages.size() >= count;
            }
        }

        int messageCount() {
            synchronized (messages) {
                return messages.size();
            }
        }

        List<String> messages() {
            synchronized (messages) {
                return new ArrayList<String>(messages);
            }
        }

        List<String> messagesAfter(int count) {
            synchronized (messages) {
                ArrayList<String> result = new ArrayList<String>();
                for (int i = count; i < messages.size(); i++) {
                    result.add(messages.get(i));
                }
                return result;
            }
        }
    }
}
