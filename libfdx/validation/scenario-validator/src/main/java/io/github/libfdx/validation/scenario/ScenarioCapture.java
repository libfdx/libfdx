package io.github.libfdx.validation.scenario;

public final class ScenarioCapture {
    private final String name;
    private final int frame;
    private final long elapsedMillis;
    private final String path;
    private final String baselinePath;
    private final boolean baselineRequired;
    private final Boolean baselineMatched;
    private final String comparisonMessage;

    public ScenarioCapture(String name, int frame, long elapsedMillis, String path) {
        this(name, frame, elapsedMillis, path, null, false, null, null);
    }

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

    public String name() {
        return name;
    }

    public int frame() {
        return frame;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public String path() {
        return path;
    }

    public String baselinePath() {
        return baselinePath;
    }

    public boolean baselineRequired() {
        return baselineRequired;
    }

    public Boolean baselineMatched() {
        return baselineMatched;
    }

    public String comparisonMessage() {
        return comparisonMessage;
    }

    public ScenarioCapture baseline(String baselinePath, boolean required, Boolean matched, String message) {
        return new ScenarioCapture(name, frame, elapsedMillis, path, baselinePath, required, matched, message);
    }
}
