package io.github.libfdx.net.webrtc.transport;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.buffer.NetBufferPool;
import io.github.libfdx.net.config.NetChannelConfig;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.config.NetEndpointConfig;
import io.github.libfdx.net.packet.NetPacket;
import io.github.libfdx.net.packet.NetPacketQueue;
import io.github.libfdx.net.transform.NetPacketTransform;
import io.github.libfdx.net.transport.NetSendResult;
import io.github.libfdx.net.transport.NetStats;
import io.github.libfdx.net.transform.NetTransformContext;
import io.github.libfdx.net.transform.NetTransformResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import io.github.libfdx.net.webrtc.config.WebRtcEndpointSettings;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannel;
import io.github.libfdx.net.webrtc.platform.WebRtcIceCandidate;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnection;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionListener;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionState;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescription;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingClient;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingCodec;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingListener;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessage;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessageType;
import io.github.libfdx.net.webrtc.WebRtcProvider;
abstract class AbstractWebRtcEndpoint implements WebRtcSignalingListener {
    private final Object lock = new Object();
    private final ArrayDeque<Runnable> events = new ArrayDeque<Runnable>();
    private final HashMap<String, WebRtcNetConnection> connectionsByPeerId =
            new HashMap<String, WebRtcNetConnection>();
    private final ArrayList<WebRtcNetConnection> connections = new ArrayList<WebRtcNetConnection>();
    private final WebRtcSignalingCodec codec = new WebRtcSignalingCodec();
    private final NetTransformContext receiveTransformContext = new NetTransformContext();
    private final NetPacketQueue receiveQueue;
    private final NetBufferPool buffers;
    private final NetStats stats = new NetStats();
    private final NetEndpointConfig config;
    private final WebRtcEndpointSettings settings;
    private final WebRtcPeerConnectionProvider peerConnections;
    private final WebRtcSignalingClient signaling;
    private int nextConnectionId = 1;
    private boolean closed;
    private String localPeerId;

    AbstractWebRtcEndpoint(NetEndpointConfig config, WebRtcPlatformFactory factory) {
        if (config == null) {
            throw new FdxException("WebRTC endpoint config cannot be null");
        }
        if (factory == null) {
            throw new FdxException("WebRTC platform factory cannot be null");
        }
        this.config = config;
        settings = WebRtcEndpointSettings.from(config);
        buffers = new NetBufferPool(config.buffers());
        receiveQueue = new NetPacketQueue(buffers, config.processing(), stats);
        peerConnections = factory.peerConnectionProvider();
        signaling = factory.signalingClient();
        localPeerId = settings.peerId();
    }

    public ProviderId providerId() {
        return WebRtcProvider.ID;
    }

    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    public NetBufferPool buffers() {
        return buffers;
    }

    public NetStats stats() {
        return stats;
    }

    public void process(float deltaTime) {
        if (closed) {
            return;
        }
        signaling.process(deltaTime);
        synchronized (lock) {
            for (int i = 0; i < connections.size(); i++) {
                connections.get(i).process(deltaTime);
            }
        }
        dispatchEvents();
        synchronized (lock) {
            receiveQueue.dispatch(deltaTime, (NetConnection connection, NetPacket packet) -> {
                dispatchMessage(connection, packet);
            });
        }
        dispatchEvents();
    }

    public void dispose() {
        close();
    }

    public boolean isDisposed() {
        return closed;
    }

