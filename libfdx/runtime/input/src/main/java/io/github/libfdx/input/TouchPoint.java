package io.github.libfdx.input;

public final class TouchPoint {
    private final int id;
    private final int x;
    private final int y;
    private final float pressure;

    public TouchPoint(int id, int x, int y, float pressure) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.pressure = pressure;
    }

    public int id() {
        return id;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public float pressure() {
        return pressure;
    }
}
