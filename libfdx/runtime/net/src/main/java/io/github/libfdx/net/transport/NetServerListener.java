package io.github.libfdx.net.transport;

import io.github.libfdx.net.packet.NetPacket;

/**
 * Receives server endpoint events.
 *
 * @author xpenatan
 */
public interface NetServerListener {
    void started(NetServer server);

    void connected(NetConnection connection);

    void disconnected(NetConnection connection);

    void message(NetConnection connection, NetPacket packet);

    void error(Throwable error);
}
