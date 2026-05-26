package io.github.libfdx.validation.scenario;

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

    public static ScenarioValidator create(ScenarioHost host, ScenarioCatalog catalog) {
        return new ScenarioValidator(host, catalog);
    }

    public static ScenarioValidator fromSystemProperties(ScenarioHost host, ScenarioCatalog catalog) {
        return create(host, catalog).config(ScenarioValidationConfig.fromSystemProperties());
    }

    public ScenarioValidator config(ScenarioValidationConfig config) {
        this.config = config != null ? config : ScenarioValidationConfig.defaults();
        return this;
    }

    public ScenarioValidationConfig config() {
        return config;
    }

    public ScenarioValidator select(String selection) {
        this.config = config.selection(selection);
        return this;
    }

    public ScenarioValidator mode(ScenarioValidationMode mode) {
        this.config = config.mode(mode);
        return this;
    }

    public ScenarioValidator capturePolicy(ScenarioCapturePolicy capturePolicy) {
        this.config = config.capturePolicy(capturePolicy);
        return this;
    }

    public ScenarioReport run() {
        return host.run(catalog, config.selection());
    }
}
