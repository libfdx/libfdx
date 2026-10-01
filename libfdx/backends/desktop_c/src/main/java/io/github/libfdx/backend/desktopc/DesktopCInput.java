package io.github.libfdx.backend.desktopc;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.MouseButton;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

/** Owns a window's native pointer queue; install, drain and dispose run on the application thread. */
@Include("libfdx_input.h")
final class DesktopCInput {
    private static final MouseButton[] BUTTONS = MouseButton.values();
    private static final Queue NATIVE_QUEUE = new Queue() {
        public long install(long window) { return nativeInstall(Address.fromLong(window)).toLong(); }
        public int poll(long handle, double[] event) {
            return nativePoll(Address.fromLong(handle), Address.ofData(event));
        }
        public void dispose(long handle) { nativeDispose(Address.fromLong(handle)); }
    };

    private final Queue queue;
    private final double[] event = new double[8];
    private long handle;

    DesktopCInput(long window) {
        this(window, NATIVE_QUEUE);
    }

    DesktopCInput(long window, Queue queue) {
        this.queue = queue;
        handle = queue.install(window);
        if (handle == 0L) {
            throw new FdxException("Unable to install desktop_c pointer input: occupied GLFW user pointer or allocation failure");
        }
    }

    void drain(DefaultInput input) {
        if (handle == 0L) return;
        int result;
        while ((result = queue.poll(handle, event)) > 0) {
            dispatch(event, input);
        }
        if (result < 0) {
            throw new FdxException("Unable to grow desktop_c pointer event queue");
        }
    }

    static void dispatch(double[] event, DefaultInput input) {
        int value = (int) event[1];
        int x = (int) event[3], y = (int) event[4];
        int screenX = (int) event[5], screenY = (int) event[6];
        switch ((int) event[0]) {
            case 3:
                input.dispatchPointerMoved(x, y, screenX, screenY);
                break;
            case 4:
                MouseButton button = value >= 0 && value < 5 ? BUTTONS[value + 1] : MouseButton.UNKNOWN;
                if ((int) event[2] == 0) input.dispatchPointerUp(button, x, y, screenX, screenY);
                else input.dispatchPointerDown(button, x, y, screenX, screenY);
                break;
            case 5:
                // GLFW positive Y scrolls upward; framework positive Y scrolls downward.
                input.dispatchScrolled(x, y, screenX, screenY, (float) event[1], (float) -event[2]);
                break;
            case 6:
                if (value == 0) {
                    for (MouseButton pressed : BUTTONS) {
                        if (input.isMouseButtonPressed(pressed)) {
                            input.dispatchPointerUp(pressed, x, y, screenX, screenY);
                        }
                    }
                }
                break;
            default:
                throw new FdxException("Unknown desktop_c pointer event: " + (int) event[0]);
        }
    }

    void dispose() {
        long closingHandle = handle;
        handle = 0L;
        if (closingHandle != 0L) queue.dispose(closingHandle);
    }

    interface Queue {
        long install(long window);
        int poll(long handle, double[] event);
        void dispose(long handle);
    }

    @Import(name = "fdxInputInstall")
    private static native Address nativeInstall(Address window);

    @Import(name = "fdxInputPoll")
    private static native int nativePoll(Address handle, Address event);

    @Import(name = "fdxInputDispose")
    private static native void nativeDispose(Address handle);
}
