package io.github.libfdx.net.transport;

/**
 * Reports the result of a send attempt.
 *
 * @author xpenatan
 */
public enum NetSendResult {
    SENT,
    QUEUED,
    DROPPED_BACKPRESSURE,
    UNSUPPORTED_DELIVERY,
    NOT_CONNECTED,
    FAILED
}
