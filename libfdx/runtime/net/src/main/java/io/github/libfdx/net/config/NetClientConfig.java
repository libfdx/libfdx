package io.github.libfdx.net.config;

import io.github.libfdx.core.ProviderId;

/**
 * Base configuration for client endpoints.
 *
 * @author xpenatan
 */
public abstract class NetClientConfig extends NetEndpointConfig {
    protected NetClientConfig(Builder<?> builder) {
        super(builder);
    }

    /**
     * Base builder for client configs.
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
