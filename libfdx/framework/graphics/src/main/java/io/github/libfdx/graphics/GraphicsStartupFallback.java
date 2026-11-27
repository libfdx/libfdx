package io.github.libfdx.graphics;

/** An attachment provider that offers a single alternative if its initial attachment fails. */
public interface GraphicsStartupFallback extends GraphicsAttachmentProvider {
    /** Supplies the non-null alternative on demand, before a ready attachment has been exposed to the application. */
    GraphicsAttachmentProvider fallbackProvider();

    /**
     * Whether the failed attempt may have bound the native window to an incompatible graphics API.
     * Called before disposal with this provider's failed attachment, or null if creation did not complete.
     */
    boolean requiresFreshWindow(GraphicsAttachment failedAttachment);

}
