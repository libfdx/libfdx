package io.github.libfdx.backend.desktopcpp;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

import io.github.libfdx.input.Cursor;
import io.github.libfdx.input.CursorShape;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;

/** Drains GLFW events on the application thread and owns its native cursor. */
@NativeInclude("libfdx_jnative.hpp")
final class DesktopCppInput implements Cursor {
    private static final Key[] KEYS = Key.values();
    private static final MouseButton[] BUTTONS = MouseButton.values();
    private final long window;
    private final double[] event = new double[8];
    private boolean visible = true;
    private boolean captured;
    private CursorShape shape = CursorShape.DEFAULT;

    DesktopCppInput(long window) {
        this.window = window;
        install(window);
    }

    void drain(DefaultInput input, DesktopCppApplicationBackend.WindowDisplay display) {
        while (pending(window) && next(window, event)) {
            dispatch(event, input, display);
        }
    }

    static void dispatch(
            double[] event,
            DefaultInput input,
            DesktopCppApplicationBackend.WindowDisplay display) {
        int kind = (int) event[0];
        int value = (int) event[1];
        int action = (int) event[2];
        int x = (int) event[3], y = (int) event[4];
        int screenX = (int) event[5], screenY = (int) event[6];
        switch (kind) {
            case 1:
                Key key = mapKey(value);
                if (action == 0) input.dispatchKeyUp(key);
                else input.dispatchKeyDown(key);
                break;
            case 2:
                input.dispatchTextInput(new String(Character.toChars(value)));
                break;
            case 3:
                input.dispatchPointerMoved(x, y, screenX, screenY);
                break;
            case 4:
                MouseButton button =
                        value >= 0 && value < 5 ? BUTTONS[value + 1] : MouseButton.UNKNOWN;
                if (action == 0) input.dispatchPointerUp(button, x, y, screenX, screenY);
                else input.dispatchPointerDown(button, x, y, screenX, screenY);
                break;
            case 5:
                // GLFW scroll Y is positive upward; framework scroll Y is positive downward.
                input.dispatchScrolled(x, y, screenX, screenY, (float) event[1], -(float) event[2]);
                break;
            case 6:
                if (value == 0) releasePressed(input);
                break;
            case 7:
                display.windowSizeChanged(value, action);
                break;
            case 8:
                display.framebufferSizeChanged(value, action);
                break;
            case 9:
                display.contentScaleChanged((float) event[1], (float) event[2]);
                break;
            default:
                throw new IllegalStateException("Unknown native input event: " + kind);
        }
    }

    private static void releasePressed(DefaultInput input) {
        for (Key key : KEYS) if (input.isKeyPressed(key)) input.dispatchKeyUp(key);
        for (MouseButton button : BUTTONS) {
            if (input.isMouseButtonPressed(button))
                input.dispatchPointerUp(
                        button,
                        input.pointerX(),
                        input.pointerY(),
                        input.pointerScreenX(),
                        input.pointerScreenY());
        }
    }

    private static Key mapKey(int key) {
        if (key >= 65 && key <= 90) return KEYS[Key.A.ordinal() + key - 65];
        if (key >= 48 && key <= 57) return KEYS[Key.NUM_0.ordinal() + key - 48];
        if (key >= 290 && key <= 301) return KEYS[Key.F1.ordinal() + key - 290];
        return switch (key) {
            case 259 -> Key.BACKSPACE;
            case 258 -> Key.TAB;
            case 257, 335 -> Key.ENTER;
            case 256 -> Key.ESCAPE;
            case 32 -> Key.SPACE;
            case 263 -> Key.LEFT;
            case 262 -> Key.RIGHT;
            case 265 -> Key.UP;
            case 264 -> Key.DOWN;
            case 268 -> Key.HOME;
            case 269 -> Key.END;
            case 266 -> Key.PAGE_UP;
            case 267 -> Key.PAGE_DOWN;
            case 261 -> Key.DELETE;
            case 340 -> Key.SHIFT_LEFT;
            case 344 -> Key.SHIFT_RIGHT;
            case 341 -> Key.CONTROL_LEFT;
            case 345 -> Key.CONTROL_RIGHT;
            case 342 -> Key.ALT_LEFT;
            case 346 -> Key.ALT_RIGHT;
            default -> Key.UNKNOWN;
        };
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public boolean isCaptured() {
        return captured;
    }

    @Override
    public CursorShape shape() {
        return shape;
    }

    @Override
    public void visible(boolean value) {
        visible = value;
        cursor(window, captured ? 2 : visible ? 0 : 1, shape.ordinal());
    }

    @Override
    public void captured(boolean value) {
        captured = value;
        cursor(window, captured ? 2 : visible ? 0 : 1, shape.ordinal());
    }

    @Override
    public void shape(CursorShape value) {
        shape = value == null ? CursorShape.DEFAULT : value;
        cursor(window, captured ? 2 : visible ? 0 : 1, shape.ordinal());
    }

    void dispose() {
        uninstall(window);
    }

    @NativeImport("fdx_cpp_input_install")
    private static native void install(long window);

    @NativeImport("fdx_cpp_input_next")
    private static native boolean next(long window, double[] event);

    // Reads the native queue on its owning application thread; no managed handles or callbacks.
    @NativeImport(value = "fdx_cpp_input_pending", leaf = true)
    private static native boolean pending(long window);

    @NativeImport("fdx_cpp_input_cursor")
    private static native void cursor(long window, int mode, int shape);

    @NativeImport("fdx_cpp_input_uninstall")
    private static native void uninstall(long window);
}
