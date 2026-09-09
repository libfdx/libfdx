package io.github.libfdx.backend.android;

import android.opengl.EGL14;
import android.opengl.GLES30;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;

/** Queries the actual current GLES context; does not induce a GPU reset. */
public final class AndroidGlesResetDetectionTest extends ApplicationAdapter {
    private Fdx fdx;

    @Override public void create(Fdx fdx) {
        this.fdx = fdx;
        require(fdx.graphics().main().providerId().equals(AndroidGlesProvider.ID), "Expected direct GLES");
        AndroidGlesApi api = new AndroidGlesApi(1);
        long query = AndroidGlesApi.findResetStatusQuery();
        require(query != 0 && query != -1, "No native reset-status query; route not validated");
        int[] value = new int[1];
        GLES30.glGetIntegerv(0x8256, value, 0); // GL_RESET_NOTIFICATION_STRATEGY, core/KHR/EXT.
        require(value[0] == 0x8252, "Driver did not enable reset notifications: 0x" + Integer.toHexString(value[0]));
        require(!api.isContextLost(), "Fresh GLES context reports loss");
        require(GLES30.glGetError() == GLES30.GL_NO_ERROR, "Reset query caused a GL error");
        GLES30.glEnable(-1);
        require(!api.isContextLost(), "Ordinary error was treated as context loss");
        require(GLES30.glGetError() == GLES30.GL_INVALID_ENUM, "Reset query consumed ordinary GL errors");
        require(GLES30.glGetError() == GLES30.GL_NO_ERROR, "Unexpected trailing GL error");
        System.out.println("[info] GLES_RESET_QUERY_PASS version=" + GLES30.glGetString(GLES30.GL_VERSION)
                + " strategy=0x" + Integer.toHexString(value[0]) + " renderer=" + GLES30.glGetString(GLES30.GL_RENDERER));
        System.out.println("[info] GLES_RESET_EXTENSIONS " + GLES30.glGetString(GLES30.GL_EXTENSIONS));
        System.out.println("[info] EGL_RESET_EXTENSIONS " + EGL14.eglQueryString(EGL14.eglGetCurrentDisplay(), EGL14.EGL_EXTENSIONS));
        api.closeShaderPreparation();
    }
    @Override public void render() { fdx.app().requestExit(); }
    private static void require(boolean condition, String message) { if (!condition) throw new FdxException(message); }
}
