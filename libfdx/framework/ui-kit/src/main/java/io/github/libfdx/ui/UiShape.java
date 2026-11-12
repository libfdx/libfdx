package io.github.libfdx.ui;

/** Immutable surface geometry in UI units. Circles fit inside the shorter side of their bounds. */
public final class UiShape {
    private static final UiShape RECTANGLE = new UiShape(0, false);
    private static final UiShape CIRCLE = new UiShape(0, true);
    private final float radius;
    private final boolean circle;

    private UiShape(float radius, boolean circle) {
        this.radius = radius;
        this.circle = circle;
    }

    public static UiShape rectangle() {
        return RECTANGLE;
    }

    /** Creates rounded corners whose radius is clamped to half the shorter side. */
    public static UiShape rounded(float radius) {
        if (!Float.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("Corner radius must be finite and nonnegative");
        }
        return radius == 0 ? RECTANGLE : new UiShape(radius, false);
    }

    public static UiShape circle() {
        return CIRCLE;
    }

    /** Tests a point in the same coordinate system as the supplied bounds. */
    public boolean contains(UiRect bounds, float x, float y) {
        boolean withinCircleHeight = !circle || Math.abs(y - bounds.y() - bounds.height() * 0.5f)
                <= Math.min(bounds.width(), bounds.height()) * 0.5f;
        return bounds.width() > 0 && bounds.height() > 0 && bounds.contains(x, y)
                && withinCircleHeight && x >= left(bounds, y) && x <= right(bounds, y);
    }

    boolean rectangular() {
        return this == RECTANGLE;
    }

    float left(UiRect bounds, float y) {
        return bounds.x() + inset(bounds, y);
    }

    float right(UiRect bounds, float y) {
        return bounds.right() - inset(bounds, y);
    }

    private float inset(UiRect bounds, float y) {
        float halfWidth = bounds.width() * 0.5f;
        float halfHeight = bounds.height() * 0.5f;
        float r = circle ? Math.min(halfWidth, halfHeight) : Math.min(radius, Math.min(halfWidth, halfHeight));
        float distance = Math.abs(y - bounds.y() - halfHeight);
        if (circle) {
            if (distance > r) return halfWidth;
            return halfWidth - (float) Math.sqrt(Math.max(0, r * r - distance * distance));
        }
        float cornerDistance = Math.max(0, distance - halfHeight + r);
        return r - (float) Math.sqrt(Math.max(0, r * r - cornerDistance * cornerDistance));
    }

}
