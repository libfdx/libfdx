package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.jParser.api.NativeObject;
import com.github.xpenatan.webgpu.WGPUInstance;
import com.github.xpenatan.webgpu.WGPUSurface;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.NativeWindow;

/**
 * Represents a WGPU native surface.
 *
 * @author xpenatan
 */
final class WGPUNativeSurface {
    private WGPUNativeSurface() {
    }

    static SurfaceHandle create(WGPUInstance instance, NativeWindow nativeWindow) {
        WGPUSurface surface;
        Object owner = null;
        switch (nativeWindow.platform()) {
            case WINDOWS:
                surface = instance.createWindowsSurface(NativeObject.native_new().native_setAddress(nativeWindow.windowHandle()));
                break;
            case X11:
                surface = instance.createLinuxSurface(false,
                        NativeObject.native_new().native_setAddress(nativeWindow.windowHandle()),
                        NativeObject.native_new().native_setAddress(nativeWindow.displayHandle()));
                break;
            case WAYLAND:
                surface = instance.createLinuxSurface(true,
                        NativeObject.native_new().native_setAddress(nativeWindow.windowHandle()),
                        NativeObject.native_new().native_setAddress(nativeWindow.displayHandle()));
                break;
            case MACOS:
                surface = instance.createMacSurface(NativeObject.native_new().native_setAddress(nativeWindow.windowHandle()));
                break;
            case ANDROID:
                SurfaceHandle androidSurface = createAndroidSurface(instance, nativeWindow.objectHandle());
                surface = androidSurface.surface();
                owner = androidSurface.owner();
                break;
            case WEB:
                surface = instance.createWebSurface(webCanvasSelector(nativeWindow));
                break;
            default:
                throw new FdxException("Unsupported native window platform for WGPU surface: " + nativeWindow.platform());
        }

        if (surface == null) {
            throw new FdxException("Could not create a valid WGPU surface");
        }
        return new SurfaceHandle(surface, owner);
    }

    private static SurfaceHandle createAndroidSurface(WGPUInstance instance, Object window) {
        try {
            Class<?> androidWindowClass = Class.forName("com.github.xpenatan.webgpu.WGPUAndroidWindow");
            Object androidWindow = androidWindowClass.getConstructor().newInstance();
            androidWindowClass.getMethod("initLogcat").invoke(androidWindow);
            androidWindowClass.getMethod("createAndroidSurface", Object.class).invoke(androidWindow, window);
            WGPUSurface surface = (WGPUSurface) WGPUInstance.class
                    .getMethod("createAndroidSurface", androidWindowClass)
                    .invoke(instance, androidWindow);
            return new SurfaceHandle(surface, androidWindow);
        } catch (Throwable error) {
            throw new FdxException("Could not create WGPU Android surface", error);
        }
    }

    private static String webCanvasSelector(NativeWindow nativeWindow) {
        String selector = nativeWindow.webSelector();
        if (selector == null || selector.length() == 0) {
            selector = webCanvasId(nativeWindow.objectHandle());
        }
        if (selector == null || selector.length() == 0) {
            throw new FdxException("WGPU web surface requires a canvas selector");
        }
        return selector.charAt(0) == '#' ? selector : "#" + selector;
    }

    private static String webCanvasId(Object objectHandle) {
        if (objectHandle instanceof String) {
            return (String) objectHandle;
        }
        if (objectHandle == null) {
            return null;
        }
        try {
            Object id = objectHandle.getClass().getMethod("getId").invoke(objectHandle);
            return id instanceof String ? (String) id : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Represents a surface handle.
     *
     * @author xpenatan
     */
    static final class SurfaceHandle {
        private final WGPUSurface surface;
        private final Object owner;

        SurfaceHandle(WGPUSurface surface, Object owner) {
            this.surface = surface;
            this.owner = owner;
        }

        WGPUSurface surface() {
            return surface;
        }

        Object owner() {
            return owner;
        }
    }
}
