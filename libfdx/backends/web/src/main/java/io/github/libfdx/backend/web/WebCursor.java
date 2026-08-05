package io.github.libfdx.backend.web;

import io.github.libfdx.input.Cursor;
import io.github.libfdx.input.CursorShape;
import org.teavm.jso.JSBody;
import org.teavm.jso.dom.html.HTMLCanvasElement;

/** Browser-backed cursor state, including Pointer Lock capture. */
final class WebCursor implements Cursor {
    private final HTMLCanvasElement canvas;
    private boolean visible = true;
    private boolean captureIntent;
    private boolean requestPending;
    private boolean captureBlocked;
    private CursorShape shape = CursorShape.DEFAULT;

    WebCursor(HTMLCanvasElement canvas) {
        this.canvas = canvas;
        applyCursorStyle();
    }

    /** Allows a failed or user-cancelled lock to be requested by a new gesture. */
    void beginUserGesture() {
        if (!pointerLocked() && !requestPending) {
            captureBlocked = false;
        }
    }

    /** Synchronizes the cursor contract after the browser changes Pointer Lock state. */
    void pointerLockChanged() {
        requestPending = false;
        if (pointerLocked()) {
            captureBlocked = false;
            if (!captureIntent) {
                exitPointerLock(canvas);
            }
        } else if (captureIntent) {
            captureIntent = false;
            captureBlocked = true;
        }
    }

    /** Handles an asynchronous Pointer Lock request failure. */
    void pointerLockFailed() {
        requestPending = false;
        captureIntent = false;
        captureBlocked = true;
    }

    boolean pointerLocked() {
        return isPointerLocked(canvas);
    }

    void dispose() {
        captureIntent = false;
        requestPending = false;
        captureBlocked = false;
        if (pointerLocked()) {
            exitPointerLock(canvas);
        }
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void visible(boolean visible) {
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
        applyCursorStyle();
    }

    @Override
    public boolean isCaptured() {
        return captureIntent || requestPending || pointerLocked();
    }

    @Override
    public void captured(boolean captured) {
        if (captured) {
            if (captureIntent || requestPending || captureBlocked) {
                return;
            }
            captureIntent = true;
            if (!pointerLocked()) {
                if (!isPointerLockSupported(canvas)) {
                    pointerLockFailed();
                    return;
                }
                requestPending = true;
                try {
                    requestPointerLock(canvas);
                } catch (RuntimeException error) {
                    pointerLockFailed();
                }
            }
            return;
        }

        captureIntent = false;
        requestPending = false;
        captureBlocked = false;
        if (pointerLocked()) {
            exitPointerLock(canvas);
        }
    }

    @Override
    public CursorShape shape() {
        return shape;
    }

    @Override
    public void shape(CursorShape shape) {
        CursorShape actualShape = shape != null ? shape : CursorShape.DEFAULT;
        if (this.shape == actualShape) {
            return;
        }
        this.shape = actualShape;
        applyCursorStyle();
    }

    private void applyCursorStyle() {
        setCursorStyle(canvas, visible ? cssCursor(shape) : "none");
    }

    private static String cssCursor(CursorShape shape) {
        switch (shape) {
            case POINTER: return "pointer";
            case TEXT: return "text";
            case CROSSHAIR: return "crosshair";
            case MOVE: return "move";
            case RESIZE_HORIZONTAL: return "ew-resize";
            case RESIZE_VERTICAL: return "ns-resize";
            case NOT_ALLOWED: return "not-allowed";
            case DEFAULT:
            default: return "default";
        }
    }

    @JSBody(params = { "canvas" }, script =
            "var request = canvas.requestPointerLock || canvas.webkitRequestPointerLock;\n" +
            "if (request) request.call(canvas);")
    private static native void requestPointerLock(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas" }, script =
            "return !!(canvas.requestPointerLock || canvas.webkitRequestPointerLock);")
    private static native boolean isPointerLockSupported(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas" }, script =
            "return document.pointerLockElement === canvas || document.webkitPointerLockElement === canvas;")
    private static native boolean isPointerLocked(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas" }, script =
            "if (document.pointerLockElement === canvas || document.webkitPointerLockElement === canvas) {\n" +
            "  var exit = document.exitPointerLock || document.webkitExitPointerLock;\n" +
            "  if (exit) exit.call(document);\n" +
            "}")
    private static native void exitPointerLock(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas", "cursor" }, script = "canvas.style.cursor = cursor;")
    private static native void setCursorStyle(HTMLCanvasElement canvas, String cursor);
}
