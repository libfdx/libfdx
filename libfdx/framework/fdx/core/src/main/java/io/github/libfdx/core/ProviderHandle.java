package io.github.libfdx.core;

/**
 * Defines a typed handle for provider state.
 *
 * @author xpenatan
 */
public interface ProviderHandle {
    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    ProviderId providerId();

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    <T> T as();
}
