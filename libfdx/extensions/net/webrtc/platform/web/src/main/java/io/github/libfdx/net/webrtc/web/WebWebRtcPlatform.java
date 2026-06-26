package io.github.libfdx.net.webrtc.web;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.spi.NetworkProvider;
import io.github.libfdx.net.webrtc.transport.WebRtcNetworkProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;
import io.github.libfdx.net.webrtc.WebRtcProvider;

/**
 * Web WebRTC platform entry point.
 *
 * @author xpenatan
 */
public final class WebWebRtcPlatform {
    private WebWebRtcPlatform() {
    }

    public static ProviderId providerId() {
        return WebRtcProvider.ID;
    }

    public static boolean bindingsAvailable() {
        return WebWebRtcPeerConnectionProvider.supported();
    }

    public static WebRtcPlatformFactory factory() {
        return new WebWebRtcPlatformFactory();
    }

    public static NetworkProvider networkProvider() {
        return new WebRtcNetworkProvider(factory());
    }
}
