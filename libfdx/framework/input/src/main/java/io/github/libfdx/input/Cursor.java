package io.github.libfdx.input;

/**
 * Defines the contract for cursor implementations.
 *
 * @author xpenatan
 */
public interface Cursor {
    /**
     * Returns whether visible is enabled or true.
     *
     * @return true if visible is enabled or true; false otherwise
     */
    boolean isVisible();

    /**
     * Runs the visible step.
     *
     * @param visible the visible
     */
    void visible(boolean visible);

    /**
     * Returns whether captured is enabled or true.
     *
     * @return true if captured is enabled or true; false otherwise
     */
    boolean isCaptured();

    /**
     * Runs the captured step.
     *
     * @param captured the captured
     */
    void captured(boolean captured);

    /**
     * Returns the shape.
     *
     * @return the shape
     */
    CursorShape shape();

    /**
     * Runs the shape step.
     *
     * @param shape the shape
     */
    void shape(CursorShape shape);
}
