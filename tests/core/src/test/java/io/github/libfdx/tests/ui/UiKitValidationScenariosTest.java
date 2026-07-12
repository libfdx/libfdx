package io.github.libfdx.tests.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.validation.scenario.Scenario;
import io.github.libfdx.validation.scenario.ScenarioCapturePolicy;
import io.github.libfdx.validation.scenario.ScenarioValidationMode;
import org.junit.jupiter.api.Test;

class UiKitValidationScenariosTest {
    @Test
    void selectionFiltersAndRebasesThePlan() {
        UiKitValidationScenarios.Plan plan = plan();

        UiKitValidationScenarios.Plan selected = plan.select(" visual ", ScenarioValidationMode.MIXED);

        assertEquals(1, selected.entries().length);
        assertEquals("visual", selected.entries()[0].name());
        assertEquals(0L, selected.entries()[0].frame());
        assertFalse(selected.fullPlan());
        assertThrows(IllegalArgumentException.class,
                () -> plan.select("missing", ScenarioValidationMode.MIXED));
    }

    @Test
    void visualModeKeepsOnlyVisualEntries() {
        UiKitValidationScenarios.Plan plan = plan();

        UiKitValidationScenarios.Plan visual = plan.select("all", ScenarioValidationMode.VISUAL);
        UiKitValidationScenarios.Plan mixed = plan.select("all", ScenarioValidationMode.MIXED);

        assertEquals(1, visual.entries().length);
        assertEquals("visual", visual.entries()[0].name());
        assertFalse(visual.fullPlan());
        assertEquals(2, mixed.entries().length);
        assertTrue(mixed.fullPlan());
    }

    @Test
    void entryAppliesCapturePolicyAndMode() {
        UiKitValidationScenarios.Entry entry = plan().entries()[1];

        assertTrue(entry.captureOnSuccess(ScenarioCapturePolicy.ALL));
        assertTrue(entry.captureOnSuccess(ScenarioCapturePolicy.SCENARIO_LISTED));
        assertFalse(entry.captureOnSuccess(ScenarioCapturePolicy.FAILED));
        assertFalse(entry.captureOnSuccess(ScenarioCapturePolicy.NONE));
        assertTrue(entry.captureOnFailure(ScenarioCapturePolicy.FAILED));
        assertFalse(entry.captureOnFailure(ScenarioCapturePolicy.SCENARIO_LISTED));
        assertTrue(entry.validateVisual(ScenarioValidationMode.MIXED));
        assertTrue(entry.validateVisual(ScenarioValidationMode.VISUAL));
        assertFalse(entry.validateVisual(ScenarioValidationMode.BEHAVIOR));
    }

    private UiKitValidationScenarios.Plan plan() {
        return UiKitValidationScenarios.builder(true)
                .entry(4L, false, false, Scenario.named("behavior"))
                .entry(9L, true, true, Scenario.named("visual"))
                .build();
    }
}
