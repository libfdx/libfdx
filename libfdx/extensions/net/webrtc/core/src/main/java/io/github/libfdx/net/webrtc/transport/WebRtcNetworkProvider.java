package io.github.libfdx.net.webrtc.transport;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.spi.DefaultNetTransports;
import io.github.libfdx.net.spi.DefaultNetwork;
import io.github.libfdx.net.Network;
import io.github.libfdx.net.spi.NetworkProvider;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;

/**
 * Creates a network service containing the WebRTC transport provider.
 *
 * @author xpenatan
 */
public final class WebRtcNetworkProvider implements NetworkProvider {
    private final WebRtcPlatformFactory factory;

    public WebRtcNetworkProvider(WebRtcPlatformFactory factory) {
        if (factory == null) {
            throw new FdxException("WebRTC platform factory cannot be null");
        }
        this.factory = factory;
    }

    @Override
    public ProviderId providerId() {
        return factory.providerId();
    }

    @Override
    public Network createNetwork() {
        return new DefaultNetwork(providerId(), null, null,
                new DefaultNetTransports(new WebRtcNetTransportProvider(factory)));
    }
}
