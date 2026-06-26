package io.github.libfdx.net.webrtc.transport;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.config.NetClientConfig;
import io.github.libfdx.net.config.NetPeerConfig;
import io.github.libfdx.net.config.NetServerConfig;
import io.github.libfdx.net.transport.NetClient;
import io.github.libfdx.net.transport.NetClientListener;
import io.github.libfdx.net.transport.NetPeerGroup;
import io.github.libfdx.net.transport.NetPeerListener;
import io.github.libfdx.net.transport.NetServer;
import io.github.libfdx.net.transport.NetServerListener;
import io.github.libfdx.net.transport.NetTransportProvider;
import io.github.libfdx.net.transport.NetTransports;
import io.github.libfdx.net.webrtc.config.WebRtcClientConfig;
import io.github.libfdx.net.webrtc.config.WebRtcPeerConfig;
import io.github.libfdx.net.webrtc.config.WebRtcServerConfig;
import io.github.libfdx.net.webrtc.platform.WebRtcPlatformFactory;
import io.github.libfdx.net.webrtc.WebRtcProvider;

/**
 * WebRTC NetTransports provider.
 *
 * @author xpenatan
 */
public final class WebRtcNetTransportProvider implements NetTransportProvider {
    private final WebRtcPlatformFactory factory;

    public WebRtcNetTransportProvider(WebRtcPlatformFactory factory) {
        if (factory == null) {
            throw new FdxException("WebRTC platform factory cannot be null");
        }
        this.factory = factory;
    }

    @Override
    public ProviderId providerId() {
        return WebRtcProvider.ID;
    }

    @Override
    public boolean supportsClient() {
        return factory.peerConnectionProvider().isSupported();
    }

    @Override
    public boolean supportsServer() {
        return factory.peerConnectionProvider().isSupported();
    }

    @Override
    public boolean supportsPeerGroup() {
        return factory.peerConnectionProvider().isSupported();
    }

    @Override
    public NetClient connect(NetClientConfig config, NetClientListener listener) {
        if (!(config instanceof WebRtcClientConfig)) {
            throw new FdxException("WebRTC client requires WebRtcClientConfig");
        }
        return new WebRtcNetClient((WebRtcClientConfig) config, listener, factory);
    }

    @Override
    public NetServer listen(NetServerConfig config, NetServerListener listener) {
        if (!(config instanceof WebRtcServerConfig)) {
            throw new FdxException("WebRTC server requires WebRtcServerConfig");
        }
        return new WebRtcNetServer((WebRtcServerConfig) config, listener, factory);
    }

    @Override
    public NetPeerGroup join(NetPeerConfig config, NetPeerListener listener) {
        if (!(config instanceof WebRtcPeerConfig)) {
            throw new FdxException("WebRTC peer group requires WebRtcPeerConfig");
        }
        return new WebRtcNetPeerGroup((WebRtcPeerConfig) config, listener, factory);
    }
}
