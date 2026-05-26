package io.github.libfdx.ui;

public final class UiGesture {
    private final String name;
    private final int minimumPressMillis;
    private final float minimumDragDistance;

    private UiGesture(String name, int minimumPressMillis, float minimumDragDistance) {
        this.name = name;
        this.minimumPressMillis = Math.max(0, minimumPressMillis);
        this.minimumDragDistance = Math.max(0.0f, minimumDragDistance);
    }

    public static UiGesture click() {
        return new UiGesture("click", 0, 0.0f);
    }

    public static UiGesture longPress(int minimumPressMillis) {
        return new UiGesture("long-press", minimumPressMillis, 0.0f);
    }

    public static UiGesture drag(float minimumDragDistance) {
        return new UiGesture("drag", 0, minimumDragDistance);
    }

    public String name() {
        return name;
    }

    public int minimumPressMillis() {
        return minimumPressMillis;
    }

    public float minimumDragDistance() {
        return minimumDragDistance;
    }
}
