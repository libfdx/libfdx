package io.github.libfdx.validation.scenario;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;

/**
 * Represents the result of a scenario operation.
 *
 * @author xpenatan
 */
public final class ScenarioResult {
    private final String scenarioName;
    private final boolean passed;
    private final String operationName;
    private final int operationIndex;
    private final String message;
    private final int frame;
    private final long elapsedMillis;
    private final Array<String> operationNames;
    private final Array<String> recentEvents;
    private final Array<ScenarioCapture> captures;
    private final boolean visualBaselineRequired;

    private ScenarioResult(String scenarioName, boolean passed, String operationName, int operationIndex,
            String message, int frame, long elapsedMillis, ArrayView<String> operationNames,
            ArrayView<String> recentEvents, ArrayView<ScenarioCapture> captures, boolean visualBaselineRequired) {
        this.scenarioName = scenarioName;
        this.passed = passed;
        this.operationName = operationName;
        this.operationIndex = operationIndex;
        this.message = message;
        this.frame = frame;
        this.elapsedMillis = elapsedMillis;
        this.operationNames = copy(operationNames);
        this.recentEvents = copy(recentEvents);
        this.captures = copy(captures);
        this.visualBaselineRequired = visualBaselineRequired;
    }

    /**
     * Creates a scenario result.
     *
     * @param scenarioName the scenario name
     * @param frame the frame index
     * @param elapsedMillis the elapsed millis
     * @return a new scenario result
     */
    public static ScenarioResult passed(String scenarioName, int frame, long elapsedMillis) {
        return new ScenarioResult(scenarioName, true, null, -1, null, frame, elapsedMillis,
                Array.<String>emptyView(), Array.<String>emptyView(),
                Array.<ScenarioCapture>emptyView(), false);
    }

    /**
     * Creates a scenario result.
     *
     * @param scenarioName the scenario name
     * @param operationName the operation name
     * @param operationIndex the operation index
     * @param message the message
     * @param frame the frame index
     * @param elapsedMillis the elapsed millis
     * @return a new scenario result
     */
    public static ScenarioResult failed(String scenarioName, String operationName, int operationIndex, String message,
            int frame, long elapsedMillis) {
        return new ScenarioResult(scenarioName, false, operationName, operationIndex, message, frame, elapsedMillis,
                Array.<String>emptyView(), Array.<String>emptyView(),
                Array.<ScenarioCapture>emptyView(), false);
    }

    static ScenarioResult passed(String scenarioName, int frame, long elapsedMillis,
            ArrayView<String> operationNames, ArrayView<String> recentEvents, ArrayView<ScenarioCapture> captures,
            boolean visualBaselineRequired) {
        return new ScenarioResult(scenarioName, true, null, -1, null, frame, elapsedMillis,
                operationNames, recentEvents, captures, visualBaselineRequired);
    }

    static ScenarioResult failed(String scenarioName, String operationName, int operationIndex, String message,
            int frame, long elapsedMillis, ArrayView<String> operationNames, ArrayView<String> recentEvents,
            ArrayView<ScenarioCapture> captures, boolean visualBaselineRequired) {
        return new ScenarioResult(scenarioName, false, operationName, operationIndex, message, frame, elapsedMillis,
                operationNames, recentEvents, captures, visualBaselineRequired);
    }

    /**
     * Returns the scenario name.
     *
     * @return the scenario name
     */
    public String scenarioName() {
        return scenarioName;
    }

    /**
     * Returns the passed.
     *
     * @return true if passed succeeds or is active; false otherwise
     */
    public boolean passed() {
        return passed;
    }

    /**
     * Returns the operation name.
     *
     * @return the operation name
     */
    public String operationName() {
        return operationName;
    }

    /**
     * Returns the operation index.
     *
     * @return the operation index
     */
    public int operationIndex() {
        return operationIndex;
    }

    /**
     * Returns the message.
     *
     * @return the message
     */
    public String message() {
        return message;
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
     * Returns the operation names.
     *
     * @return the operation names
     */
    public ArrayView<String> operationNames() {
        return operationNames.view();
    }

    /**
     * Returns the recent events.
     *
     * @return the recent events
     */
    public ArrayView<String> recentEvents() {
        return recentEvents.view();
    }

    /**
     * Returns the captures.
     *
     * @return the captures
     */
    public ArrayView<ScenarioCapture> captures() {
        return captures.view();
    }

    /**
     * Returns the visual baseline required.
     *
     * @return true if visual baseline required succeeds or is active; false otherwise
     */
    public boolean visualBaselineRequired() {
        return visualBaselineRequired;
    }

    private static <T> Array<T> copy(ArrayView<T> values) {
        return values != null ? new Array<T>(values) : new Array<T>(0);
    }
}
