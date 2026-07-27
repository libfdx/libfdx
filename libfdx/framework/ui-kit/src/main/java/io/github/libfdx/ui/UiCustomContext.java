package io.github.libfdx.ui;

/**
 * Represents an ui custom context.
 *
 * @author xpenatan
 */
public final class UiCustomContext {
    private UiMeasureFunction measureFunction;
    private UiDrawFunction drawFunction;
    private UiSurfaceInput surfaceInput;

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

    /**
     * Sets the input handler that makes this custom node an interactive surface.
     *
     * <p>Use a focusable modifier when the surface also needs key or text input.
     * Returning {@link UiPointerResult#CAPTURE} from the pointer callback keeps
     * drag operations routed to this surface outside its bounds.</p>
     *
     * @param surfaceInput the surface input handler
     */
    public void input(UiSurfaceInput surfaceInput) {
        this.surfaceInput = surfaceInput;
    }

    UiMeasureFunction measureFunction() {
        return measureFunction;
    }

    UiDrawFunction drawFunction() {
        return drawFunction;
    }

    UiSurfaceInput surfaceInput() {
        return surfaceInput;
    }

    void reset() {
        measureFunction = null;
        drawFunction = null;
        surfaceInput = null;
    }
}
