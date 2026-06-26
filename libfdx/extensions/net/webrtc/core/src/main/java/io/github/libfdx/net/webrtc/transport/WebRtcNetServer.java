package io.github.libfdx.net.webrtc.transport;

import io.github.libfdx.net.buffer.NetBuffer;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.packet.NetPacket;
import io.github.libfdx.net.transport.NetSendResult;
import io.github.libfdx.net.transport.NetServer;
import io.github.libfdx.net.transport.NetServerListener;
import io.github.libfdx.net.webrtc.config.WebRtcServerConfig;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;

/**
 * WebRTC host/server endpoint.
 *
 * @author xpenatan
 */
public final class WebRtcNetServer extends AbstractWebRtcEndpoint implements NetServer {
    private final NetServerListener listener;
    private final int maxConnections;

    public WebRtcNetServer(WebRtcServerConfig config, NetServerListener listener, WebRtcPlatformFactory factory) {
        super(config, factory);
        this.listener = listener;
        maxConnections = config.maxConnections();
        start();
    }

    @Override
    public int connectionCount() {
        return connectionCountInternal();
    }

    @Override
    public NetConnection connectionAt(int index) {
        return connectionAtInternal(index);
    }

    @Override
    public NetSendResult broadcast(int channelId, NetBuffer buffer) {
        NetSendResult result = NetSendResult.SENT;
        for (int i = 0; i < connectionCount(); i++) {
            NetSendResult sendResult = connectionAt(i).send(channelId, buffer);
            if (sendResult != NetSendResult.SENT) {
                result = sendResult;
            }
        }
        return result;
    }

    @Override
    protected void endpointReady() {
        if (listener != null) {
            listener.started(this);
        }
    }

    @Override
    protected void peerJoined(String remotePeerId) {
        if (connectionCount() < maxConnections) {
            sendConnectRequest(remotePeerId);
        }
    }

    @Override
    protected void dispatchConnected(NetConnection connection) {
        if (listener != null) {
            listener.connected(connection);
        }
    }

    @Override
    protected void dispatchDisconnected(NetConnection connection) {
        if (listener != null) {
            listener.disconnected(connection);
        }
    }

    @Override
    protected void dispatchMessage(NetConnection connection, NetPacket packet) {
        if (listener != null) {
            listener.message(connection, packet);
        }
    }

    @Override
    protected void dispatchError(Throwable error) {
        if (listener != null) {
            listener.error(error);
        }
    }
}
