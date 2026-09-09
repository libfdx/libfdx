package io.github.libfdx.backend.android;

import android.opengl.EGL14;
import android.opengl.EGL15;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.view.Surface;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContextLostException;
import io.github.libfdx.graphics.gl.GLSurface;

/**
 * Represents an android gles surface.
 *
 * @author xpenatan
 */
final class AndroidGlesSurface implements GLSurface, Disposable {
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x00000040;
    private static final int EGL_CONTEXT_OPENGL_RESET_NOTIFICATION_STRATEGY_EXT = 0x3138;
    private static final int EGL_LOSE_CONTEXT_ON_RESET_EXT = 0x31BF;

    private final EGLDisplay eglDisplay;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private boolean disposed;
    private boolean contextLost;

    AndroidGlesSurface(Surface surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new FdxException("Could not get Android EGL display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw eglException("eglInitialize");
        }

        try {
            EGLConfig eglConfig = chooseConfig();
            int[] contextAttributes = contextAttributes(version[0], version[1],
                    EGL14.eglQueryString(eglDisplay, EGL14.EGL_EXTENSIONS));
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT,
                    contextAttributes, 0);
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                throw eglException("eglCreateContext");
            }

            int[] surfaceAttributes = {EGL14.EGL_NONE};
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttributes, 0);
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw eglException("eglCreateWindowSurface");
            }
            makeCurrent();
            EGL14.eglSwapInterval(eglDisplay, 1);
        } catch (RuntimeException | Error failure) {
            dispose();
            throw failure;
        }
    }

    static int[] contextAttributes(int major, int minor, String extensions) {
        // Unlike EGL 1.5, KHR_create_context alone does not enable this attribute for GLES.
        boolean notifications = extensions != null
                && (" " + extensions + " ").contains(" EGL_EXT_create_context_robustness ");
        if (notifications) return new int[] { EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL_CONTEXT_OPENGL_RESET_NOTIFICATION_STRATEGY_EXT, EGL_LOSE_CONTEXT_ON_RESET_EXT, EGL14.EGL_NONE };
        if (major > 1 || major == 1 && minor >= 5) return new int[] { EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL15.EGL_CONTEXT_OPENGL_RESET_NOTIFICATION_STRATEGY, EGL15.EGL_LOSE_CONTEXT_ON_RESET, EGL14.EGL_NONE };
        return new int[] { EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE };
    }

    private EGLConfig chooseConfig() {
        int[] configAttributes = {
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 24,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0, configs.length,
                numConfigs, 0) || numConfigs[0] == 0) {
            throw eglException("eglChooseConfig");
        }
        return configs[0];
    }

    /**
     * Runs the make current step.
     */
    @Override
    public void makeCurrent() {
        if (contextLost) throw new GraphicsContextLostException(AndroidGlesProvider.ID);
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw eglException("eglMakeCurrent");
        }
    }

    /**
     * Runs the swap buffers step.
     */
    @Override
    public void swapBuffers() {
        if (contextLost) throw new GraphicsContextLostException(AndroidGlesProvider.ID);
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            throw eglException("eglSwapBuffers");
        }
    }

    /**
     * Runs the release current step.
     */
    @Override
    public void releaseCurrent() {
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (EGL14.eglGetCurrentContext().equals(eglContext) && eglContext != EGL14.EGL_NO_CONTEXT) releaseCurrent();
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface);
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext);
        EGL14.eglTerminate(eglDisplay);
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private FdxException eglException(String operation) {
        int error = EGL14.eglGetError();
        if (error == EGL14.EGL_CONTEXT_LOST) contextLost = true;
        return eglException(operation, error);
    }

    static FdxException eglException(String operation, int error) {
        return error == EGL14.EGL_CONTEXT_LOST ? new GraphicsContextLostException(AndroidGlesProvider.ID)
                : new FdxException(operation + " failed with EGL error 0x" + Integer.toHexString(error));
    }
}
