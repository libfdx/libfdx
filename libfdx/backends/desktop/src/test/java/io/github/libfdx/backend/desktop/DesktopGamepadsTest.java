package io.github.libfdx.backend.desktop;

import io.github.libfdx.input.*;
import org.lwjgl.glfw.GLFWGamepadState;
import org.junit.jupiter.api.Test;
import static org.lwjgl.glfw.GLFW.*;
import static org.junit.jupiter.api.Assertions.*;

final class DesktopGamepadsTest {
    @Test void standardizedButtonsAndTriggerRangesAreCopiedIntoReusableState() {
        try(GLFWGamepadState source=GLFWGamepadState.calloc()) {
            GamepadState target=new GamepadState();
            source.buttons(GLFW_GAMEPAD_BUTTON_A,(byte)GLFW_PRESS);
            source.buttons(GLFW_GAMEPAD_BUTTON_LEFT_THUMB,(byte)GLFW_PRESS);
            source.buttons(GLFW_GAMEPAD_BUTTON_GUIDE,(byte)GLFW_PRESS);
            source.axes(GLFW_GAMEPAD_AXIS_LEFT_X,-.75f);
            source.axes(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER,-1);
            source.axes(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER,.5f);
            DesktopGamepads.copy(source,target);
            assertTrue(target.pressed(GamepadButton.SOUTH)); assertTrue(target.pressed(GamepadButton.LEFT_STICK));
            assertTrue(target.pressed(GamepadButton.GUIDE)); assertFalse(target.pressed(GamepadButton.BACK));
            assertEquals(-.75f,target.axis(GamepadAxis.LEFT_X)); assertEquals(0,target.axis(GamepadAxis.LEFT_TRIGGER));
            assertEquals(.75f,target.axis(GamepadAxis.RIGHT_TRIGGER));
            source.axes(GLFW_GAMEPAD_AXIS_LEFT_X,Float.NaN); source.buttons(GLFW_GAMEPAD_BUTTON_A,(byte)GLFW_RELEASE);
            DesktopGamepads.copy(source,target); assertFalse(target.pressed(GamepadButton.SOUTH)); assertEquals(0,target.axis(GamepadAxis.LEFT_X));
        }
    }
}
