package io.github.libfdx.net.spi;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.buffer.NetBufferPool;
import io.github.libfdx.net.buffer.NetBufferPoolConfig;
import io.github.libfdx.net.config.NetClientConfig;
import io.github.libfdx.net.config.NetPeerConfig;
import io.github.libfdx.net.config.NetServerConfig;
import io.github.libfdx.net.packet.NetPacket;
import io.github.libfdx.net.transform.NetPacketTransform;
import io.github.libfdx.net.transport.NetClient;
import io.github.libfdx.net.transport.NetClientListener;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetConnectionState;
import io.github.libfdx.net.transport.NetPeerGroup;
import io.github.libfdx.net.transport.NetPeerListener;
import io.github.libfdx.net.transport.NetSendResult;
import io.github.libfdx.net.transport.NetServer;
import io.github.libfdx.net.transport.NetServerListener;
import io.github.libfdx.net.transport.NetStats;
import io.github.libfdx.net.transport.NetTransportProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests provider-neutral transport dispatch.
 *
 * @author xpenatan
 */
final class DefaultNetTransportsTest {
    private static final ProviderId FAKE_PROVIDER = ProviderId.of("fake");
    private static final ProviderId OTHER_PROVIDER = ProviderId.of("other");

    @Test
    void dispatchesClientCreationToProvider() {
        FakeProvider provider = new FakeProvider();
        DefaultNetTransports transports = new DefaultNetTransports(provider);
        RecordingClientListener listener = new RecordingClientListener();

        NetClient client = transports.connect(FakeClientConfig.builder().build(), listener);

        assertSame(provider.client, client);
        assertSame(listener, provider.listener);
        assertTrue(transports.supports(FAKE_PROVIDER));
        assertFalse(transports.supports(OTHER_PROVIDER));
    }

    @Test
    void callbacksDispatchOnlyDuringProcess() {
        FakeProvider provider = new FakeProvider();
        RecordingClientListener listener = new RecordingClientListener();
        FakeClient client = (FakeClient)new DefaultNetTransports(provider)
                .connect(FakeClientConfig.builder().build(), listener);

        client.enqueueConnected();
        assertEquals(0, listener.connectedCount);

        client.process(1.0f / 60.0f);
        assertEquals(1, listener.connectedCount);
        assertSame(client.connection, listener.connection);
    }

    @Test
    void unsupportedProviderFailsClearly() {
        DefaultNetTransports transports = new DefaultNetTransports();

        assertThrows(FdxException.class, () -> transports.connect(FakeClientConfig.builder().build(),
                new RecordingClientListener()));
    }

    private static final class FakeClientConfig extends NetClientConfig {
        private FakeClientConfig(Builder builder) {
            super(builder);
        }

        static Builder builder() {
            return new Builder();
        }

        private static final class Builder extends NetClientConfig.Builder<Builder> {
            private Builder() {
                super(FAKE_PROVIDER);
            }

            FakeClientConfig build() {
                return new FakeClientConfig(this);
            }

            @Override
            protected Builder self() {
                return this;
            }
        }
    }

    private static final class FakeProvider implements NetTransportProvider {
        private final FakeClient client = new FakeClient();
        private NetClientListener listener;

        @Override
        public ProviderId providerId() {
            return FAKE_PROVIDER;
        }

        @Override
        public boolean supportsClient() {
            return true;
        }

        @Override
        public boolean supportsServer() {
            return false;
        }

        @Override
        public boolean supportsPeerGroup() {
            return false;
        }

        @Override
        public NetClient connect(NetClientConfig config, NetClientListener listener) {
            this.listener = listener;
            client.listener = listener;
            return client;
        }

        @Override
        public NetServer listen(NetServerConfig config, NetServerListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NetPeerGroup join(NetPeerConfig config, NetPeerListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeClient implements NetClient {
        private final NetBufferPool buffers = new NetBufferPool(NetBufferPoolConfig.builder()
                .initialPackets(1)
                .maxPackets(1)
                .packetBytes(32)
                .build());
        private final NetStats stats = new NetStats();
        private final FakeConnection connection = new FakeConnection();
        private NetClientListener listener;
        private boolean connectedPending;
        private boolean disposed;

        void enqueueConnected() {
            connectedPending = true;
        }

        @Override
        public void process(float deltaTime) {
            if (connectedPending) {
                connectedPending = false;
                listener.connected(connection);
            }
        }

        @Override
        public NetConnection connection() {
            return connection;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public NetBufferPool buffers() {
            return buffers;
        }

        @Override
        public NetStats stats() {
            return stats;
        }

        @Override
        public ProviderId providerId() {
            return FAKE_PROVIDER;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class FakeConnection implements NetConnection {
        @Override
        public int id() {
            return 1;
        }

        @Override
        public NetConnectionState state() {
            return NetConnectionState.CONNECTED;
        }

        @Override
        public NetSendResult send(int channelId, NetBuffer buffer) {
            return NetSendResult.SENT;
        }

        @Override
        public NetSendResult send(int channelId, byte[] bytes, int offset, int length) {
            return NetSendResult.SENT;
        }

        @Override
        public void setTransform(int channelId, NetPacketTransform transform) {
        }

        @Override
        public void close() {
        }

        @Override
        public ProviderId providerId() {
            return FAKE_PROVIDER;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }

        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }
    }

    private static final class RecordingClientListener implements NetClientListener {
        private int connectedCount;
        private NetConnection connection;

        @Override
        public void connected(NetConnection connection) {
            connectedCount++;
            this.connection = connection;
        }

        @Override
        public void disconnected(NetConnection connection) {
        }

        @Override
        public void message(NetConnection connection, NetPacket packet) {
        }

        @Override
        public void error(Throwable error) {
        }
    }
}
