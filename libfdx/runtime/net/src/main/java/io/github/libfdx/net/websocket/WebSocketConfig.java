package io.github.libfdx.net.websocket;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.net.http.HttpHeaders;

/**
 * Describes a WebSocket connection.
 *
 * @author xpenatan
 */
public final class WebSocketConfig {
    private final String url;
    private final HttpHeaders headers;
    private final String[] protocols;

    private WebSocketConfig(Builder builder) {
        if (builder.url == null || builder.url.isEmpty()) {
            throw new FdxException("WebSocket URL cannot be empty");
        }
        url = builder.url;
        headers = builder.headers != null ? builder.headers : new HttpHeaders();
        protocols = builder.protocols != null ? builder.protocols.clone() : new String[0];
    }

    public static Builder builder(String url) {
        return new Builder().url(url);
    }

    public String url() {
        return url;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public String[] protocols() {
        return protocols.clone();
    }

    /**
     * Builds WebSocket configs.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private String url;
        private HttpHeaders headers;
        private String[] protocols;

        private Builder() {
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder headers(HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder protocols(String... protocols) {
            this.protocols = protocols != null ? protocols.clone() : null;
            return this;
        }

        public WebSocketConfig build() {
            return new WebSocketConfig(this);
        }
    }
}
