package io.github.libfdx.validation.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScenarioReport {
    private final ArrayList<ScenarioResult> results = new ArrayList<ScenarioResult>();
    private final ArrayList<ScenarioValidationCell> validationCells = new ArrayList<ScenarioValidationCell>();

    public ScenarioReport add(ScenarioResult result) {
        if (result != null) {
            results.add(result);
        }
        return this;
    }

    public List<ScenarioResult> results() {
        return Collections.unmodifiableList(results);
    }

    public ScenarioReport addCell(ScenarioValidationCell cell) {
        if (cell != null) {
            validationCells.add(cell);
        }
        return this;
    }

    public List<ScenarioValidationCell> validationCells() {
        return Collections.unmodifiableList(validationCells);
    }

    public boolean passed() {
        for (int i = 0; i < results.size(); i++) {
            if (!results.get(i).passed()) {
                return false;
            }
        }
        for (int i = 0; i < validationCells.size(); i++) {
            if (validationCells.get(i).status() == ScenarioValidationCellStatus.BLOCKED) {
                return false;
            }
        }
        return true;
    }

    public int resultCount() {
        return results.size();
    }

    public int passedCount() {
        int count = 0;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).passed()) {
                count++;
            }
        }
        return count;
    }

    public int failureCount() {
        int count = 0;
        for (int i = 0; i < results.size(); i++) {
            if (!results.get(i).passed()) {
                count++;
            }
        }
        return count;
    }

    public List<ScenarioCapture> captures() {
        ArrayList<ScenarioCapture> captures = new ArrayList<ScenarioCapture>();
        for (int i = 0; i < results.size(); i++) {
            captures.addAll(results.get(i).captures());
        }
        return Collections.unmodifiableList(captures);
    }

    public ScenarioResult result(String scenarioName) {
        if (scenarioName == null) {
            return null;
        }
        for (int i = 0; i < results.size(); i++) {
            ScenarioResult result = results.get(i);
            if (scenarioName.equals(result.scenarioName())) {
                return result;
            }
        }
        return null;
    }

    public String summary() {
        return "ScenarioReport{passed=" + passed()
                + ", results=" + results.size()
                + ", failures=" + failureCount()
                + ", captures=" + captures().size()
                + ", validationCells=" + validationCells.size()
                + "}";
    }
}
