package io.github.libfdx.input;

public interface Cursor {
    boolean isVisible();

    void visible(boolean visible);

    boolean isCaptured();

    void captured(boolean captured);

    CursorShape shape();

    void shape(CursorShape shape);
}
