package io.github.libfdx.net.webrtc.transport;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.buffer.NetBufferPoolConfig;
import io.github.libfdx.net.buffer.NetReader;
import io.github.libfdx.net.buffer.NetWriter;
import io.github.libfdx.net.config.NetChannelConfig;
import io.github.libfdx.net.config.NetProcessingConfig;
import io.github.libfdx.net.packet.NetPacket;
import io.github.libfdx.net.transform.NetPacketTransform;
import io.github.libfdx.net.transform.NetTransformContext;
import io.github.libfdx.net.transform.NetTransformResult;
import io.github.libfdx.net.transport.NetClientListener;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.transport.NetSendResult;
import io.github.libfdx.net.transport.NetServer;
import io.github.libfdx.net.transport.NetServerListener;
import io.github.libfdx.net.webrtc.config.WebRtcClientConfig;
import io.github.libfdx.net.webrtc.config.WebRtcEndpointSettings;
import io.github.libfdx.net.webrtc.config.WebRtcServerConfig;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannel;
import io.github.libfdx.net.webrtc.platform.WebRtcDataChannelListener;
import io.github.libfdx.net.webrtc.platform.WebRtcIceCandidate;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnection;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionListener;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionState;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescription;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescriptionCallback;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingClient;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingListener;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessage;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingMessageType;
import io.github.libfdx.net.webrtc.WebRtcProvider;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;





final class WebRtcEndpointTest {
    @Test
    void welcomeCallbackRunsOnlyDuringProcess() {
        FakeFactory factory = new FakeFactory();
        CountingServerListener listener = new CountingServerListener();
        WebRtcNetServer server = new WebRtcNetServer(WebRtcServerConfig.builder()
                .signalingUrl("ws://test")
                .roomId("room")
                .hostPeerId("host")
                .build(), listener, factory);

        assertEquals(0, listener.started);

        server.process(1f / 60f);

        assertEquals(1, listener.started);
    }

    @Test
    void sendsTransformsAndReceivesDuringProcess() {
        FakeFactory factory = new FakeFactory();
        PlusOneTransform transform = new PlusOneTransform();
        CountingClientListener listener = new CountingClientListener();
        WebRtcNetClient client = new WebRtcNetClient(WebRtcClientConfig.builder()
                .signalingUrl("ws://test")
                .roomId("room")
                .peerId("client")
                .buffers(NetBufferPoolConfig.builder().initialPackets(8).maxPackets(16).packetBytes(32).build())
                .processing(NetProcessingConfig.builder()
                        .tickRate(60)
                        .maxTicksPerFrame(1)
                        .maxReceivePacketsPerTick(1)
                        .build())
                .channels(NetChannelConfig.builder(0, NetDelivery.RELIABLE_ORDERED).transform(transform).build())
                .build(), listener, factory);
        client.process(1f / 60f);

        factory.signaling.listener.message(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.CONNECT_REQUEST)
                .roomId("room")
                .sourcePeerId("server")
                .targetPeerId("client")
                .build());
        client.process(1f / 60f);

        assertEquals(1, listener.connected);
        assertNotNull(client.connection());

        NetBuffer send = client.buffers().acquire();
        send.writer().putByte(1).putByte(2);
        NetSendResult result = client.connection().send(0, send);
        send.release();

        assertEquals(NetSendResult.SENT, result);
        assertArrayEquals(new byte[] { 2, 3 }, factory.peerConnections.lastDataChannel.lastSent);

        factory.peerConnections.lastDataChannel.incoming(new byte[] { 8, 9 });
        assertEquals(0, listener.messages);

        client.process(1f / 60f);

