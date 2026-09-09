package io.github.libfdx.backend.android;

import android.opengl.EGL14;
import io.github.libfdx.graphics.GraphicsContextLostException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AndroidGlesSurfaceTest {
    @Test void requestsNotificationsOnlyWhenTheGlesExtensionIsAdvertised() {
        assertArrayEquals(new int[] {0x3098, 3, 0x3138, 0x31BF, 0x3038},
                AndroidGlesSurface.contextAttributes(1, 4, "EGL_KHR_create_context EGL_EXT_create_context_robustness EGL_EXT_other"));
        for (String extensions : new String[] {null, "", "EGL_KHR_create_context", "EGL_EXT_create_context_robustness_extra"}) {
            assertArrayEquals(new int[] {0x3098, 3, 0x3038}, AndroidGlesSurface.contextAttributes(1, 4, extensions));
        }
        assertArrayEquals(new int[] {0x3098, 3, 0x31BD, 0x31BF, 0x3038}, AndroidGlesSurface.contextAttributes(1, 5, ""));
    }

    @Test void onlyContextLostIsTerminalAndOtherEglErrorsKeepTheirCode() {
        var loss = assertInstanceOf(GraphicsContextLostException.class,
                AndroidGlesSurface.eglException("eglSwapBuffers", EGL14.EGL_CONTEXT_LOST));
        assertEquals(AndroidGlesProvider.ID, loss.providerId());
        var surface = AndroidGlesSurface.eglException("eglSwapBuffers", EGL14.EGL_BAD_SURFACE);
        assertFalse(surface instanceof GraphicsContextLostException);
        assertTrue(surface.getMessage().contains("eglSwapBuffers failed with EGL error 0x300d"));
    }
}
