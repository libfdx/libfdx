package io.github.libfdx.ui;

public final class UiCustomContext {
    private UiMeasureFunction measureFunction;
    private UiDrawFunction drawFunction;

    public void measure(UiMeasureFunction measureFunction) {
        this.measureFunction = measureFunction;
    }

    public void draw(UiDrawFunction drawFunction) {
        this.drawFunction = drawFunction;
    }

    UiMeasureFunction measureFunction() {
        return measureFunction;
    }

    UiDrawFunction drawFunction() {
        return drawFunction;
    }
}
