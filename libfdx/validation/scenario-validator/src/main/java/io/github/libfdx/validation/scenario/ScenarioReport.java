package io.github.libfdx.validation.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a report produced by a scenario.
 *
 * @author xpenatan
 */
public final class ScenarioReport {
    private final ArrayList<ScenarioResult> results = new ArrayList<ScenarioResult>();
    private final ArrayList<ScenarioValidationCell> validationCells = new ArrayList<ScenarioValidationCell>();

    /**
     * Sets the add and returns this scenario report.
     *
     * @param result the result
     * @return this scenario report for chaining
     */
    public ScenarioReport add(ScenarioResult result) {
        if (result != null) {
            results.add(result);
        }
        return this;
    }

    /**
     * Returns the results.
     *
     * @return the results
     */
    public List<ScenarioResult> results() {
        return Collections.unmodifiableList(results);
    }

    /**
     * Adds the cell.
     *
     * @param cell the cell
     * @return this scenario report for chaining
     */
    public ScenarioReport addCell(ScenarioValidationCell cell) {
        if (cell != null) {
            validationCells.add(cell);
        }
        return this;
    }

    /**
     * Returns the validation cells.
     *
     * @return the validation cells
     */
    public List<ScenarioValidationCell> validationCells() {
        return Collections.unmodifiableList(validationCells);
    }

    /**
     * Returns the passed.
     *
     * @return true if passed succeeds or is active; false otherwise
     */
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

    /**
     * Returns the result count.
     *
     * @return the result count
     */
    public int resultCount() {
        return results.size();
    }

    /**
     * Returns the passed count.
     *
     * @return the passed count
     */
    public int passedCount() {
        int count = 0;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).passed()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the failure count.
     *
     * @return the failure count
     */
    public int failureCount() {
        int count = 0;
        for (int i = 0; i < results.size(); i++) {
            if (!results.get(i).passed()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the captures.
     *
     * @return the captures
     */
    public List<ScenarioCapture> captures() {
        ArrayList<ScenarioCapture> captures = new ArrayList<ScenarioCapture>();
        for (int i = 0; i < results.size(); i++) {
            captures.addAll(results.get(i).captures());
        }
        return Collections.unmodifiableList(captures);
    }

    /**
     * Runs the result step.
     *
     * @param scenarioName the scenario name
     * @return the result
     */
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

    /**
     * Returns the summary.
     *
     * @return the summary
     */
    public String summary() {
        return "ScenarioReport{passed=" + passed()
                + ", results=" + results.size()
                + ", failures=" + failureCount()
                + ", captures=" + captures().size()
                + ", validationCells=" + validationCells.size()
                + "}";
    }
}
