package io.github.libfdx.net.packet;

import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.buffer.NetReader;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetDelivery;

/**
 * Represents a received network packet.
 *
 * @author xpenatan
 */
public final class NetPacket {
    private NetConnection connection;
    private NetBuffer buffer;
    private int channelId;
    private NetDelivery delivery;

    /**
     * Creates a packet view.
     */
    public NetPacket() {
    }

    /**
     * Sets this packet view.
     *
     * @param connection the connection
     * @param channelId the channel ID
     * @param delivery the delivery mode
     * @param buffer the buffer
     * @return this packet
     */
    public NetPacket set(NetConnection connection, int channelId, NetDelivery delivery, NetBuffer buffer) {
        this.connection = connection;
        this.channelId = channelId;
        this.delivery = delivery;
        this.buffer = buffer;
        if (buffer != null) {
            buffer.rewind();
        }
        return this;
    }

    public NetConnection connection() {
        return connection;
    }

    public int channelId() {
        return channelId;
    }

    public NetDelivery delivery() {
        return delivery;
    }

    public NetReader reader() {
        return buffer.reader();
    }

    public NetBuffer buffer() {
        return buffer;
    }

    public int length() {
        return buffer.length();
    }

    /**
     * Retains the backing buffer beyond the current callback.
     *
     * @return this packet
     */
    public NetPacket retain() {
        buffer.retain();
        return this;
    }

    /**
     * Releases the backing buffer.
     */
    public void release() {
        buffer.release();
    }
}
