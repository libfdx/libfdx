package io.github.libfdx.application;

import io.github.libfdx.core.ProviderHandle;

public interface Application extends ProviderHandle {
    ApplicationLifecycle lifecycle();

    float deltaTime();

    long frameId();

    void requestExit();
}
