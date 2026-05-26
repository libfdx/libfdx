package io.github.libfdx.input;

public final class DefaultCursor implements Cursor {
    private boolean visible = true;
    private boolean captured;
    private CursorShape shape = CursorShape.DEFAULT;

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void visible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean isCaptured() {
        return captured;
    }

    @Override
    public void captured(boolean captured) {
        this.captured = captured;
    }

    @Override
    public CursorShape shape() {
        return shape;
    }

    @Override
    public void shape(CursorShape shape) {
        this.shape = shape != null ? shape : CursorShape.DEFAULT;
    }
}
