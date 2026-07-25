package io.github.libfdx.validation.scenario.ui.kit;

import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.ScenarioAssertion;
import io.github.libfdx.validation.scenario.ScenarioAssertions;

/**
 * Represents an ui scenario assertions.
 *
 * @author xpenatan
 */
public final class UiScenarioAssertions {
    private UiScenarioAssertions() {
    }

    /**
     * Runs the exists step.
     *
     * @param validationId the validation ID
     * @return the exists
     */
    public static ScenarioAssertion exists(String validationId) {
        return exists(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the exists step.
     *
     * @param target the target value
     * @return the exists
     */
    public static ScenarioAssertion exists(UiScenarioTarget target) {
        return ScenarioAssertions.assertion("ui.exists(" + target.description() + ")", target::require);
    }

    /**
     * Runs the absent step.
     *
     * @param validationId the validation ID
     * @return the absent
     */
    public static ScenarioAssertion absent(String validationId) {
        return absent(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the absent step.
     *
     * @param target the target value
     * @return the absent
     */
    public static ScenarioAssertion absent(UiScenarioTarget target) {
        return ScenarioAssertions.assertion("ui.absent(" + target.description() + ")", context -> {
            UiRoot root = context.requireProbe(UiRoot.class);
            UiNode node = target.resolve(root);
            context.assertTrue(node == null, "UI target exists: " + target.description());
        });
    }

    /**
     * Runs the visible step.
     *
     * @param validationId the validation ID
     * @return the visible
     */
    public static ScenarioAssertion visible(String validationId) {
        return visible(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the visible step.
     *
     * @param target the target value
     * @return the visible
     */
    public static ScenarioAssertion visible(UiScenarioTarget target) {
        return ScenarioAssertions.assertion("ui.visible(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(UiScenarioTargets.renderedVisible(node),
                    "UI target is not visible: " + target.description());
        });
    }

    /**
     * Runs the not visible step.
     *
     * @param validationId the validation ID
     * @return the not visible
     */
    public static ScenarioAssertion notVisible(String validationId) {
        return notVisible(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the not visible step.
     *
     * @param target the target value
     * @return the not visible
     */
    public static ScenarioAssertion notVisible(UiScenarioTarget target) {
        return ScenarioAssertions.assertion("ui.notVisible(" + target.description() + ")", context -> {
            UiRoot root = context.requireProbe(UiRoot.class);
            UiNode node = target.resolve(root);
            context.assertTrue(!UiScenarioTargets.renderedVisible(node),
                    "UI target is visible: " + target.description());
        });
    }

    /**
     * Runs the text equals step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @return the text equals
     */
    public static ScenarioAssertion textEquals(String validationId, String expected) {
        return textEquals(UiScenarioTargets.id(validationId), expected);
    }

    /**
     * Runs the text equals step.
     *
     * @param target the target value
     * @param expected the expected
     * @return the text equals
     */
    public static ScenarioAssertion textEquals(UiScenarioTarget target, String expected) {
        return ScenarioAssertions.assertion("ui.textEquals(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertEquals(expected, UiScenarioTargets.textValue(node),
                    "UI text mismatch for " + target.description());
        });
    }

    /**
     * Runs the text contains step.
     *
     * @param validationId the validation ID
     * @param expectedPart the expected part
     * @return the text contains
     */
    public static ScenarioAssertion textContains(String validationId, String expectedPart) {
        return textContains(UiScenarioTargets.id(validationId), expectedPart);
    }

    /**
     * Runs the text contains step.
     *
     * @param target the target value
     * @param expectedPart the expected part
     * @return the text contains
     */
    public static ScenarioAssertion textContains(UiScenarioTarget target, String expectedPart) {
        return ScenarioAssertions.assertion("ui.textContains(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            String actual = UiScenarioTargets.textValue(node);
            context.assertTrue(actual != null && expectedPart != null && actual.indexOf(expectedPart) >= 0,
                    "UI text did not contain expected part for " + target.description()
                            + " expectedPart=" + expectedPart + " actual=" + actual);
        });
    }

    /**
     * Runs the value equals step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @return the value equals
     */
    public static ScenarioAssertion valueEquals(String validationId, Object expected) {
        return valueEquals(UiScenarioTargets.id(validationId), expected);
    }

    /**
     * Runs the value equals step.
     *
     * @param target the target value
     * @param expected the expected
     * @return the value equals
     */
    public static ScenarioAssertion valueEquals(UiScenarioTarget target, Object expected) {
        return ScenarioAssertions.assertion("ui.valueEquals(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertEquals(expected, node.value(), "UI value mismatch for " + target.description());
        });
    }

    /**
     * Runs the int value step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @return the int value
     */
    public static ScenarioAssertion intValue(String validationId, int expected) {
        return intValue(UiScenarioTargets.id(validationId), expected);
    }

    /**
     * Runs the int value step.
     *
     * @param target the target value
     * @param expected the expected
     * @return the int value
     */
    public static ScenarioAssertion intValue(UiScenarioTarget target, int expected) {
        return ScenarioAssertions.assertion("ui.intValue(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertEquals(Integer.valueOf(expected), Integer.valueOf(node.intValue()),
                    "UI int value mismatch for " + target.description());
        });
    }

