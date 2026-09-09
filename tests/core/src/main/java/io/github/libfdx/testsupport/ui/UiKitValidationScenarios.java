package io.github.libfdx.testsupport.ui;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.validation.scenario.Scenario;
import io.github.libfdx.validation.scenario.ScenarioCatalog;
import io.github.libfdx.validation.scenario.ScenarioCapturePolicy;
import io.github.libfdx.validation.scenario.ScenarioValidationMode;
import java.util.ArrayList;

/**
 * Represents an ui kit validation scenarios.
 *
 * @author xpenatan
 */
public final class UiKitValidationScenarios {
    public static final String BUTTON_PRESS = "buttons.press";
    public static final String CHECKBOX_SECTION_OPTION = "checkboxes.sectionOption";
    public static final String SLIDER_VOLUME = "sliders.volume";
    public static final String PROGRESS_VOLUME = "progress.volume";
    public static final String PROGRESS_WIDE = "progress.wide";
    public static final String TABS_DEMO = "tabs.demo";
    public static final String SETTINGS_TEXT_SIZE_SLIDER = "settings.textSize";
    public static final String HEADER_MODAL_BUTTON = "header.modal";
    public static final String POPUP_BLOCK_INPUT = "popup.blockInput";
    public static final String POPUP_CLOSE_BUTTON = "popup.close";
    public static final String MODAL_ID = "uikit-modal";
    public static final String TOOLTIP_TEXT_TARGET = "tooltips.text";
    public static final String TOOLTIP_CHECKBOX_TARGET = "tooltips.checkbox";
    public static final String TOOLTIP_TEXT_FIELD_TARGET = "tooltips.textField";
    public static final String EMOJI_FIELD = "text.emoji";
    public static final String SWITCH_NOTIFICATIONS = "toggles.notifications";
    public static final String RADIO_QUALITY = "toggles.quality";
    public static final String LOADING_BAR = "loading.bar";
    public static final String LOADING_SPINNER = "loading.spinner";
    public static final String COLLAPSE_BASIC = "collapse.basic";
    public static final String THEME_COBALT = "themes.cobalt";
    public static final String CONSTRAINED_ALPHA = "layout.alpha";
    public static final String CONSTRAINED_BETA = "layout.beta";
    public static final String CONSTRAINED_GAMMA = "layout.gamma";

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
