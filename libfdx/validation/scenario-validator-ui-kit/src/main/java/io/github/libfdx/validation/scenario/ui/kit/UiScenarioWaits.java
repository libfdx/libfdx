package io.github.libfdx.validation.scenario.ui.kit;

import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.ScenarioWaits;

/**
 * Represents an ui scenario waits.
 *
 * @author xpenatan
 */
public final class UiScenarioWaits {
    private UiScenarioWaits() {
    }

    /**
     * Runs the exists step.
     *
     * @param validationId the validation ID
     * @return the exists
     */
    public static ScenarioWaits.ConfigurableWait exists(String validationId) {
        return exists(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the exists step.
     *
     * @param target the target value
     * @return the exists
     */
    public static ScenarioWaits.ConfigurableWait exists(UiScenarioTarget target) {
        return ScenarioWaits.until("ui.exists(" + target.description() + ")", context -> {
            UiRoot root = context.probe(UiRoot.class);
            return root != null && target.resolve(root) != null;
        });
    }

    /**
     * Runs the visible step.
     *
     * @param validationId the validation ID
     * @return the visible
     */
    public static ScenarioWaits.ConfigurableWait visible(String validationId) {
        return visible(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the visible step.
     *
     * @param target the target value
     * @return the visible
     */
    public static ScenarioWaits.ConfigurableWait visible(UiScenarioTarget target) {
        return ScenarioWaits.until("ui.visible(" + target.description() + ")", context -> {
            UiRoot root = context.probe(UiRoot.class);
            UiNode node = root != null ? target.resolve(root) : null;
            return UiScenarioTargets.renderedVisible(node);
        });
    }

    /**
     * Runs the not visible step.
     *
     * @param validationId the validation ID
     * @return the not visible
     */
    public static ScenarioWaits.ConfigurableWait notVisible(String validationId) {
        return notVisible(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the not visible step.
     *
     * @param target the target value
     * @return the not visible
     */
    public static ScenarioWaits.ConfigurableWait notVisible(UiScenarioTarget target) {
        return ScenarioWaits.until("ui.notVisible(" + target.description() + ")", context -> {
            UiRoot root = context.probe(UiRoot.class);
            UiNode node = root != null ? target.resolve(root) : null;
            return !UiScenarioTargets.renderedVisible(node);
        });
    }

    /**
     * Runs the text equals step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @return the text equals
     */
    public static ScenarioWaits.ConfigurableWait textEquals(String validationId, String expected) {
        return textEquals(UiScenarioTargets.id(validationId), expected);
    }

    /**
     * Runs the text equals step.
     *
     * @param target the target value
     * @param expected the expected
     * @return the text equals
     */
    public static ScenarioWaits.ConfigurableWait textEquals(UiScenarioTarget target, String expected) {
        return ScenarioWaits.until("ui.textEquals(" + target.description() + ")", context -> {
            UiRoot root = context.probe(UiRoot.class);
            UiNode node = root != null ? target.resolve(root) : null;
            String actual = UiScenarioTargets.textValue(node);
            return expected != null && expected.equals(actual);
        });
    }

    /**
     * Runs the value equals step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @return the value equals
     */
    public static ScenarioWaits.ConfigurableWait valueEquals(String validationId, Object expected) {
        return valueEquals(UiScenarioTargets.id(validationId), expected);
    }

    /**
     * Runs the value equals step.
     *
     * @param target the target value
     * @param expected the expected
     * @return the value equals
     */
    public static ScenarioWaits.ConfigurableWait valueEquals(UiScenarioTarget target, Object expected) {
        return ScenarioWaits.until("ui.valueEquals(" + target.description() + ")", context -> {
            UiRoot root = context.probe(UiRoot.class);
            UiNode node = root != null ? target.resolve(root) : null;
            Object actual = node != null ? node.value() : null;
            return expected == null ? actual == null : expected.equals(actual);
        });
    }
}