    /**
     * Runs the float value step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @return the float value
     */
    public static ScenarioAssertion floatValue(String validationId, float expected) {
        return floatValue(UiScenarioTargets.id(validationId), expected, 0.0001f);
    }

    /**
     * Runs the float value step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @param tolerance the tolerance
     * @return the float value
     */
    public static ScenarioAssertion floatValue(String validationId, float expected, float tolerance) {
        return floatValue(UiScenarioTargets.id(validationId), expected, tolerance);
    }

    /**
     * Runs the float value step.
     *
     * @param target the target value
     * @param expected the expected
     * @param tolerance the tolerance
     * @return the float value
     */
    public static ScenarioAssertion floatValue(UiScenarioTarget target, float expected, float tolerance) {
        return ScenarioAssertions.assertion("ui.floatValue(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            float delta = Math.abs(node.floatValue() - expected);
            context.assertTrue(delta <= Math.max(0.0f, tolerance),
                    "UI float value mismatch for " + target.description() + " expected=" + expected
                            + " actual=" + node.floatValue());
        });
    }

    /**
     * Runs the checked step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @return the checked
     */
    public static ScenarioAssertion checked(String validationId, boolean expected) {
        return checked(UiScenarioTargets.id(validationId), expected);
    }

    /**
     * Runs the checked step.
     *
     * @param target the target value
     * @param expected the expected
     * @return the checked
     */
    public static ScenarioAssertion checked(UiScenarioTarget target, boolean expected) {
        return ScenarioAssertions.assertion("ui.checked(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.CHECKBOX
                            || node.type() == UiNodeType.SWITCH
                            || node.type() == UiNodeType.RADIO_BUTTON
                            || node.type() == UiNodeType.COLLAPSE_BAR,
                    "UI target is not a checkable control: " + target.description());
            context.assertEquals(Boolean.valueOf(expected), Boolean.valueOf(node.checked()),
                    "UI checked state mismatch for " + target.description());
        });
    }

    /**
     * Runs the enabled step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @return the enabled
     */
    public static ScenarioAssertion enabled(String validationId, boolean expected) {
        return enabled(UiScenarioTargets.id(validationId), expected);
    }

    /**
     * Runs the enabled step.
     *
     * @param target the target value
     * @param expected the expected
     * @return the enabled
     */
    public static ScenarioAssertion enabled(UiScenarioTarget target, boolean expected) {
        return ScenarioAssertions.assertion("ui.enabled(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            boolean actual = node.modifier() == null || node.modifier().enabled();
            context.assertEquals(Boolean.valueOf(expected), Boolean.valueOf(actual),
                    "UI enabled state mismatch for " + target.description());
        });
    }

    /**
     * Runs the type step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @return the type
     */
    public static ScenarioAssertion type(String validationId, UiNodeType expected) {
        return type(UiScenarioTargets.id(validationId), expected);
    }

    /**
     * Runs the type step.
     *
     * @param target the target value
     * @param expected the expected
     * @return the type
     */
    public static ScenarioAssertion type(UiScenarioTarget target, UiNodeType expected) {
        return ScenarioAssertions.assertion("ui.type(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertEquals(expected, node.type(), "UI type mismatch for " + target.description());
        });
    }

    /**
     * Runs the bounds at least step.
     *
     * @param validationId the validation ID
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the bounds at least
     */
    public static ScenarioAssertion boundsAtLeast(String validationId, float width, float height) {
        return boundsAtLeast(UiScenarioTargets.id(validationId), width, height);
    }

    /**
     * Runs the bounds at least step.
     *
     * @param target the target value
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the bounds at least
     */
    public static ScenarioAssertion boundsAtLeast(UiScenarioTarget target, float width, float height) {
        return ScenarioAssertions.assertion("ui.boundsAtLeast(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.bounds().width() >= width && node.bounds().height() >= height,
                    "UI bounds too small for " + target.description() + " expectedAtLeast="
                            + width + "x" + height + " actual="
                            + node.bounds().width() + "x" + node.bounds().height());
        });
    }

    /**
     * Runs the bounds inside viewport step.
     *
     * @param validationId the validation ID
     * @return the bounds inside viewport
     */
    public static ScenarioAssertion boundsInsideViewport(String validationId) {
        return boundsInsideViewport(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the bounds inside viewport step.
     *
     * @param target the target value
     * @return the bounds inside viewport
     */
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

    /**
     * Runs the focused step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @return the focused
     */
    public static ScenarioAssertion focused(String validationId, boolean expected) {
        return focused(UiScenarioTargets.id(validationId), expected);
    }

    /**
     * Runs the focused step.
     *
     * @param target the target value
     * @param expected the expected
     * @return the focused
     */
    public static ScenarioAssertion focused(UiScenarioTarget target, boolean expected) {
        return ScenarioAssertions.assertion("ui.focused(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertEquals(Boolean.valueOf(expected), Boolean.valueOf(node.focused()),
                    "UI focused state mismatch for " + target.description());
        });
    }

    /**
     * Runs the modal open step.
     *
     * @param modalId the modal ID
     * @return the modal open
     */
    public static ScenarioAssertion modalOpen(String modalId) {
        return visible(UiScenarioTargets.typeAndKey(UiNodeType.MODAL, modalId));
    }

    /**
     * Runs the popup open step.
     *
     * @param popupId the popup ID
     * @return the popup open
     */
    public static ScenarioAssertion popupOpen(String popupId) {
        return visible(UiScenarioTargets.typeAndKey(UiNodeType.POPUP, popupId));
    }

    /**
     * Runs the active tab step.
     *
     * @param validationId the validation ID
     * @param expectedIndex the expected index
     * @return the active tab
     */
    public static ScenarioAssertion activeTab(String validationId, int expectedIndex) {
        return activeTab(UiScenarioTargets.id(validationId), expectedIndex);
    }

    /**
     * Runs the active tab step.
     *
     * @param target the target value
     * @param expectedIndex the expected index
     * @return the active tab
     */
    public static ScenarioAssertion activeTab(UiScenarioTarget target, int expectedIndex) {
        return ScenarioAssertions.assertion("ui.activeTab(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.TABS, "UI target is not tabs: " + target.description());
            context.assertEquals(Integer.valueOf(expectedIndex), Integer.valueOf(node.intValue()),
                    "Active tab mismatch for " + target.description());
        });
    }

    /**
     * Runs the slider value step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @return the slider value
     */
    public static ScenarioAssertion sliderValue(String validationId, float expected) {
        return sliderValue(UiScenarioTargets.id(validationId), expected, 0.0001f);
    }

    /**
     * Runs the slider value step.
     *
     * @param validationId the validation ID
     * @param expected the expected
     * @param tolerance the tolerance
     * @return the slider value
     */
    public static ScenarioAssertion sliderValue(String validationId, float expected, float tolerance) {
        return sliderValue(UiScenarioTargets.id(validationId), expected, tolerance);
    }

    /**
     * Runs the slider value step.
     *
     * @param target the target value
     * @param expected the expected
     * @param tolerance the tolerance
     * @return the slider value
     */
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
