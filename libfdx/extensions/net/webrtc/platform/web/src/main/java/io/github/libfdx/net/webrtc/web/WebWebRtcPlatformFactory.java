package io.github.libfdx.net.webrtc.web;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;
import io.github.libfdx.net.webrtc.WebRtcProvider;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingClient;

/**
 * Web WebRTC factory.
 *
 * @author xpenatan
 */
public final class WebWebRtcPlatformFactory implements WebRtcPlatformFactory {
    private final WebWebRtcPeerConnectionProvider peerConnections = new WebWebRtcPeerConnectionProvider();

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
        return new WebWebRtcSignalingClient();
    }

    @Override
    public void dispose() {
        peerConnections.dispose();
    }

    @Override
    public boolean isDisposed() {
        return peerConnections.isDisposed();
    }
}
