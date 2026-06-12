package io.github.libfdx.ui;

import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.input.PointerType;
import io.github.libfdx.input.TextInputEvent;
import io.github.libfdx.input.TouchEvent;
import io.github.libfdx.input.TouchPoint;

/**
 * Represents an ui input handler.
 *
 * @author xpenatan
 */
final class UiInputHandler extends InputAdapter {
    private final UiRoot root;

    UiInputHandler(UiRoot root) {
        this.root = root;
    }

    /**
     * Runs the key down step.
     *
     * @param event the event
     * @return true if key down succeeds or is active; false otherwise
     */
    @Override
    public boolean keyDown(KeyEvent event) {
        return root.handleKeyDown(event);
    }

    /**
     * Runs the pointer down step.
     *
     * @param event the event
     * @return true if pointer down succeeds or is active; false otherwise
     */
    @Override
    public boolean pointerDown(PointerEvent event) {
        return root.handlePointerDown(event);
    }

    /**
     * Runs the pointer up step.
     *
     * @param event the event
     * @return true if pointer up succeeds or is active; false otherwise
     */
    @Override
    public boolean pointerUp(PointerEvent event) {
        return root.handlePointerUp(event);
    }

    /**
     * Runs the pointer moved step.
     *
     * @param event the event
     * @return true if pointer moved succeeds or is active; false otherwise
     */
    @Override
    public boolean pointerMoved(PointerEvent event) {
        return root.handlePointerMoved(event);
    }

    /**
     * Runs the scrolled step.
     *
     * @param event the event
     * @return true if scrolled succeeds or is active; false otherwise
     */
    @Override
    public boolean scrolled(PointerEvent event) {
        return root.handleScrolled(event);
    }

    /**
     * Runs the touch down step.
     *
     * @param event the event
     * @return true if touch down succeeds or is active; false otherwise
     */
    @Override
    public boolean touchDown(TouchEvent event) {
        return root.handlePointerDown(touchPointer(event, MouseButton.LEFT));
    }

    /**
     * Runs the touch up step.
     *
     * @param event the event
     * @return true if touch up succeeds or is active; false otherwise
     */
    @Override
    public boolean touchUp(TouchEvent event) {
        return root.handlePointerUp(touchPointer(event, MouseButton.LEFT));
    }

    /**
     * Runs the touch moved step.
     *
     * @param event the event
     * @return true if touch moved succeeds or is active; false otherwise
     */
    @Override
    public boolean touchMoved(TouchEvent event) {
        return root.handlePointerMoved(touchPointer(event, MouseButton.UNKNOWN));
    }

    /**
     * Runs the text input step.
     *
     * @param event the event
     * @return true if text input succeeds or is active; false otherwise
     */
    @Override
    public boolean textInput(TextInputEvent event) {
        return root.handleTextInput(event);
    }

    private static PointerEvent touchPointer(TouchEvent event, MouseButton button) {
        TouchPoint point = event.point();
        return new PointerEvent(event.timeNanos(), point.id(), PointerType.TOUCH, button, point.x(), point.y(), 0.0f,
                0.0f);
    }
}
