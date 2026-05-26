package io.github.libfdx.application;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderId;

public interface ApplicationBackend extends Disposable {
    ProviderId providerId();

    void start(ApplicationConfig config, ApplicationListener listener);
}
