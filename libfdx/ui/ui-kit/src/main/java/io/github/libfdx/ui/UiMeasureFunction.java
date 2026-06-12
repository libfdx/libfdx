package io.github.libfdx.ui;

/**
 * Defines the contract for ui measure function implementations.
 *
 * @author xpenatan
 */
public interface UiMeasureFunction {
    /**
     * Runs the measure step.
     *
     * @param constraints the constraints
     * @return the measure
     */
    UiSize measure(UiLayoutConstraints constraints);
}
