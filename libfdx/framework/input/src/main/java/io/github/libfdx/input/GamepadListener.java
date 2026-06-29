package io.github.libfdx.input;

/**
 * Receives callbacks for gamepad events.
 *
 * @author xpenatan
 */
public interface GamepadListener {
    /**
     * Runs the connected step.
     *
     * @param gamepad the gamepad
     */
    void connected(Gamepad gamepad);

    /**
     * Runs the disconnected step.
     *
     * @param gamepad the gamepad
     */
    void disconnected(Gamepad gamepad);
}
