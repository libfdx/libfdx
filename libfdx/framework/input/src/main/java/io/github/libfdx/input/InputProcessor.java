package io.github.libfdx.input;

/**
 * Defines the contract for input processor implementations.
 *
 * @author xpenatan
 */
public interface InputProcessor {
    /**
     * Runs the key down step.
     *
     * @param event the event
     * @return true if key down succeeds or is active; false otherwise
     */
    boolean keyDown(KeyEvent event);

    /**
     * Runs the key up step.
     *
     * @param event the event
     * @return true if key up succeeds or is active; false otherwise
     */
    boolean keyUp(KeyEvent event);

    /**
     * Runs the pointer down step.
     *
     * @param event the event
     * @return true if pointer down succeeds or is active; false otherwise
     */
    boolean pointerDown(PointerEvent event);

    /**
     * Runs the pointer up step.
     *
     * @param event the event
     * @return true if pointer up succeeds or is active; false otherwise
     */
    boolean pointerUp(PointerEvent event);

    /**
     * Runs the pointer moved step.
     *
     * @param event the event
     * @return true if pointer moved succeeds or is active; false otherwise
     */
    boolean pointerMoved(PointerEvent event);

    /**
     * Runs the scrolled step.
     *
     * @param event the event
     * @return true if scrolled succeeds or is active; false otherwise
     */
    boolean scrolled(PointerEvent event);

    /**
     * Runs the touch down step.
     *
     * @param event the event
     * @return true if touch down succeeds or is active; false otherwise
     */
    boolean touchDown(TouchEvent event);

    /**
     * Runs the touch up step.
     *
     * @param event the event
     * @return true if touch up succeeds or is active; false otherwise
     */
    boolean touchUp(TouchEvent event);

    /**
     * Runs the touch moved step.
     *
     * @param event the event
     * @return true if touch moved succeeds or is active; false otherwise
     */
    boolean touchMoved(TouchEvent event);

    /**
     * Runs the text input step.
     *
     * @param event the event
     * @return true if text input succeeds or is active; false otherwise
     */
    boolean textInput(TextInputEvent event);
}
