package io.github.libfdx.validation.scenario;

/**
 * Represents a scenario validation cell.
 *
 * @author xpenatan
 */
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

    /**
     * Creates a scenario validation cell.
     *
     * @param platform the platform
     * @param api the API
     * @return a new scenario validation cell
     */
    public static ScenarioValidationCell pass(String platform, String api) {
        return new ScenarioValidationCell(platform, api, ScenarioValidationCellStatus.PASS, null);
    }

    /**
     * Creates a scenario validation cell.
     *
     * @param platform the platform
     * @param api the API
     * @param reason the reason
     * @return a new scenario validation cell
     */
    public static ScenarioValidationCell blocked(String platform, String api, String reason) {
        return new ScenarioValidationCell(platform, api, ScenarioValidationCellStatus.BLOCKED, reason);
    }

    /**
     * Creates a scenario validation cell.
     *
     * @param platform the platform
     * @param api the API
     * @param reason the reason
     * @return a new scenario validation cell
     */
    public static ScenarioValidationCell notRun(String platform, String api, String reason) {
        return new ScenarioValidationCell(platform, api, ScenarioValidationCellStatus.NOT_RUN, reason);
    }

    /**
     * Returns the platform.
     *
     * @return the platform
     */
    public String platform() {
        return platform;
    }

    /**
     * Returns the API.
     *
     * @return the API
     */
    public String api() {
        return api;
    }

    /**
     * Returns the status.
     *
     * @return the status
     */
    public ScenarioValidationCellStatus status() {
        return status;
    }

    /**
     * Returns the reason.
     *
     * @return the reason
     */
    public String reason() {
        return reason;
    }
}
