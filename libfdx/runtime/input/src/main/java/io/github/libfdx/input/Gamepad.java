package io.github.libfdx.input;

import io.github.libfdx.core.ProviderHandle;

/**
 * Defines the contract for gamepad implementations.
 *
 * @author xpenatan
 */
public interface Gamepad extends ProviderHandle {
    /**
     * Returns the ID.
     *
     * @return the ID
     */
    String id();

    /**
     * Returns the name.
     *
     * @return the name
     */
    String name();

    /**
     * Returns the index.
     *
     * @return the index
     */
    int index();

    /**
     * Returns whether connected is enabled or true.
     *
     * @return true if connected is enabled or true; false otherwise
     */
    boolean isConnected();

    /**
     * Returns the mapping.
     *
     * @return the mapping
     */
    GamepadMapping mapping();

    /**
     * Returns the state.
     *
     * @return the state
     */
    GamepadState state();

    /**
     * Runs the axis step.
     *
     * @param axis the axis
     * @return the axis
     */
    float axis(GamepadAxis axis);

    /**
     * Runs the pressed step.
     *
     * @param button the button
     * @return true if pressed succeeds or is active; false otherwise
     */
    boolean pressed(GamepadButton button);
}
