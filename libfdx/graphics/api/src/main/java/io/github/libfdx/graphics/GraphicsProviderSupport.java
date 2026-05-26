package io.github.libfdx.graphics;

public interface GraphicsProviderSupport {
    boolean isSupported();

    String supportFailureReason();
}
