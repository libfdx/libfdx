package io.github.libfdx.graphics;

public final class LoadOp {
    private static final LoadOp LOAD = new LoadOp(false, 0.0f, 0.0f, 0.0f, 0.0f);

    private final boolean clear;
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    private LoadOp(boolean clear, float red, float green, float blue, float alpha) {
        this.clear = clear;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    public static LoadOp clear(float red, float green, float blue, float alpha) {
        return new LoadOp(true, red, green, blue, alpha);
    }

    public static LoadOp load() {
        return LOAD;
    }

    public boolean isClear() {
        return clear;
    }

    public float red() {
        return red;
    }

    public float green() {
        return green;
    }

    public float blue() {
        return blue;
    }

    public float alpha() {
        return alpha;
    }
}
