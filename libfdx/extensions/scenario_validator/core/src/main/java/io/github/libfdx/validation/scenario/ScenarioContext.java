package io.github.libfdx.validation.scenario;

import java.util.Objects;

/**
 * Represents a scenario context.
 *
 * @author xpenatan
 */
public final class ScenarioContext {
    private final ScenarioHost host;
    private final Scenario scenario;
    private final ScenarioValidationConfig validationConfig;

    ScenarioContext(ScenarioHost host, Scenario scenario, ScenarioValidationConfig validationConfig) {
        this.host = host;
        this.scenario = scenario;
        this.validationConfig = validationConfig != null ? validationConfig : ScenarioValidationConfig.defaults();
    }

    /**
     * Returns the host.
     *
     * @return the host
     */
    public ScenarioHost host() {
        return host;
    }

    /**
     * Returns the scenario.
     *
     * @return the scenario
     */
    public Scenario scenario() {
        return scenario;
    }

    ScenarioValidationConfig validationConfig() {
        return validationConfig;
    }

    /**
     * Returns the events.
     *
     * @return the events
     */
    public ScenarioEvents events() {
        return host.events();
    }

    /**
     * Runs the emit step.
     *
     * @param event the event
     */
    public void emit(String event) {
        host.events().emit(event);
    }

    /**
     * Runs the probe step.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @return the probe
     */
    public <T> T probe(Class<T> type) {
        return host.probe(type);
    }

    /**
     * Runs the require probe step.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @return the require probe
     */
    public <T> T requireProbe(Class<T> type) {
        T probe = host.probe(type);
        if (probe == null) {
            fail("Missing scenario probe: " + type.getName());
        }
        return probe;
    }

    /**
     * Runs the request capture step.
     *
     * @param name the name
     */
    public void requestCapture(String name) {
        host.requestCapture(name, this);
    }

    /**
     * Returns whether this instance has capture.
     *
     * @param name the name
     * @return true if this instance has capture; false otherwise
     */
    public boolean hasCapture(String name) {
        return host.capture(name) != null;
    }

    /**
     * Runs the capture step.
     *
     * @param name the name
     * @return the capture
     */
    public ScenarioCapture capture(String name) {
        return host.capture(name);
    }

    /**
     * Returns the frame.
     *
     * @return the frame
     */
    public int frame() {
        return host.frame();
    }

    /**
     * Returns the elapsed millis.
     *
     * @return the elapsed millis
     */
    public long elapsedMillis() {
        return host.elapsedMillis();
    }

    /**
     * Runs the assert true step.
     *
     * @param condition the condition
     * @param message the message
     */
    public void assertTrue(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }

    /**
     * Runs the assert equals step.
     *
     * @param expected the expected
     * @param actual the actual
     * @param message the message
     */
    public void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            fail(message + " expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    /**
     * Runs the fail step.
     *
     * @param message the message
     */
    public void fail(String message) {
        throw new ScenarioFailure(message);
    }
}
