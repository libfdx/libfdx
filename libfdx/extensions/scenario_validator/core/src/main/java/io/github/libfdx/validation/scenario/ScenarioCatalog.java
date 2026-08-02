package io.github.libfdx.validation.scenario;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;

/**
 * Represents a scenario catalog.
 *
 * @author xpenatan
 */
public final class ScenarioCatalog {
    private final Array<Scenario> scenarios = new Array<Scenario>();

    private ScenarioCatalog() {
    }

    /**
     * Creates a scenario catalog.
     *
     * @return a new scenario catalog
     */
    public static ScenarioCatalog create() {
        return new ScenarioCatalog();
    }

    /**
     * Sets the add and returns this scenario catalog.
     *
     * @param scenario the scenario
     * @return this scenario catalog for chaining
     */
    public ScenarioCatalog add(Scenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("Scenario cannot be null.");
        }
        scenarios.add(scenario);
        return this;
    }

    /**
     * Sets the add and returns this scenario catalog.
     *
     * @param catalog the catalog
     * @return this scenario catalog for chaining
     */
    public ScenarioCatalog add(ScenarioCatalog catalog) {
        if (catalog != null) {
            scenarios.addAll(catalog.scenarios);
        }
        return this;
    }

    /**
     * Returns the scenarios.
     *
     * @return the scenarios
     */
    public ArrayView<Scenario> scenarios() {
        return scenarios.view();
    }

    /**
     * Runs the select step.
     *
     * @param selection the selection
     * @return the select
     */
    public ArrayView<Scenario> select(String selection) {
        String normalizedSelection = selection != null ? selection.trim() : "";
        if (normalizedSelection.length() == 0 || "all".equalsIgnoreCase(normalizedSelection)) {
            return scenarios();
        }
        Array<String> names = new Array<String>();
        String[] parts = normalizedSelection.split(",");
        for (int i = 0; i < parts.length; i++) {
            String name = parts[i].trim();
            if (name.length() > 0) {
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        Array<Scenario> selected = new Array<Scenario>();
        for (int i = 0; i < scenarios.size(); i++) {
            Scenario scenario = scenarios.get(i);
            if (names.removeValue(scenario.name())) {
                selected.add(scenario);
            }
        }
        if (!names.isEmpty()) {
            throw new IllegalArgumentException("Unknown scenario selection: [" + join(names) + "]");
        }
        return selected.view();
    }

    private static String join(ArrayView<String> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(values.get(i));
        }
        return result.toString();
    }
}
