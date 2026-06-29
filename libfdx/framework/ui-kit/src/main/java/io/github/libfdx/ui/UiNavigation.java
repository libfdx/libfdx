package io.github.libfdx.ui;

/**
 * Represents an ui navigation.
 *
 * @author xpenatan
 */
public final class UiNavigation {
    private final boolean keyboard;
    private final boolean gamepad;
    private final boolean pointer;

    private UiNavigation(boolean keyboard, boolean gamepad, boolean pointer) {
        this.keyboard = keyboard;
        this.gamepad = gamepad;
        this.pointer = pointer;
    }

    /**
     * Creates an UI navigation.
     *
     * @return a new UI navigation
     */
    public static UiNavigation all() {
        return new UiNavigation(true, true, true);
    }

    /**
     * Sets the keyboard and returns this UI navigation.
     *
     * @param keyboard the keyboard
     * @return this UI navigation for chaining
     */
    public UiNavigation keyboard(boolean keyboard) {
        return new UiNavigation(keyboard, gamepad, pointer);
    }

    /**
     * Sets the gamepad and returns this UI navigation.
     *
     * @param gamepad the gamepad
     * @return this UI navigation for chaining
     */
    public UiNavigation gamepad(boolean gamepad) {
        return new UiNavigation(keyboard, gamepad, pointer);
    }

    /**
     * Sets the pointer and returns this UI navigation.
     *
     * @param pointer the pointer
     * @return this UI navigation for chaining
     */
    public UiNavigation pointer(boolean pointer) {
        return new UiNavigation(keyboard, gamepad, pointer);
    }

    /**
     * Returns the keyboard.
     *
     * @return true if keyboard succeeds or is active; false otherwise
     */
    public boolean keyboard() {
        return keyboard;
    }

    /**
     * Returns the gamepad.
     *
     * @return true if gamepad succeeds or is active; false otherwise
     */
    public boolean gamepad() {
        return gamepad;
    }

    /**
     * Returns the pointer.
     *
     * @return true if pointer succeeds or is active; false otherwise
     */
    public boolean pointer() {
        return pointer;
    }
}
