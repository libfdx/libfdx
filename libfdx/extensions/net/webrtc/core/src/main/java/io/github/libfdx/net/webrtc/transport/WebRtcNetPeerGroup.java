package io.github.libfdx.net.webrtc.transport;

import io.github.libfdx.net.transport.NetConnection;
import io.github.libfdx.net.packet.NetPacket;
import io.github.libfdx.net.transport.NetPeerGroup;
import io.github.libfdx.net.transport.NetPeerListener;
import io.github.libfdx.net.webrtc.config.WebRtcPeerConfig;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;

/**
 * WebRTC peer-group endpoint.
 *
 * @author xpenatan
 */
public final class WebRtcNetPeerGroup extends AbstractWebRtcEndpoint implements NetPeerGroup {
    private final NetPeerListener listener;
    private final int maxPeers;

    public WebRtcNetPeerGroup(WebRtcPeerConfig config, NetPeerListener listener, WebRtcPlatformFactory factory) {
        super(config, factory);
        this.listener = listener;
        maxPeers = config.maxPeers();
        start();
    }

    @Override
    public int peerCount() {
        return connectionCountInternal();
    }

    @Override
    public NetConnection peerAt(int index) {
        return connectionAtInternal(index);
    }

    @Override
    protected void endpointReady() {
        if (listener != null) {
            listener.joined(this);
        }
    }

    @Override
    protected void peerJoined(String remotePeerId) {
        if (peerCount() < maxPeers && localPeerId() != null && localPeerId().compareTo(remotePeerId) < 0) {
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
