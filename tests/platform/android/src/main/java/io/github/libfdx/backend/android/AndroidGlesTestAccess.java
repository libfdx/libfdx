package io.github.libfdx.backend.android;

import io.github.libfdx.graphics.gl.GLApi;

/** Test-only access to the Android GLES binding. */
public final class AndroidGlesTestAccess {
    private AndroidGlesTestAccess() {
    }

    public static GLApi createApi() {
        return new AndroidGlesApi(1);
    }

    public static long findResetStatusQuery() {
        return AndroidGlesApi.findResetStatusQuery();
    }

}
