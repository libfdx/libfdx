package io.github.libfdx.net.packet;

import io.github.libfdx.net.transport.NetConnection;

/**
 * Receives packet dispatch from a reusable packet queue.
 *
 * @author xpenatan
 */
public interface NetPacketHandler {
    /**
     * Handles one packet.
     *
     * @param connection the connection
     * @param packet the packet view
     */
    void message(NetConnection connection, NetPacket packet);
}
