package io.github.libfdx.net.http;

/**
 * Stores an HTTP status.
 *
 * @author xpenatan
 */
public final class HttpStatus {
    private final int code;
    private final String reason;

    /**
     * Creates an HTTP status.
     *
     * @param code the status code
     * @param reason the reason
     */
    public HttpStatus(int code, String reason) {
        this.code = code;
        this.reason = reason != null ? reason : "";
    }

    public int code() {
        return code;
    }

    public String reason() {
        return reason;
    }

    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }
}
