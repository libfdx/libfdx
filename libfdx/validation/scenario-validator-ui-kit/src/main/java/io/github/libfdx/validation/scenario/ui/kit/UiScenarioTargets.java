package io.github.libfdx.validation.scenario.ui.kit;

import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;

public final class UiScenarioTargets {
    private static final float VISIBLE_ALPHA_THRESHOLD = 0.001f;

    private UiScenarioTargets() {
    }

    public static UiScenarioTarget id(String validationId) {
        return new UiScenarioTarget("id(" + validationId + ")", validationId,
                node -> node.modifier() != null && validationId != null
                        && validationId.equals(node.modifier().validationId()));
    }

    public static UiScenarioTarget semanticLabel(String label) {
        return new UiScenarioTarget("semanticLabel(" + label + ")", "semanticLabel:" + label,
                node -> node.modifier() != null && label != null && label.equals(node.modifier().semanticLabel()));
    }

    public static UiScenarioTarget type(UiNodeType type) {
        return new UiScenarioTarget("type(" + type + ")", node -> node.type() == type);
    }

    public static UiScenarioTarget key(String key) {
        return new UiScenarioTarget("key(" + key + ")", "key:" + key,
                node -> key != null && key.equals(node.key()));
    }

    public static UiScenarioTarget typeAndKey(UiNodeType type, String key) {
        return new UiScenarioTarget("typeAndKey(" + type + "," + key + ")", type + ":" + key,
                node -> node.type() == type && key != null && key.equals(node.key()));
    }

    public static UiScenarioTarget typeAndText(UiNodeType type, String text) {
        return new UiScenarioTarget("typeAndText(" + type + "," + text + ")", type + ":" + text,
                node -> node.type() == type && text != null && text.equals(textValue(node)));
    }

    public static String textValue(UiNode node) {
        if (node == null) {
            return null;
        }
        if (node.text() != null) {
            return node.text();
        }
        Object value = node.value();
        return value != null ? String.valueOf(value) : null;
    }

    static boolean renderedVisible(UiNode node) {
        return node != null && node.visible()
                && (node.modifier() == null || node.modifier().alpha() > VISIBLE_ALPHA_THRESHOLD);
    }
}
