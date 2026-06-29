package io.github.libfdx.validation.scenario.ui.kit;

import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.ScenarioAction;
import io.github.libfdx.validation.scenario.ScenarioActions;
import io.github.libfdx.validation.scenario.ScenarioContext;
import io.github.libfdx.validation.scenario.ScenarioInputDriver;

/**
 * Represents an ui scenario actions.
 *
 * @author xpenatan
 */
public final class UiScenarioActions {
    private UiScenarioActions() {
    }

    /**
     * Runs the click step.
     *
     * @param validationId the validation ID
     * @return the click
     */
    public static ScenarioAction click(String validationId) {
        return click(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the click step.
     *
     * @param target the target value
     * @return the click
     */
    public static ScenarioAction click(UiScenarioTarget target) {
        return ScenarioActions.callback("ui.click(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.visible(), "UI target is not visible: " + target.description());
            context.assertTrue(usableBounds(node), "UI target does not have usable bounds: " + target.description());
            ScenarioInputDriver input = requireInput(context);
            UiRoot root = context.requireProbe(UiRoot.class);
            float x = displayCenterX(root, node);
            float y = displayCenterY(root, node);
            input.pointerMove(x, y);
            input.pointerDown(x, y);
            input.pointerUp(x, y);
            context.emit("ui.clicked:" + target.eventName());
        });
    }

    /**
     * Runs the press step.
     *
     * @param validationId the validation ID
     * @return the press
     */
    public static ScenarioAction press(String validationId) {
        return press(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the press step.
     *
     * @param target the target value
     * @return the press
     */
    public static ScenarioAction press(UiScenarioTarget target) {
        return ScenarioActions.callback("ui.press(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.visible(), "UI target is not visible: " + target.description());
            ScenarioInputDriver input = requireInput(context);
            UiRoot root = context.requireProbe(UiRoot.class);
            float x = displayCenterX(root, node);
            float y = displayCenterY(root, node);
            input.pointerMove(x, y);
            input.pointerDown(x, y);
            context.emit("ui.pressed:" + target.eventName());
        });
    }

    /**
     * Runs the release step.
     *
     * @param validationId the validation ID
     * @return the release
     */
    public static ScenarioAction release(String validationId) {
        return release(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the release step.
     *
     * @param target the target value
     * @return the release
     */
    public static ScenarioAction release(UiScenarioTarget target) {
        return ScenarioActions.callback("ui.release(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.visible(), "UI target is not visible: " + target.description());
            ScenarioInputDriver input = requireInput(context);
            UiRoot root = context.requireProbe(UiRoot.class);
            float x = displayCenterX(root, node);
            float y = displayCenterY(root, node);
            input.pointerUp(x, y);
            context.emit("ui.released:" + target.eventName());
        });
    }

    /**
     * Runs the hover step.
     *
     * @param validationId the validation ID
     * @return the hover
     */
    public static ScenarioAction hover(String validationId) {
        return hover(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the hover step.
     *
     * @param target the target value
     * @return the hover
     */
    public static ScenarioAction hover(UiScenarioTarget target) {
        return ScenarioActions.callback("ui.hover(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.visible(), "UI target is not visible: " + target.description());
            ScenarioInputDriver input = requireInput(context);
            UiRoot root = context.requireProbe(UiRoot.class);
            input.pointerMove(displayCenterX(root, node), displayCenterY(root, node));
            context.emit("ui.hovered:" + target.eventName());
        });
    }

    /**
     * Runs the focus step.
     *
     * @param validationId the validation ID
     * @return the focus
     */
    public static ScenarioAction focus(String validationId) {
        return focus(UiScenarioTargets.id(validationId));
    }

    /**
     * Runs the focus step.
     *
     * @param target the target value
     * @return the focus
     */
    public static ScenarioAction focus(UiScenarioTarget target) {
        return ScenarioActions.callback("ui.focus(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.visible(), "UI target is not visible: " + target.description());
            ScenarioInputDriver input = requireInput(context);
            UiRoot root = context.requireProbe(UiRoot.class);
            float x = displayCenterX(root, node);
            float y = displayCenterY(root, node);
            input.pointerMove(x, y);
            input.pointerDown(x, y);
            input.pointerUp(x, y);
            context.emit("ui.focused:" + target.eventName());
            context.emit("ui.focusChanged:" + target.eventName());
        });
    }

    /**
     * Runs the type step.
     *
     * @param validationId the validation ID
     * @param text the text
     * @return the type
     */
    public static ScenarioAction type(String validationId, String text) {
        return type(UiScenarioTargets.id(validationId), text);
    }

    /**
     * Runs the type step.
     *
     * @param target the target value
     * @param text the text
     * @return the type
     */
    public static ScenarioAction type(UiScenarioTarget target, String text) {
        return ScenarioActions.callback("ui.type(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.visible(), "UI target is not visible: " + target.description());
            focus(target).perform(context);
            requireInput(context).text(text != null ? text : "");
            context.emit("ui.textChanged:" + target.eventName());
        });
    }

    /**
     * Runs the drag step.
     *
     * @param validationId the validation ID
     * @param startX the start x
     * @param startY the start y
     * @param endX the end x
     * @param endY the end y
     * @return the drag
     */
    public static ScenarioAction drag(String validationId, float startX, float startY, float endX, float endY) {
        return drag(UiScenarioTargets.id(validationId), startX, startY, endX, endY);
    }

    /**
     * Runs the drag step.
     *
     * @param target the target value
     * @param startX the start x
     * @param startY the start y
     * @param endX the end x
     * @param endY the end y
     * @return the drag
     */
    public static ScenarioAction drag(UiScenarioTarget target, float startX, float startY, float endX, float endY) {
        return ScenarioActions.callback("ui.drag(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.visible(), "UI target is not visible: " + target.description());
            ScenarioInputDriver input = requireInput(context);
            UiRoot root = context.requireProbe(UiRoot.class);
            UiRect bounds = node.bounds();
            float downX = root.displayX(bounds.x() + bounds.width() * clamp01(startX));
            float downY = root.displayY(bounds.y() + bounds.height() * clamp01(startY));
            float upX = root.displayX(bounds.x() + bounds.width() * clamp01(endX));
            float upY = root.displayY(bounds.y() + bounds.height() * clamp01(endY));
            input.pointerMove(downX, downY);
            input.pointerDown(downX, downY);
            input.pointerMove(upX, upY);
            input.pointerUp(upX, upY);
            context.emit("ui.dragged:" + target.eventName());
        });
    }

    /**
     * Runs the drag slider step.
     *
     * @param validationId the validation ID
     * @param value the value
     * @return the drag slider
     */
    public static ScenarioAction dragSlider(String validationId, float value) {
        return dragSlider(UiScenarioTargets.id(validationId), value);
    }

    /**
     * Runs the drag slider step.
     *
     * @param target the target value
     * @param value the value
     * @return the drag slider
     */
    public static ScenarioAction dragSlider(UiScenarioTarget target, float value) {
        return ScenarioActions.callback("ui.dragSlider(" + target.description() + "," + value + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.SLIDER, "UI target is not a slider: " + target.description());
            float start = sliderProgress(node, node.floatValue());
            float end = sliderProgress(node, value);
            dragSlider(target, start, end).perform(context);
            context.emit("ui.valueChanged:" + target.eventName());
        });
    }

    /**
     * Runs the drag slider step.
     *
     * @param validationId the validation ID
     * @param startProgress the start progress
     * @param endProgress the end progress
     * @return the drag slider
     */
    public static ScenarioAction dragSlider(String validationId, float startProgress, float endProgress) {
        return dragSlider(UiScenarioTargets.id(validationId), startProgress, endProgress);
    }

    /**
     * Runs the drag slider step.
     *
     * @param target the target value
     * @param startProgress the start progress
     * @param endProgress the end progress
     * @return the drag slider
     */
    public static ScenarioAction dragSlider(UiScenarioTarget target, float startProgress, float endProgress) {
        return ScenarioActions.callback("ui.dragSlider(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.SLIDER, "UI target is not a slider: " + target.description());
            drag(target, startProgress, 0.5f, endProgress, 0.5f).perform(context);
            context.emit("ui.slider.dragged:" + target.eventName() + ":" + node.floatValue());
            context.emit("ui.valueChanged:" + target.eventName());
        });
    }

    /**
     * Runs the click tab step.
     *
     * @param validationId the validation ID
     * @param index the index
     * @return the click tab
     */
    public static ScenarioAction clickTab(String validationId, int index) {
        return clickTab(UiScenarioTargets.id(validationId), index);
    }

    /**
     * Runs the click tab step.
     *
     * @param target the target value
     * @param index the index
     * @return the click tab
     */
    public static ScenarioAction clickTab(UiScenarioTarget target, int index) {
        return ScenarioActions.callback("ui.clickTab(" + target.description() + "," + index + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.TABS, "UI target is not tabs: " + target.description());
            context.assertTrue(node.selectTab(index), "Unable to select tab " + index + ": " + target.description());
            context.emit("ui.tab.selected:" + target.eventName() + ":" + index);
            context.emit("ui.valueChanged:" + target.eventName());
        });
    }

    /**
     * Runs the slider value step.
     *
     * @param validationId the validation ID
     * @param value the value
     * @return the slider value
     */
    public static ScenarioAction sliderValue(String validationId, float value) {
        return sliderValue(UiScenarioTargets.id(validationId), value);
    }

    /**
     * Runs the slider value step.
     *
     * @param target the target value
     * @param value the value
     * @return the slider value
     */
    public static ScenarioAction sliderValue(UiScenarioTarget target, float value) {
        return ScenarioActions.callback("ui.sliderValue(" + target.description() + "," + value + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.SLIDER, "UI target is not a slider: " + target.description());
            context.assertTrue(node.setSliderValue(value), "Unable to set slider value: " + target.description());
            context.emit("ui.slider.value:" + target.eventName() + ":" + node.floatValue());
            context.emit("ui.valueChanged:" + target.eventName());
        });
    }

    /**
     * Runs the capture step.
     *
     * @param name the name
     * @return the capture
     */
    public static ScenarioAction capture(String name) {
        return ScenarioActions.capture(name);
    }

    private static boolean usableBounds(UiNode node) {
        return node != null && node.bounds().width() > 0.0f && node.bounds().height() > 0.0f;
    }

    private static float displayCenterX(UiRoot root, UiNode node) {
        UiRect bounds = node.bounds();
        return root.displayX(bounds.x() + bounds.width() * 0.5f);
    }

    private static float displayCenterY(UiRoot root, UiNode node) {
        UiRect bounds = node.bounds();
        return root.displayY(bounds.y() + bounds.height() * 0.5f);
    }

    private static ScenarioInputDriver requireInput(ScenarioContext context) {
        ScenarioInputDriver input = context.host().inputDriver();
        if (input == null) {
            context.fail("Scenario host does not provide an input driver.");
        }
        return input;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float sliderProgress(UiNode node, float value) {
        float minimum = node.sliderMinimum();
        float maximum = node.sliderMaximum();
        float span = maximum - minimum;
        if (span == 0.0f) {
            return 0.0f;
        }
        return clamp01((value - minimum) / span);
    }
}
