package io.github.libfdx.application;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderId;

/**
 * Defines the contract for application backend implementations.
 *
 * @author xpenatan
 */
public interface ApplicationBackend extends Disposable {
    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    ProviderId providerId();

    /**
     * Runs the start step.
     *
     * @param config the configuration
     * @param listener the listener
     */
    void start(ApplicationConfig config, ApplicationListener listener);
}
