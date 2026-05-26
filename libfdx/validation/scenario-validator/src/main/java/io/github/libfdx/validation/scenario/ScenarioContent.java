package io.github.libfdx.validation.scenario;

@FunctionalInterface
public interface ScenarioContent<T> {
    void build(T setup);
}
