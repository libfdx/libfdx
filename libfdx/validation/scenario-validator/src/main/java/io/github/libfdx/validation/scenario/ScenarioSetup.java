package io.github.libfdx.validation.scenario;

/**
 * Defines the contract for scenario setup implementations.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
@FunctionalInterface
public interface ScenarioSetup<T> {
    /**
     * Returns the create.
     *
     * @return the created value
     */
    T create();
}
