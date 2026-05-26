package io.github.libfdx.input;

import io.github.libfdx.core.ProviderHandle;
import java.util.List;

public interface Gamepads extends ProviderHandle {
    List<Gamepad> connected();

    Gamepad find(int index);

    void addListener(GamepadListener listener);

    void removeListener(GamepadListener listener);
}
