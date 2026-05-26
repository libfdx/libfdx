package io.github.libfdx.validation.scenario;

public interface ScenarioAction {
    String name();

    void perform(ScenarioContext context);
}
