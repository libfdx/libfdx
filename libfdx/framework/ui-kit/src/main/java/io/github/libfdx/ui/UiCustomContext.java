package io.github.libfdx.ui;

/**
 * Represents an ui custom context.
 *
 * @author xpenatan
 */
public final class UiCustomContext {
    private UiMeasureFunction measureFunction;
    private UiDrawFunction drawFunction;

    /**
     * Runs the measure step.
     *
     * @param measureFunction the measure function
     */
    public void measure(UiMeasureFunction measureFunction) {
        this.measureFunction = measureFunction;
    }

    /**
     * Draws the current content.
     *
     * @param drawFunction the draw function
     */
    public void draw(UiDrawFunction drawFunction) {
        this.drawFunction = drawFunction;
    }

    UiMeasureFunction measureFunction() {
        return measureFunction;
    }

    UiDrawFunction drawFunction() {
        return drawFunction;
    }

    void reset() {
        measureFunction = null;
        drawFunction = null;
    }
}
