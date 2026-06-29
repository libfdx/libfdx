package io.github.libfdx.net.http;

import io.github.libfdx.core.FdxFuture;

/**
 * Sends HTTP requests asynchronously.
 *
 * @author xpenatan
 */
public interface HttpClient {
    /**
     * Sends a request.
     *
     * @param request the request
     * @return the response future
     */
    FdxFuture<HttpResponse> send(HttpRequest request);
}
