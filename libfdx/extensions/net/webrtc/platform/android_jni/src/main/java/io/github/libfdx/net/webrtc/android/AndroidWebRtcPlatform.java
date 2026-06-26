package io.github.libfdx.net.webrtc.android;

import android.content.Context;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.spi.NetworkProvider;
import io.github.libfdx.net.webrtc.transport.WebRtcNetworkProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;
import io.github.libfdx.net.webrtc.WebRtcProvider;

/**
 * Android WebRTC platform entry point.
 *
 * @author xpenatan
 */
public final class AndroidWebRtcPlatform {
    private AndroidWebRtcPlatform() {
    }

    public static ProviderId providerId() {
        return WebRtcProvider.ID;
    }

    public static boolean bindingsAvailable() {
        return true;
    }

    public static WebRtcPlatformFactory factory(Context context) {
        return new AndroidWebRtcPlatformFactory(context);
    }

    public static NetworkProvider networkProvider(Context context) {
        return new WebRtcNetworkProvider(factory(context));
    }
}
