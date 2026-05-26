package io.github.libfdx.input;

public interface InputCapabilities {
    boolean supportsKeyboard();

    boolean supportsPointer();

    boolean supportsTouch();

    boolean supportsTextInput();

    boolean supportsCursor();

    boolean supportsGamepads();
}
