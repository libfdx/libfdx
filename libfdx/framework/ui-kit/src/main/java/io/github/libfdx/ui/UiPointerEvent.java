package io.github.libfdx.ui;

import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerType;

/**
 * Provides UI-space pointer data to an interactive custom surface.
 *
 * <p>The UI root reuses one instance for event delivery. A handler may read this
 * object only for the duration of its callback and must not retain it.</p>
 *
 * @author xpenatan
 */
public final class UiPointerEvent {
    private UiPointerPhase phase = UiPointerPhase.CANCEL;
    private long timeNanos;
    private int pointerId;
    private PointerType pointerType = PointerType.MOUSE;
    private MouseButton button = MouseButton.UNKNOWN;
    private float x;
    private float y;
    private float localX;
    private float localY;
    private float scrollX;
    private float scrollY;
    private float surfaceWidth;
    private float surfaceHeight;
    private boolean inside;
    private boolean captured;
    private boolean focused;

    UiPointerEvent() {
    }

    UiPointerEvent configure(UiPointerPhase phase, long timeNanos, int pointerId, PointerType pointerType,
            MouseButton button, float x, float y, float scrollX, float scrollY, UiRect bounds,
            boolean captured, boolean focused) {
        this.phase = phase != null ? phase : UiPointerPhase.CANCEL;
        this.timeNanos = timeNanos;
        this.pointerId = pointerId;
        this.pointerType = pointerType != null ? pointerType : PointerType.MOUSE;
        this.button = button != null ? button : MouseButton.UNKNOWN;
        this.x = x;
        this.y = y;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        this.localX = bounds != null ? x - bounds.x() : x;
        this.localY = bounds != null ? y - bounds.y() : y;
        this.surfaceWidth = bounds != null ? bounds.width() : 0.0f;
        this.surfaceHeight = bounds != null ? bounds.height() : 0.0f;
        this.inside = bounds != null && bounds.contains(x, y);
        this.captured = captured;
        this.focused = focused;
        return this;
    }

    /**
     * Returns the pointer phase.
     *
     * @return the pointer phase
     */
    public UiPointerPhase phase() {
        return phase;
    }

    /**
     * Returns the event timestamp.
     *
     * @return the timestamp in nanoseconds
     */
    public long timeNanos() {
        return timeNanos;
    }

    /**
     * Returns the pointer ID.
     *
     * @return the pointer ID
     */
    public int pointerId() {
        return pointerId;
    }

    /**
     * Returns the pointer type.
     *
     * @return the pointer type
     */
    public PointerType pointerType() {
        return pointerType;
    }

    /**
     * Returns the pointer button.
     *
     * @return the pointer button
     */
    public MouseButton button() {
        return button;
    }

    /**
     * Returns the horizontal position in UI-root coordinates.
     *
     * @return the horizontal UI coordinate
     */
    public float x() {
        return x;
    }

    /**
     * Returns the vertical position in UI-root coordinates.
     *
     * @return the vertical UI coordinate
     */
    public float y() {
        return y;
    }

    /**
     * Returns the horizontal position relative to the surface.
     *
     * @return the local horizontal coordinate
     */
    public float localX() {
        return localX;
    }

    /**
     * Returns the vertical position relative to the surface.
     *
     * @return the local vertical coordinate
     */
    public float localY() {
        return localY;
    }

    /**
     * Returns the horizontal scroll amount.
     *
     * @return the horizontal scroll amount
     */
    public float scrollX() {
        return scrollX;
    }

    /**
     * Returns the vertical scroll amount.
     *
     * @return the vertical scroll amount
     */
    public float scrollY() {
        return scrollY;
    }

    /**
     * Returns the current surface width.
     *
     * @return the surface width
     */
    public float surfaceWidth() {
        return surfaceWidth;
    }

    /**
     * Returns the current surface height.
     *
     * @return the surface height
     */
    public float surfaceHeight() {
        return surfaceHeight;
    }

    /**
     * Returns whether the pointer is inside the current surface bounds.
     *
     * @return true when the pointer is inside the surface
     */
    public boolean inside() {
        return inside;
    }

    /**
     * Returns whether this surface owned pointer capture before the callback.
     *
     * @return true when the surface owns pointer capture
     */
    public boolean captured() {
        return captured;
    }

    /**
     * Returns whether the surface is focused.
     *
     * @return true when the surface is focused
     */
    public boolean focused() {
        return focused;
    }
}
