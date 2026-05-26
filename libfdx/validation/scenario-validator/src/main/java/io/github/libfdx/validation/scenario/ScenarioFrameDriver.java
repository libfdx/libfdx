package io.github.libfdx.validation.scenario;

@FunctionalInterface
public interface ScenarioFrameDriver {
    void advance(ScenarioContext context);
}
