package io.github.libfdx.validation.scenario.ui.kit;

import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.ScenarioWaits;

public final class UiScenarioWaits {
    private UiScenarioWaits() {
    }

    public static ScenarioWaits.ConfigurableWait exists(String validationId) {
        return exists(UiScenarioTargets.id(validationId));
    }

    public static ScenarioWaits.ConfigurableWait exists(UiScenarioTarget target) {
        return ScenarioWaits.until("ui.exists(" + target.description() + ")", context -> {
            UiRoot root = context.probe(UiRoot.class);
            return root != null && target.resolve(root) != null;
        });
    }

    public static ScenarioWaits.ConfigurableWait visible(String validationId) {
        return visible(UiScenarioTargets.id(validationId));
    }

    public static ScenarioWaits.ConfigurableWait visible(UiScenarioTarget target) {
        return ScenarioWaits.until("ui.visible(" + target.description() + ")", context -> {
            UiRoot root = context.probe(UiRoot.class);
            UiNode node = root != null ? target.resolve(root) : null;
            return UiScenarioTargets.renderedVisible(node);
        });
    }

    public static ScenarioWaits.ConfigurableWait notVisible(String validationId) {
        return notVisible(UiScenarioTargets.id(validationId));
    }

    public static ScenarioWaits.ConfigurableWait notVisible(UiScenarioTarget target) {
        return ScenarioWaits.until("ui.notVisible(" + target.description() + ")", context -> {
            UiRoot root = context.probe(UiRoot.class);
            UiNode node = root != null ? target.resolve(root) : null;
            return !UiScenarioTargets.renderedVisible(node);
        });
    }

    public static ScenarioWaits.ConfigurableWait textEquals(String validationId, String expected) {
        return textEquals(UiScenarioTargets.id(validationId), expected);
    }

    public static ScenarioWaits.ConfigurableWait textEquals(UiScenarioTarget target, String expected) {
        return ScenarioWaits.until("ui.textEquals(" + target.description() + ")", context -> {
            UiRoot root = context.probe(UiRoot.class);
            UiNode node = root != null ? target.resolve(root) : null;
            String actual = UiScenarioTargets.textValue(node);
            return expected != null && expected.equals(actual);
        });
    }

    public static ScenarioWaits.ConfigurableWait valueEquals(String validationId, Object expected) {
        return valueEquals(UiScenarioTargets.id(validationId), expected);
    }

    public static ScenarioWaits.ConfigurableWait valueEquals(UiScenarioTarget target, Object expected) {
        return ScenarioWaits.until("ui.valueEquals(" + target.description() + ")", context -> {
            UiRoot root = context.probe(UiRoot.class);
            UiNode node = root != null ? target.resolve(root) : null;
            Object actual = node != null ? node.value() : null;
            return expected == null ? actual == null : expected.equals(actual);
        });
    }
}
