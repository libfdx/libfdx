package io.github.libfdx.input;

/**
 * Represents a gamepad state.
 *
 * @author xpenatan
 */
public final class GamepadState {
    private final float[] axes = new float[GamepadAxis.values().length];
    private final boolean[] buttons = new boolean[GamepadButton.values().length];

    /**
     * Runs the axis step.
     *
     * @param axis the axis
     * @return the axis
     */
    public float axis(GamepadAxis axis) {
        return axis != null ? axes[axis.ordinal()] : 0.0f;
    }

    /**
     * Runs the pressed step.
     *
     * @param button the button
     * @return true if pressed succeeds or is active; false otherwise
     */
    public boolean pressed(GamepadButton button) {
        return button != null && buttons[button.ordinal()];
    }

    /**
     * Runs the axis step.
     *
     * @param axis the axis
     * @param value the value
     */
    public void axis(GamepadAxis axis, float value) {
        if (axis != null) {
            axes[axis.ordinal()] = value;
        }
    }

    /**
     * Runs the button step.
     *
     * @param button the button
     * @param pressed the pressed
     */
    public void button(GamepadButton button, boolean pressed) {
        if (button != null) {
            buttons[button.ordinal()] = pressed;
        }
    }
}