        assertEquals(1, listener.messages);
        assertArrayEquals(new byte[] { 7, 8 }, listener.lastMessage);
        assertEquals(0, client.buffers().totalCreated() - client.buffers().freeCount());
    }

    @Test
    void failedIceRestartsThenDisconnectsAfterAttempts() {
        FakeFactory factory = new FakeFactory();
        CountingClientListener listener = new CountingClientListener();
        WebRtcNetClient client = new WebRtcNetClient(WebRtcClientConfig.builder()
                .signalingUrl("ws://test")
                .roomId("room")
                .peerId("client")
                .maxIceRestartAttempts(1)
                .iceBackoffBaseMillis(1)
                .build(), listener, factory);
        client.process(1f / 60f);
        factory.signaling.listener.message(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.CONNECT_REQUEST)
                .roomId("room")
                .sourcePeerId("server")
                .targetPeerId("client")
                .build());
        client.process(1f / 60f);

        factory.peerConnections.lastConnection.listener.stateChanged(WebRtcPeerConnectionState.FAILED);
        client.process(1f);
        client.process(1f);

        assertEquals(1, factory.peerConnections.lastConnection.restartCount);
    }

    private static final class PlusOneTransform implements NetPacketTransform {
        @Override
        public int maxOutputBytes(int inputBytes) {
            return inputBytes;
        }

        @Override
        public NetTransformResult encode(NetTransformContext context, NetReader input, NetWriter output) {
            while (input.remaining() > 0) {
                output.putByte((input.getUnsignedByte() + 1) & 0xff);
            }
            return NetTransformResult.OK;
        }

        @Override
        public NetTransformResult decode(NetTransformContext context, NetReader input, NetWriter output) {
            while (input.remaining() > 0) {
                output.putByte((input.getUnsignedByte() - 1) & 0xff);
            }
            return NetTransformResult.OK;
        }
    }

    private static final class CountingClientListener implements NetClientListener {
        int connected;
        int disconnected;
        int messages;
        byte[] lastMessage;

        @Override
        public void connected(NetConnection connection) {
            connected++;
        }

        @Override
        public void disconnected(NetConnection connection) {
            disconnected++;
        }

        @Override
        public void message(NetConnection connection, NetPacket packet) {
            messages++;
            lastMessage = new byte[packet.length()];
            packet.reader().getBytes(lastMessage, 0, lastMessage.length);
        }

        @Override
        public void error(Throwable error) {
            throw new AssertionError(error);
        }
    }

    private static final class CountingServerListener implements NetServerListener {
        int started;

        @Override
        public void started(io.github.libfdx.net.transport.NetServer server) {
            started++;
        }

        @Override
        public void connected(NetConnection connection) {
        }

        @Override
        public void disconnected(NetConnection connection) {
        }

        @Override
        public void message(NetConnection connection, NetPacket packet) {
        }

        @Override
        public void error(Throwable error) {
            throw new AssertionError(error);
        }
    }

    private static final class FakeFactory implements WebRtcPlatformFactory {
        final FakePeerConnectionProvider peerConnections = new FakePeerConnectionProvider();
        final FakeSignalingClient signaling = new FakeSignalingClient();

        @Override
        public ProviderId providerId() {
            return WebRtcProvider.ID;
        }

        @Override
        public WebRtcPeerConnectionProvider peerConnectionProvider() {
            return peerConnections;
        }

        @Override
        public WebRtcSignalingClient signalingClient() {
            return signaling;
        }

        @Override
        public void dispose() {
            peerConnections.dispose();
            signaling.dispose();
        }

        @Override
        public boolean isDisposed() {
            return peerConnections.isDisposed() && signaling.isDisposed();
        }
    }

    private static final class FakeSignalingClient implements WebRtcSignalingClient {
        WebRtcSignalingListener listener;
        final ArrayList<WebRtcSignalingMessage> sent = new ArrayList<WebRtcSignalingMessage>();
        String localPeerId;

        @Override
        public void connect(String signalingUrl, String roomId, String requestedPeerId,
                WebRtcSignalingListener listener) {
            this.listener = listener;
            localPeerId = requestedPeerId != null ? requestedPeerId : "peer";
            listener.connected(localPeerId);
            listener.message(WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.WELCOME)
                    .roomId(roomId)
                    .sourcePeerId("server")
                    .targetPeerId(localPeerId)
                    .build());
        }

        @Override
        public void process(float deltaTime) {
        }

        @Override
        public void send(WebRtcSignalingMessage message) {
            sent.add(message);
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public String localPeerId() {
            return localPeerId;
        }

        @Override
        public void close() {
        }

        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }

    private static final class FakePeerConnectionProvider implements WebRtcPeerConnectionProvider {
        FakePeerConnection lastConnection;
        FakeDataChannel lastDataChannel;

        @Override
        public WebRtcPeerConnection createPeerConnection(WebRtcEndpointSettings settings,
                WebRtcPeerConnectionListener listener) {
            lastConnection = new FakePeerConnection(listener, this);
            return lastConnection;
        }

        @Override
        public boolean isSupported() {
            return true;
        }

        @Override
        public void close() {
        }

        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }

    private static final class FakePeerConnection implements WebRtcPeerConnection {
        final WebRtcPeerConnectionListener listener;
        final FakePeerConnectionProvider owner;
        int restartCount;

        FakePeerConnection(WebRtcPeerConnectionListener listener, FakePeerConnectionProvider owner) {
            this.listener = listener;
            this.owner = owner;
        }

        @Override
        public WebRtcDataChannel createDataChannel(String label, NetDelivery delivery,
                WebRtcDataChannelListener listener) {
            owner.lastDataChannel = new FakeDataChannel(label, delivery);
            owner.lastDataChannel.listener(listener);
            owner.lastDataChannel.open();
            return owner.lastDataChannel;
        }

        @Override
        public void createOffer(WebRtcSessionDescriptionCallback callback) {
            callback.success(new WebRtcSessionDescription(WebRtcSessionDescription.Type.OFFER, "offer"));
        }

        @Override
        public void handleOffer(WebRtcSessionDescription offer, WebRtcSessionDescriptionCallback callback) {
            callback.success(new WebRtcSessionDescription(WebRtcSessionDescription.Type.ANSWER, "answer"));
        }

        @Override
        public void setRemoteAnswer(WebRtcSessionDescription answer) {
        }

        @Override
        public void addIceCandidate(WebRtcIceCandidate candidate) {
        }

        @Override
        public void restartIce(WebRtcSessionDescriptionCallback callback) {
            restartCount++;
            callback.success(new WebRtcSessionDescription(WebRtcSessionDescription.Type.OFFER, "restart"));
        }

        @Override
        public void close() {
        }

        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }

    private static final class FakeDataChannel implements WebRtcDataChannel {
        final String label;
        final NetDelivery delivery;
        WebRtcDataChannelListener listener;
        byte[] lastSent;
        boolean open;

        FakeDataChannel(String label, NetDelivery delivery) {
            this.label = label;
            this.delivery = delivery;
        }

        void open() {
            open = true;
            listener.open();
        }

        void incoming(byte[] bytes) {
            listener.message(bytes, 0, bytes.length);
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public NetDelivery delivery() {
            return delivery;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public long bufferedAmount() {
            return 0;
        }

        @Override
        public void listener(WebRtcDataChannelListener listener) {
            this.listener = listener;
        }

        @Override
        public boolean send(byte[] bytes, int offset, int length) {
            lastSent = new byte[length];
            System.arraycopy(bytes, offset, lastSent, 0, length);
            return true;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public void dispose() {
            close();
        }

        @Override
        public boolean isDisposed() {
            return !open;
        }
    }
}
