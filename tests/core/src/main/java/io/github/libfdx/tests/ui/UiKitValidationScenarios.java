package io.github.libfdx.tests.ui;

import io.github.libfdx.validation.scenario.Scenario;
import io.github.libfdx.validation.scenario.ScenarioCatalog;
import io.github.libfdx.validation.scenario.ScenarioCapturePolicy;
import io.github.libfdx.validation.scenario.ScenarioValidationMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an ui kit validation scenarios.
 *
 * @author xpenatan
 */
final class UiKitValidationScenarios {
    static final String BUTTON_PRESS = "buttons.press";
    static final String CHECKBOX_SECTION_OPTION = "checkboxes.sectionOption";
    static final String SLIDER_VOLUME = "sliders.volume";
    static final String PROGRESS_VOLUME = "progress.volume";
    static final String PROGRESS_WIDE = "progress.wide";
    static final String TABS_DEMO = "tabs.demo";
    static final String SETTINGS_TEXT_SIZE_SLIDER = "settings.textSize";
    static final String HEADER_MODAL_BUTTON = "header.modal";
    static final String POPUP_BLOCK_INPUT = "popup.blockInput";
    static final String POPUP_CLOSE_BUTTON = "popup.close";
    static final String MODAL_ID = "uikit-modal";
    static final String TOOLTIP_TEXT_TARGET = "tooltips.text";
    static final String TOOLTIP_CHECKBOX_TARGET = "tooltips.checkbox";
    static final String TOOLTIP_TEXT_FIELD_TARGET = "tooltips.textField";

    /**
     * Represents a plan.
     *
     * @author xpenatan
     */
    static final class Plan {
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

        Entry[] entries() {
            return entries;
        }

        boolean fullPlan() {
            return fullPlan;
        }

        Plan select(String selection, ScenarioValidationMode mode) {
            if (entries.length == 0) {
                return this;
            }
            List<Scenario> selectedScenarios = catalog.select(selection);
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
    static final class Entry {
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

        Scenario scenario() {
            return scenario;
        }

        String name() {
            return scenario.name();
        }

        long frame() {
            return frame;
        }

        boolean captureImage() {
            return captureImage;
        }

        boolean validateVisual() {
            return validateVisual;
        }

        boolean captureOnSuccess(ScenarioCapturePolicy policy) {
            ScenarioCapturePolicy effectivePolicy = policy != null
                    ? policy
                    : ScenarioCapturePolicy.SCENARIO_LISTED;
            return effectivePolicy == ScenarioCapturePolicy.ALL
                    || (effectivePolicy == ScenarioCapturePolicy.SCENARIO_LISTED && captureImage);
        }

        boolean captureOnFailure(ScenarioCapturePolicy policy) {
            return policy == ScenarioCapturePolicy.ALL || policy == ScenarioCapturePolicy.FAILED;
        }

        boolean validateVisual(ScenarioValidationMode mode) {
            return validateVisual && mode != ScenarioValidationMode.BEHAVIOR;
        }
    }

    /**
     * Builds value instances and related output.
     *
     * @author xpenatan
     */
    static final class Builder {
        private final boolean active;
        private final ScenarioCatalog catalog = ScenarioCatalog.create();
        private final ArrayList<Entry> entries = new ArrayList<Entry>();

        private Builder(boolean active) {
            this.active = active;
        }

        Builder entry(long frame, boolean captureImage, boolean validateVisual, Scenario scenario) {
            if (active) {
                catalog.add(scenario);
                entries.add(new Entry(scenario, frame, captureImage, validateVisual));
            }
            return this;
        }

        Plan build() {
            return new Plan(catalog, entries.toArray(new Entry[0]), active);
        }
    }

    private UiKitValidationScenarios() {
    }

    static Builder builder(boolean active) {
        return new Builder(active);
    }
}
