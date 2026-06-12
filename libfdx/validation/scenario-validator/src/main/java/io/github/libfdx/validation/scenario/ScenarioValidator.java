package io.github.libfdx.validation.scenario;

/**
 * Represents a scenario validator.
 *
 * @author xpenatan
 */
public final class ScenarioValidator {
    private final ScenarioHost host;
    private final ScenarioCatalog catalog;
    private ScenarioValidationConfig config = ScenarioValidationConfig.defaults();

    private ScenarioValidator(ScenarioHost host, ScenarioCatalog catalog) {
        if (host == null) {
            throw new IllegalArgumentException("Scenario host cannot be null.");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("Scenario catalog cannot be null.");
        }
        this.host = host;
        this.catalog = catalog;
    }

    /**
     * Creates a scenario validator.
     *
     * @param host the host
     * @param catalog the catalog
     * @return a new scenario validator
     */
    public static ScenarioValidator create(ScenarioHost host, ScenarioCatalog catalog) {
        return new ScenarioValidator(host, catalog);
    }

    /**
     * Creates a scenario validator.
     *
     * @param host the host
     * @param catalog the catalog
     * @return a new scenario validator
     */
    public static ScenarioValidator fromSystemProperties(ScenarioHost host, ScenarioCatalog catalog) {
        return create(host, catalog).config(ScenarioValidationConfig.fromSystemProperties());
    }

    /**
     * Sets the config and returns this scenario validator.
     *
     * @param config the configuration
     * @return this scenario validator for chaining
     */
    public ScenarioValidator config(ScenarioValidationConfig config) {
        this.config = config != null ? config : ScenarioValidationConfig.defaults();
        return this;
    }

    /**
     * Returns the config.
     *
     * @return the config
     */
    public ScenarioValidationConfig config() {
        return config;
    }

    /**
     * Sets the select and returns this scenario validator.
     *
     * @param selection the selection
     * @return this scenario validator for chaining
     */
    public ScenarioValidator select(String selection) {
        this.config = config.selection(selection);
        return this;
    }

    /**
     * Sets the mode and returns this scenario validator.
     *
     * @param mode the mode
     * @return this scenario validator for chaining
     */
    public ScenarioValidator mode(ScenarioValidationMode mode) {
        this.config = config.mode(mode);
        return this;
    }

    /**
     * Sets the capture policy and returns this scenario validator.
     *
     * @param capturePolicy the capture policy
     * @return this scenario validator for chaining
     */
    public ScenarioValidator capturePolicy(ScenarioCapturePolicy capturePolicy) {
        this.config = config.capturePolicy(capturePolicy);
        return this;
    }

    /**
     * Returns the run.
     *
     * @return the run
     */
    public ScenarioReport run() {
        return host.run(catalog, config.selection());
    }
}
