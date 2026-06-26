package io.github.libfdx.net.webrtc.android;

import android.content.Context;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.webrtc.platform.WebRtcPeerConnectionProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;
import io.github.libfdx.net.webrtc.WebRtcProvider;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingClient;

/**
 * Android WebRTC factory.
 *
 * @author xpenatan
 */
public final class AndroidWebRtcPlatformFactory implements WebRtcPlatformFactory {
    private final AndroidWebRtcPeerConnectionProvider peerConnections;

    AndroidWebRtcPlatformFactory(Context context) {
        peerConnections = new AndroidWebRtcPeerConnectionProvider(context);
    }

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
        return new AndroidWebRtcSignalingClient();
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
