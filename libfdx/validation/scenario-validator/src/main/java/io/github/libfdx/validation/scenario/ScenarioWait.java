package io.github.libfdx.validation.scenario;

public interface ScenarioWait {
    String name();

    boolean complete(ScenarioContext context, long startMillis, int startFrame);

    long timeoutMillis();

    int timeoutFrames();

    String lastObservedValue();
}
