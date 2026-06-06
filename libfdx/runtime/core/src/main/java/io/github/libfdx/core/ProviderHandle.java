package io.github.libfdx.core;

public interface ProviderHandle {
    ProviderId providerId();

    <T> T as();
}
