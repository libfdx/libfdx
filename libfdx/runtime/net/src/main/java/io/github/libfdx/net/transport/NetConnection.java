package io.github.libfdx.net.transport;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;
import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.transform.NetPacketTransform;

/**
 * Represents a connection to one remote endpoint.
 *
 * @author xpenatan
 */
public interface NetConnection extends ProviderHandle, Disposable {
    /**
     * Returns the local connection ID.
     *
     * @return the ID
     */
    int id();

    /**
     * Returns the connection state.
     *
     * @return the state
     */
    NetConnectionState state();

    /**
     * Sends a pooled packet.
     *
     * @param channelId the channel ID
     * @param buffer the packet buffer
     * @return the send result
     */
    NetSendResult send(int channelId, NetBuffer buffer);

    /**
     * Sends bytes.
     *
     * @param channelId the channel ID
     * @param bytes the bytes
     * @param offset the offset
     * @param length the length
     * @return the send result
     */
    NetSendResult send(int channelId, byte[] bytes, int offset, int length);

    /**
     * Sets or clears a transform override for a channel.
     *
     * @param channelId the channel ID
     * @param transform the transform, or null
     */
    void setTransform(int channelId, NetPacketTransform transform);

    /**
     * Closes this connection.
     */
    void close();
}
