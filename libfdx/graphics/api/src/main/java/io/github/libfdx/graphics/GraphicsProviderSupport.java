package io.github.libfdx.graphics;

/**
 * Defines the contract for graphics provider support implementations.
 *
 * @author xpenatan
 */
public interface GraphicsProviderSupport {
    /**
     * Returns whether supported is enabled or true.
     *
     * @return true if supported is enabled or true; false otherwise
     */
    boolean isSupported();

    /**
     * Returns the support failure reason.
     *
     * @return the support failure reason
     */
    String supportFailureReason();
}
