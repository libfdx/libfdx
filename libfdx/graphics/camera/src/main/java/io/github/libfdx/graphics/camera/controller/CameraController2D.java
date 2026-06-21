package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.input.Input;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.input.TouchEvent;
import io.github.libfdx.input.TouchPoint;

/**
 * Provides reusable 2D camera pan and zoom input.
 *
 * @author xpenatan
 */
public final class CameraController2D implements Disposable {
    public interface PointerRegion extends CameraPointerRegion {
        @Override
        boolean contains(int x, int y);
    }

    private static final float DEFAULT_ZOOM_SPEED = 0.12f;
    private static final float DEFAULT_MIN_ZOOM = 0.05f;
    private static final float DEFAULT_MAX_ZOOM = 64.0f;

    private final Input input;
    private final Camera camera;
    private final InputAdapter processor;
    private boolean enabled = true;
    private boolean touchEnabled = true;
    private boolean dragging;
    private boolean touchDragging;
    private boolean disposed;
    private int touchPointerId = -1;
    private int lastX;
    private int lastY;
    private float zoomSpeed = DEFAULT_ZOOM_SPEED;
    private float minZoom = DEFAULT_MIN_ZOOM;
    private float maxZoom = DEFAULT_MAX_ZOOM;
    private float pendingScrollY;
    private CameraPointerRegion pointerRegion;
    private Runnable activationListener;

    /**
     * Creates a 2D camera controller.
     *
     * @param input the input
     * @param camera the camera
     */
    public CameraController2D(Input input, Camera camera) {
        if (camera == null) {
            throw new FdxException("CameraController2D camera cannot be null");
        }
        this.input = input;
        this.camera = camera;
        camera.projection(CameraProjection.ORTHOGRAPHIC).direction(0.0f, 0.0f, -1.0f).up(0.0f, 1.0f, 0.0f);
        processor = new InputAdapter() {
            @Override
            public boolean pointerDown(PointerEvent event) {
                if (!enabled) {
                    return false;
                }
                if (event.button() == MouseButton.RIGHT && acceptsPointer(event.x(), event.y())) {
                    activate();
                    beginDrag(event.x(), event.y(), false, -1);
                }
                return false;
            }

            @Override
            public boolean pointerUp(PointerEvent event) {
                if (!enabled) {
                    return false;
                }
                if (event.button() == MouseButton.RIGHT && dragging && !touchDragging) {
                    continueDrag(event.x(), event.y());
                    endDrag();
                }
                return false;
            }

            @Override
            public boolean pointerMoved(PointerEvent event) {
                if (!enabled) {
                    return false;
                }
                if (dragging && !touchDragging) {
                    continueDrag(event.x(), event.y());
                }
                return false;
            }

            @Override
            public boolean scrolled(PointerEvent event) {
                if (!enabled) {
                    return false;
                }
                if (acceptsPointer(event.x(), event.y())) {
                    activate();
                    pendingScrollY += event.scrollY();
                }
                return false;
            }

            @Override
            public boolean touchDown(TouchEvent event) {
                if (!enabled) {
                    return false;
                }
                TouchPoint point = event.point();
                if (touchEnabled && !dragging && point != null && acceptsPointer(point.x(), point.y())) {
                    activate();
                    beginDrag(point.x(), point.y(), true, point.id());
                }
                return false;
            }

            @Override
            public boolean touchUp(TouchEvent event) {
                if (!enabled) {
                    return false;
                }
                TouchPoint point = event.point();
                if (touchDragging && point != null && point.id() == touchPointerId) {
                    continueDrag(point.x(), point.y());
                    endDrag();
                }
                return false;
            }

            @Override
            public boolean touchMoved(TouchEvent event) {
                if (!enabled) {
                    return false;
                }
                TouchPoint point = event.point();
                if (touchDragging && point != null && point.id() == touchPointerId) {
                    continueDrag(point.x(), point.y());
                }
                return false;
            }
        };
        if (input != null) {
            input.addProcessor(processor);
        }
    }

