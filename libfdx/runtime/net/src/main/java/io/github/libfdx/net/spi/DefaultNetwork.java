package io.github.libfdx.net.spi;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.net.http.HttpClient;
import io.github.libfdx.net.Network;
import io.github.libfdx.net.NetworkCapabilities;
import io.github.libfdx.net.transport.NetTransports;
import io.github.libfdx.net.websocket.WebSocketClient;

/**
 * Provides a basic immutable network service implementation.
 *
 * @author xpenatan
 */
public final class DefaultNetwork implements Network {
    private final ProviderId providerId;
    private final HttpClient httpClient;
    private final WebSocketClient webSocketClient;
    private final NetTransports transports;
    private final NetworkCapabilities capabilities;

    /**
     * Creates a default network service.
     *
     * @param providerId the provider ID
     * @param httpClient the HTTP client
     * @param webSocketClient the WebSocket client
     * @param transports the transport factory
     */
    public DefaultNetwork(ProviderId providerId, HttpClient httpClient, WebSocketClient webSocketClient,
            NetTransports transports) {
        if (providerId == null) {
            throw new FdxException("Network provider ID cannot be null");
        }
        this.providerId = providerId;
        this.httpClient = httpClient;
        this.webSocketClient = webSocketClient;
        this.transports = transports;
        this.capabilities = new DefaultNetworkCapabilities(httpClient != null, webSocketClient != null, transports);
    }

    @Override
    public NetworkCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public HttpClient httpClient() {
        return httpClient;
    }

    @Override
    public WebSocketClient webSocketClient() {
        return webSocketClient;
    }

    @Override
    public NetTransports transports() {
        return transports;
    }

    @Override
    public ProviderId providerId() {
        return providerId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }
}
