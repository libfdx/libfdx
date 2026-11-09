package io.github.libfdx.tests.utils.uikittest.validation;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.validation.scenario.Scenario;
import io.github.libfdx.validation.scenario.ScenarioCapturePolicy;
import io.github.libfdx.validation.scenario.ScenarioCatalog;
import io.github.libfdx.validation.scenario.ScenarioValidationMode;
import java.util.ArrayList;

/**
 * Represents an ui kit validation scenarios.
 *
 * @author xpenatan
 */
final class UiKitValidationScenarios {
    /**
     * Represents a plan.
     *
     * @author xpenatan
     */
    public static final class Plan {
        private final ScenarioCatalog catalog;
        private final Entry[] entries;
        private final boolean fullPlan;

        private Plan(ScenarioCatalog catalog, Entry[] entries, boolean fullPlan) {
            this.catalog = catalog;
            this.entries = entries;
            this.fullPlan = fullPlan;
        }

        ScenarioCatalog catalog() {
            return catalog;
        }

        public Entry[] entries() {
            return entries;
        }

        public boolean fullPlan() {
            return fullPlan;
        }

        public Plan select(String selection, ScenarioValidationMode mode) {
            if (entries.length == 0) {
                return this;
            }
            ArrayView<Scenario> selectedScenarios = catalog.select(selection);
            ScenarioValidationMode effectiveMode = mode != null ? mode : ScenarioValidationMode.MIXED;
            ScenarioCatalog selectedCatalog = ScenarioCatalog.create();
            ArrayList<Entry> selectedEntries = new ArrayList<Entry>();
            for (int i = 0; i < entries.length; i++) {
                Entry entry = entries[i];
                if (!selectedScenarios.contains(entry.scenario())) {
                    continue;
                }
                if (effectiveMode == ScenarioValidationMode.VISUAL && !entry.validateVisual()) {
                    continue;
                }
                selectedCatalog.add(entry.scenario());
                selectedEntries.add(new Entry(entry.scenario(), selectedEntries.size(),
                        entry.captureImage(), entry.validateVisual()));
            }
            if (selectedEntries.isEmpty()) {
                throw new IllegalArgumentException("No UIKit validation scenarios match selection '"
                        + selection + "' in " + effectiveMode + " mode.");
            }
            return new Plan(selectedCatalog, selectedEntries.toArray(new Entry[0]),
                    selectedEntries.size() == entries.length);
        }
    }

    /**
     * Represents an entry.
     *
     * @author xpenatan
     */
    public static final class Entry {
        private final Scenario scenario;
        private final long frame;
        private final boolean captureImage;
        private final boolean validateVisual;

        private Entry(Scenario scenario, long frame, boolean captureImage, boolean validateVisual) {
            this.scenario = scenario;
            this.frame = frame;
            this.captureImage = captureImage;
            this.validateVisual = validateVisual;
        }

        public Scenario scenario() {
            return scenario;
        }

        public String name() {
            return scenario.name();
        }

        public long frame() {
            return frame;
        }

        boolean captureImage() {
            return captureImage;
        }

        boolean validateVisual() {
            return validateVisual;
        }

        public boolean captureOnSuccess(ScenarioCapturePolicy policy) {
            ScenarioCapturePolicy effectivePolicy = policy != null
                    ? policy
                    : ScenarioCapturePolicy.SCENARIO_LISTED;
            return effectivePolicy == ScenarioCapturePolicy.ALL
                    || (effectivePolicy == ScenarioCapturePolicy.SCENARIO_LISTED && captureImage);
        }

        public boolean captureOnFailure(ScenarioCapturePolicy policy) {
            return policy == ScenarioCapturePolicy.ALL || policy == ScenarioCapturePolicy.FAILED;
        }

        public boolean validateVisual(ScenarioValidationMode mode) {
            return validateVisual && mode != ScenarioValidationMode.BEHAVIOR;
        }
    }

    /**
     * Builds value instances and related output.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private final boolean active;
        private final ScenarioCatalog catalog = ScenarioCatalog.create();
        private final ArrayList<Entry> entries = new ArrayList<Entry>();

        private Builder(boolean active) {
            this.active = active;
        }

        public Builder entry(long frame, boolean captureImage, boolean validateVisual, Scenario scenario) {
            if (active) {
                catalog.add(scenario);
                entries.add(new Entry(scenario, frame, captureImage, validateVisual));
            }
            return this;
        }

        public Plan build() {
            return new Plan(catalog, entries.toArray(new Entry[0]), active);
        }
    }

    private UiKitValidationScenarios() {
    }

    public static Builder builder(boolean active) {
        return new Builder(active);
    }
}
