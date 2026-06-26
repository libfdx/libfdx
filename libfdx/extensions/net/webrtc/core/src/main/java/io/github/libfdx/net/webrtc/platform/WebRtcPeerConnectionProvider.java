package io.github.libfdx.net.webrtc.platform;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.net.webrtc.config.WebRtcEndpointSettings;

/**
 * Creates provider-native peer connections.
 *
 * @author xpenatan
 */
public interface WebRtcPeerConnectionProvider extends Disposable {
    WebRtcPeerConnection createPeerConnection(WebRtcEndpointSettings settings,
            WebRtcPeerConnectionListener listener);

    boolean isSupported();

    void close();
}
