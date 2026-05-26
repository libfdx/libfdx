package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

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

    public static NativeWindow windows(long windowHandle) {
        return windows(0L, windowHandle);
    }

    public static NativeWindow windows(long backendHandle, long windowHandle) {
        return new NativeWindow(NativeWindowPlatform.WINDOWS, backendHandle, 0L, windowHandle);
    }

    public static NativeWindow x11(long displayHandle, long windowHandle) {
        return x11(0L, displayHandle, windowHandle);
    }

    public static NativeWindow x11(long backendHandle, long displayHandle, long windowHandle) {
        if (displayHandle == 0L) {
            throw new FdxException("X11 display handle cannot be zero");
        }
        return new NativeWindow(NativeWindowPlatform.X11, backendHandle, displayHandle, windowHandle);
    }

    public static NativeWindow wayland(long displayHandle, long windowHandle) {
        return wayland(0L, displayHandle, windowHandle);
    }

    public static NativeWindow wayland(long backendHandle, long displayHandle, long windowHandle) {
        if (displayHandle == 0L) {
            throw new FdxException("Wayland display handle cannot be zero");
        }
        return new NativeWindow(NativeWindowPlatform.WAYLAND, backendHandle, displayHandle, windowHandle);
    }

    public static NativeWindow macos(long windowHandle) {
        return macos(0L, windowHandle);
    }

    public static NativeWindow macos(long backendHandle, long windowHandle) {
        return new NativeWindow(NativeWindowPlatform.MACOS, backendHandle, 0L, windowHandle);
    }

    public static NativeWindow glfw(long windowHandle) {
        return new NativeWindow(NativeWindowPlatform.GLFW, windowHandle, 0L, windowHandle);
    }

    public static NativeWindow android(Object surface) {
        return new NativeWindow(NativeWindowPlatform.ANDROID, 0L, 0L, 0L, surface);
    }

    public static NativeWindow web(Object canvas) {
        return new NativeWindow(NativeWindowPlatform.WEB, 0L, 0L, 0L, canvas);
    }

    public static NativeWindow web(Object canvas, String selector) {
        return new NativeWindow(NativeWindowPlatform.WEB, 0L, 0L, 0L, canvas, selector);
    }

    public NativeWindowPlatform platform() {
        return platform;
    }

    public long backendHandle() {
        return backendHandle;
    }

    public long displayHandle() {
        return displayHandle;
    }

    public long windowHandle() {
        return windowHandle;
    }

    public Object objectHandle() {
        return objectHandle;
    }

    public String webSelector() {
        return webSelector;
    }
}