    public void close() {
        ArrayList<WebRtcNetConnection> copy;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            copy = new ArrayList<WebRtcNetConnection>(connections);
            connections.clear();
            connectionsByPeerId.clear();
            receiveQueue.clear();
        }
        for (int i = 0; i < copy.size(); i++) {
            copy.get(i).closeWithoutEndpointCallback();
        }
        signaling.close();
    }

    int connectionCountInternal() {
        synchronized (lock) {
            return connections.size();
        }
    }

    WebRtcNetConnection connectionAtInternal(int index) {
        synchronized (lock) {
            return connections.get(index);
        }
    }

    NetChannelConfig channelConfig(int channelId) {
        NetChannelConfig[] channels = config.channels();
        for (int i = 0; i < channels.length; i++) {
            if (channels[i].id() == channelId) {
                return channels[i];
            }
        }
        return null;
    }

    NetChannelConfig[] channels() {
        return config.channels();
    }

    NetPacketTransform defaultTransform() {
        return config.defaultTransform();
    }

    WebRtcEndpointSettings settings() {
        return settings;
    }

    WebRtcSignalingCodec signalingCodec() {
        return codec;
    }

    void start() {
        signaling.connect(settings.signalingUrl(), settings.roomId(), settings.peerId(), this);
    }

    void sendSignaling(WebRtcSignalingMessageType type, String targetPeerId, JsonValue payload) {
        signaling.send(WebRtcSignalingMessage.builder(type)
                .roomId(settings.roomId())
                .sourcePeerId(localPeerId)
                .targetPeerId(targetPeerId)
                .payload(payload)
                .build());
    }

    void enqueueInbound(WebRtcNetConnection connection, int channelId, NetDelivery delivery,
            NetPacketTransform transform, byte[] bytes, int offset, int length) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (length > buffers.config().packetBytes()) {
                if (delivery == NetDelivery.UNRELIABLE_UNORDERED) {
                    stats.droppedUnreliable();
                }
                return;
            }
            if (transform == null) {
                NetBuffer buffer = buffers.tryAcquire();
                if (buffer == null) {
                    if (delivery == NetDelivery.UNRELIABLE_UNORDERED) {
                        stats.droppedUnreliable();
                    }
                    return;
                }
                buffer.set(bytes, offset, length);
                NetSendResult result = receiveQueue.enqueue(connection, channelId, delivery, buffer);
                if (result != NetSendResult.QUEUED) {
                    buffer.release();
                }
                return;
            }
            NetBuffer input = buffers.tryAcquire();
            NetBuffer output = buffers.tryAcquire();
            if (input == null || output == null) {
                if (input != null) {
                    input.release();
                }
                if (output != null) {
                    output.release();
                }
                if (delivery == NetDelivery.UNRELIABLE_UNORDERED) {
                    stats.droppedUnreliable();
                }
                return;
            }
            input.set(bytes, offset, length);
            NetTransformResult transformResult = transform.decode(
                    receiveTransformContext.set(WebRtcProvider.ID, connection, channelId, delivery),
                    input.reader().rewind(), output.writer());
            input.release();
            if (transformResult != NetTransformResult.OK) {
                output.release();
                return;
            }
            output.rewind();
            NetSendResult queueResult = receiveQueue.enqueue(connection, channelId, delivery, output);
            if (queueResult != NetSendResult.QUEUED) {
                output.release();
            }
        }
    }

    void onConnectionConnected(WebRtcNetConnection connection) {
        enqueueEvent(new Runnable() {
            @Override
            public void run() {
                dispatchConnected(connection);
            }
        });
    }

    void onConnectionDisconnected(WebRtcNetConnection connection) {
        boolean removed;
        synchronized (lock) {
            removed = connections.remove(connection);
            connectionsByPeerId.remove(connection.remotePeerId());
        }
        if (removed) {
            enqueueEvent(new Runnable() {
                @Override
                public void run() {
                    dispatchDisconnected(connection);
                }
            });
        }
    }

    void onConnectionError(final Throwable error) {
        enqueueEvent(new Runnable() {
            @Override
            public void run() {
                dispatchError(error);
            }
        });
    }

    @Override
    public void connected(String localPeerId) {
        if (localPeerId != null && !localPeerId.trim().isEmpty()) {
            this.localPeerId = localPeerId;
        }
    }

    @Override
    public void message(WebRtcSignalingMessage message) {
        if (message == null || closed) {
            return;
        }
        if (!isForLocal(message)) {
            return;
        }
        WebRtcSignalingMessageType type = message.type();
        if (type == WebRtcSignalingMessageType.WELCOME) {
            String assigned = message.targetPeerId();
            if (assigned == null) {
                assigned = message.payload().stringValue("peerId", null);
            }
            if (assigned != null) {
                localPeerId = assigned;
            }
            enqueueEvent(new Runnable() {
                @Override
                public void run() {
                    endpointReady();
                }
            });
        }
        else if (type == WebRtcSignalingMessageType.PEER_JOINED) {
            String remotePeerId = peerIdFrom(message);
            if (remotePeerId != null && !remotePeerId.equals(localPeerId)) {
                peerJoined(remotePeerId);
            }
        }
        else if (type == WebRtcSignalingMessageType.PEER_LEFT) {
            String remotePeerId = peerIdFrom(message);
            WebRtcNetConnection connection = connection(remotePeerId);
            if (connection != null) {
                connection.close();
            }
        }
        else if (type == WebRtcSignalingMessageType.CONNECT_REQUEST) {
            beginOffer(message.sourcePeerId());
        }
        else if (type == WebRtcSignalingMessageType.OFFER) {
            handleOffer(message.sourcePeerId(), codec.readSessionDescription(message.payload()));
        }
        else if (type == WebRtcSignalingMessageType.ANSWER) {
            WebRtcNetConnection connection = connection(message.sourcePeerId());
            if (connection != null) {
                connection.setRemoteAnswer(codec.readSessionDescription(message.payload()));
            }
        }
        else if (type == WebRtcSignalingMessageType.ICE) {
            WebRtcNetConnection connection = connection(message.sourcePeerId());
            if (connection != null) {
                connection.addIceCandidate(codec.readIceCandidate(message.payload()));
            }
        }
        else if (type == WebRtcSignalingMessageType.PING) {
            sendSignaling(WebRtcSignalingMessageType.PONG, message.sourcePeerId(), JsonValue.object());
        }
        else if (type == WebRtcSignalingMessageType.ERROR) {
            final String text = message.payload().stringValue("message", "WebRTC signaling error");
            onConnectionError(new FdxException(text));
        }
    }

    @Override
    public void disconnected(final String reason) {
        enqueueEvent(new Runnable() {
            @Override
            public void run() {
                dispatchError(new FdxException(reason != null ? reason : "WebRTC signaling disconnected"));
            }
        });
    }

    @Override
    public void error(Throwable error) {
        onConnectionError(error);
    }

    protected void sendConnectRequest(String remotePeerId) {
        if (remotePeerId != null && !remotePeerId.equals(localPeerId)) {
            sendSignaling(WebRtcSignalingMessageType.CONNECT_REQUEST, remotePeerId, JsonValue.object());
        }
    }

    protected String localPeerId() {
        return localPeerId;
    }

    protected abstract void endpointReady();

    protected abstract void peerJoined(String remotePeerId);

    protected abstract void dispatchConnected(NetConnection connection);

    protected abstract void dispatchDisconnected(NetConnection connection);

    protected abstract void dispatchMessage(NetConnection connection, NetPacket packet);

    protected abstract void dispatchError(Throwable error);

    private WebRtcNetConnection connection(String remotePeerId) {
        synchronized (lock) {
            return connectionsByPeerId.get(remotePeerId);
        }
    }

    private WebRtcNetConnection requireConnection(String remotePeerId) {
        WebRtcNetConnection existing;
        synchronized (lock) {
            existing = connectionsByPeerId.get(remotePeerId);
            if (existing != null) {
                return existing;
            }
            final WebRtcNetConnection connection = new WebRtcNetConnection(this, nextConnectionId++, remotePeerId);
            WebRtcPeerConnection peerConnection = peerConnections.createPeerConnection(settings,
                    new WebRtcPeerConnectionListener() {
                        @Override
                        public void iceCandidate(WebRtcIceCandidate candidate) {
                            sendSignaling(WebRtcSignalingMessageType.ICE, connection.remotePeerId(),
                                    codec.writeIceCandidate(candidate));
                        }

                        @Override
                        public void dataChannel(WebRtcDataChannel dataChannel) {
                            connection.attachDataChannel(dataChannel);
                        }

                        @Override
                        public void stateChanged(WebRtcPeerConnectionState state) {
                            connection.peerStateChanged(state);
                        }

                        @Override
                        public void error(Throwable error) {
                            onConnectionError(error);
                        }
                    });
            connection.peerConnection(peerConnection);
            connectionsByPeerId.put(remotePeerId, connection);
            connections.add(connection);
            return connection;
        }
    }

    private void beginOffer(String remotePeerId) {
        if (remotePeerId == null || remotePeerId.equals(localPeerId)) {
            return;
        }
        final WebRtcNetConnection connection = requireConnection(remotePeerId);
        connection.createDefaultDataChannels();
        connection.createOffer();
    }

    private void handleOffer(String remotePeerId, WebRtcSessionDescription offer) {
        if (remotePeerId == null || remotePeerId.equals(localPeerId)) {
            return;
        }
        final WebRtcNetConnection connection = requireConnection(remotePeerId);
        connection.handleOffer(offer);
    }

    private boolean isForLocal(WebRtcSignalingMessage message) {
        String roomId = message.roomId();
        String targetPeerId = message.targetPeerId();
        return (roomId == null || settings.roomId().equals(roomId))
                && (targetPeerId == null || targetPeerId.equals(localPeerId) || localPeerId == null);
    }

    private String peerIdFrom(WebRtcSignalingMessage message) {
        String peerId = message.sourcePeerId();
        if (peerId == null) {
            peerId = message.payload().stringValue("peerId", null);
        }
        return peerId;
    }

    private void enqueueEvent(Runnable event) {
        synchronized (events) {
            events.add(event);
        }
    }

    private void dispatchEvents() {
        while (true) {
            Runnable event;
            synchronized (events) {
                event = events.poll();
            }
            if (event == null) {
                return;
            }
            event.run();
        }
    }
}
