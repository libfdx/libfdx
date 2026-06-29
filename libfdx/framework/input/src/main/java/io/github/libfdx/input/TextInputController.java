package io.github.libfdx.input;

/**
 * Defines the contract for text input controller implementations.
 *
 * @author xpenatan
 */
public interface TextInputController {
    TextInputController NONE = new TextInputController() {
        @Override
        public void showTextInput(TextInputRequest request) {
        }

        @Override
        public void updateTextInput(TextInputRequest request) {
        }

        @Override
        public void hideTextInput() {
        }
    };

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
}
