package io.github.libfdx.validation.scenario;

import java.util.Objects;

public final class ScenarioContext {
    private final ScenarioHost host;
    private final Scenario scenario;

    ScenarioContext(ScenarioHost host, Scenario scenario) {
        this.host = host;
        this.scenario = scenario;
    }

    public ScenarioHost host() {
        return host;
    }

    public Scenario scenario() {
        return scenario;
    }

    public ScenarioEvents events() {
        return host.events();
    }

    public void emit(String event) {
        host.events().emit(event);
    }

    public <T> T probe(Class<T> type) {
        return host.probe(type);
    }

    public <T> T requireProbe(Class<T> type) {
        T probe = host.probe(type);
        if (probe == null) {
            fail("Missing scenario probe: " + type.getName());
        }
        return probe;
    }

    public void requestCapture(String name) {
        host.requestCapture(name, this);
    }

    public boolean hasCapture(String name) {
        return host.capture(name) != null;
    }

    public ScenarioCapture capture(String name) {
        return host.capture(name);
    }

    public int frame() {
        return host.frame();
    }

    public long elapsedMillis() {
        return host.elapsedMillis();
    }

    public void assertTrue(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }

    public void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            fail(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    public void fail(String message) {
        throw new ScenarioFailure(message);
    }
}
