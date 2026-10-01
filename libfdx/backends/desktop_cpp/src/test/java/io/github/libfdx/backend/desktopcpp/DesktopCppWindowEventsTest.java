package io.github.libfdx.backend.desktopcpp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DesktopCppWindowEventsTest {
    @Test
    void resizeAndScaleEventsUpdateIndependentDisplayDimensions() {
        var display = new DesktopCppApplicationBackend.WindowDisplay(0, "test");
        dispatch(display, 7, 960, 640);
        dispatch(display, 8, 1920, 1280);
        dispatch(display, 9, 2, 2);
        assertEquals(960, display.width());
        assertEquals(640, display.height());
        assertEquals(1920, display.framebufferWidth());
        assertEquals(1280, display.framebufferHeight());
        assertEquals(2, display.contentScaleX());
        assertEquals(2, display.contentScaleY());

        dispatch(display, 8, 0, 0);
        assertEquals(960, display.width());
        assertEquals(0, display.framebufferWidth());
        dispatch(display, 7, 800, 600);
        dispatch(display, 8, 1200, 900);
        dispatch(display, 9, 1.5, 1.5);
        assertEquals(800, display.width());
        assertEquals(600, display.height());
        assertEquals(1200, display.framebufferWidth());
        assertEquals(900, display.framebufferHeight());
        assertEquals(1.5f, display.contentScaleX());
    }

    @Test
    void invalidScaleEventsKeepTheExistingFallback() {
        var display = new DesktopCppApplicationBackend.WindowDisplay(0, "test");
        dispatch(display, 9, Double.NaN, 0);
        assertEquals(1, display.contentScaleX());
        assertEquals(1, display.contentScaleY());
        dispatch(display, 9, -1, Double.POSITIVE_INFINITY);
        assertEquals(1, display.contentScaleX());
        assertEquals(1, display.contentScaleY());
    }

    private static void dispatch(
            DesktopCppApplicationBackend.WindowDisplay display, int kind, double x, double y) {
        DesktopCppInput.dispatch(new double[] {kind, x, y, 0, 0, 0, 0, 0}, null, display);
    }
}
