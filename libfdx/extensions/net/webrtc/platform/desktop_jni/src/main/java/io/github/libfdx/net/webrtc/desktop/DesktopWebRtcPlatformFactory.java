package io.github.libfdx.net.webrtc.desktop;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;
import io.github.libfdx.net.webrtc.WebRtcProvider;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingClient;

/**
 * Desktop WebRTC factory.
 *
 * @author xpenatan
 */
public final class DesktopWebRtcPlatformFactory implements WebRtcPlatformFactory {
    private final DesktopWebRtcPeerConnectionProvider peerConnections =
            new DesktopWebRtcPeerConnectionProvider();

    @Override
    public ProviderId providerId() {
        return WebRtcProvider.ID;
    }

    @Override
    public WebRtcPeerConnectionProvider peerConnectionProvider() {
        return peerConnections;
    }

    @Override
    public WebRtcSignalingClient signalingClient() {
        return new DesktopWebRtcSignalingClient();
    }

    public void dispose() {
        peerConnections.dispose();
    }

    @Override
    public boolean isDisposed() {
        return peerConnections.isDisposed();
    }
}
