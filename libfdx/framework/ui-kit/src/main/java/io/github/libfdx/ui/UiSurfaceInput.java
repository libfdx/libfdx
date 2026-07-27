package io.github.libfdx.ui;

import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.TextInputEvent;

/**
 * Receives input for an interactive custom UI surface.
 *
 * <p>Implementations are retained by the custom node until the next
 * composition. Pointer events are callback-scoped borrowed values. Returning
 * {@link UiPointerResult#CAPTURE} routes subsequent move and up operations for
 * that pointer to the surface even when it leaves the surface bounds.</p>
 *
 * @author xpenatan
 */
public interface UiSurfaceInput {
    /**
     * Handles a pointer operation.
     *
     * @param event the callback-scoped pointer event
     * @return the handling and capture result
     */
    default UiPointerResult pointer(UiPointerEvent event) {
        return UiPointerResult.IGNORED;
    }

    /**
     * Handles a key-down operation while the surface is focused.
     *
     * @param event the key event
     * @return true when the operation was handled
     */
    default boolean keyDown(KeyEvent event) {
        return false;
    }

    /**
     * Handles text input while the surface is focused.
     *
     * @param event the text input event
     * @return true when the operation was handled
     */
    default boolean textInput(TextInputEvent event) {
        return false;
    }

    /**
     * Reports a focus transition.
     *
     * @param focused true when the surface gained focus
     */
    default void focusChanged(boolean focused) {
    }
}
