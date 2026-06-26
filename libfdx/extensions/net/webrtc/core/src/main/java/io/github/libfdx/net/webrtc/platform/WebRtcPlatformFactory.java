package io.github.libfdx.net.webrtc.platform;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.webrtc.signaling.WebRtcSignalingClient;

/**
 * Supplies platform WebRTC and signaling implementations without static global factories.
 *
 * @author xpenatan
 */
public interface WebRtcPlatformFactory extends Disposable {
    ProviderId providerId();

    WebRtcPeerConnectionProvider peerConnectionProvider();

    WebRtcSignalingClient signalingClient();
}
