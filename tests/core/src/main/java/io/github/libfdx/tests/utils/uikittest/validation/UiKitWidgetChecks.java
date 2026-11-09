package io.github.libfdx.tests.utils.uikittest.validation;

import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.COMPACT_CHECKBOX_SIZE;
import static io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseWidgets.DEFAULT_CHECKBOX_SIZE;
import static io.github.libfdx.tests.utils.uikittest.validation.UiKitTestInput.clamp;
import static io.github.libfdx.tests.utils.uikittest.validation.UiKitTestInput.find;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.display.Display;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.validation.scenario.Scenario;
import io.github.libfdx.validation.scenario.ScenarioContext;
import io.github.libfdx.validation.scenario.ScenarioInputDriver;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioTargets;

/** Checks widget geometry, overlays, settings, and theme changes. */
final class UiKitWidgetChecks {
    private final UiKitShowcase showcase;
    private final UiRoot root;
    private final DefaultInput input;
    private final Display display;
    private final UiKitTestInput interaction;

    private boolean textScaleReleaseCommitChecked;
    private boolean checkboxAlignmentChecked;
    private boolean popupPassthroughChecked;
    private boolean popupBlockingChecked;
    private boolean progressBarChecked;
    private boolean tabsChecked;
    private boolean newWidgetsChecked;
    private boolean themeSwitchingChecked;
    private boolean constrainedLayoutChecked;

    UiKitWidgetChecks(UiKitShowcase showcase, UiRoot root, DefaultInput input,
            Display display, UiKitTestInput interaction) {
        this.showcase = showcase;
        this.root = root;
        this.input = input;
        this.display = display;
        this.interaction = interaction;
    }

    void verifyClickAndPopupChecks() {
        if (showcase.clickCount() <= 0) {
            throw new FdxException("UiKitTest button callback was not invoked by input dispatch");
        }
        if (!showcase.checked()) {
            throw new FdxException("UiKitTest checkbox callback was not invoked by input dispatch");
        }
        if (!checkboxAlignmentChecked) {
            throw new FdxException("UiKitTest did not check checkbox label alignment");
        }
        if (!popupPassthroughChecked) {
            throw new FdxException("UiKitTest did not check non-blocking popup input pass-through");
        }
        if (!popupBlockingChecked) {
            throw new FdxException("UiKitTest did not check blocking popup input capture");
        }
    }

    void verifyScaleAndTabs() {
        if (showcase.volume() < 0.95f) {
            throw new FdxException("UiKitTest slider drag did not keep updating after leaving widget bounds");
        }
        if (showcase.scaleGlobally() && Math.abs(showcase.uiScale() - showcase.initialUiScale()) < 0.01f) {
            throw new FdxException("UiKitTest text size setting did not change the global UI scale");
        }
        if (!textScaleReleaseCommitChecked) {
            throw new FdxException("UiKitTest did not verify touch text-size scaling commits on release");
        }
        if (!progressBarChecked) {
            throw new FdxException("UiKitTest did not validate progress bar nodes");
        }
        if (!tabsChecked) {
            throw new FdxException("UiKitTest did not validate tab switching");
        }
    }

    void verifyWidgetChecks() {
        if (!newWidgetsChecked) {
            throw new FdxException(
                    "UiKitTest did not validate switches, radio choices, loading, and disclosure panels");
        }
        if (!themeSwitchingChecked) {
            throw new FdxException("UiKitTest did not validate theme switching");
        }
        if (!constrainedLayoutChecked) {
            throw new FdxException("UiKitTest did not validate constrained button layout");
        }
    }

