package io.github.libfdx.net.transport;

import io.github.libfdx.net.packet.NetPacket;

/**
 * Receives client endpoint events.
 *
 * @author xpenatan
 */
public interface NetClientListener {
    void connected(NetConnection connection);

    void disconnected(NetConnection connection);

    void message(NetConnection connection, NetPacket packet);

    void error(Throwable error);
}
