package io.github.libfdx.net.transport;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;
import io.github.libfdx.net.buffer.NetBufferPool;

/**
 * Represents a peer-to-peer endpoint.
 *
 * @author xpenatan
 */
public interface NetPeerGroup extends ProviderHandle, Disposable {
    /**
     * Processes queued network work on the application thread.
     *
     * @param deltaTime the frame delta time in seconds
     */
    void process(float deltaTime);

    /**
     * Returns the buffer pool.
     *
     * @return the buffers
     */
    NetBufferPool buffers();

    /**
     * Returns network stats.
     *
     * @return the stats
     */
    NetStats stats();

    /**
     * Returns the number of connected peers.
     *
     * @return the peer count
     */
    int peerCount();

    /**
     * Returns a peer connection by index.
     *
     * @param index the index
     * @return the connection
     */
    NetConnection peerAt(int index);
}
