package io.github.libfdx.validation.scenario;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents a scenario host.
 *
 * @author xpenatan
 */
public final class ScenarioHost {
    private final Map<Class<?>, Object> probes = new LinkedHashMap<Class<?>, Object>();
    private final Map<String, ScenarioCapture> captures = new LinkedHashMap<String, ScenarioCapture>();
    private final ScenarioEvents events = new ScenarioEvents();
    private ScenarioFrameDriver frameDriver;
    private ScenarioInputDriver inputDriver;
    private ScenarioCaptureDriver captureDriver;
    private Object screen;
    private long frameDeltaMillis = 16L;
    private long elapsedMillis;
    private int frame;
    private float pointerX;
    private float pointerY;

    private ScenarioHost() {
    }

    /**
     * Creates a scenario host.
     *
     * @return a new scenario host
     */
    public static ScenarioHost create() {
        return new ScenarioHost();
    }

    /**
     * Returns the events.
     *
     * @return the events
     */
    public ScenarioEvents events() {
        return events;
    }

    /**
     * Sets the frame driver and returns this scenario host.
     *
     * @param frameDriver the frame driver
     * @return this scenario host for chaining
     */
    public ScenarioHost frameDriver(ScenarioFrameDriver frameDriver) {
        this.frameDriver = frameDriver;
        return this;
    }

    /**
     * Returns the frame driver.
     *
     * @return the frame driver
     */
    public ScenarioFrameDriver frameDriver() {
        return frameDriver;
    }

    /**
     * Sets the input driver and returns this scenario host.
     *
     * @param inputDriver the input driver
     * @return this scenario host for chaining
     */
    public ScenarioHost inputDriver(ScenarioInputDriver inputDriver) {
        this.inputDriver = inputDriver;
        return this;
    }

    /**
     * Returns the input driver.
     *
     * @return the input driver
     */
    public ScenarioInputDriver inputDriver() {
        return inputDriver;
    }

    /**
     * Sets the capture driver and returns this scenario host.
     *
     * @param captureDriver the capture driver
     * @return this scenario host for chaining
     */
    public ScenarioHost captureDriver(ScenarioCaptureDriver captureDriver) {
        this.captureDriver = captureDriver;
        return this;
    }

    /**
     * Returns the capture driver.
     *
     * @return the capture driver
     */
    public ScenarioCaptureDriver captureDriver() {
        return captureDriver;
    }

    /**
     * Sets the screen and returns this scenario host.
     *
     * @param screen the screen
     * @return this scenario host for chaining
     */
    public ScenarioHost screen(Object screen) {
        this.screen = screen;
        return this;
    }

    /**
     * Returns the screen.
     *
     * @return the screen
     */
    public Object screen() {
        return screen;
    }

    /**
     * Sets the frame delta millis and returns this scenario host.
     *
     * @param frameDeltaMillis the frame delta millis
     * @return this scenario host for chaining
     */
    public ScenarioHost frameDeltaMillis(long frameDeltaMillis) {
        if (frameDeltaMillis <= 0L) {
            throw new IllegalArgumentException("Frame delta must be positive.");
        }
        this.frameDeltaMillis = frameDeltaMillis;
        return this;
    }

    /**
     * Returns the frame delta millis.
     *
     * @return the frame delta millis
     */
    public long frameDeltaMillis() {
        return frameDeltaMillis;
    }

    /**
     * Returns the frame.
     *
     * @return the frame
     */
    public int frame() {
        return frame;
    }

    /**
     * Returns the elapsed millis.
     *
     * @return the elapsed millis
     */
    public long elapsedMillis() {
        return elapsedMillis;
    }

    /**
     * Sets the pointer and returns this scenario host.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return this scenario host for chaining
     */
    public ScenarioHost pointer(float x, float y) {
        this.pointerX = x;
        this.pointerY = y;
        return this;
    }

    /**
     * Returns the pointer x.
     *
     * @return the pointer x
     */
    public float pointerX() {
        return pointerX;
    }

    /**
     * Returns the pointer y.
     *
     * @return the pointer y
     */
    public float pointerY() {
        return pointerY;
    }

    /**
     * Registers the probe.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @param probe the probe
     * @return this scenario host for chaining
     */
    public <T> ScenarioHost registerProbe(Class<T> type, T probe) {
        if (type == null) {
            throw new IllegalArgumentException("Probe type cannot be null.");
        }
        if (probe == null) {
            probes.remove(type);
        } else {
            probes.put(type, probe);
        }
        return this;
    }

    /**
     * Runs the probe step.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @return the probe
     */
    public <T> T probe(Class<T> type) {
        Object probe = probes.get(type);
        return type != null && type.isInstance(probe) ? type.cast(probe) : null;
    }

    /**
     * Runs the request capture step.
     *
     * @param name the name
     * @param context the context
     * @return the request capture
     */
    public ScenarioCapture requestCapture(String name, ScenarioContext context) {
        ScenarioCapture capture = captureDriver != null
                ? captureDriver.capture(name, context)
                : new ScenarioCapture(name, frame, elapsedMillis, null);
        if (capture != null) {
            captures.put(capture.name(), capture);
            events.emit("capture.ready:" + capture.name());
        }
        return capture;
    }

    /**
     * Runs the capture step.
     *
     * @param name the name
     * @return the capture
     */
    public ScenarioCapture capture(String name) {
        return captures.get(name);
    }

    /**
     * Returns the captures.
     *
     * @return the captures
     */
    public List<ScenarioCapture> captures() {
        if (captures.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<ScenarioCapture>(captures.values()));
    }

    /**
     * Runs the clear scenario state step.
     */
    public void clearScenarioState() {
        events.clear();
        captures.clear();
    }

    /**
     * Runs the advance frame step.
     *
     * @param context the context
     */
    public void advanceFrame(ScenarioContext context) {
        frame++;
        elapsedMillis += frameDeltaMillis;
        if (frameDriver != null) {
            frameDriver.advance(context);
        }
    }

    /**
     * Runs the run step.
     *
     * @param scenario the scenario
     * @return the run
     */
    public ScenarioResult run(Scenario scenario) {
        clearScenarioState();
        return scenario.run(this);
    }

    /**
     * Runs the run step.
     *
     * @param catalog the catalog
     * @param selection the selection
     * @return the run
     */
    public ScenarioReport run(ScenarioCatalog catalog, String selection) {
        ScenarioReport report = new ScenarioReport();
        for (Scenario scenario : catalog.select(selection)) {
            report.add(run(scenario));
        }
        return report;
    }
}
