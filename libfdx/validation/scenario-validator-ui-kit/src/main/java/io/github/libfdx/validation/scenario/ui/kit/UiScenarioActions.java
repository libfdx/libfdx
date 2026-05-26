package io.github.libfdx.validation.scenario.ui.kit;

import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.ScenarioAction;
import io.github.libfdx.validation.scenario.ScenarioActions;
import io.github.libfdx.validation.scenario.ScenarioContext;
import io.github.libfdx.validation.scenario.ScenarioInputDriver;

public final class UiScenarioActions {
    private UiScenarioActions() {
    }

    public static ScenarioAction click(String validationId) {
        return click(UiScenarioTargets.id(validationId));
    }

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

    public static ScenarioAction press(String validationId) {
        return press(UiScenarioTargets.id(validationId));
    }

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

    public static ScenarioAction release(String validationId) {
        return release(UiScenarioTargets.id(validationId));
    }

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

    public static ScenarioAction hover(String validationId) {
        return hover(UiScenarioTargets.id(validationId));
    }

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

    public static ScenarioAction focus(String validationId) {
        return focus(UiScenarioTargets.id(validationId));
    }

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

    public static ScenarioAction type(String validationId, String text) {
        return type(UiScenarioTargets.id(validationId), text);
    }

    public static ScenarioAction type(UiScenarioTarget target, String text) {
        return ScenarioActions.callback("ui.type(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.visible(), "UI target is not visible: " + target.description());
            focus(target).perform(context);
            requireInput(context).text(text != null ? text : "");
            context.emit("ui.textChanged:" + target.eventName());
        });
    }

    public static ScenarioAction drag(String validationId, float startX, float startY, float endX, float endY) {
        return drag(UiScenarioTargets.id(validationId), startX, startY, endX, endY);
    }

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

    public static ScenarioAction dragSlider(String validationId, float value) {
        return dragSlider(UiScenarioTargets.id(validationId), value);
    }

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

    public static ScenarioAction dragSlider(String validationId, float startProgress, float endProgress) {
        return dragSlider(UiScenarioTargets.id(validationId), startProgress, endProgress);
    }

    public static ScenarioAction dragSlider(UiScenarioTarget target, float startProgress, float endProgress) {
        return ScenarioActions.callback("ui.dragSlider(" + target.description() + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.SLIDER, "UI target is not a slider: " + target.description());
            drag(target, startProgress, 0.5f, endProgress, 0.5f).perform(context);
            context.emit("ui.slider.dragged:" + target.eventName() + ":" + node.floatValue());
            context.emit("ui.valueChanged:" + target.eventName());
        });
    }

    public static ScenarioAction clickTab(String validationId, int index) {
        return clickTab(UiScenarioTargets.id(validationId), index);
    }

    public static ScenarioAction clickTab(UiScenarioTarget target, int index) {
        return ScenarioActions.callback("ui.clickTab(" + target.description() + "," + index + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.TABS, "UI target is not tabs: " + target.description());
            context.assertTrue(node.selectTab(index), "Unable to select tab " + index + ": " + target.description());
            context.emit("ui.tab.selected:" + target.eventName() + ":" + index);
            context.emit("ui.valueChanged:" + target.eventName());
        });
    }

    public static ScenarioAction sliderValue(String validationId, float value) {
        return sliderValue(UiScenarioTargets.id(validationId), value);
    }

    public static ScenarioAction sliderValue(UiScenarioTarget target, float value) {
        return ScenarioActions.callback("ui.sliderValue(" + target.description() + "," + value + ")", context -> {
            UiNode node = target.require(context);
            context.assertTrue(node.type() == UiNodeType.SLIDER, "UI target is not a slider: " + target.description());
            context.assertTrue(node.setSliderValue(value), "Unable to set slider value: " + target.description());
            context.emit("ui.slider.value:" + target.eventName() + ":" + node.floatValue());
            context.emit("ui.valueChanged:" + target.eventName());
        });
    }

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
