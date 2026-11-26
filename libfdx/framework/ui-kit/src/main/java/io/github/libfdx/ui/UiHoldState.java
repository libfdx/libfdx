package io.github.libfdx.ui;

/** Application-owned held-button state. Read and consume presses on the UI thread. */
public final class UiHoldState {
    private int pointers;
    private boolean pressed;

    /** Returns whether at least one pointer holds this button. */
    public boolean down() {
        return pointers > 0;
    }

    /** Consumes the press edge, including a tap released between two game updates. */
    public boolean consumePressed() {
        boolean result = pressed;
        pressed = false;
        return result;
    }

    void press() {
        if (pointers++ == 0) pressed = true;
    }

    void release() {
        pointers--;
    }

    void activate() {
        pressed = true;
    }

}
