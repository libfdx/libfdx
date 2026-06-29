package io.github.libfdx.net.spi;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.NetworkCapabilities;
import io.github.libfdx.net.transport.NetTransports;

/**
 * Provides a simple immutable network capability set.
 *
 * @author xpenatan
 */
public final class DefaultNetworkCapabilities implements NetworkCapabilities {
    private final boolean http;
    private final boolean webSocket;
    private final NetTransports transports;

    /**
     * Creates default network capabilities.
     *
     * @param http whether HTTP is supported
     * @param webSocket whether WebSocket is supported
     * @param transports the transport factory
     */
    public DefaultNetworkCapabilities(boolean http, boolean webSocket, NetTransports transports) {
        this.http = http;
        this.webSocket = webSocket;
        this.transports = transports;
    }

    @Override
    public boolean supportsHttp() {
        return http;
    }

    @Override
    public boolean supportsWebSocket() {
        return webSocket;
    }

    @Override
    public boolean supportsTransports() {
        return transports != null;
    }

    @Override
    public boolean supportsTransport(ProviderId providerId) {
        return transports != null && transports.supports(providerId);
    }
}
