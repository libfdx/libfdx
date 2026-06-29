package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.input.Input;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.InputCapabilities;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.input.TouchEvent;
import io.github.libfdx.input.TouchPoint;

abstract class CameraInputController3D implements Disposable {
    protected final Camera camera;
    protected final Input input;
    private final InputAdapter processor;
    private CameraInputBindings3D bindings = CameraInputBindings3D.defaults();
    private CameraPointerRegion pointerRegion;
    private Runnable activationListener;
    private boolean enabled = true;
    private boolean keyboardEnabled = true;
    private boolean touchEnabled = true;
    private boolean disposed;
    private boolean dragging;
    private boolean touchDragging;
    private int touchPointerId = -1;
    private int lastX;
    private int lastY;
    private float sensitivityDegrees = 0.32f;
    private boolean invertX;
    private boolean invertY;
    private float pendingYawDegrees;
    private float pendingPitchDegrees;
    private float pendingScrollY;

    CameraInputController3D(Input input, Camera camera) {
        this.input = input;
        this.camera = camera;
        processor = new InputAdapter() {
            @Override
            public boolean pointerDown(PointerEvent event) {
                if (!enabled) {
                    return false;
                }
                if (event.button() == bindings.lookButton() && acceptsPointer(event.x(), event.y())) {
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
                if (event.button() == bindings.lookButton() && dragging && !touchDragging) {
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

    protected final CameraInputBindings3D bindings() {
        return bindings;
    }

    protected CameraInputController3D bindings(CameraInputBindings3D bindings) {
        this.bindings = bindings != null ? bindings : CameraInputBindings3D.defaults();
        return this;
    }

    protected CameraInputController3D pointerRegion(CameraPointerRegion pointerRegion) {
        this.pointerRegion = pointerRegion;
        if (dragging && !acceptsPointer(lastX, lastY)) {
            endDrag();
        }
        return this;
    }

    protected CameraInputController3D activationListener(Runnable activationListener) {
        this.activationListener = activationListener;
        return this;
    }

    protected CameraInputController3D enabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        pendingYawDegrees = 0.0f;
        pendingPitchDegrees = 0.0f;
        pendingScrollY = 0.0f;
        endDrag();
        return this;
    }

    protected final boolean enabled() {
        return enabled;
    }

    protected CameraInputController3D keyboardEnabled(boolean keyboardEnabled) {
        this.keyboardEnabled = keyboardEnabled;
        return this;
    }

    protected CameraInputController3D touchEnabled(boolean touchEnabled) {
        this.touchEnabled = touchEnabled;
        if (!touchEnabled && touchDragging) {
            endDrag();
        }
        return this;
    }

    protected CameraInputController3D sensitivity(float sensitivityDegrees) {
        this.sensitivityDegrees = Math.max(0.0f, sensitivityDegrees);
        return this;
    }

    protected CameraInputController3D invert(boolean invertX, boolean invertY) {
        this.invertX = invertX;
        this.invertY = invertY;
        return this;
    }

    protected final float consumeYawDegrees() {
        float value = pendingYawDegrees;
        pendingYawDegrees = 0.0f;
        return value;
    }

    protected final float consumePitchDegrees() {
        float value = pendingPitchDegrees;
        pendingPitchDegrees = 0.0f;
        return value;
    }

    protected final float consumeScrollY() {
        float value = pendingScrollY;
        pendingScrollY = 0.0f;
        return value;
    }

    protected final boolean hasPointerDelta() {
        return pendingYawDegrees != 0.0f || pendingPitchDegrees != 0.0f;
    }

    protected final void updatePointerState() {
        if (input == null || !enabled) {
            return;
        }
        int x = input.pointerX();
        int y = input.pointerY();
        InputCapabilities capabilities = input.capabilities();
        boolean touchActive = touchEnabled
                && capabilities != null
                && capabilities.supportsTouch()
                && input.isMouseButtonPressed(bindings.touchLookButton())
                && acceptsPointer(x, y);
        if (!dragging && !(acceptsPointer(x, y)
                && (input.isMouseButtonPressed(bindings.lookButton()) || touchActive))) {
            endDrag();
        }
        lastX = x;
        lastY = y;
    }

    protected final boolean key(Key key) {
        return keyboardEnabled && input != null && input.isKeyPressed(key);
    }

    protected final boolean key(Key key, Key alternate) {
        return key(key) || key(alternate);
    }

    protected final boolean fastActive() {
        return key(bindings.fastKey(), bindings.alternateFastKey());
    }

    protected final boolean boostActive() {
        return key(bindings.boostKey(), bindings.alternateBoostKey());
    }

    protected final boolean acceptsPointer(int x, int y) {
        return pointerRegion == null || pointerRegion.contains(x, y);
    }

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
        float yaw = (lastX - x) * sensitivityDegrees;
        float pitch = (y - lastY) * sensitivityDegrees;
        pendingYawDegrees += invertX ? -yaw : yaw;
        pendingPitchDegrees += invertY ? -pitch : pitch;
        lastX = x;
        lastY = y;
    }

    private void endDrag() {
        dragging = false;
        touchDragging = false;
        touchPointerId = -1;
    }

    private void activate() {
        if (activationListener != null) {
            activationListener.run();
        }
    }
}
