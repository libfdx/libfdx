package io.github.libfdx.validation.scenario;

import java.util.Collections;
import java.util.List;

public final class ScenarioResult {
    private final String scenarioName;
    private final boolean passed;
    private final String operationName;
    private final int operationIndex;
    private final String message;
    private final int frame;
    private final long elapsedMillis;
    private final List<String> operationNames;
    private final List<String> recentEvents;
    private final List<ScenarioCapture> captures;
    private final boolean visualBaselineRequired;

    private ScenarioResult(String scenarioName, boolean passed, String operationName, int operationIndex,
            String message, int frame, long elapsedMillis, List<String> operationNames, List<String> recentEvents,
            List<ScenarioCapture> captures, boolean visualBaselineRequired) {
        this.scenarioName = scenarioName;
        this.passed = passed;
        this.operationName = operationName;
        this.operationIndex = operationIndex;
        this.message = message;
        this.frame = frame;
        this.elapsedMillis = elapsedMillis;
        this.operationNames = operationNames != null ? operationNames : Collections.<String>emptyList();
        this.recentEvents = recentEvents != null ? recentEvents : Collections.<String>emptyList();
        this.captures = captures != null ? captures : Collections.<ScenarioCapture>emptyList();
        this.visualBaselineRequired = visualBaselineRequired;
    }

    public static ScenarioResult passed(String scenarioName, int frame, long elapsedMillis) {
        return new ScenarioResult(scenarioName, true, null, -1, null, frame, elapsedMillis,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.<ScenarioCapture>emptyList(), false);
    }

    public static ScenarioResult failed(String scenarioName, String operationName, int operationIndex, String message,
            int frame, long elapsedMillis) {
        return new ScenarioResult(scenarioName, false, operationName, operationIndex, message, frame, elapsedMillis,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.<ScenarioCapture>emptyList(), false);
    }

    static ScenarioResult passed(String scenarioName, int frame, long elapsedMillis, List<String> operationNames,
            List<String> recentEvents, List<ScenarioCapture> captures, boolean visualBaselineRequired) {
        return new ScenarioResult(scenarioName, true, null, -1, null, frame, elapsedMillis,
                operationNames, recentEvents, captures, visualBaselineRequired);
    }

    static ScenarioResult failed(String scenarioName, String operationName, int operationIndex, String message,
            int frame, long elapsedMillis, List<String> operationNames, List<String> recentEvents,
            List<ScenarioCapture> captures, boolean visualBaselineRequired) {
        return new ScenarioResult(scenarioName, false, operationName, operationIndex, message, frame, elapsedMillis,
                operationNames, recentEvents, captures, visualBaselineRequired);
    }

    public String scenarioName() {
        return scenarioName;
    }

    public boolean passed() {
        return passed;
    }

    public String operationName() {
        return operationName;
    }

    public int operationIndex() {
        return operationIndex;
    }

    public String message() {
        return message;
    }

    public int frame() {
        return frame;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public List<String> operationNames() {
        return operationNames;
    }

    public List<String> recentEvents() {
        return recentEvents;
    }

    public List<ScenarioCapture> captures() {
        return captures;
    }

    public boolean visualBaselineRequired() {
        return visualBaselineRequired;
    }
}
