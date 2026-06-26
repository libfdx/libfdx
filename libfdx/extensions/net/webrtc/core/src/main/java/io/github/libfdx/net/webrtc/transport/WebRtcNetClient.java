package io.github.libfdx.net.webrtc.transport;

import io.github.libfdx.net.packet.NetPacket;
import io.github.libfdx.net.transport.NetClient;
import io.github.libfdx.net.transport.NetClientListener;
import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.transport.NetConnectionState;
import io.github.libfdx.net.webrtc.config.WebRtcClientConfig;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;

/**
 * WebRTC client endpoint.
 *
 * @author xpenatan
 */
public final class WebRtcNetClient extends AbstractWebRtcEndpoint implements NetClient {
    private final NetClientListener listener;
    private NetConnection connection;

    public WebRtcNetClient(WebRtcClientConfig config, NetClientListener listener, WebRtcPlatformFactory factory) {
        super(config, factory);
        this.listener = listener;
        start();
    }

    @Override
    public NetConnection connection() {
        return connection;
    }

    @Override
    public boolean isConnected() {
        return connection != null && connection.state() == io.github.libfdx.net.transport.NetConnectionState.CONNECTED;
    }

    @Override
    protected void endpointReady() {
    }

    @Override
    protected void peerJoined(String remotePeerId) {
    }

    @Override
    protected void dispatchConnected(NetConnection connection) {
        this.connection = connection;
        if (listener != null) {
            listener.connected(connection);
        }
    }

    @Override
    protected void dispatchDisconnected(NetConnection connection) {
        if (this.connection == connection) {
            this.connection = null;
        }
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
