package io.github.libfdx.validation.scenario;

/**
 * Defines the contract for scenario action implementations.
 *
 * @author xpenatan
 */
public interface ScenarioAction {
    /**
     * Returns the name.
     *
     * @return the name
     */
    String name();

    /**
     * Runs the perform step.
     *
     * @param context the context
     */
    void perform(ScenarioContext context);
}
