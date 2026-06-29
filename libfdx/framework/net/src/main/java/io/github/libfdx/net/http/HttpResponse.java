package io.github.libfdx.net.http;

/**
 * Stores an HTTP response.
 *
 * @author xpenatan
 */
public final class HttpResponse {
    private final HttpStatus status;
    private final HttpHeaders headers;
    private final HttpBody body;

    /**
     * Creates an HTTP response.
     *
     * @param status the status
     * @param headers the headers
     * @param body the body
     */
    public HttpResponse(HttpStatus status, HttpHeaders headers, HttpBody body) {
        this.status = status;
        this.headers = headers != null ? headers : new HttpHeaders();
        this.body = body;
    }

    public HttpStatus status() {
        return status;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public HttpBody body() {
        return body;
    }
}
