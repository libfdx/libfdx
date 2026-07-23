package io.github.libfdx.input;

import io.github.libfdx.core.FdxService;
import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for input implementations.
 *
 * @author xpenatan
 */
public interface Input extends FdxService, ProviderHandle {
    /**
     * Returns the capabilities.
     *
     * @return the capabilities
     */
    InputCapabilities capabilities();

    /**
     * Adds the processor.
     *
     * @param processor the processor
     */
    void addProcessor(InputProcessor processor);

    /**
     * Removes the processor.
     *
     * @param processor the processor
     */
    void removeProcessor(InputProcessor processor);

    /**
     * Runs the show text input step.
     *
     * @param request the request
     */
    void showTextInput(TextInputRequest request);

    /**
     * Runs the update text input step.
     *
     * @param request the request
     */
    void updateTextInput(TextInputRequest request);

    /**
     * Runs the hide text input step.
     */
    void hideTextInput();

    /**
     * Returns the text clipboard used by this input backend.
     *
     * @return the clipboard
     */
    Clipboard clipboard();

    /**
     * Returns whether key pressed is enabled or true.
     *
     * @param key the key
     * @return true if key pressed is enabled or true; false otherwise
     */
    boolean isKeyPressed(Key key);

    /**
     * Returns whether mouse button pressed is enabled or true.
     *
     * @param button the button
     * @return true if mouse button pressed is enabled or true; false otherwise
     */
    boolean isMouseButtonPressed(MouseButton button);

    /**
     * Returns the pointer x.
     *
     * @return the pointer x
     */
    int pointerX();

    /**
     * Returns the pointer y.
     *
     * @return the pointer y
     */
    int pointerY();

    /**
     * Returns the pointer x position in screen coordinates.
     *
     * @return the screen x position
     */
    default int pointerScreenX() {
        return pointerX();
    }

    /**
     * Returns the pointer y position in screen coordinates.
     *
     * @return the screen y position
     */
    default int pointerScreenY() {
        return pointerY();
    }

    /**
     * Returns the cursor.
     *
     * @return the cursor
     */
    Cursor cursor();

    /**
     * Returns the gamepads.
     *
     * @return the gamepads
     */
    Gamepads gamepads();
}
