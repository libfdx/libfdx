package io.github.libfdx.display;

import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for display implementations.
 *
 * @author xpenatan
 */
public interface Display extends ProviderHandle {
    /**
     * Returns the display x position in screen coordinates.
     *
     * @return the x position
     */
    default int x() {
        return 0;
    }

    /**
     * Returns the display y position in screen coordinates.
     *
     * @return the y position
     */
    default int y() {
        return 0;
    }

    /**
     * Sets the display position in screen coordinates.
     *
     * @param x the x position
     * @param y the y position
     */
    default void position(int x, int y) {
        throw new UnsupportedOperationException("This display does not support changing position");
    }

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
     * Sets the logical display size.
     *
     * @param width the width
     * @param height the height
     */
    default void size(int width, int height) {
        throw new UnsupportedOperationException("This display does not support changing size");
    }

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

    /**
     * Returns the monitor x position in screen coordinates.
     *
     * @return the monitor x position
     */
    default int monitorX() {
        return x();
    }

    /**
     * Returns the monitor y position in screen coordinates.
     *
     * @return the monitor y position
     */
    default int monitorY() {
        return y();
    }

    /**
     * Returns the monitor width.
     *
     * @return the monitor width
     */
    default int monitorWidth() {
        return width();
    }

    /**
     * Returns the monitor height.
     *
     * @return the monitor height
     */
    default int monitorHeight() {
        return height();
    }

    /**
     * Returns the monitor work-area x position.
     *
     * @return the work-area x position
     */
    default int workAreaX() {
        return monitorX();
    }

    /**
     * Returns the monitor work-area y position.
     *
     * @return the work-area y position
     */
    default int workAreaY() {
        return monitorY();
    }

    /**
     * Returns the monitor work-area width.
     *
     * @return the work-area width
     */
    default int workAreaWidth() {
        return monitorWidth();
    }

    /**
     * Returns the monitor work-area height.
     *
     * @return the work-area height
     */
    default int workAreaHeight() {
        return monitorHeight();
    }

    /**
     * Makes the display visible.
     */
    default void show() {
    }

    /**
     * Requests input focus for the display.
     */
    default void focus() {
    }

    /**
     * Returns whether the display has input focus.
     *
     * @return true when focused
     */
    default boolean focused() {
        return true;
    }

    /**
     * Returns whether the display is minimized.
     *
     * @return true when minimized
     */
    default boolean minimized() {
        return false;
    }

    /**
     * Sets the display opacity.
     *
     * @param opacity the opacity in the range 0 to 1
     */
    default void opacity(float opacity) {
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
