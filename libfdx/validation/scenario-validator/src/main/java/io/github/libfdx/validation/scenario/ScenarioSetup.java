package io.github.libfdx.validation.scenario;

@FunctionalInterface
public interface ScenarioSetup<T> {
    T create();
}
