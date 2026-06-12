package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Represents a native window.
 *
 * @author xpenatan
 */
public final class NativeWindow {
    private final NativeWindowPlatform platform;
    private final long backendHandle;
    private final long displayHandle;
    private final long windowHandle;
    private final Object objectHandle;
    private final String webSelector;

    private NativeWindow(NativeWindowPlatform platform, long backendHandle, long displayHandle, long windowHandle) {
        this(platform, backendHandle, displayHandle, windowHandle, null, null);
    }

    private NativeWindow(NativeWindowPlatform platform, long backendHandle, long displayHandle, long windowHandle,
            Object objectHandle) {
        this(platform, backendHandle, displayHandle, windowHandle, objectHandle, null);
    }

    private NativeWindow(NativeWindowPlatform platform, long backendHandle, long displayHandle, long windowHandle,
            Object objectHandle, String webSelector) {
        if (platform == null) {
            throw new FdxException("Native window platform cannot be null");
        }
        if ((platform == NativeWindowPlatform.ANDROID || platform == NativeWindowPlatform.WEB)
                && objectHandle == null) {
            throw new FdxException(platform + " native window object cannot be null");
        }
        if (platform != NativeWindowPlatform.ANDROID && platform != NativeWindowPlatform.WEB && windowHandle == 0L) {
            throw new FdxException("Native window handle cannot be zero");
        }
        this.platform = platform;
        this.backendHandle = backendHandle;
        this.displayHandle = displayHandle;
        this.windowHandle = windowHandle;
        this.objectHandle = objectHandle;
        this.webSelector = webSelector;
    }

    /**
     * Creates a native window.
     *
     * @param windowHandle the window handle
     * @return a new native window
     */
    public static NativeWindow windows(long windowHandle) {
        return windows(0L, windowHandle);
    }

    /**
     * Creates a native window.
     *
     * @param backendHandle the backend handle
     * @param windowHandle the window handle
     * @return a new native window
     */
    public static NativeWindow windows(long backendHandle, long windowHandle) {
        return new NativeWindow(NativeWindowPlatform.WINDOWS, backendHandle, 0L, windowHandle);
    }

    /**
     * Creates a native window.
     *
     * @param displayHandle the display handle
     * @param windowHandle the window handle
     * @return a new native window
     */
    public static NativeWindow x11(long displayHandle, long windowHandle) {
        return x11(0L, displayHandle, windowHandle);
    }

    /**
     * Creates a native window.
     *
     * @param backendHandle the backend handle
     * @param displayHandle the display handle
     * @param windowHandle the window handle
     * @return a new native window
     */
    public static NativeWindow x11(long backendHandle, long displayHandle, long windowHandle) {
        if (displayHandle == 0L) {
            throw new FdxException("X11 display handle cannot be zero");
        }
        return new NativeWindow(NativeWindowPlatform.X11, backendHandle, displayHandle, windowHandle);
    }

    /**
     * Creates a native window.
     *
     * @param displayHandle the display handle
     * @param windowHandle the window handle
     * @return a new native window
     */
    public static NativeWindow wayland(long displayHandle, long windowHandle) {
        return wayland(0L, displayHandle, windowHandle);
    }

    /**
     * Creates a native window.
     *
     * @param backendHandle the backend handle
     * @param displayHandle the display handle
     * @param windowHandle the window handle
     * @return a new native window
     */
    public static NativeWindow wayland(long backendHandle, long displayHandle, long windowHandle) {
        if (displayHandle == 0L) {
            throw new FdxException("Wayland display handle cannot be zero");
        }
        return new NativeWindow(NativeWindowPlatform.WAYLAND, backendHandle, displayHandle, windowHandle);
    }

    /**
     * Creates a native window.
     *
     * @param windowHandle the window handle
     * @return a new native window
     */
    public static NativeWindow macos(long windowHandle) {
        return macos(0L, windowHandle);
    }

    /**
     * Creates a native window.
     *
     * @param backendHandle the backend handle
     * @param windowHandle the window handle
     * @return a new native window
     */
    public static NativeWindow macos(long backendHandle, long windowHandle) {
        return new NativeWindow(NativeWindowPlatform.MACOS, backendHandle, 0L, windowHandle);
    }

    /**
     * Creates a native window.
     *
     * @param windowHandle the window handle
     * @return a new native window
     */
    public static NativeWindow glfw(long windowHandle) {
        return new NativeWindow(NativeWindowPlatform.GLFW, windowHandle, 0L, windowHandle);
    }

    /**
     * Creates a native window.
     *
     * @param surface the surface
     * @return a new native window
     */
    public static NativeWindow android(Object surface) {
        return new NativeWindow(NativeWindowPlatform.ANDROID, 0L, 0L, 0L, surface);
    }

    /**
     * Creates a native window.
     *
     * @param canvas the canvas
     * @return a new native window
     */
    public static NativeWindow web(Object canvas) {
        return new NativeWindow(NativeWindowPlatform.WEB, 0L, 0L, 0L, canvas);
    }

    /**
     * Creates a native window.
     *
     * @param canvas the canvas
     * @param selector the selector
     * @return a new native window
     */
    public static NativeWindow web(Object canvas, String selector) {
        return new NativeWindow(NativeWindowPlatform.WEB, 0L, 0L, 0L, canvas, selector);
    }

    /**
     * Returns the platform.
     *
     * @return the platform
     */
    public NativeWindowPlatform platform() {
        return platform;
    }

    /**
     * Returns the backend handle.
     *
     * @return the backend handle
     */
    public long backendHandle() {
        return backendHandle;
    }

    /**
     * Returns the display handle.
     *
     * @return the display handle
     */
    public long displayHandle() {
        return displayHandle;
    }

    /**
     * Returns the window handle.
     *
     * @return the window handle
     */
    public long windowHandle() {
        return windowHandle;
    }

    /**
     * Returns the object handle.
     *
     * @return the object handle
     */
    public Object objectHandle() {
        return objectHandle;
    }

    /**
     * Returns the web selector.
     *
     * @return the web selector
     */
    public String webSelector() {
        return webSelector;
    }
}
