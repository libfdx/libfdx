package io.github.libfdx.net.websocket;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderHandle;

/**
 * Represents an active WebSocket connection.
 *
 * @author xpenatan
 */
public interface WebSocket extends ProviderHandle, Disposable {
    /**
     * Returns whether the socket is open.
     *
     * @return true if open
     */
    boolean isOpen();

    /**
     * Sends text.
     *
     * @param text the text
     * @return the send future
     */
    FdxFuture<Void> sendText(String text);

    /**
     * Sends binary data.
     *
     * @param bytes the bytes
     * @return the send future
     */
    FdxFuture<Void> sendBinary(byte[] bytes);

    /**
     * Closes the socket.
     *
     * @param code the close code
     * @param reason the reason
     * @return the close future
     */
    FdxFuture<Void> close(int code, String reason);
}
