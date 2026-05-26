package io.github.libfdx.input;

import io.github.libfdx.core.FdxService;
import io.github.libfdx.core.ProviderHandle;

public interface Input extends FdxService, ProviderHandle {
    InputCapabilities capabilities();

    void addProcessor(InputProcessor processor);

    void removeProcessor(InputProcessor processor);

    void showTextInput(TextInputRequest request);

    void updateTextInput(TextInputRequest request);

    void hideTextInput();

    boolean isKeyPressed(Key key);

    boolean isMouseButtonPressed(MouseButton button);

    int pointerX();

    int pointerY();

    Cursor cursor();

    Gamepads gamepads();
}
