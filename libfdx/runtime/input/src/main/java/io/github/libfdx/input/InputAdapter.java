package io.github.libfdx.input;

/**
 * Provides default behavior for input callbacks.
 *
 * @author xpenatan
 */
public class InputAdapter implements InputProcessor {
    /**
     * Runs the key down step.
     *
     * @param event the event
     * @return true if key down succeeds or is active; false otherwise
     */
    @Override
    public boolean keyDown(KeyEvent event) {
        return false;
    }

    /**
     * Runs the key up step.
     *
     * @param event the event
     * @return true if key up succeeds or is active; false otherwise
     */
    @Override
    public boolean keyUp(KeyEvent event) {
        return false;
    }

    /**
     * Runs the pointer down step.
     *
     * @param event the event
     * @return true if pointer down succeeds or is active; false otherwise
     */
    @Override
    public boolean pointerDown(PointerEvent event) {
        return false;
    }

    /**
     * Runs the pointer up step.
     *
     * @param event the event
     * @return true if pointer up succeeds or is active; false otherwise
     */
    @Override
    public boolean pointerUp(PointerEvent event) {
        return false;
    }

    /**
     * Runs the pointer moved step.
     *
     * @param event the event
     * @return true if pointer moved succeeds or is active; false otherwise
     */
    @Override
    public boolean pointerMoved(PointerEvent event) {
        return false;
    }

    /**
     * Runs the scrolled step.
     *
     * @param event the event
     * @return true if scrolled succeeds or is active; false otherwise
     */
    @Override
    public boolean scrolled(PointerEvent event) {
        return false;
    }

    /**
     * Runs the touch down step.
     *
     * @param event the event
     * @return true if touch down succeeds or is active; false otherwise
     */
    @Override
    public boolean touchDown(TouchEvent event) {
        return false;
    }

    /**
     * Runs the touch up step.
     *
     * @param event the event
     * @return true if touch up succeeds or is active; false otherwise
     */
    @Override
    public boolean touchUp(TouchEvent event) {
        return false;
    }

    /**
     * Runs the touch moved step.
     *
     * @param event the event
     * @return true if touch moved succeeds or is active; false otherwise
     */
    @Override
    public boolean touchMoved(TouchEvent event) {
        return false;
    }

    /**
     * Runs the text input step.
     *
     * @param event the event
     * @return true if text input succeeds or is active; false otherwise
     */
    @Override
    public boolean textInput(TextInputEvent event) {
        return false;
    }
}
