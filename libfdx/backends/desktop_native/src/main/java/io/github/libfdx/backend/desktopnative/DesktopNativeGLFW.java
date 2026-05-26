package io.github.libfdx.backend.desktopnative;

import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

@Include("GLFW/glfw3.h")
final class DesktopNativeGLFW {
    static final int TRUE = 1;
    static final int FALSE = 0;
    static final int VISIBLE = 0x00020004;
    static final int RESIZABLE = 0x00020003;
    static final int CLIENT_API = 0x00022001;
    static final int CONTEXT_VERSION_MAJOR = 0x00022002;
    static final int CONTEXT_VERSION_MINOR = 0x00022003;
    static final int OPENGL_FORWARD_COMPAT = 0x00022006;
    static final int OPENGL_PROFILE = 0x00022008;
    static final int NO_API = 0;
    static final int OPENGL_API = 0x00030001;
    static final int OPENGL_ANY_PROFILE = 0;
    static final int OPENGL_CORE_PROFILE = 0x00032001;
    static final int OPENGL_COMPAT_PROFILE = 0x00032002;

    private DesktopNativeGLFW() {
    }

    static boolean init() {
        return glfwInit();
    }

    static void terminate() {
        glfwTerminate();
    }

    static void defaultWindowHints() {
        glfwDefaultWindowHints();
    }

    static void windowHint(int hint, int value) {
        glfwWindowHint(hint, value);
    }

    static long createWindow(int width, int height, String title) {
        return glfwCreateWindow(width, height, title, Address.fromLong(0L), Address.fromLong(0L)).toLong();
    }

    static void destroyWindow(long window) {
        glfwDestroyWindow(Address.fromLong(window));
    }

    static void showWindow(long window) {
        glfwShowWindow(Address.fromLong(window));
    }

    static void setWindowTitle(long window, String title) {
        glfwSetWindowTitle(Address.fromLong(window), title);
    }

    static boolean windowShouldClose(long window) {
        return glfwWindowShouldClose(Address.fromLong(window));
    }

    static void setWindowShouldClose(long window, boolean shouldClose) {
        glfwSetWindowShouldClose(Address.fromLong(window), shouldClose);
    }

    static void pollEvents() {
        glfwPollEvents();
    }

    static void waitEventsTimeout(double timeoutSeconds) {
        glfwWaitEventsTimeout(timeoutSeconds);
    }

    static void makeContextCurrent(long window) {
        glfwMakeContextCurrent(Address.fromLong(window));
    }

    static void swapInterval(int interval) {
        glfwSwapInterval(interval);
    }

    static void swapBuffers(long window) {
        glfwSwapBuffers(Address.fromLong(window));
    }

    static void getWindowSize(long window, int[] width, int[] height) {
        glfwGetWindowSize(Address.fromLong(window), Address.ofData(width), Address.ofData(height));
    }

    static void getFramebufferSize(long window, int[] width, int[] height) {
        glfwGetFramebufferSize(Address.fromLong(window), Address.ofData(width), Address.ofData(height));
    }

    static void getWindowContentScale(long window, float[] scaleX, float[] scaleY) {
        glfwGetWindowContentScale(Address.fromLong(window), Address.ofData(scaleX), Address.ofData(scaleY));
    }

    @Import(name = "glfwInit")
    private static native boolean glfwInit();

    @Import(name = "glfwTerminate")
    private static native void glfwTerminate();

    @Import(name = "glfwDefaultWindowHints")
    private static native void glfwDefaultWindowHints();

    @Import(name = "glfwWindowHint")
    private static native void glfwWindowHint(int hint, int value);

    @Import(name = "glfwCreateWindow")
    private static native Address glfwCreateWindow(int width, int height, String title, Address monitor, Address share);

    @Import(name = "glfwDestroyWindow")
    private static native void glfwDestroyWindow(Address window);

    @Import(name = "glfwShowWindow")
    private static native void glfwShowWindow(Address window);

    @Import(name = "glfwSetWindowTitle")
    private static native void glfwSetWindowTitle(Address window, String title);

    @Import(name = "glfwWindowShouldClose")
    private static native boolean glfwWindowShouldClose(Address window);

    @Import(name = "glfwSetWindowShouldClose")
    private static native void glfwSetWindowShouldClose(Address window, boolean shouldClose);

    @Import(name = "glfwPollEvents")
    private static native void glfwPollEvents();

    @Import(name = "glfwWaitEventsTimeout")
    private static native void glfwWaitEventsTimeout(double timeoutSeconds);

    @Import(name = "glfwMakeContextCurrent")
    private static native void glfwMakeContextCurrent(Address window);

    @Import(name = "glfwSwapInterval")
    private static native void glfwSwapInterval(int interval);

    @Import(name = "glfwSwapBuffers")
    private static native void glfwSwapBuffers(Address window);

    @Import(name = "glfwGetWindowSize")
    private static native void glfwGetWindowSize(Address window, Address width, Address height);

    @Import(name = "glfwGetFramebufferSize")
    private static native void glfwGetFramebufferSize(Address window, Address width, Address height);

    @Import(name = "glfwGetWindowContentScale")
    private static native void glfwGetWindowContentScale(Address window, Address scaleX, Address scaleY);
}
