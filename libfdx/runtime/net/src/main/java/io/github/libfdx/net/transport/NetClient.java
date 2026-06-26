package io.github.libfdx.net.transport;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;
import io.github.libfdx.net.buffer.NetBufferPool;

/**
 * Represents a client endpoint.
 *
 * @author xpenatan
 */
public interface NetClient extends ProviderHandle, Disposable {
    /**
     * Processes queued network work on the application thread.
     *
     * @param deltaTime the frame delta time in seconds
     */
    void process(float deltaTime);

    /**
     * Returns the server connection, or null when not connected.
     *
     * @return the connection
     */
    NetConnection connection();

    /**
     * Returns whether the client is connected.
     *
     * @return true if connected
     */
    boolean isConnected();

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
}
