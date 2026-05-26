package io.github.libfdx.validation.scenario;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    public static ScenarioHost create() {
        return new ScenarioHost();
    }

    public ScenarioEvents events() {
        return events;
    }

    public ScenarioHost frameDriver(ScenarioFrameDriver frameDriver) {
        this.frameDriver = frameDriver;
        return this;
    }

    public ScenarioFrameDriver frameDriver() {
        return frameDriver;
    }

    public ScenarioHost inputDriver(ScenarioInputDriver inputDriver) {
        this.inputDriver = inputDriver;
        return this;
    }

    public ScenarioInputDriver inputDriver() {
        return inputDriver;
    }

    public ScenarioHost captureDriver(ScenarioCaptureDriver captureDriver) {
        this.captureDriver = captureDriver;
        return this;
    }

    public ScenarioCaptureDriver captureDriver() {
        return captureDriver;
    }

    public ScenarioHost screen(Object screen) {
        this.screen = screen;
        return this;
    }

    public Object screen() {
        return screen;
    }

    public ScenarioHost frameDeltaMillis(long frameDeltaMillis) {
        if (frameDeltaMillis <= 0L) {
            throw new IllegalArgumentException("Frame delta must be positive.");
        }
        this.frameDeltaMillis = frameDeltaMillis;
        return this;
    }

    public long frameDeltaMillis() {
        return frameDeltaMillis;
    }

    public int frame() {
        return frame;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public ScenarioHost pointer(float x, float y) {
        this.pointerX = x;
        this.pointerY = y;
        return this;
    }

    public float pointerX() {
        return pointerX;
    }

    public float pointerY() {
        return pointerY;
    }

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

    public <T> T probe(Class<T> type) {
        Object probe = probes.get(type);
        return type != null && type.isInstance(probe) ? type.cast(probe) : null;
    }

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

    public ScenarioCapture capture(String name) {
        return captures.get(name);
    }

    public List<ScenarioCapture> captures() {
        if (captures.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<ScenarioCapture>(captures.values()));
    }

    public void clearScenarioState() {
        events.clear();
        captures.clear();
    }

    public void advanceFrame(ScenarioContext context) {
        frame++;
        elapsedMillis += frameDeltaMillis;
        if (frameDriver != null) {
            frameDriver.advance(context);
        }
    }

    public ScenarioResult run(Scenario scenario) {
        clearScenarioState();
        return scenario.run(this);
    }

    public ScenarioReport run(ScenarioCatalog catalog, String selection) {
        ScenarioReport report = new ScenarioReport();
        for (Scenario scenario : catalog.select(selection)) {
            report.add(run(scenario));
        }
        return report;
    }
}
