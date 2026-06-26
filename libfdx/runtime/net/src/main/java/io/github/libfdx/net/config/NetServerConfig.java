package io.github.libfdx.net.config;

import io.github.libfdx.core.ProviderId;

/**
 * Base configuration for server endpoints.
 *
 * @author xpenatan
 */
public abstract class NetServerConfig extends NetEndpointConfig {
    protected NetServerConfig(Builder<?> builder) {
        super(builder);
    }

    /**
     * Base builder for server configs.
     *
     * @param <T> the concrete builder type
     *
     * @author xpenatan
     */
    public abstract static class Builder<T extends Builder<T>> extends NetEndpointConfig.Builder<T> {
        protected Builder(ProviderId providerId) {
            super(providerId);
        }
    }
}
