package io.github.libfdx.input;

/**
 * Defines the contract for input capabilities implementations.
 *
 * @author xpenatan
 */
public interface InputCapabilities {
    /**
     * Returns the supports keyboard.
     *
     * @return true if supports keyboard succeeds or is active; false otherwise
     */
    boolean supportsKeyboard();

    /**
     * Returns the supports pointer.
     *
     * @return true if supports pointer succeeds or is active; false otherwise
     */
    boolean supportsPointer();

    /**
     * Returns the supports touch.
     *
     * @return true if supports touch succeeds or is active; false otherwise
     */
    boolean supportsTouch();

    /**
     * Returns the supports text input.
     *
     * @return true if supports text input succeeds or is active; false otherwise
     */
    boolean supportsTextInput();

    /**
     * Returns the supports cursor.
     *
     * @return true if supports cursor succeeds or is active; false otherwise
     */
    boolean supportsCursor();

    /**
     * Returns the supports gamepads.
     *
     * @return true if supports gamepads succeeds or is active; false otherwise
     */
    boolean supportsGamepads();
}
