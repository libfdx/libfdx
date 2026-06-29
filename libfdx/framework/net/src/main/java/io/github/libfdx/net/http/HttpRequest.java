package io.github.libfdx.net.http;

import io.github.libfdx.core.FdxException;

/**
 * Describes an HTTP request.
 *
 * @author xpenatan
 */
public final class HttpRequest {
    private final HttpMethod method;
    private final String url;
    private final HttpHeaders headers;
    private final HttpBody body;

    private HttpRequest(Builder builder) {
        if (builder.method == null) {
            throw new FdxException("HTTP method cannot be null");
        }
        if (builder.url == null || builder.url.isEmpty()) {
            throw new FdxException("HTTP URL cannot be empty");
        }
        method = builder.method;
        url = builder.url;
        headers = builder.headers != null ? builder.headers : new HttpHeaders();
        body = builder.body;
    }

    /**
     * Creates a GET request.
     *
     * @param url the URL
     * @return the request
     */
    public static HttpRequest get(String url) {
        return builder(HttpMethod.GET, url).build();
    }

    /**
     * Creates a POST request.
     *
     * @param url the URL
     * @param body the body
     * @return the request
     */
    public static HttpRequest post(String url, HttpBody body) {
        return builder(HttpMethod.POST, url).body(body).build();
    }

    /**
     * Creates a request builder.
     *
     * @param method the method
     * @param url the URL
     * @return the builder
     */
    public static Builder builder(HttpMethod method, String url) {
        return new Builder().method(method).url(url);
    }

    public HttpMethod method() {
        return method;
    }

    public String url() {
        return url;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public HttpBody body() {
        return body;
    }

    /**
     * Builds HTTP requests.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private HttpMethod method;
        private String url;
        private HttpHeaders headers;
        private HttpBody body;

        private Builder() {
        }

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder headers(HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder header(String name, String value) {
            if (headers == null) {
                headers = new HttpHeaders();
            }
            headers.add(name, value);
            return this;
        }

        public Builder body(HttpBody body) {
            this.body = body;
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(this);
        }
    }
}
