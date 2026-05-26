package io.github.libfdx.validation.scenario;

public final class ScenarioValidationCell {
    private final String platform;
    private final String api;
    private final ScenarioValidationCellStatus status;
    private final String reason;

    private ScenarioValidationCell(String platform, String api, ScenarioValidationCellStatus status, String reason) {
        this.platform = platform != null ? platform : "";
        this.api = api != null ? api : "";
        this.status = status != null ? status : ScenarioValidationCellStatus.NOT_RUN;
        this.reason = reason;
    }

    public static ScenarioValidationCell pass(String platform, String api) {
        return new ScenarioValidationCell(platform, api, ScenarioValidationCellStatus.PASS, null);
    }

    public static ScenarioValidationCell blocked(String platform, String api, String reason) {
        return new ScenarioValidationCell(platform, api, ScenarioValidationCellStatus.BLOCKED, reason);
    }

    public static ScenarioValidationCell notRun(String platform, String api, String reason) {
        return new ScenarioValidationCell(platform, api, ScenarioValidationCellStatus.NOT_RUN, reason);
    }

    public String platform() {
        return platform;
    }

    public String api() {
        return api;
    }

    public ScenarioValidationCellStatus status() {
        return status;
    }

    public String reason() {
        return reason;
    }
}
