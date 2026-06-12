package io.github.libfdx.input;

/**
 * Provides the default implementation of a cursor.
 *
 * @author xpenatan
 */
public final class DefaultCursor implements Cursor {
    private boolean visible = true;
    private boolean captured;
    private CursorShape shape = CursorShape.DEFAULT;

    /**
     * Returns whether visible is enabled or true.
     *
     * @return true if visible is enabled or true; false otherwise
     */
    @Override
    public boolean isVisible() {
        return visible;
    }

    /**
     * Runs the visible step.
     *
     * @param visible the visible
     */
    @Override
    public void visible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Returns whether captured is enabled or true.
     *
     * @return true if captured is enabled or true; false otherwise
     */
    @Override
    public boolean isCaptured() {
        return captured;
    }

    /**
     * Runs the captured step.
     *
     * @param captured the captured
     */
    @Override
    public void captured(boolean captured) {
        this.captured = captured;
    }

    /**
     * Returns the shape.
     *
     * @return the shape
     */
    @Override
    public CursorShape shape() {
        return shape;
    }

    /**
     * Runs the shape step.
     *
     * @param shape the shape
     */
    @Override
    public void shape(CursorShape shape) {
        this.shape = shape != null ? shape : CursorShape.DEFAULT;
    }
}
