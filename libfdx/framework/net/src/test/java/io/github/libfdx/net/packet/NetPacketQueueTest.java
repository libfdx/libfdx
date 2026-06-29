package io.github.libfdx.net.packet;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.buffer.NetBufferPool;
import io.github.libfdx.net.buffer.NetBufferPoolConfig;
import io.github.libfdx.net.config.NetProcessingConfig;
import io.github.libfdx.net.transform.NetPacketTransform;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetConnectionState;
import io.github.libfdx.net.transport.NetDelivery;
import io.github.libfdx.net.transport.NetSendResult;
import io.github.libfdx.net.transport.NetStats;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests reusable packet queue dispatch limits.
 *
 * @author xpenatan
 */
final class NetPacketQueueTest {
    private static final byte[] ONE_BYTE = new byte[] { 1 };
    private static final ProviderId TEST_PROVIDER = ProviderId.of("test");

    @Test
    void dispatchOccursOnlyWhenProcessBudgetAllowsIt() {
        NetPacketQueue queue = queue(3, NetProcessingConfig.builder()
                .tickRate(1)
                .maxTicksPerFrame(1)
                .maxReceivePacketsPerTick(2)
                .build());
        CountingHandler handler = new CountingHandler();
        FakeConnection connection = new FakeConnection();

        queue.enqueue(connection, 0, NetDelivery.RELIABLE_ORDERED, ONE_BYTE, 0, ONE_BYTE.length);
        queue.enqueue(connection, 0, NetDelivery.RELIABLE_ORDERED, ONE_BYTE, 0, ONE_BYTE.length);
        queue.enqueue(connection, 0, NetDelivery.RELIABLE_ORDERED, ONE_BYTE, 0, ONE_BYTE.length);

        assertEquals(0, queue.dispatch(0.5f, handler));
        assertEquals(0, handler.count);

        assertEquals(2, queue.dispatch(0.5f, handler));
        assertEquals(2, handler.count);
        assertEquals(1, queue.size());

        assertEquals(1, queue.dispatch(1.0f, handler));
        assertEquals(3, handler.count);
        assertSame(connection, handler.lastConnection);
    }

    @Test
    void dispatchHonorsReceiveByteLimit() {
        NetPacketQueue queue = queue(3, NetProcessingConfig.builder()
                .tickRate(1)
                .maxTicksPerFrame(1)
                .maxReceivePacketsPerTick(0)
                .maxReceiveBytesPerTick(2)
                .build());
        CountingHandler handler = new CountingHandler();
        FakeConnection connection = new FakeConnection();

        queue.enqueue(connection, 0, NetDelivery.RELIABLE_ORDERED, ONE_BYTE, 0, ONE_BYTE.length);
        queue.enqueue(connection, 0, NetDelivery.RELIABLE_ORDERED, ONE_BYTE, 0, ONE_BYTE.length);
        queue.enqueue(connection, 0, NetDelivery.RELIABLE_ORDERED, ONE_BYTE, 0, ONE_BYTE.length);

        assertEquals(2, queue.dispatch(1.0f, handler));
        assertEquals(1, queue.size());
    }

    @Test
    void queueReportsReliableBackpressureAndUnreliableDrop() {
        NetStats stats = new NetStats();
        NetPacketQueue queue = new NetPacketQueue(new NetBufferPool(NetBufferPoolConfig.builder()
                .initialPackets(1)
                .maxPackets(1)
                .packetBytes(8)
                .build()), NetProcessingConfig.builder()
                .tickRate(1)
                .maxTicksPerFrame(1)
                .dropUnreliableWhenBehind(true)
                .build(), stats);
        FakeConnection connection = new FakeConnection();

        assertEquals(NetSendResult.QUEUED,
                queue.enqueue(connection, 0, NetDelivery.RELIABLE_ORDERED, ONE_BYTE, 0, ONE_BYTE.length));
        assertEquals(NetSendResult.DROPPED_BACKPRESSURE,
                queue.enqueue(connection, 0, NetDelivery.RELIABLE_ORDERED, ONE_BYTE, 0, ONE_BYTE.length));
        assertEquals(0, stats.droppedUnreliablePackets());

        assertEquals(NetSendResult.DROPPED_BACKPRESSURE,
                queue.enqueue(connection, 1, NetDelivery.UNRELIABLE_UNORDERED, ONE_BYTE, 0, ONE_BYTE.length));
        assertEquals(1, stats.droppedUnreliablePackets());
    }

    private static NetPacketQueue queue(int maxPackets, NetProcessingConfig processing) {
        return new NetPacketQueue(new NetBufferPool(NetBufferPoolConfig.builder()
                .initialPackets(maxPackets)
                .maxPackets(maxPackets)
                .packetBytes(8)
                .build()), processing, new NetStats());
    }

    private static final class CountingHandler implements NetPacketHandler {
        private int count;
        private NetConnection lastConnection;

        @Override
        public void message(NetConnection connection, NetPacket packet) {
            count++;
            lastConnection = connection;
            assertEquals(1, packet.reader().getUnsignedByte());
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
