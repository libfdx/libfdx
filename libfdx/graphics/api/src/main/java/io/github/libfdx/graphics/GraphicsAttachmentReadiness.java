package io.github.libfdx.graphics;

/**
 * Defines the contract for graphics attachment readiness implementations.
 *
 * @author xpenatan
 */
public interface GraphicsAttachmentReadiness {
    /**
     * Returns whether ready is enabled or true.
     *
     * @return true if ready is enabled or true; false otherwise
     */
    boolean isReady();
}
