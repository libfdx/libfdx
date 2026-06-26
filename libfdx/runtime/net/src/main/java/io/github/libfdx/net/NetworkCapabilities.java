package io.github.libfdx.net;

import io.github.libfdx.core.ProviderId;

/**
 * Describes supported network features.
 *
 * @author xpenatan
 */
public interface NetworkCapabilities {
    /**
     * Returns whether HTTP is supported.
     *
     * @return true if supported
     */
    boolean supportsHttp();

    /**
     * Returns whether WebSocket is supported.
     *
     * @return true if supported
     */
    boolean supportsWebSocket();

    /**
     * Returns whether message transports are supported.
     *
     * @return true if supported
     */
    boolean supportsTransports();

    /**
     * Returns whether a transport provider is supported.
     *
     * @param providerId the provider ID
     * @return true if supported
     */
    boolean supportsTransport(ProviderId providerId);
}
