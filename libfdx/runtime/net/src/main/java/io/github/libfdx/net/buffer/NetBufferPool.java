package io.github.libfdx.net.buffer;

import io.github.libfdx.core.FdxException;

/**
 * Owns reusable network packet buffers.
 *
 * @author xpenatan
 */
public final class NetBufferPool {
    private final NetBufferPoolConfig config;
    private final NetBuffer[] freeBuffers;
    private int freeCount;
    private int totalCreated;

    /**
     * Creates a buffer pool.
     *
     * @param config the config
     */
    public NetBufferPool(NetBufferPoolConfig config) {
        this.config = config != null ? config : NetBufferPoolConfig.defaults();
        freeBuffers = new NetBuffer[this.config.maxPackets()];
        for (int i = 0; i < this.config.initialPackets(); i++) {
            freeBuffers[freeCount++] = new NetBuffer(this, this.config.packetBytes());
            totalCreated++;
        }
    }

    /**
     * Acquires a buffer or fails clearly when the pool is exhausted.
     *
     * @return the buffer
     */
    public NetBuffer acquire() {
        NetBuffer buffer = tryAcquire();
        if (buffer == null) {
            throw new FdxException("Network buffer pool exhausted");
        }
        return buffer;
    }

    /**
     * Acquires a buffer if one is available.
     *
     * @return the buffer, or null
     */
    public NetBuffer tryAcquire() {
        NetBuffer buffer;
        if (freeCount > 0) {
            buffer = freeBuffers[--freeCount];
            freeBuffers[freeCount] = null;
        } else if (totalCreated < config.maxPackets()) {
            buffer = new NetBuffer(this, config.packetBytes());
            totalCreated++;
        } else {
            return null;
        }
        buffer.acquireFromPool();
        return buffer;
    }

    /**
     * Returns the number of free buffers.
     *
     * @return the free count
     */
    public int freeCount() {
        return freeCount;
    }

    /**
     * Returns the number of created buffers.
     *
     * @return the created count
     */
    public int totalCreated() {
        return totalCreated;
    }

    /**
     * Returns the config.
     *
     * @return the config
     */
    public NetBufferPoolConfig config() {
        return config;
    }

    void release(NetBuffer buffer) {
        if (buffer == null) {
            return;
        }
        if (freeCount >= freeBuffers.length) {
            throw new FdxException("Network buffer pool is already full");
        }
        freeBuffers[freeCount++] = buffer;
    }
}
