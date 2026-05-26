package io.github.libfdx.validation.scenario;

@FunctionalInterface
public interface ScenarioCaptureDriver {
    ScenarioCapture capture(String name, ScenarioContext context);
}
