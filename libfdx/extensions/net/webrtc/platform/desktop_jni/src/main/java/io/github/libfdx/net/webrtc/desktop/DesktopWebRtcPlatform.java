package io.github.libfdx.net.webrtc.desktop;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.spi.NetworkProvider;
import io.github.libfdx.net.webrtc.transport.WebRtcNetworkProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;
import io.github.libfdx.net.webrtc.WebRtcProvider;

/**
 * Desktop WebRTC platform entry point.
 *
 * @author xpenatan
 */
public final class DesktopWebRtcPlatform {
    private DesktopWebRtcPlatform() {
    }

    public static ProviderId providerId() {
        return WebRtcProvider.ID;
    }

    public static boolean bindingsAvailable() {
        return true;
    }

    public static WebRtcPlatformFactory factory() {
        return new DesktopWebRtcPlatformFactory();
    }

    public static NetworkProvider networkProvider() {
        return new WebRtcNetworkProvider(factory());
    }
}