    void checkCheckboxLabelAlignment() {
        if (checkboxAlignmentChecked) {
            return;
        }
        UiNode defaultCheckbox = find(root.rootNode(), UiNodeType.CHECKBOX, "Section option");
        UiNode compactCheckbox = find(root.rootNode(), UiNodeType.CHECKBOX, "Visual debug");
        assertAlignedCenters(defaultCheckbox, find(root.rootNode(), UiNodeType.TEXT, "Section option"),
                "Section option checkbox label");
        assertAlignedCenters(compactCheckbox, find(root.rootNode(), UiNodeType.TEXT, "Visual debug"),
                "Visual debug checkbox label");
        assertNodeSize(defaultCheckbox, DEFAULT_CHECKBOX_SIZE, DEFAULT_CHECKBOX_SIZE, "default checkbox");
        assertNodeSize(compactCheckbox, COMPACT_CHECKBOX_SIZE, COMPACT_CHECKBOX_SIZE, "custom checkbox");
        checkboxAlignmentChecked = true;
    }

    void assertTooltipPanelAwayFromOrigin(ScenarioContext context, String tooltipKey) {
        UiNode tooltip = UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP, tooltipKey).resolve(root);
        context.assertTrue(renderedVisible(tooltip), "Tooltip is not rendered during fade: " + tooltipKey);
        UiNode panel = firstRenderedChild(tooltip);
        context.assertTrue(panel != null, "Tooltip has no rendered panel during fade: " + tooltipKey);
        UiRect bounds = panel.bounds();
        context.assertTrue(bounds.x() > 1.0f || bounds.y() > 1.0f,
                "Tooltip panel moved to the root origin during fade: " + tooltipKey
                        + " bounds=" + bounds.x() + "," + bounds.y() + " "
                        + bounds.width() + "x" + bounds.height());
    }

    void hoverAwayFromTooltipTargets(ScenarioContext context) {
        ScenarioInputDriver inputDriver = context.host().inputDriver();
        context.assertTrue(inputDriver != null, "Scenario host does not provide an input driver.");
        float x = 4.0f;
        float y = display != null ? Math.max(4.0f, display.height() - 4.0f) : 4.0f;
        inputDriver.pointerMove(x, y);
    }

    void hoverValidationTarget(ScenarioContext context, String validationId, float xPercent, float yPercent) {
        UiNode node = UiScenarioTargets.id(validationId).require(context);
        context.assertTrue(renderedVisible(node), "UI target is not rendered: " + validationId);
        ScenarioInputDriver inputDriver = context.host().inputDriver();
        context.assertTrue(inputDriver != null, "Scenario host does not provide an input driver.");
        UiRect bounds = node.bounds();
        context.assertTrue(bounds.width() > 0.0f && bounds.height() > 0.0f,
                "UI target does not have usable bounds: " + validationId);
        float x = bounds.x() + bounds.width() * clamp(xPercent, 0.0f, 1.0f);
        float y = bounds.y() + bounds.height() * clamp(yPercent, 0.0f, 1.0f);
        inputDriver.pointerMove(root.displayX(x), root.displayY(y));
    }

    void validateProgressBars() {
        UiNode volumeProgress = find(root.rootNode(), UiNodeType.PROGRESS_BAR, "Volume progress");
        UiNode wideProgress = find(root.rootNode(), UiNodeType.PROGRESS_BAR, "Wide progress");
        if (volumeProgress == null || wideProgress == null) {
            throw new FdxException("UiKitTest could not find progress bar nodes");
        }
        if (volumeProgress.bounds().width() <= 20.0f || volumeProgress.bounds().height() <= 4.0f) {
            throw new FdxException("UiKitTest volume progress bar did not receive usable bounds: "
                    + volumeProgress.bounds().width() + "x" + volumeProgress.bounds().height());
        }
        if (Math.abs(volumeProgress.floatValue() - showcase.volume()) > 0.001f) {
            throw new FdxException("UiKitTest volume progress bar did not mirror slider state: progress="
                    + volumeProgress.floatValue() + ", volume=" + showcase.volume());
        }
        progressBarChecked = true;
    }

    void validateTabs() {
        showcase.tabs().activeDemoTab.set(0);
        interaction.settleValidationLayout();
        UiNode tabs = find(root.rootNode(), UiNodeType.TABS, "Demo tabs");
        if (tabs == null) {
            throw new FdxException("UiKitTest could not find tabs node");
        }
        if (tabs.bounds().width() <= 120.0f || tabs.bounds().height() <= 24.0f) {
            throw new FdxException("UiKitTest tabs did not receive usable bounds: "
                    + tabs.bounds().width() + "x" + tabs.bounds().height());
        }
        if (showcase.tabs().activeDemoTab.get() != 0) {
            throw new FdxException("UiKitTest tabs did not start from the first active tab: " + showcase.tabs().activeDemoTab.get());
        }
        UiRect bounds = tabs.bounds();
        int x = root.displayX(bounds.x() + bounds.width() * 0.375f);
        int y = root.displayY(bounds.y() + bounds.height() * 0.5f);
        input.dispatchPointerMoved(x, y);
        input.dispatchPointerDown(MouseButton.LEFT, x, y);
        input.dispatchPointerUp(MouseButton.LEFT, x, y);
        interaction.settleValidationLayout();
        if (showcase.tabs().activeDemoTab.get() != 1) {
            throw new FdxException("UiKitTest tabs did not select the second tab: " + showcase.tabs().activeDemoTab.get());
        }
        showcase.tabs().activeDemoTab.set(3);
        interaction.settleValidationLayout();
        tabs = find(root.rootNode(), UiNodeType.TABS, "Demo tabs");
        if (tabs == null) {
            throw new FdxException("UiKitTest could not find tabs node for border click validation");
        }
        bounds = tabs.bounds();
        x = root.displayX(bounds.x() + bounds.width() * 0.875f);
        y = root.displayY(bounds.y() + 1.0f);
        input.dispatchPointerMoved(x, y);
        input.dispatchPointerDown(MouseButton.LEFT, x, y);
        input.dispatchPointerUp(MouseButton.LEFT, x, y);
        interaction.settleValidationLayout();
        if (showcase.tabs().activeDemoTab.get() != 3) {
            throw new FdxException("UiKitTest tab border click selected the wrong tab: " + showcase.tabs().activeDemoTab.get());
        }
        showcase.tabs().activeDemoTab.set(1);
        interaction.settleValidationLayout();
        tabsChecked = true;
    }

    void validateToggleChoices() {
        UiNode toggle = find(root.rootNode(), UiNodeType.SWITCH, "Notifications");
        if (toggle == null || toggle.bounds().width() < 48.0f || toggle.bounds().height() < 28.0f) {
            throw new FdxException("UiKitTest switch did not receive usable bounds");
        }
        boolean before = showcase.toggles().switchEnabled.get();
        interaction.click(toggle);
        interaction.settleValidationLayout();
        if (showcase.toggles().switchEnabled.get() == before) {
            throw new FdxException("UiKitTest switch did not toggle through pointer input");
        }
        toggle = find(root.rootNode(), UiNodeType.SWITCH, "Notifications");
        interaction.click(toggle);
        interaction.settleValidationLayout();

        showcase.toggles().radioChoice.set(0);
        interaction.settleValidationLayout();
        UiNode quality = find(root.rootNode(), UiNodeType.RADIO_BUTTON, "Quality");
        if (quality == null || quality.bounds().height() < 28.0f) {
            throw new FdxException("UiKitTest radio choice did not receive a usable target");
        }
        interaction.click(quality);
        interaction.settleValidationLayout();
        if (showcase.toggles().radioChoice.get() != 1) {
            throw new FdxException("UiKitTest radio choice did not update the shared state: " + showcase.toggles().radioChoice.get());
        }
    }

    void validateLoadingIndicators() {
        UiNode bar = find(root.rootNode(), UiNodeType.LOADING_BAR, "Loading bar");
        UiNode spinner = find(root.rootNode(), UiNodeType.LOADING_SPINNER, "Loading spinner");
        if (bar == null || spinner == null) {
            throw new FdxException("UiKitTest could not find both loading indicators");
        }
        if (bar.bounds().width() <= 20.0f || bar.bounds().height() < 4.0f) {
            throw new FdxException("UiKitTest loading bar did not receive usable bounds: "
                    + bar.bounds().width() + "x" + bar.bounds().height());
        }
        if (spinner.bounds().width() < 20.0f || spinner.bounds().height() < 20.0f) {
            throw new FdxException("UiKitTest spinner did not receive usable bounds: "
                    + spinner.bounds().width() + "x" + spinner.bounds().height());
        }
    }

    void validateCollapseBars() {
        UiNode collapse = find(root.rootNode(), UiNodeType.COLLAPSE_BAR, "Basic disclosure");
        if (collapse == null || collapse.bounds().height() < 44.0f || collapse.children().isEmpty()) {
            throw new FdxException("UiKitTest expanded collapse bar did not compose its body");
        }
        interaction.click(collapse);
        interaction.settleValidationLayout();
        collapse = find(root.rootNode(), UiNodeType.COLLAPSE_BAR, "Basic disclosure");
        if (collapse == null || collapse.bounds().height() < 44.0f
                || collapse.checked() || !collapse.children().isEmpty()) {
            throw new FdxException("UiKitTest collapsed disclosure retained visible body content");
        }
        interaction.click(collapse);
        interaction.settleValidationLayout();
        collapse = find(root.rootNode(), UiNodeType.COLLAPSE_BAR, "Basic disclosure");
        if (collapse == null || !collapse.checked() || collapse.children().isEmpty()) {
            throw new FdxException("UiKitTest disclosure did not recompose its expanded body");
        }
        newWidgetsChecked = true;
    }

    void validateConstrainedButtonLayout() {
        UiNode row = find(root.rootNode(), UiNodeType.ROW, "Constrained row");
        UiNode alpha = find(root.rootNode(), UiNodeType.BUTTON, "Alpha");
        UiNode beta = find(root.rootNode(), UiNodeType.BUTTON, "Beta");
        UiNode gamma = find(root.rootNode(), UiNodeType.BUTTON, "Gamma");
        if (row == null || alpha == null || beta == null || gamma == null) {
            throw new FdxException("UiKitTest could not find constrained layout nodes");
        }
        assertInside(alpha.bounds(), row.bounds(), "Alpha");
        assertInside(beta.bounds(), row.bounds(), "Beta");
        assertInside(gamma.bounds(), row.bounds(), "Gamma");
        if (alpha.bounds().width() >= 150.0f || beta.bounds().width() >= 150.0f
                || gamma.bounds().width() >= 150.0f) {
            throw new FdxException("UiKitTest constrained row did not shrink fixed-width children");
        }
        constrainedLayoutChecked = true;
    }

    void validateActiveTheme(int index) {
        if (showcase.activeTheme() != index || root.theme() != showcase.theme(index)) {
            throw new FdxException("UiKitTest did not apply the " + showcase.themeName(index) + " theme");
        }
        themeSwitchingChecked = true;
    }

    void validatePressedConstrainedButton() {
        UiNode gamma = find(root.rootNode(), UiNodeType.BUTTON, "Gamma");
        UiStyle button = root.theme() != null ? root.theme().style("button") : null;
        if (gamma == null || !gamma.pressed()) {
            throw new FdxException("UiKitTest did not hold Gamma in its pressed state for validation");
        }
        validateSingleLineButtonState("default", button);
        validateSingleLineButtonState("hover", button != null ? button.hover() : null);
        validateSingleLineButtonState("pressed", button != null ? button.pressed() : null);
        validateSingleLineButtonState("disabled", button != null ? button.disabled() : null);
        UiStyle selectedNavigation = root.theme() != null ? root.theme().style("nav-active") : null;
        validateSingleLineButtonState("selected navigation", selectedNavigation);
        validateSingleLineButtonState("selected navigation hover",
                selectedNavigation != null ? selectedNavigation.hover() : null);
        validateSingleLineButtonState("selected navigation pressed",
                selectedNavigation != null ? selectedNavigation.pressed() : null);
    }

    void checkPopupPassThrough() {
        int before = showcase.clickCount();
        interaction.click(find(root.rootNode(), UiNodeType.BUTTON, "Press"));
        if (showcase.clickCount() <= before) {
            throw new FdxException("UiKitTest non-blocking popup did not allow input behind it");
        }
        popupPassthroughChecked = true;
    }

    void checkPopupBlocking() {
        int before = showcase.clickCount();
        interaction.click(find(root.rootNode(), UiNodeType.BUTTON, "Press"));
        if (showcase.clickCount() != before) {
            throw new FdxException("UiKitTest blocking popup allowed input behind it");
        }
        interaction.click(find(root.rootNode(), UiNodeType.BUTTON, "Keep"));
        if (showcase.clickCount() <= before) {
            throw new FdxException("UiKitTest blocking popup did not allow input inside the popup");
        }
        interaction.click(find(root.rootNode(), UiNodeType.CHECKBOX, "Block input"));
        if (showcase.overlays().blockPopupInput.get()) {
            throw new FdxException("UiKitTest blocking popup did not allow turning blocking input off inside popup");
        }
        popupBlockingChecked = true;
    }

    void dragVolumeSlider() {
        dragSlider("Volume setting", 0.20f, 1.35f);
    }

    void validateTextSizeSliderReleaseCommit() {
        UiNode slider = find(root.rootNode(), UiNodeType.SLIDER, "Text size setting");
        UiNode nav = find(root.rootNode(), UiNodeType.SCROLL, "Sections nav");
        if (showcase.compactViewport() && slider != null && nav != null) {
            float targetY = showcase.navScroll().y();
            if (slider.bounds().bottom() > nav.bounds().bottom()) {
                targetY += slider.bounds().bottom() - nav.bounds().bottom() + 8.0f;
            } else if (slider.bounds().y() < nav.bounds().y()) {
                targetY -= nav.bounds().y() - slider.bounds().y() + 8.0f;
            }
            showcase.navScroll().scrollTo(showcase.navScroll().x(), targetY);
            root.requestCompose();
            interaction.settleValidationLayout();
            slider = find(root.rootNode(), UiNodeType.SLIDER, "Text size setting");
        }
        if (slider == null) {
            throw new FdxException("UiKitTest could not find the text size slider");
        }
        UiRect bounds = slider.bounds();
        int downX = root.displayX(bounds.x() + bounds.width() * 0.50f);
        int y = root.displayY(bounds.y() + bounds.height() * 0.5f);
        int moveX = root.displayX(bounds.x() + bounds.width() * 0.78f);
        float appliedBefore = root.uiScale();

        input.dispatchTouchDown(0, downX, y, 1.0f);
        input.dispatchTouchMoved(0, moveX, y, 1.0f);
        if (Math.abs(showcase.uiScale() - appliedBefore) < 0.01f) {
            input.dispatchTouchUp(0, moveX, y, 1.0f);
            throw new FdxException("UiKitTest text size touch drag did not update the slider state: slider="
                    + bounds.x() + "," + bounds.y() + " " + bounds.width() + "x" + bounds.height()
                    + ", enabled=" + slider.modifier().enabled()
                    + ", nav=" + (nav != null ? nav.bounds() : "missing")
                    + ", scrollY=" + showcase.navScroll().y() + "/" + showcase.navScroll().maxY());
        }
        showcase.applyGlobalScale(input);
        if (Math.abs(root.uiScale() - appliedBefore) > 0.0001f) {
            input.dispatchTouchUp(0, moveX, y, 1.0f);
            throw new FdxException("UiKitTest text size scale applied before touch release: before="
                    + appliedBefore + ", during=" + root.uiScale() + ", target=" + showcase.uiScale());
        }

        input.dispatchTouchUp(0, moveX, y, 1.0f);
        showcase.applyGlobalScale(input);
        if (Math.abs(root.uiScale() - showcase.uiScale()) > 0.0001f) {
            throw new FdxException("UiKitTest text size scale did not apply after touch release: root="
                    + root.uiScale() + ", target=" + showcase.uiScale());
        }
        textScaleReleaseCommitChecked = true;
        interaction.settleValidationLayout();
    }

    private void assertAlignedCenters(UiNode left, UiNode right, String label) {
        if (left == null || right == null) {
            throw new FdxException("UiKitTest could not find " + label + " nodes");
        }
        float leftCenter = left.bounds().y() + left.bounds().height() * 0.5f;
        float rightCenter = right.bounds().y() + right.bounds().height() * 0.5f;
        if (Math.abs(leftCenter - rightCenter) > 0.5f) {
            throw new FdxException("UiKitTest did not center " + label + ": left="
                    + left.bounds().y() + " " + left.bounds().height() + ", right="
                    + right.bounds().y() + " " + right.bounds().height());
        }
    }

    private void assertNodeSize(UiNode node, float width, float height, String label) {
        if (node == null) {
            throw new FdxException("UiKitTest could not find " + label + " node");
        }
        if (Math.abs(node.bounds().width() - width) > 0.5f || Math.abs(node.bounds().height() - height) > 0.5f) {
            throw new FdxException("UiKitTest unexpected " + label + " size: actual="
                    + node.bounds().width() + "x" + node.bounds().height() + ", expected=" + width + "x" + height);
        }
    }

    private UiNode firstRenderedChild(UiNode node) {
        if (node == null) {
            return null;
        }
        for (int i = 0; i < node.children().size(); i++) {
            UiNode child = node.children().get(i);
            if (renderedVisible(child) && child.bounds().width() > 0.0f && child.bounds().height() > 0.0f) {
                return child;
            }
        }
        return null;
    }

    private boolean renderedVisible(UiNode node) {
        return node != null && node.visible()
                && (node.modifier() == null || node.modifier().alpha() > 0.001f);
    }

    private void validateSingleLineButtonState(String state, UiStyle style) {
        UiTextStyle text = style != null ? style.textStyle() : null;
        if (text == null || text.wrap() || !text.ellipsis()) {
            throw new FdxException("UiKitTest " + state
                    + " button style can wrap or clip overflowing text");
        }
    }

    private void assertInside(UiRect child, UiRect parent, String label) {
        if (child.width() < 0.0f || child.height() < 0.0f
                || child.x() < parent.x() - 0.5f || child.y() < parent.y() - 0.5f
                || child.right() > parent.right() + 0.5f || child.bottom() > parent.bottom() + 0.5f) {
            throw new FdxException("UiKitTest " + label + " escaped constrained bounds: child="
                    + child.x() + "," + child.y() + " " + child.width() + "x" + child.height()
                    + ", parent=" + parent.x() + "," + parent.y() + " "
                    + parent.width() + "x" + parent.height());
        }
    }

    private void dragSlider(String semanticLabel, float startProgress, float endProgress) {
        UiNode slider = find(root.rootNode(), UiNodeType.SLIDER, semanticLabel);
        if (slider == null) {
            return;
        }
        UiRect bounds = slider.bounds();
        int downX = root.displayX(bounds.x() + bounds.width() * startProgress);
        int downY = root.displayY(bounds.y() + bounds.height() * 0.5f);
        int moveX = root.displayX(bounds.x() + bounds.width() * endProgress);
        int moveY = root.displayY(bounds.y() + bounds.height() * 0.5f);
        input.dispatchPointerMoved(downX, downY);
        input.dispatchPointerDown(MouseButton.LEFT, downX, downY);
        input.dispatchPointerMoved(moveX, moveY);
        input.dispatchPointerUp(MouseButton.LEFT, moveX, moveY);
        interaction.settleValidationLayout();
    }

}
