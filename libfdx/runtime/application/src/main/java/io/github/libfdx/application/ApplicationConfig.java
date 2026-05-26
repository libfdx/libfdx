package io.github.libfdx.application;

import io.github.libfdx.core.ProviderId;

public class ApplicationConfig {
    private ProviderId graphicsProvider;
    private ProviderId audioProvider;
    private ProviderId gamepadProvider;

    public ProviderId graphicsProvider() {
        return graphicsProvider;
    }

    public ApplicationConfig graphicsProvider(ProviderId providerId) {
        this.graphicsProvider = providerId;
        return this;
    }

    public ProviderId audioProvider() {
        return audioProvider;
    }

    public ApplicationConfig audioProvider(ProviderId providerId) {
        this.audioProvider = providerId;
        return this;
    }

    public ProviderId gamepadProvider() {
        return gamepadProvider;
    }

    public ApplicationConfig gamepadProvider(ProviderId providerId) {
        this.gamepadProvider = providerId;
        return this;
    }
}
