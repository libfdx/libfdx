package io.github.libfdx.input;

public interface GamepadListener {
    void connected(Gamepad gamepad);

    void disconnected(Gamepad gamepad);
}
