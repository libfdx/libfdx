package io.github.libfdx.ui;

/**
 * Represents an ui gesture.
 *
 * @author xpenatan
 */
public final class UiGesture {
    private final String name;
    private final int minimumPressMillis;
    private final float minimumDragDistance;

    private UiGesture(String name, int minimumPressMillis, float minimumDragDistance) {
        this.name = name;
        this.minimumPressMillis = Math.max(0, minimumPressMillis);
        this.minimumDragDistance = Math.max(0.0f, minimumDragDistance);
    }

    /**
     * Creates an UI gesture.
     *
     * @return a new UI gesture
     */
    public static UiGesture click() {
        return new UiGesture("click", 0, 0.0f);
    }

    /**
     * Creates an UI gesture.
     *
     * @param minimumPressMillis the minimum press millis
     * @return a new UI gesture
     */
    public static UiGesture longPress(int minimumPressMillis) {
        return new UiGesture("long-press", minimumPressMillis, 0.0f);
    }

    /**
     * Creates an UI gesture.
     *
     * @param minimumDragDistance the minimum drag distance
     * @return a new UI gesture
     */
    public static UiGesture drag(float minimumDragDistance) {
        return new UiGesture("drag", 0, minimumDragDistance);
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the minimum press millis.
     *
     * @return the minimum press millis
     */
    public int minimumPressMillis() {
        return minimumPressMillis;
    }

    /**
     * Returns the minimum drag distance.
     *
     * @return the minimum drag distance
     */
    public float minimumDragDistance() {
        return minimumDragDistance;
    }
}
