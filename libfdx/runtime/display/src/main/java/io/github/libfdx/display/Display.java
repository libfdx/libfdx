package io.github.libfdx.display;

import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for display implementations.
 *
 * @author xpenatan
 */
public interface Display extends ProviderHandle {
    /**
     * Returns the title.
     *
     * @return the title
     */
    String title();

    /**
     * Runs the title step.
     *
     * @param title the title
     */
    void title(String title);

    /**
     * Returns the width.
     *
     * @return the width
     */
    int width();

    /**
     * Returns the height.
     *
     * @return the height
     */
    int height();

    /**
     * Returns the framebuffer width.
     *
     * @return the framebuffer width
     */
    int framebufferWidth();

    /**
     * Returns the framebuffer height.
     *
     * @return the framebuffer height
     */
    int framebufferHeight();

    /**
     * Returns the content scale x.
     *
     * @return the content scale x
     */
    default float contentScaleX() {
        return inferredContentScale(framebufferWidth(), width());
    }

    /**
     * Returns the content scale y.
     *
     * @return the content scale y
     */
    default float contentScaleY() {
        return inferredContentScale(framebufferHeight(), height());
    }

    /**
     * Returns the content scale.
     *
     * @return the content scale
     */
    default float contentScale() {
        return Math.max(1.0f, (contentScaleX() + contentScaleY()) * 0.5f);
    }

    private static float inferredContentScale(int framebufferSize, int logicalSize) {
        if (framebufferSize <= 0 || logicalSize <= 0) {
            return 1.0f;
        }
        float scale = framebufferSize / (float) logicalSize;
        return scale > 0.0f && Float.isFinite(scale) ? scale : 1.0f;
    }

    /**
     * Returns the close requested.
     *
     * @return true if close requested succeeds or is active; false otherwise
     */
    boolean closeRequested();

    /**
     * Runs the request close step.
     */
    void requestClose();
}
