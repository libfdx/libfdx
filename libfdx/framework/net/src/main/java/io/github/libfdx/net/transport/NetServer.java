package io.github.libfdx.net.transport;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;
import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.buffer.NetBufferPool;

/**
 * Represents a server endpoint.
 *
 * @author xpenatan
 */
public interface NetServer extends ProviderHandle, Disposable {
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
     * Returns the number of connected clients.
     *
     * @return the connection count
     */
    int connectionCount();

    /**
     * Returns a connection by index.
     *
     * @param index the index
     * @return the connection
     */
    NetConnection connectionAt(int index);

    /**
     * Broadcasts a pooled packet.
     *
     * @param channelId the channel ID
     * @param buffer the buffer
     * @return the send result
     */
    NetSendResult broadcast(int channelId, NetBuffer buffer);
}
