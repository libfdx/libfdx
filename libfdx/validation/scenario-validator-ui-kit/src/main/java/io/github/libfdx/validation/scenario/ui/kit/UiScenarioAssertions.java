package io.github.libfdx.validation.scenario.ui.kit;

import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.ScenarioAssertion;
import io.github.libfdx.validation.scenario.ScenarioAssertions;

public final class UiScenarioAssertions {
    private UiScenarioAssertions() {
    }

    public static ScenarioAssertion exists(String validationId) {
        return exists(UiScenarioTargets.id(validationId));
    }

    public static ScenarioAssertion exists(UiScenarioTarget target) {
        return ScenarioAssertions.assertion("ui.exists(" + target.description() + ")", target::require);
    }

    public static ScenarioAssertion absent(String validationId) {
        return absent(UiScenarioTargets.id(validationId));
    }

    public static ScenarioAssertion absent(UiScenarioTarget target) {
        return ScenarioAssertions.assertion("ui.absent(" + target.description() + ")", context -> {
            UiRoot root = context.requireProbe(UiRoot.class);
            UiNode node = target.resolve(root);
            context.assertTrue(node == null, "UI target exists: " + target.description());
        });
    }

    public static ScenarioAssertion visible(String validationId) {
        return visible(UiScenarioTargets.id(validationId));
    }

