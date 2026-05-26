package io.github.libfdx.validation.scenario;

public interface ScenarioAssertion {
    String name();

    void verify(ScenarioContext context);
}
