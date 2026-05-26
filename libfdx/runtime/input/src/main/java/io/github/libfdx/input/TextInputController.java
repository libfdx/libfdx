package io.github.libfdx.input;

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

    void showTextInput(TextInputRequest request);

    void updateTextInput(TextInputRequest request);

    void hideTextInput();
}
