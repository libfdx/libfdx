package io.github.libfdx.validation.scenario;

/**
 * Represents a scenario capture.
 *
 * @author xpenatan
 */
public final class ScenarioCapture {
    private final String name;
    private final int frame;
    private final long elapsedMillis;
    private final String path;
    private final String baselinePath;
    private final boolean baselineRequired;
    private final Boolean baselineMatched;
    private final String comparisonMessage;

    /**
     * Creates a scenario capture.
     *
     * @param name the name
     * @param frame the frame index
     * @param elapsedMillis the elapsed millis
     * @param path the asset or file path
     */
    public ScenarioCapture(String name, int frame, long elapsedMillis, String path) {
        this(name, frame, elapsedMillis, path, null, false, null, null);
    }

    /**
     * Creates a scenario capture.
     *
     * @param name the name
     * @param frame the frame index
     * @param elapsedMillis the elapsed millis
     * @param path the asset or file path
     * @param baselinePath the baseline path
     * @param baselineRequired the baseline required
     * @param baselineMatched the baseline matched
     * @param comparisonMessage the comparison message
     */
    public ScenarioCapture(String name, int frame, long elapsedMillis, String path, String baselinePath,
            boolean baselineRequired, Boolean baselineMatched, String comparisonMessage) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Capture name cannot be empty.");
        }
        this.name = name;
        this.frame = frame;
        this.elapsedMillis = elapsedMillis;
        this.path = path;
        this.baselinePath = baselinePath;
        this.baselineRequired = baselineRequired;
        this.baselineMatched = baselineMatched;
        this.comparisonMessage = comparisonMessage;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
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
     * Returns the path.
     *
     * @return the path
     */
    public String path() {
        return path;
    }

    /**
     * Returns the baseline path.
     *
     * @return the baseline path
     */
    public String baselinePath() {
        return baselinePath;
    }

    /**
     * Returns the baseline required.
     *
     * @return true if baseline required succeeds or is active; false otherwise
     */
    public boolean baselineRequired() {
        return baselineRequired;
    }

    /**
     * Returns the baseline matched.
     *
     * @return the baseline matched
     */
    public Boolean baselineMatched() {
        return baselineMatched;
    }

    /**
     * Returns the comparison message.
     *
     * @return the comparison message
     */
    public String comparisonMessage() {
        return comparisonMessage;
    }

    /**
     * Sets the baseline and returns this scenario capture.
     *
     * @param baselinePath the baseline path
     * @param required the required
     * @param matched the matched
     * @param message the message
     * @return this scenario capture for chaining
     */
    public ScenarioCapture baseline(String baselinePath, boolean required, Boolean matched, String message) {
        return new ScenarioCapture(name, frame, elapsedMillis, path, baselinePath, required, matched, message);
    }
}
