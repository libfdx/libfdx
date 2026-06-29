package io.github.libfdx.net.websocket;

/**
 * Receives WebSocket events.
 *
 * @author xpenatan
 */
public interface WebSocketListener {
    void opened(WebSocket socket);

    void text(WebSocket socket, String message);

    void binary(WebSocket socket, byte[] message);

    void error(WebSocket socket, Throwable error);

    void closed(WebSocket socket, int code, String reason);
}