    /**
     * Sets the camera position.
     *
     * @param x the x
     * @param y the y
     * @return this controller
     */
    public CameraController2D position(float x, float y) {
        camera.position(x, y, camera.position().z()).update();
        return this;
    }

    /**
     * Sets the accepted pointer region.
     *
     * @param pointerRegion the pointer region, or null to accept all pointer input
     * @return this controller
     */
    public CameraController2D pointerRegion(CameraPointerRegion pointerRegion) {
        this.pointerRegion = pointerRegion;
        if (dragging && !acceptsPointer(lastX, lastY)) {
            endDrag();
        }
        return this;
    }

    /**
     * Sets a callback invoked when this controller accepts direct pointer input.
     *
     * @param activationListener the activation callback, or null
     * @return this controller
     */
    public CameraController2D activationListener(Runnable activationListener) {
        this.activationListener = activationListener;
        return this;
    }

    /**
     * Sets whether this controller consumes input.
     *
     * @param enabled true to enable input handling
     * @return this controller
     */
    public CameraController2D enabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        pendingScrollY = 0.0f;
        endDrag();
        return this;
    }

    /**
     * Sets whether touch drag input is enabled.
     *
     * @param touchEnabled true to enable touch drag input
     * @return this controller
     */
    public CameraController2D touchEnabled(boolean touchEnabled) {
        this.touchEnabled = touchEnabled;
        if (!touchEnabled && touchDragging) {
            endDrag();
        }
        return this;
    }

    /**
     * Sets the zoom range.
     *
     * @param minZoom the minimum zoom
     * @param maxZoom the maximum zoom
     * @return this controller
     */
    public CameraController2D zoomRange(float minZoom, float maxZoom) {
        if (minZoom <= 0.0f || maxZoom < minZoom || Float.isNaN(minZoom) || Float.isNaN(maxZoom)) {
            throw new FdxException("CameraController2D zoom range is invalid");
        }
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        camera.zoom(clamp(camera.zoom(), minZoom, maxZoom)).update();
        return this;
    }

    /**
     * Sets the zoom speed.
     *
     * @param zoomSpeed the scroll zoom speed
     * @return this controller
     */
    public CameraController2D zoomSpeed(float zoomSpeed) {
        if (zoomSpeed < 0.0f || Float.isNaN(zoomSpeed)) {
            throw new FdxException("CameraController2D zoom speed cannot be negative");
        }
        this.zoomSpeed = zoomSpeed;
        return this;
    }

    /**
     * Updates camera input and applies the camera transform.
     *
     * @param deltaSeconds the frame delta in seconds
     * @return this controller
     */
    public CameraController2D update(float deltaSeconds) {
        if (!enabled) {
            camera.update();
            return this;
        }
        applyScroll();
        camera.update();
        return this;
    }

    /**
     * Releases the input hook.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        if (input != null) {
            input.removeProcessor(processor);
        }
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private void beginDrag(int x, int y, boolean touch, int pointerId) {
        dragging = true;
        touchDragging = touch;
        touchPointerId = pointerId;
        lastX = x;
        lastY = y;
    }

    private void continueDrag(int x, int y) {
        float dx = (lastX - x) * camera.zoom();
        float dy = (y - lastY) * camera.zoom();
        camera.position(camera.position().x() + dx, camera.position().y() + dy, camera.position().z());
        lastX = x;
        lastY = y;
    }

    private void endDrag() {
        dragging = false;
        touchDragging = false;
        touchPointerId = -1;
    }

    private void applyScroll() {
        if (pendingScrollY == 0.0f) {
            return;
        }
        float nextZoom = camera.zoom() * (1.0f + pendingScrollY * zoomSpeed);
        camera.zoom(clamp(nextZoom, minZoom, maxZoom));
        pendingScrollY = 0.0f;
    }

    private boolean acceptsPointer(int x, int y) {
        return pointerRegion == null || pointerRegion.contains(x, y);
    }

    private void activate() {
        if (activationListener != null) {
            activationListener.run();
        }
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}
