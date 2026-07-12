package io.github.libfdx.validation.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a scenario catalog.
 *
 * @author xpenatan
 */
public final class ScenarioCatalog {
    private final ArrayList<Scenario> scenarios = new ArrayList<Scenario>();

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
    public List<Scenario> scenarios() {
        return Collections.unmodifiableList(scenarios);
    }

    /**
     * Runs the select step.
     *
     * @param selection the selection
     * @return the select
     */
    public List<Scenario> select(String selection) {
        String normalizedSelection = selection != null ? selection.trim() : "";
        if (normalizedSelection.length() == 0 || "all".equalsIgnoreCase(normalizedSelection)) {
            return scenarios();
        }
        Set<String> names = new LinkedHashSet<String>();
        String[] parts = normalizedSelection.split(",");
        for (int i = 0; i < parts.length; i++) {
            String name = parts[i].trim();
            if (name.length() > 0) {
                names.add(name);
            }
        }
        ArrayList<Scenario> selected = new ArrayList<Scenario>();
        for (int i = 0; i < scenarios.size(); i++) {
            Scenario scenario = scenarios.get(i);
            if (names.remove(scenario.name())) {
                selected.add(scenario);
            }
        }
        if (!names.isEmpty()) {
            throw new IllegalArgumentException("Unknown scenario selection: " + names);
        }
        return Collections.unmodifiableList(selected);
    }
}
