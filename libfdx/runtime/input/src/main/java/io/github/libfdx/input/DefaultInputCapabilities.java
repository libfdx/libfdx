package io.github.libfdx.input;

/**
 * Provides the default implementation of an input capabilities.
 *
 * @author xpenatan
 */
public final class DefaultInputCapabilities implements InputCapabilities {
    private final boolean keyboard;
    private final boolean pointer;
    private final boolean touch;
    private final boolean textInput;
    private final boolean cursor;
    private final boolean gamepads;

    /**
     * Creates a default input capabilities.
     *
     * @param keyboard the keyboard
     * @param pointer the pointer
     * @param touch the touch
     * @param textInput the text input
     * @param cursor the cursor
     * @param gamepads the gamepads
     */
    public DefaultInputCapabilities(boolean keyboard, boolean pointer, boolean touch, boolean textInput,
            boolean cursor, boolean gamepads) {
        this.keyboard = keyboard;
        this.pointer = pointer;
        this.touch = touch;
        this.textInput = textInput;
        this.cursor = cursor;
        this.gamepads = gamepads;
    }

    /**
     * Creates a default input capabilities.
     *
     * @return a new default input capabilities
     */
    public static DefaultInputCapabilities none() {
        return new DefaultInputCapabilities(false, false, false, false, false, false);
    }

    /**
     * Creates a default input capabilities.
     *
     * @return a new default input capabilities
     */
    public static DefaultInputCapabilities desktop() {
        return new DefaultInputCapabilities(true, true, false, true, true, false);
    }

    /**
     * Creates a default input capabilities.
     *
     * @return a new default input capabilities
     */
    public static DefaultInputCapabilities touch() {
        return new DefaultInputCapabilities(false, true, true, true, false, false);
    }

    /**
     * Returns the supports keyboard.
     *
     * @return true if supports keyboard succeeds or is active; false otherwise
     */
    @Override
    public boolean supportsKeyboard() {
        return keyboard;
    }

    /**
     * Returns the supports pointer.
     *
     * @return true if supports pointer succeeds or is active; false otherwise
     */
    @Override
    public boolean supportsPointer() {
        return pointer;
    }

    /**
     * Returns the supports touch.
     *
     * @return true if supports touch succeeds or is active; false otherwise
     */
    @Override
    public boolean supportsTouch() {
        return touch;
    }

    /**
     * Returns the supports text input.
     *
     * @return true if supports text input succeeds or is active; false otherwise
     */
    @Override
    public boolean supportsTextInput() {
        return textInput;
    }

    /**
     * Returns the supports cursor.
     *
     * @return true if supports cursor succeeds or is active; false otherwise
     */
    @Override
    public boolean supportsCursor() {
        return cursor;
    }

    /**
     * Returns the supports gamepads.
     *
     * @return true if supports gamepads succeeds or is active; false otherwise
     */
    @Override
    public boolean supportsGamepads() {
        return gamepads;
    }
}
