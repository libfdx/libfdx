package io.github.libfdx.backend.psp;

import io.github.libfdx.backend.psp.natives.PSPInputApi;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.input.DefaultCursor;
import io.github.libfdx.input.DefaultGamepad;
import io.github.libfdx.input.DefaultGamepads;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.DefaultInputCapabilities;
import io.github.libfdx.input.GamepadAxis;
import io.github.libfdx.input.GamepadButton;
import io.github.libfdx.input.GamepadMapping;
import io.github.libfdx.input.GamepadState;
import io.github.libfdx.input.Key;

final class PspInputController {
    private static final float ANALOG_CENTER = 128.0f;
    private static final float ANALOG_RANGE = 127.0f;
    private static final float ANALOG_DEAD_ZONE = 0.08f;
    private static final long DIRECTION_REPEAT_DELAY_MILLIS = 360L;
    private static final long DIRECTION_REPEAT_INTERVAL_MILLIS = 120L;
    private static final int[] DIRECTION_MASKS = {
            PSPInputApi.PSP_CTRL_UP,
            PSPInputApi.PSP_CTRL_DOWN,
            PSPInputApi.PSP_CTRL_LEFT,
            PSPInputApi.PSP_CTRL_RIGHT
    };
    private static final Key[] DIRECTION_KEYS = {
            Key.UP,
            Key.DOWN,
            Key.LEFT,
            Key.RIGHT
    };

    private final DefaultGamepads gamepads = new DefaultGamepads();
    private final DefaultGamepad gamepad = new DefaultGamepad("psp:0", "PSP Controls", 0, GamepadMapping.STANDARD);
    private final DefaultInput input;
    private final long[] nextDirectionRepeatMillis = new long[DIRECTION_MASKS.length];
    private int previousButtons;

    PspInputController() {
        PSPInputApi.initInput();
        gamepads.connect(gamepad);
        input = new DefaultInput(ProviderId.of("psp_input"),
                new DefaultInputCapabilities(false, false, false, false, false, true), new DefaultCursor(), gamepads);
        poll();
    }

    DefaultInput input() {
        return input;
    }

    void poll() {
        int buttons = PSPInputApi.pollInput();
        GamepadState state = gamepad.state();
        state.button(GamepadButton.SOUTH, pressed(buttons, PSPInputApi.PSP_CTRL_CROSS));
        state.button(GamepadButton.EAST, pressed(buttons, PSPInputApi.PSP_CTRL_CIRCLE));
        state.button(GamepadButton.WEST, pressed(buttons, PSPInputApi.PSP_CTRL_SQUARE));
        state.button(GamepadButton.NORTH, pressed(buttons, PSPInputApi.PSP_CTRL_TRIANGLE));
        state.button(GamepadButton.LEFT_BUMPER, pressed(buttons, PSPInputApi.PSP_CTRL_LTRIGGER));
        state.button(GamepadButton.RIGHT_BUMPER, pressed(buttons, PSPInputApi.PSP_CTRL_RTRIGGER));
        state.button(GamepadButton.BACK, pressed(buttons, PSPInputApi.PSP_CTRL_SELECT));
        state.button(GamepadButton.START, pressed(buttons, PSPInputApi.PSP_CTRL_START));
        state.button(GamepadButton.DPAD_UP, pressed(buttons, PSPInputApi.PSP_CTRL_UP));
        state.button(GamepadButton.DPAD_DOWN, pressed(buttons, PSPInputApi.PSP_CTRL_DOWN));
        state.button(GamepadButton.DPAD_LEFT, pressed(buttons, PSPInputApi.PSP_CTRL_LEFT));
        state.button(GamepadButton.DPAD_RIGHT, pressed(buttons, PSPInputApi.PSP_CTRL_RIGHT));
        state.axis(GamepadAxis.LEFT_X, axis(PSPInputApi.analogX()));
        state.axis(GamepadAxis.LEFT_Y, axis(PSPInputApi.analogY()));
        dispatchKeyInput(buttons);
        previousButtons = buttons;
    }

    private void dispatchKeyInput(int buttons) {
        long nowMillis = System.currentTimeMillis();
        dispatchDirectionalKeys(buttons, nowMillis);
        dispatchKeyEdge(buttons, PSPInputApi.PSP_CTRL_CROSS, Key.ENTER);
        dispatchKeyEdge(buttons, PSPInputApi.PSP_CTRL_SQUARE, Key.SPACE);
        dispatchKeyEdge(buttons, PSPInputApi.PSP_CTRL_CIRCLE, Key.ESCAPE);
        dispatchKeyEdge(buttons, PSPInputApi.PSP_CTRL_LTRIGGER, Key.PAGE_UP);
        dispatchKeyEdge(buttons, PSPInputApi.PSP_CTRL_RTRIGGER, Key.PAGE_DOWN);
    }

    private void dispatchDirectionalKeys(int buttons, long nowMillis) {
        for (int i = 0; i < DIRECTION_MASKS.length; i++) {
            int mask = DIRECTION_MASKS[i];
            Key key = DIRECTION_KEYS[i];
            boolean down = pressed(buttons, mask);
            boolean wasDown = pressed(previousButtons, mask);
            if (down && !wasDown) {
                input.dispatchKeyDown(key);
                nextDirectionRepeatMillis[i] = nowMillis + DIRECTION_REPEAT_DELAY_MILLIS;
            } else if (down && nextDirectionRepeatMillis[i] > 0L && nowMillis >= nextDirectionRepeatMillis[i]) {
                input.dispatchKeyDown(key);
                nextDirectionRepeatMillis[i] = nowMillis + DIRECTION_REPEAT_INTERVAL_MILLIS;
            } else if (!down && wasDown) {
                input.dispatchKeyUp(key);
                nextDirectionRepeatMillis[i] = 0L;
            }
        }
    }

    private void dispatchKeyEdge(int buttons, int mask, Key key) {
        boolean down = pressed(buttons, mask);
        boolean wasDown = pressed(previousButtons, mask);
        if (down && !wasDown) {
            input.dispatchKeyDown(key);
        } else if (!down && wasDown) {
            input.dispatchKeyUp(key);
        }
    }

    private static boolean pressed(int buttons, int mask) {
        return (buttons & mask) != 0;
    }

    private static float axis(int value) {
        float normalized = (value - ANALOG_CENTER) / ANALOG_RANGE;
        if (normalized < -1.0f) {
            normalized = -1.0f;
        } else if (normalized > 1.0f) {
            normalized = 1.0f;
        }
        return Math.abs(normalized) < ANALOG_DEAD_ZONE ? 0.0f : normalized;
    }
}
