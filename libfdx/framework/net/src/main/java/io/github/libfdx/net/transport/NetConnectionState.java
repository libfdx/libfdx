package io.github.libfdx.net.transport;

/**
 * Lists transport connection states.
 *
 * @author xpenatan
 */
public enum NetConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    FAILED
}