    public static ScenarioAssertion visible(UiScenarioTarget target) {
        return ScenarioAssertions.assertion("ui.visible(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(UiScenarioTargets.renderedVisible(node),
                    "UI target is not visible: " + target.description());
        });
    }

    public static ScenarioAssertion notVisible(String validationId) {
        return notVisible(UiScenarioTargets.id(validationId));
    }

    public static ScenarioAssertion notVisible(UiScenarioTarget target) {
        return ScenarioAssertions.assertion("ui.notVisible(" + target.description() + ")", context -> {
            UiRoot root = context.requireProbe(UiRoot.class);
            UiNode node = target.resolve(root);
            context.assertTrue(!UiScenarioTargets.renderedVisible(node),
                    "UI target is visible: " + target.description());
        });
    }

    public static ScenarioAssertion textEquals(String validationId, String expected) {
        return textEquals(UiScenarioTargets.id(validationId), expected);
    }

    public static ScenarioAssertion textEquals(UiScenarioTarget target, String expected) {
        return ScenarioAssertions.assertion("ui.textEquals(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertEquals(expected, UiScenarioTargets.textValue(node),
                    "UI text mismatch for " + target.description());
        });
    }

    public static ScenarioAssertion textContains(String validationId, String expectedPart) {
        return textContains(UiScenarioTargets.id(validationId), expectedPart);
    }

    public static ScenarioAssertion textContains(UiScenarioTarget target, String expectedPart) {
        return ScenarioAssertions.assertion("ui.textContains(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            String actual = UiScenarioTargets.textValue(node);
            context.assertTrue(actual != null && expectedPart != null && actual.indexOf(expectedPart) >= 0,
                    "UI text did not contain expected part for " + target.description()
                            + " expectedPart=" + expectedPart + " actual=" + actual);
        });
    }

    public static ScenarioAssertion valueEquals(String validationId, Object expected) {
        return valueEquals(UiScenarioTargets.id(validationId), expected);
    }

    public static ScenarioAssertion valueEquals(UiScenarioTarget target, Object expected) {
        return ScenarioAssertions.assertion("ui.valueEquals(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertEquals(expected, node.value(), "UI value mismatch for " + target.description());
        });
    }

    public static ScenarioAssertion intValue(String validationId, int expected) {
        return intValue(UiScenarioTargets.id(validationId), expected);
    }

    public static ScenarioAssertion intValue(UiScenarioTarget target, int expected) {
        return ScenarioAssertions.assertion("ui.intValue(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertEquals(Integer.valueOf(expected), Integer.valueOf(node.intValue()),
                    "UI int value mismatch for " + target.description());
        });
    }

    public static ScenarioAssertion floatValue(String validationId, float expected) {
        return floatValue(UiScenarioTargets.id(validationId), expected, 0.0001f);
    }

    public static ScenarioAssertion floatValue(String validationId, float expected, float tolerance) {
        return floatValue(UiScenarioTargets.id(validationId), expected, tolerance);
    }

    public static ScenarioAssertion floatValue(UiScenarioTarget target, float expected, float tolerance) {
        return ScenarioAssertions.assertion("ui.floatValue(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            float delta = Math.abs(node.floatValue() - expected);
            context.assertTrue(delta <= Math.max(0.0f, tolerance),
                    "UI float value mismatch for " + target.description() + " expected=" + expected
                            + " actual=" + node.floatValue());
        });
    }

    public static ScenarioAssertion checked(String validationId, boolean expected) {
        return checked(UiScenarioTargets.id(validationId), expected);
    }

    public static ScenarioAssertion checked(UiScenarioTarget target, boolean expected) {
        return ScenarioAssertions.assertion("ui.checked(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.CHECKBOX,
                    "UI target is not a checkbox: " + target.description());
            context.assertEquals(Boolean.valueOf(expected), Boolean.valueOf(node.checked()),
                    "UI checked state mismatch for " + target.description());
        });
    }

    public static ScenarioAssertion enabled(String validationId, boolean expected) {
        return enabled(UiScenarioTargets.id(validationId), expected);
    }

    public static ScenarioAssertion enabled(UiScenarioTarget target, boolean expected) {
        return ScenarioAssertions.assertion("ui.enabled(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            boolean actual = node.modifier() == null || node.modifier().enabled();
            context.assertEquals(Boolean.valueOf(expected), Boolean.valueOf(actual),
                    "UI enabled state mismatch for " + target.description());
        });
    }

    public static ScenarioAssertion type(String validationId, UiNodeType expected) {
        return type(UiScenarioTargets.id(validationId), expected);
    }

    public static ScenarioAssertion type(UiScenarioTarget target, UiNodeType expected) {
        return ScenarioAssertions.assertion("ui.type(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertEquals(expected, node.type(), "UI type mismatch for " + target.description());
        });
    }

    public static ScenarioAssertion boundsAtLeast(String validationId, float width, float height) {
        return boundsAtLeast(UiScenarioTargets.id(validationId), width, height);
    }

    public static ScenarioAssertion boundsAtLeast(UiScenarioTarget target, float width, float height) {
        return ScenarioAssertions.assertion("ui.boundsAtLeast(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.bounds().width() >= width && node.bounds().height() >= height,
                    "UI bounds too small for " + target.description() + " expectedAtLeast="
                            + width + "x" + height + " actual="
                            + node.bounds().width() + "x" + node.bounds().height());
        });
    }

    public static ScenarioAssertion boundsInsideViewport(String validationId) {
        return boundsInsideViewport(UiScenarioTargets.id(validationId));
    }

    public static ScenarioAssertion boundsInsideViewport(UiScenarioTarget target) {
        return ScenarioAssertions.assertion("ui.boundsInsideViewport(" + target.description() + ")", context -> {
            UiRoot root = context.requireProbe(UiRoot.class);
            UiNode node = target.require(context);
            float width = root.display() != null ? root.display().width() : 0.0f;
            float height = root.display() != null ? root.display().height() : 0.0f;
            context.assertTrue(node.bounds().x() >= 0.0f && node.bounds().y() >= 0.0f
                            && node.bounds().right() <= width && node.bounds().bottom() <= height,
                    "UI bounds outside viewport for " + target.description() + " bounds="
                            + node.bounds().x() + "," + node.bounds().y() + " "
                            + node.bounds().width() + "x" + node.bounds().height()
                            + ", viewport=" + width + "x" + height);
        });
    }

    public static ScenarioAssertion focused(String validationId, boolean expected) {
        return focused(UiScenarioTargets.id(validationId), expected);
    }

    public static ScenarioAssertion focused(UiScenarioTarget target, boolean expected) {
        return ScenarioAssertions.assertion("ui.focused(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertEquals(Boolean.valueOf(expected), Boolean.valueOf(node.focused()),
                    "UI focused state mismatch for " + target.description());
        });
    }

    public static ScenarioAssertion modalOpen(String modalId) {
        return visible(UiScenarioTargets.typeAndKey(UiNodeType.MODAL, modalId));
    }

    public static ScenarioAssertion popupOpen(String popupId) {
        return visible(UiScenarioTargets.typeAndKey(UiNodeType.POPUP, popupId));
    }

    public static ScenarioAssertion activeTab(String validationId, int expectedIndex) {
        return activeTab(UiScenarioTargets.id(validationId), expectedIndex);
    }

    public static ScenarioAssertion activeTab(UiScenarioTarget target, int expectedIndex) {
        return ScenarioAssertions.assertion("ui.activeTab(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.TABS, "UI target is not tabs: " + target.description());
            context.assertEquals(Integer.valueOf(expectedIndex), Integer.valueOf(node.intValue()),
                    "Active tab mismatch for " + target.description());
        });
    }

    public static ScenarioAssertion sliderValue(String validationId, float expected) {
        return sliderValue(UiScenarioTargets.id(validationId), expected, 0.0001f);
    }

    public static ScenarioAssertion sliderValue(String validationId, float expected, float tolerance) {
        return sliderValue(UiScenarioTargets.id(validationId), expected, tolerance);
    }

    public static ScenarioAssertion sliderValue(UiScenarioTarget target, float expected, float tolerance) {
        return ScenarioAssertions.assertion("ui.sliderValue(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.SLIDER, "UI target is not a slider: " + target.description());
            float delta = Math.abs(node.floatValue() - expected);
            context.assertTrue(delta <= Math.max(0.0f, tolerance),
                    "Slider value mismatch for " + target.description() + " expected=" + expected
                            + " actual=" + node.floatValue());
        });
    }
}
