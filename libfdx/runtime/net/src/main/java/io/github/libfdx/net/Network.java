package io.github.libfdx.net;

import io.github.libfdx.core.FdxService;
import io.github.libfdx.core.ProviderHandle;
import io.github.libfdx.net.http.HttpClient;
import io.github.libfdx.net.transport.NetTransports;
import io.github.libfdx.net.websocket.WebSocketClient;

/**
 * Defines the provider-neutral network service.
 *
 * @author xpenatan
 */
public interface Network extends FdxService, ProviderHandle {
    /**
     * Returns the network capabilities.
     *
     * @return the capabilities
     */
    NetworkCapabilities capabilities();

    /**
     * Returns the HTTP client, or null when unsupported.
     *
     * @return the HTTP client
     */
    HttpClient httpClient();

    /**
     * Returns the WebSocket client, or null when unsupported.
     *
     * @return the WebSocket client
     */
    WebSocketClient webSocketClient();

    /**
     * Returns the multiplayer transport factory, or null when unsupported.
     *
     * @return the transports
     */
    NetTransports transports();
}
