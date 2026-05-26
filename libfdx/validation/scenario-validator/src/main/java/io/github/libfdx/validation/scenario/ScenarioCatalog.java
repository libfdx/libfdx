package io.github.libfdx.validation.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ScenarioCatalog {
    private final ArrayList<Scenario> scenarios = new ArrayList<Scenario>();

    private ScenarioCatalog() {
    }

    public static ScenarioCatalog create() {
        return new ScenarioCatalog();
    }

    public ScenarioCatalog add(Scenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("Scenario cannot be null.");
        }
        scenarios.add(scenario);
        return this;
    }

    public ScenarioCatalog add(ScenarioCatalog catalog) {
        if (catalog != null) {
            scenarios.addAll(catalog.scenarios);
        }
        return this;
    }

    public List<Scenario> scenarios() {
        return Collections.unmodifiableList(scenarios);
    }

    public List<Scenario> select(String selection) {
        if (selection == null || selection.length() == 0 || "all".equals(selection)) {
            return scenarios();
        }
        Set<String> names = new LinkedHashSet<String>();
        String[] parts = selection.split(",");
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
