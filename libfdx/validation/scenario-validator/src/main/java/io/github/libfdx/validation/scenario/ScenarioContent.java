package io.github.libfdx.validation.scenario;

/**
 * Defines the contract for scenario content implementations.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
@FunctionalInterface
public interface ScenarioContent<T> {
    /**
     * Runs the build step.
     *
     * @param setup the setup
     */
    void build(T setup);
}
