package io.github.libfdx.backend.android;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.view.Surface;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.gl.GLSurface;

final class AndroidGlesSurface implements GLSurface, Disposable {
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x00000040;

    private final EGLDisplay eglDisplay;
    private final EGLContext eglContext;
    private final EGLSurface eglSurface;
    private boolean disposed;

    AndroidGlesSurface(Surface surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new FdxException("Could not get Android EGL display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw eglException("eglInitialize");
        }

        EGLConfig eglConfig = chooseConfig();
        int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
        };
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

    @Override
    public void makeCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw eglException("eglMakeCurrent");
        }
    }

    @Override
    public void swapBuffers() {
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            throw eglException("eglSwapBuffers");
        }
    }

    @Override
    public void releaseCurrent() {
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        releaseCurrent();
        EGL14.eglDestroySurface(eglDisplay, eglSurface);
        EGL14.eglDestroyContext(eglDisplay, eglContext);
        EGL14.eglTerminate(eglDisplay);
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private static FdxException eglException(String operation) {
        return new FdxException(operation + " failed with EGL error 0x"
                + Integer.toHexString(EGL14.eglGetError()));
    }
}
