package io.github.libfdx.application;

import io.github.libfdx.core.ProviderId;

/**
 * Stores configuration values for an application.
 *
 * @author xpenatan
 */
public class ApplicationConfig {
    private ProviderId graphicsProvider;
    private ProviderId audioProvider;
    private ProviderId gamepadProvider;

    /**
     * Returns the graphics provider.
     *
     * @return the graphics provider
     */
    public ProviderId graphicsProvider() {
        return graphicsProvider;
    }

    /**
     * Sets the graphics provider and returns this application config.
     *
     * @param providerId the provider ID
     * @return this application config for chaining
     */
    public ApplicationConfig graphicsProvider(ProviderId providerId) {
        this.graphicsProvider = providerId;
        return this;
    }

    /**
     * Returns the audio provider.
     *
     * @return the audio provider
     */
    public ProviderId audioProvider() {
        return audioProvider;
    }

    /**
     * Sets the audio provider and returns this application config.
     *
     * @param providerId the provider ID
     * @return this application config for chaining
     */
    public ApplicationConfig audioProvider(ProviderId providerId) {
        this.audioProvider = providerId;
        return this;
    }

    /**
     * Returns the gamepad provider.
     *
     * @return the gamepad provider
     */
    public ProviderId gamepadProvider() {
        return gamepadProvider;
    }

    /**
     * Sets the gamepad provider and returns this application config.
     *
     * @param providerId the provider ID
     * @return this application config for chaining
     */
    public ApplicationConfig gamepadProvider(ProviderId providerId) {
        this.gamepadProvider = providerId;
        return this;
    }
}
