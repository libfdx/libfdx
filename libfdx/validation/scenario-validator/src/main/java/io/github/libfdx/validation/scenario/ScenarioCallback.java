package io.github.libfdx.validation.scenario;

@FunctionalInterface
public interface ScenarioCallback {
    void run(ScenarioContext context);
}
