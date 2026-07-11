package io.github.libfdx.graphics;

import io.github.libfdx.display.Display;

/**
 * Defines the contract for graphics environment implementations.
 *
 * @author xpenatan
 */
public interface GraphicsEnvironment {
    /**
     * Returns the display.
     *
     * @return the display
     */
    Display display();

    /**
     * Returns the native window.
     *
     * @return the native window
     */
    NativeWindow nativeWindow();

    /**
     * Returns a graphics context whose device resources may be shared with the new attachment.
     *
     * @return the shared context, or null when the attachment is independent
     */
    default GraphicsContext sharedContext() {
        return null;
    }
}
