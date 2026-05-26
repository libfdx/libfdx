package io.github.libfdx.input;

import java.util.EnumMap;
import java.util.Map;

public final class GamepadState {
    private final Map<GamepadAxis, Float> axes = new EnumMap<GamepadAxis, Float>(GamepadAxis.class);
    private final Map<GamepadButton, Boolean> buttons = new EnumMap<GamepadButton, Boolean>(GamepadButton.class);

    public float axis(GamepadAxis axis) {
        Float value = axes.get(axis);
        return value != null ? value.floatValue() : 0.0f;
    }

    public boolean pressed(GamepadButton button) {
        Boolean value = buttons.get(button);
        return value != null && value.booleanValue();
    }

    public void axis(GamepadAxis axis, float value) {
        if (axis != null) {
            axes.put(axis, Float.valueOf(value));
        }
    }

    public void button(GamepadButton button, boolean pressed) {
        if (button != null) {
            buttons.put(button, Boolean.valueOf(pressed));
        }
    }
}
