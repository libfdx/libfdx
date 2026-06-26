package io.github.libfdx.net.spi;

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

/**
 * Dispatches transport creation to registered providers.
 *
 * @author xpenatan
 */
public final class DefaultNetTransports implements NetTransports {
    private final NetTransportProvider[] providers;

    /**
     * Creates default transports.
     *
     * @param providers the providers
     */
    public DefaultNetTransports(NetTransportProvider... providers) {
        this.providers = providers != null ? providers.clone() : new NetTransportProvider[0];
    }

    @Override
    public boolean supports(ProviderId providerId) {
        return find(providerId) != null;
    }

    @Override
    public NetClient connect(NetClientConfig config, NetClientListener listener) {
        if (config == null) {
            throw new FdxException("NetClientConfig cannot be null");
        }
        NetTransportProvider provider = require(config.providerId());
        if (!provider.supportsClient()) {
            throw new FdxException("Network transport provider does not support clients: " + config.providerId());
        }
        return provider.connect(config, listener);
    }

    @Override
    public NetServer listen(NetServerConfig config, NetServerListener listener) {
        if (config == null) {
            throw new FdxException("NetServerConfig cannot be null");
        }
        NetTransportProvider provider = require(config.providerId());
        if (!provider.supportsServer()) {
            throw new FdxException("Network transport provider does not support servers: " + config.providerId());
        }
        return provider.listen(config, listener);
    }

    @Override
    public NetPeerGroup join(NetPeerConfig config, NetPeerListener listener) {
        if (config == null) {
            throw new FdxException("NetPeerConfig cannot be null");
        }
        NetTransportProvider provider = require(config.providerId());
        if (!provider.supportsPeerGroup()) {
            throw new FdxException("Network transport provider does not support peer groups: " + config.providerId());
        }
        return provider.join(config, listener);
    }

    private NetTransportProvider require(ProviderId providerId) {
        NetTransportProvider provider = find(providerId);
        if (provider == null) {
            throw new FdxException("Network transport provider is not supported: " + providerId);
        }
        return provider;
    }

    private NetTransportProvider find(ProviderId providerId) {
        if (providerId == null) {
            return null;
        }
        for (int i = 0; i < providers.length; i++) {
            NetTransportProvider provider = providers[i];
            if (provider != null && providerId.equals(provider.providerId())) {
                return provider;
            }
        }
        return null;
    }
}
