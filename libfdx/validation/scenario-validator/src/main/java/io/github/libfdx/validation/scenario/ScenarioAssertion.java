package io.github.libfdx.validation.scenario;

/**
 * Defines the contract for scenario assertion implementations.
 *
 * @author xpenatan
 */
public interface ScenarioAssertion {
    /**
     * Returns the name.
     *
     * @return the name
     */
    String name();

    /**
     * Runs the verify step.
     *
     * @param context the context
     */
    void verify(ScenarioContext context);
}
