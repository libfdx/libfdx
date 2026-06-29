package io.github.libfdx.validation.scenario;

/**
 * Defines the contract for scenario callback implementations.
 *
 * @author xpenatan
 */
@FunctionalInterface
public interface ScenarioCallback {
    /**
     * Runs the run step.
     *
     * @param context the context
     */
    void run(ScenarioContext context);
}
