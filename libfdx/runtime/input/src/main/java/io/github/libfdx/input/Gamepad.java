package io.github.libfdx.input;

import io.github.libfdx.core.ProviderHandle;

public interface Gamepad extends ProviderHandle {
    String id();

    String name();

    int index();

    boolean isConnected();

    GamepadMapping mapping();

    GamepadState state();

    float axis(GamepadAxis axis);

    boolean pressed(GamepadButton button);
}
