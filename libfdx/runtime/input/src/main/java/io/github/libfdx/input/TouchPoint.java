package io.github.libfdx.input;

/**
 * Represents a touch point.
 *
 * @author xpenatan
 */
public final class TouchPoint {
    private final int id;
    private final int x;
    private final int y;
    private final float pressure;

    /**
     * Creates a touch point.
     *
     * @param id the identifier
     * @param x the x coordinate
     * @param y the y coordinate
     * @param pressure the pressure
     */
    public TouchPoint(int id, int x, int y, float pressure) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.pressure = pressure;
    }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    public int id() {
        return id;
    }

    /**
     * Returns the x.
     *
     * @return the x
     */
    public int x() {
        return x;
    }

    /**
     * Returns the y.
     *
     * @return the y
     */
    public int y() {
        return y;
    }

    /**
     * Returns the pressure.
     *
     * @return the pressure
     */
    public float pressure() {
        return pressure;
    }
}
