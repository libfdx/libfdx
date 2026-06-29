package io.github.libfdx.validation.scenario;

/**
 * Defines the contract for scenario capture driver implementations.
 *
 * @author xpenatan
 */
@FunctionalInterface
public interface ScenarioCaptureDriver {
    /**
     * Runs the capture step.
     *
     * @param name the name
     * @param context the context
     * @return the capture
     */
    ScenarioCapture capture(String name, ScenarioContext context);
}
