package io.github.libfdx.net.transform;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.buffer.NetBufferPool;
import io.github.libfdx.net.buffer.NetBufferPoolConfig;
import io.github.libfdx.net.buffer.NetReader;
import io.github.libfdx.net.buffer.NetWriter;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetConnectionState;
import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.transport.NetSendResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests packet transform hooks.
 *
 * @author xpenatan
 */
final class NetPacketTransformTest {
    private static final ProviderId TEST_PROVIDER = ProviderId.of("test");

    @Test
    void transformRoundTripUsesProvidedBuffers() {
        NetBufferPool pool = new NetBufferPool(NetBufferPoolConfig.builder()
                .initialPackets(3)
                .maxPackets(3)
                .packetBytes(32)
                .build());
        NetPacketTransform transform = new XorTransform(0x5a);
        NetTransformContext context = new NetTransformContext().set(TEST_PROVIDER, null, 1,
                NetDelivery.UNRELIABLE_UNORDERED);
        NetBuffer input = pool.acquire();
        NetBuffer encoded = pool.acquire();
        NetBuffer decoded = pool.acquire();

        input.writer().putByte(1).putByte(2).putByte(3);
        input.rewind();

        assertEquals(NetTransformResult.OK, transform.encode(context, input.reader(), encoded.writer()));
        encoded.rewind();
        assertEquals(1 ^ 0x5a, encoded.reader().getUnsignedByte());
        encoded.rewind();

        assertEquals(NetTransformResult.OK, transform.decode(context, encoded.reader(), decoded.writer()));
        decoded.rewind();
        assertEquals(1, decoded.reader().getUnsignedByte());
        assertEquals(2, decoded.reader().getUnsignedByte());
        assertEquals(3, decoded.reader().getUnsignedByte());
        assertEquals(0, decoded.reader().remaining());

        decoded.release();
        encoded.release();
        input.release();
    }

    @Test
    void connectionTransformOverrideCanChangeAtBoundary() {
        TransformConnection connection = new TransformConnection();
        NetPacketTransform first = new XorTransform(1);
        NetPacketTransform second = new XorTransform(2);

        connection.setTransform(1, first);
        assertSame(first, connection.transform(1));

        connection.setTransform(1, second);
        assertSame(second, connection.transform(1));

        connection.setTransform(1, null);
        assertNull(connection.transform(1));
    }

    private static final class XorTransform implements NetPacketTransform {
        private final int key;

        private XorTransform(int key) {
            this.key = key;
        }

        @Override
        public int maxOutputBytes(int inputBytes) {
            return inputBytes;
        }

        @Override
        public NetTransformResult encode(NetTransformContext context, NetReader input, NetWriter output) {
            while (input.remaining() > 0) {
                output.putByte(input.getUnsignedByte() ^ key);
            }
            return NetTransformResult.OK;
        }

        @Override
        public NetTransformResult decode(NetTransformContext context, NetReader input, NetWriter output) {
            return encode(context, input, output);
        }
    }

    private static final class TransformConnection implements NetConnection {
        private final NetPacketTransform[] transforms = new NetPacketTransform[2];

        NetPacketTransform transform(int channelId) {
            return transforms[channelId];
        }

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
            transforms[channelId] = transform;
        }

        @Override
        public void close() {
        }

        @Override
        public ProviderId providerId() {
            return TEST_PROVIDER;
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
}
