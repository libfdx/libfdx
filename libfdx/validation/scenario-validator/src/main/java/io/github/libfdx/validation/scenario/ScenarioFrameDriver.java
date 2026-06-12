package io.github.libfdx.validation.scenario;

/**
 * Defines the contract for scenario frame driver implementations.
 *
 * @author xpenatan
 */
@FunctionalInterface
public interface ScenarioFrameDriver {
    /**
     * Runs the advance step.
     *
     * @param context the context
     */
    void advance(ScenarioContext context);
}
