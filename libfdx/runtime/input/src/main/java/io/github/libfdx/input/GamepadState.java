package io.github.libfdx.input;

import java.util.EnumMap;
import java.util.Map;

/**
 * Represents a gamepad state.
 *
 * @author xpenatan
 */
public final class GamepadState {
    private final Map<GamepadAxis, Float> axes = new EnumMap<GamepadAxis, Float>(GamepadAxis.class);
    private final Map<GamepadButton, Boolean> buttons = new EnumMap<GamepadButton, Boolean>(GamepadButton.class);

    /**
     * Runs the axis step.
     *
     * @param axis the axis
     * @return the axis
     */
    public float axis(GamepadAxis axis) {
        Float value = axes.get(axis);
        return value != null ? value.floatValue() : 0.0f;
    }

    /**
     * Runs the pressed step.
     *
     * @param button the button
     * @return true if pressed succeeds or is active; false otherwise
     */
    public boolean pressed(GamepadButton button) {
        Boolean value = buttons.get(button);
        return value != null && value.booleanValue();
    }

    /**
     * Runs the axis step.
     *
     * @param axis the axis
     * @param value the value
     */
    public void axis(GamepadAxis axis, float value) {
        if (axis != null) {
            axes.put(axis, Float.valueOf(value));
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
            buttons.put(button, Boolean.valueOf(pressed));
        }
    }
}
