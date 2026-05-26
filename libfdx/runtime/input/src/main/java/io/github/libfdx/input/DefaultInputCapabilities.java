package io.github.libfdx.input;

public final class DefaultInputCapabilities implements InputCapabilities {
    private final boolean keyboard;
    private final boolean pointer;
    private final boolean touch;
    private final boolean textInput;
    private final boolean cursor;
    private final boolean gamepads;

    public DefaultInputCapabilities(boolean keyboard, boolean pointer, boolean touch, boolean textInput,
            boolean cursor, boolean gamepads) {
        this.keyboard = keyboard;
        this.pointer = pointer;
        this.touch = touch;
        this.textInput = textInput;
        this.cursor = cursor;
        this.gamepads = gamepads;
    }

    public static DefaultInputCapabilities none() {
        return new DefaultInputCapabilities(false, false, false, false, false, false);
    }

    public static DefaultInputCapabilities desktop() {
        return new DefaultInputCapabilities(true, true, false, true, true, false);
    }

    public static DefaultInputCapabilities touch() {
        return new DefaultInputCapabilities(false, true, true, true, false, false);
    }

    @Override
    public boolean supportsKeyboard() {
        return keyboard;
    }

    @Override
    public boolean supportsPointer() {
        return pointer;
    }

    @Override
    public boolean supportsTouch() {
        return touch;
    }

    @Override
    public boolean supportsTextInput() {
        return textInput;
    }

    @Override
    public boolean supportsCursor() {
        return cursor;
    }

    @Override
    public boolean supportsGamepads() {
        return gamepads;
    }
}
