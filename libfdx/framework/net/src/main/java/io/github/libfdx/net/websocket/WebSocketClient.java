package io.github.libfdx.net.websocket;

import io.github.libfdx.core.FdxFuture;

/**
 * Opens WebSocket connections asynchronously.
 *
 * @author xpenatan
 */
public interface WebSocketClient {
    /**
     * Connects to a WebSocket.
     *
     * @param config the config
     * @param listener the listener
     * @return the socket future
     */
    FdxFuture<WebSocket> connect(WebSocketConfig config, WebSocketListener listener);
}
