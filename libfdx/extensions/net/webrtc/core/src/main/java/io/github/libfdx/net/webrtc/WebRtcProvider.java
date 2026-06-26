package io.github.libfdx.net.webrtc;

import io.github.libfdx.core.ProviderId;

/**
 * Shared WebRTC provider identity.
 *
 * @author xpenatan
 */
public final class WebRtcProvider {
    /**
     * Provider ID used by WebRTC network transport configs and providers.
     */
    public static final ProviderId ID = ProviderId.of("webrtc");

    private WebRtcProvider() {
    }
}
