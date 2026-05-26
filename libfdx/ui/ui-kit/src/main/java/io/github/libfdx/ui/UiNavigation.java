package io.github.libfdx.ui;

public final class UiNavigation {
    private final boolean keyboard;
    private final boolean gamepad;
    private final boolean pointer;

    private UiNavigation(boolean keyboard, boolean gamepad, boolean pointer) {
        this.keyboard = keyboard;
        this.gamepad = gamepad;
        this.pointer = pointer;
    }

    public static UiNavigation all() {
        return new UiNavigation(true, true, true);
    }

    public UiNavigation keyboard(boolean keyboard) {
        return new UiNavigation(keyboard, gamepad, pointer);
    }

    public UiNavigation gamepad(boolean gamepad) {
        return new UiNavigation(keyboard, gamepad, pointer);
    }

    public UiNavigation pointer(boolean pointer) {
        return new UiNavigation(keyboard, gamepad, pointer);
    }

    public boolean keyboard() {
        return keyboard;
    }

    public boolean gamepad() {
        return gamepad;
    }

    public boolean pointer() {
        return pointer;
    }
}
