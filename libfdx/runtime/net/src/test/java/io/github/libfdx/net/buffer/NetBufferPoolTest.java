package io.github.libfdx.net.buffer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests reusable network buffers.
 *
 * @author xpenatan
 */
final class NetBufferPoolTest {
    @Test
    void releasedBuffersAreReused() {
        NetBufferPool pool = new NetBufferPool(NetBufferPoolConfig.builder()
                .initialPackets(1)
                .maxPackets(1)
                .packetBytes(16)
                .build());

        NetBuffer first = pool.acquire();
        first.writer().putByte(7);
        assertEquals(0, pool.freeCount());

        first.release();
        assertEquals(1, pool.freeCount());

        NetBuffer second = pool.acquire();
        assertSame(first, second);
        assertEquals(0, second.length());
        second.release();
    }

    @Test
    void retainedBufferReturnsToPoolAfterFinalRelease() {
        NetBufferPool pool = new NetBufferPool(NetBufferPoolConfig.builder()
                .initialPackets(1)
                .maxPackets(1)
                .packetBytes(16)
                .build());

        NetBuffer buffer = pool.acquire();
        buffer.retain();

        buffer.release();
        assertEquals(0, pool.freeCount());

        buffer.release();
        assertEquals(1, pool.freeCount());
    }

    @Test
    void poolStopsAtMaxPackets() {
        NetBufferPool pool = new NetBufferPool(NetBufferPoolConfig.builder()
                .initialPackets(0)
                .maxPackets(1)
                .packetBytes(16)
                .build());

        NetBuffer buffer = pool.acquire();

        assertNull(pool.tryAcquire());
        buffer.release();
    }

    @Test
    void readerAndWriterUseReusableBufferStorage() {
        NetBufferPool pool = new NetBufferPool(NetBufferPoolConfig.builder()
                .initialPackets(1)
                .maxPackets(1)
                .packetBytes(32)
                .build());
        NetBuffer buffer = pool.acquire();

        buffer.writer()
                .putByte(255)
                .putShort(0x1234)
                .putInt(0x12345678)
                .putFloat(1.5f);
        buffer.rewind();

        NetReader reader = buffer.reader();
        assertEquals(255, reader.getUnsignedByte());
        assertEquals(0x1234, reader.getUnsignedShort());
        assertEquals(0x12345678, reader.getInt());
        assertEquals(1.5f, reader.getFloat());
        assertEquals(0, reader.remaining());

        buffer.release();
    }
}
