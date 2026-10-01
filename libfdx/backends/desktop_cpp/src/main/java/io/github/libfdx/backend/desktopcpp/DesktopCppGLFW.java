package io.github.libfdx.backend.desktopcpp;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

/**
 * Represents a jNative GLFW.
 *
 * @author xpenatan
 */
@NativeInclude("libfdx_jnative.hpp")
final class DesktopCppGLFW {
    static final int TRUE = 1;
    static final int FALSE = 0;
    static final int VISIBLE = 0x00020004;
    static final int RESIZABLE = 0x00020003;
    static final int MAXIMIZED = 0x00020008;
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

    private DesktopCppGLFW() {}

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
        return glfwCreateWindow(width, height, title, 0L, 0L);
    }

    static void destroyWindow(long window) {
        glfwDestroyWindow(window);
    }

    static void showWindow(long window) {
        glfwShowWindow(window);
    }

    static void setWindowTitle(long window, String title) {
        glfwSetWindowTitle(window, title);
    }

    static String getClipboardString(long window) {
        return glfwGetClipboardString(window);
    }

    static void setClipboardString(long window, String text) {
        glfwSetClipboardString(window, text);
    }

    static int getError() {
        return glfwGetError(0L);
    }

    static boolean windowShouldClose(long window) {
        return glfwWindowShouldClose(window);
    }

    static void setWindowShouldClose(long window, boolean shouldClose) {
        glfwSetWindowShouldClose(window, shouldClose);
    }

    static void pollEvents() {
        glfwPollEvents();
    }

    static void waitEventsTimeout(double timeoutSeconds) {
        glfwWaitEventsTimeout(timeoutSeconds);
    }

    static void makeContextCurrent(long window) {
        glfwMakeContextCurrent(window);
    }

    static void swapInterval(int interval) {
        glfwSwapInterval(interval);
    }

    static void swapBuffers(long window) {
        glfwSwapBuffers(window);
    }

    static void getWindowSize(long window, int[] width, int[] height) {
        glfwGetWindowSize(window, width, height);
    }

    static void getFramebufferSize(long window, int[] width, int[] height) {
        glfwGetFramebufferSize(window, width, height);
    }

    static void getWindowContentScale(long window, float[] scaleX, float[] scaleY) {
        glfwGetWindowContentScale(window, scaleX, scaleY);
    }

    @NativeImport("fdx_jn_glfwInit")
    private static native boolean glfwInit();

    @NativeImport("fdx_jn_glfwTerminate")
    private static native void glfwTerminate();

    @NativeImport("fdx_jn_glfwDefaultWindowHints")
    private static native void glfwDefaultWindowHints();

    @NativeImport("fdx_jn_glfwWindowHint")
    private static native void glfwWindowHint(int hint, int value);

    @NativeImport("fdx_jn_glfwCreateWindow")
    private static native long glfwCreateWindow(
            int width, int height, String title, long monitor, long share);

    @NativeImport("fdx_jn_glfwDestroyWindow")
    private static native void glfwDestroyWindow(long window);

    @NativeImport("fdx_jn_glfwShowWindow")
    private static native void glfwShowWindow(long window);

    @NativeImport("fdx_jn_glfwSetWindowTitle")
    private static native void glfwSetWindowTitle(long window, String title);

    @NativeImport("fdx_jn_glfwGetClipboardString")
    private static native String glfwGetClipboardString(long window);

    @NativeImport("fdx_jn_glfwSetClipboardString")
    private static native void glfwSetClipboardString(long window, String text);

    @NativeImport("fdx_jn_glfwGetError")
    private static native int glfwGetError(long description);

    // GLFW only reads/writes its cached flag for the live window owned by this backend.
    @NativeImport(value = "fdx_jn_glfwWindowShouldClose", leaf = true)
    private static native boolean glfwWindowShouldClose(long window);

    @NativeImport(value = "fdx_jn_glfwSetWindowShouldClose", leaf = true)
    private static native void glfwSetWindowShouldClose(long window, boolean shouldClose);

    @NativeImport("fdx_jn_glfwPollEvents")
    private static native void glfwPollEvents();

    @NativeImport("fdx_jn_glfwWaitEventsTimeout")
    private static native void glfwWaitEventsTimeout(double timeoutSeconds);

    @NativeImport("fdx_jn_glfwMakeContextCurrent")
    private static native void glfwMakeContextCurrent(long window);

    @NativeImport("fdx_jn_glfwSwapInterval")
    private static native void glfwSwapInterval(int interval);

    @NativeImport("fdx_jn_glfwSwapBuffers")
    private static native void glfwSwapBuffers(long window);

    @NativeImport("fdx_jn_glfwGetWindowSize")
    private static native void glfwGetWindowSize(long window, int[] width, int[] height);

    @NativeImport("fdx_jn_glfwGetFramebufferSize")
    private static native void glfwGetFramebufferSize(long window, int[] width, int[] height);

    @NativeImport("fdx_jn_glfwGetWindowContentScale")
    private static native void glfwGetWindowContentScale(
            long window, float[] scaleX, float[] scaleY);
}
