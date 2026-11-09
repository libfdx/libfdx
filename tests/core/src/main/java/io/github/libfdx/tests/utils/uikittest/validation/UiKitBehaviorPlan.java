package io.github.libfdx.tests.utils.uikittest.validation;

import static io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitListsSection.inventoryItem;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_BUTTONS;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_CHECKBOXES;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_COLLAPSE;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_DRAWING;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_LISTS;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_LOADING;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_PROGRESS_BARS;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_SCROLLVIEW;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_SLIDERS;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_TABS;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_TEXT_FIELDS;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_THEMES;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_TOGGLES;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_TOOLTIPS;
import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase.SECTION_WINDOWS;
import static io.github.libfdx.tests.utils.uikittest.validation.UiKitTestInput.find;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.validation.scenario.Scenario;
import io.github.libfdx.validation.scenario.ScenarioActions;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioActions;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioAssertions;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioTargets;
import io.github.libfdx.validation.scenario.ui.kit.UiScenarioWaits;

/** Defines the ordered UI Kit scenarios and their full-run coverage requirements. */
final class UiKitBehaviorPlan {
    private final UiKitShowcase showcase;
    private final UiRoot root;
    private final DefaultInput input;
    private final Logger logger;
    private final UiKitTestInput interaction;
    private final UiKitWidgetChecks widgets;
    private final UiKitTextChecks text;
    private final UiKitWindowChecks windows;

    UiKitBehaviorPlan(UiKitShowcase showcase, UiRoot root, DefaultInput input, Display display, Logger logger,
            RecordingTextInputController textInputController, UiKitTestInput interaction) {
        this.showcase = showcase;
        this.root = root;
        this.input = input;
        this.logger = logger;
        this.interaction = interaction;
        widgets = new UiKitWidgetChecks(showcase, root, input, display, interaction);
        text = new UiKitTextChecks(showcase, root, input, display, logger, textInputController, interaction);
        windows = new UiKitWindowChecks(showcase, root, input, interaction);
    }

    UiKitValidationScenarios.Plan build(boolean captureAllScenarios, Runnable captureDrawing) {
        UiKitValidationScenarios.Builder builder = UiKitValidationScenarios.builder(true);
        builder.entry(0L, captureAllScenarios, false,
                Scenario.named("press-button-down")
                        .custom("show-buttons", context -> interaction.showValidationSection(SECTION_BUTTONS))
                        .expect(UiScenarioAssertions.enabled(UiKitValidationIds.BUTTON_PRESS, true))
                        .action(UiScenarioActions.press(UiKitValidationIds.BUTTON_PRESS)));
        builder.entry(1L, captureAllScenarios, false,
                Scenario.named("press-button-up")
                        .action(UiScenarioActions.release(UiKitValidationIds.BUTTON_PRESS))
                        .custom("show-checkboxes", context -> interaction.showValidationSection(SECTION_CHECKBOXES)));
        builder.entry(2L, true, true,
                Scenario.named("section-checkboxes")
                        .custom("show-checkboxes", context -> interaction.showValidationSection(SECTION_CHECKBOXES))
                        .action(UiScenarioActions.click(UiKitValidationIds.CHECKBOX_SECTION_OPTION))
                        .expect(UiScenarioAssertions.checked(UiKitValidationIds.CHECKBOX_SECTION_OPTION, true))
                        .custom("check-checkbox-label-alignment", context -> widgets.checkCheckboxLabelAlignment()));
        builder.entry(3L, true, true,
                Scenario.named("slider-volume")
                        .custom("show-sliders", context -> interaction.showValidationSection(SECTION_SLIDERS))
                        .custom("drag-volume-slider", context -> {
                            interaction.settleValidationLayout();
                            widgets.dragVolumeSlider();
                        })
                        .expect(UiScenarioAssertions.sliderValue(UiKitValidationIds.SLIDER_VOLUME,
                                1.0f, 0.01f)));
        builder.entry(4L, true, true,
                Scenario.named("progress-bars")
                        .custom("show-progress-bars", context -> interaction.showValidationSection(SECTION_PROGRESS_BARS))
                        .expect(UiScenarioAssertions.boundsAtLeast(UiKitValidationIds.PROGRESS_VOLUME,
                                20.0f, 4.0f))
                        .expect(UiScenarioAssertions.floatValue(UiKitValidationIds.PROGRESS_VOLUME,
                                1.0f, 0.01f))
                        .expect(UiScenarioAssertions.exists(UiKitValidationIds.PROGRESS_WIDE))
                        .custom("validate-progress-bars", context -> widgets.validateProgressBars()));
        builder.entry(5L, true, true,
                Scenario.named("tabs-switch")
                        .custom("show-tabs", context -> showValidationTabs())
                        .expect(UiScenarioAssertions.activeTab(UiKitValidationIds.TABS_DEMO, 0))
                        .action(UiScenarioActions.clickTab(UiKitValidationIds.TABS_DEMO, 1))
                        .expect(UiScenarioAssertions.activeTab(UiKitValidationIds.TABS_DEMO, 1))
                        .custom("validate-tabs-pointer", context -> widgets.validateTabs()));
        builder.entry(6L, true, true,
                Scenario.named("slider-text")
                        .custom("slider-text", context -> {
                            interaction.showValidationSection(SECTION_TEXT_FIELDS);
                            text.validateTextSelectionAndCopyPaste();
                        }));
        builder.entry(7L, true, true,
                Scenario.named("text-area-edit")
                        .custom("text-area-edit", context -> {
                            interaction.showValidationSection(SECTION_TEXT_FIELDS);
                            text.validateTextArea();
                        }));
        builder.entry(8L, true, true,
                Scenario.named("text-input-touch-drag")
                        .custom("text-input-touch-drag", context -> {
                            interaction.showValidationSection(SECTION_SCROLLVIEW);
                            text.validateTextInputTouchDrag();
                        }));
        builder.entry(8L, captureAllScenarios, true,
                Scenario.named("section-drawing")
                        .custom("section-drawing", context -> {
                            interaction.showValidationSection(SECTION_DRAWING);
                            captureDrawing.run();
                        }));
        builder.entry(9L, captureAllScenarios, true,
                Scenario.named("list-interaction")
                        .custom("list-interaction", context -> {
                            interaction.showValidationSection(SECTION_LISTS);
                            input.dispatchScrolled(root.displayX(20.0f), root.displayY(20.0f), 0.0f, 1.0f);
                            showcase.lists().inventoryList.scrollToItem(2, 0.0f);
                        }));
        builder.entry(10L, true, true,
                Scenario.named("scroll-body-drag")
                        .custom("scroll-body-drag", context -> {
                            interaction.showValidationSection(SECTION_SCROLLVIEW);
                            text.validateScrollBodyDrag();
                        }));
        builder.entry(10L, true, true,
                Scenario.named("scroll-child-drag")
                        .custom("scroll-child-drag", context -> {
                            interaction.showValidationSection(SECTION_BUTTONS);
                            text.validateScrollChildDrag();
                        }));
        builder.entry(11L, captureAllScenarios, true,
                Scenario.named("window-drag")
                        .custom("window-drag", context -> {
                            interaction.showValidationSection(SECTION_WINDOWS);
                            UiNode tools = find(root.rootNode(), UiNodeType.WINDOW, "Tools Window");
                            windows.validateWindowDrag(tools, 560.0f, 44.0f);
                        }));
        builder.entry(12L, true, true,
                Scenario.named("window-edge-tests")
                        .custom("window-edge-tests", context -> {
                            interaction.settleValidationLayout();
                            windows.dragWindowPastEdge(find(root.rootNode(), UiNodeType.WINDOW, "Tools Window"));
                            windows.moveWindowToLayoutOrigin(find(root.rootNode(), UiNodeType.WINDOW, "Tools Window"));
                            windows.validateWindowResize(find(root.rootNode(), UiNodeType.WINDOW, "Resizable Stats"),
                                    86.0f, 58.0f);
                            windows.resizeWindowPastEdge(find(root.rootNode(), UiNodeType.WINDOW, "Resizable Stats"));
                        }));
        builder.entry(14L, captureAllScenarios, true,
                Scenario.named("popup-pass-through")
                        .custom("popup-pass-through", context -> {
                            showcase.activeSection().set(SECTION_BUTTONS);
                            showcase.overlays().showPopup.set(true);
                            interaction.settleValidationLayout();
                            widgets.checkPopupPassThrough();
                        }));
        builder.entry(15L, captureAllScenarios, true,
                Scenario.named("popup-enable-block")
                        .action(UiScenarioActions.click(UiKitValidationIds.POPUP_BLOCK_INPUT))
                        .expect(UiScenarioAssertions.checked(UiKitValidationIds.POPUP_BLOCK_INPUT, true))
                        .custom("settle", context -> interaction.settleValidationLayout()));
        builder.entry(16L, captureAllScenarios, true,
                Scenario.named("popup-blocking")
                        .custom("popup-blocking", context -> {
                            interaction.settleValidationLayout();
                            widgets.checkPopupBlocking();
                        }));
        builder.entry(17L, captureAllScenarios, true,
                Scenario.named("popup-close")
                        .action(UiScenarioActions.click(UiKitValidationIds.POPUP_CLOSE_BUTTON))
                        .custom("settle", context -> interaction.settleValidationLayout()));
        builder.entry(18L, true, false,
                Scenario.named("tooltip-text-hover")
                        .custom("show-tooltips", context -> interaction.showValidationSection(SECTION_TOOLTIPS))
                        .custom("hover-visible-text", context -> widgets.hoverValidationTarget(context,
                                UiKitValidationIds.TOOLTIP_TEXT_TARGET, 0.25f, 0.5f))
                        .waitFor(UiScenarioWaits.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationIds.TOOLTIP_TEXT_TARGET)).timeoutMillis(2500L))
                        .expect(UiScenarioAssertions.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationIds.TOOLTIP_TEXT_TARGET))));
        builder.entry(19L, true, false,
                Scenario.named("tooltip-close-keeps-anchor")
                        .custom("show-tooltips", context -> interaction.showValidationSection(SECTION_TOOLTIPS))
                        .custom("hover-visible-text", context -> widgets.hoverValidationTarget(context,
                                UiKitValidationIds.TOOLTIP_TEXT_TARGET, 0.25f, 0.5f))
                        .waitFor(UiScenarioWaits.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationIds.TOOLTIP_TEXT_TARGET)).timeoutMillis(2500L))
                        .custom("hover-away", context -> widgets.hoverAwayFromTooltipTargets(context))
                        .custom("advance-fade", context -> context.host().advanceFrame(context))
                        .custom("assert-close-position", context -> widgets.assertTooltipPanelAwayFromOrigin(context,
                                UiKitValidationIds.TOOLTIP_TEXT_TARGET)));
        builder.entry(20L, true, false,
                Scenario.named("tooltip-checkbox-hover")
                        .custom("show-tooltips", context -> interaction.showValidationSection(SECTION_TOOLTIPS))
                        .custom("hover-checkbox-label", context -> widgets.hoverValidationTarget(context,
                                UiKitValidationIds.TOOLTIP_CHECKBOX_TARGET, 0.72f, 0.5f))
                        .waitFor(UiScenarioWaits.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationIds.TOOLTIP_CHECKBOX_TARGET)).timeoutMillis(2500L))
                        .expect(UiScenarioAssertions.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationIds.TOOLTIP_CHECKBOX_TARGET))));
        builder.entry(21L, true, false,
                Scenario.named("tooltip-text-field-hover")
                        .custom("show-tooltips", context -> interaction.showValidationSection(SECTION_TOOLTIPS))
                        .custom("hover-text-field-value", context -> widgets.hoverValidationTarget(context,
                                UiKitValidationIds.TOOLTIP_TEXT_FIELD_TARGET, 0.18f, 0.5f))
                        .waitFor(UiScenarioWaits.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationIds.TOOLTIP_TEXT_FIELD_TARGET)).timeoutMillis(2500L))
                        .expect(UiScenarioAssertions.visible(UiScenarioTargets.typeAndKey(UiNodeType.TOOLTIP,
                                UiKitValidationIds.TOOLTIP_TEXT_FIELD_TARGET))));
        builder.entry(22L, true, true,
                Scenario.named("text-scale-slider")
                        .custom("text-scale-slider", context -> {
                            showcase.enableGlobalScaleForValidation();
                            interaction.settleValidationLayout();
                            widgets.validateTextSizeSliderReleaseCommit();
                        })
                        .expect(UiScenarioAssertions.sliderValue(
                                UiKitValidationIds.SETTINGS_TEXT_SIZE_SLIDER, 2.02f, 0.05f)));
        builder.entry(23L, true, true,
                Scenario.named("emoji-clipboard")
                        .custom("show-text-inputs", context -> {
                            showcase.resetGlobalScaleForValidation();
                            interaction.showValidationSection(SECTION_TEXT_FIELDS);
                        })
                        .expect(UiScenarioAssertions.exists(UiKitValidationIds.EMOJI_FIELD))
                        .custom("validate-emoji-clipboard", context -> text.validateEmojiAndClipboard()));
        builder.entry(24L, true, true,
                Scenario.named("toggles-and-radio")
                        .custom("show-toggles", context -> interaction.showValidationSection(SECTION_TOGGLES))
                        .expect(UiScenarioAssertions.exists(UiKitValidationIds.SWITCH_NOTIFICATIONS))
                        .expect(UiScenarioAssertions.exists(UiKitValidationIds.RADIO_QUALITY))
                        .custom("validate-toggles", context -> widgets.validateToggleChoices()));
        builder.entry(25L, true, true,
                Scenario.named("loading-indicators")
                        .custom("show-loading", context -> interaction.showValidationSection(SECTION_LOADING))
                        .expect(UiScenarioAssertions.boundsAtLeast(
                                UiKitValidationIds.LOADING_BAR, 20.0f, 4.0f))
                        .expect(UiScenarioAssertions.boundsAtLeast(
                                UiKitValidationIds.LOADING_SPINNER, 20.0f, 20.0f))
                        .custom("validate-loading", context -> widgets.validateLoadingIndicators()));
        builder.entry(26L, true, true,
                Scenario.named("collapse-bars")
                        .custom("show-collapse", context -> interaction.showValidationSection(SECTION_COLLAPSE))
                        .expect(UiScenarioAssertions.exists(UiKitValidationIds.COLLAPSE_BASIC))
                        .custom("validate-collapse", context -> widgets.validateCollapseBars()));
        builder.entry(27L, true, true,
                Scenario.named("button-constrained-layout")
                        .custom("show-buttons", context -> interaction.showValidationSection(SECTION_BUTTONS))
                        .custom("validate-constrained-layout", context -> widgets.validateConstrainedButtonLayout())
                        .action(UiScenarioActions.press(UiKitValidationIds.CONSTRAINED_GAMMA))
                        .custom("validate-pressed-gamma", context -> widgets.validatePressedConstrainedButton())
                        .action(UiScenarioActions.release(UiKitValidationIds.CONSTRAINED_GAMMA)));
        builder.entry(28L, true, true,
                Scenario.named("theme-cobalt")
                        .custom("show-themes", context -> interaction.showValidationSection(SECTION_THEMES))
                        .action(UiScenarioActions.click(UiKitValidationIds.THEME_COBALT))
                        .custom("settle-theme", context -> interaction.settleValidationLayout())
                        .custom("validate-theme", context -> widgets.validateActiveTheme(2)));
        builder.entry(29L, true, true,
                Scenario.named("theme-graphite")
                        .custom("show-graphite", context -> prepareThemeSection(0))
                        .custom("validate-theme", context -> widgets.validateActiveTheme(0)));
        builder.entry(30L, true, true,
                Scenario.named("theme-porcelain")
                        .custom("show-porcelain", context -> prepareThemeSection(1))
                        .custom("validate-theme", context -> widgets.validateActiveTheme(1)));
        builder.entry(31L, true, true,
                Scenario.named("theme-aubergine")
                        .custom("show-aubergine", context -> prepareThemeSection(3))
                        .custom("validate-theme", context -> widgets.validateActiveTheme(3)));
        builder.entry(32L, true, true,
                Scenario.named("theme-high-contrast")
                        .custom("show-high-contrast", context -> prepareThemeSection(4))
                        .custom("validate-theme", context -> widgets.validateActiveTheme(4)));
        builder.entry(33L, true, true,
                Scenario.named("switch-off")
                        .custom("prepare-switch-on", context -> prepareToggleSwitch(true))
                        .action(UiScenarioActions.click(UiKitValidationIds.SWITCH_NOTIFICATIONS))
                        .expect(UiScenarioAssertions.checked(
                                UiKitValidationIds.SWITCH_NOTIFICATIONS, false)));
        builder.entry(34L, true, true,
                Scenario.named("switch-on")
                        .custom("prepare-switch-on", context -> prepareToggleSwitch(true))
                        .action(UiScenarioActions.click(UiKitValidationIds.SWITCH_NOTIFICATIONS))
                        .expect(UiScenarioAssertions.checked(
                                UiKitValidationIds.SWITCH_NOTIFICATIONS, false))
                        .action(UiScenarioActions.click(UiKitValidationIds.SWITCH_NOTIFICATIONS))
                        .expect(UiScenarioAssertions.checked(
                                UiKitValidationIds.SWITCH_NOTIFICATIONS, true)));
        builder.entry(35L, captureAllScenarios, true,
                Scenario.named("open-modal")
                        .custom("reset-global-scale", context -> showcase.resetGlobalScaleForValidation())
                        .custom("settle", context -> interaction.settleValidationLayout())
                        .action(UiScenarioActions.click(UiKitValidationIds.HEADER_MODAL_BUTTON))
                        .custom("settle", context -> interaction.settleValidationLayout())
                        .expect(UiScenarioAssertions.modalOpen(UiKitValidationIds.MODAL_ID))
                        .action(ScenarioActions.emit("ui.modal.opened:" + UiKitValidationIds.MODAL_ID)));
        builder.entry(36L, true, false,
                Scenario.named("disabled-focus-regression")
                        .custom("disabled-focus", context -> validateDisabledFocus()));
        builder.entry(37L, true, false,
                Scenario.named("modal-keyboard-regression")
                        .custom("modal-keyboard", context -> validateModalKeyboard()));
        builder.entry(38L, true, false,
                Scenario.named("virtual-list-regression")
                        .custom("virtual-list", context -> validateVirtualLists()));
        builder.entry(39L, true, false,
                Scenario.named("transition-midpoint")
                        .custom("transition-midpoint", context -> {
                            showcase.overlays().showModal.set(false);
                            interaction.showValidationSection(SECTION_BUTTONS);
                            root.update(1);
                            showcase.buttons().transitionPhase.set(showcase.buttons().transitionPhase.get() + 1);
                            root.update(0);
                            root.update(0.5f);
                            root.animationScale(0);
                        }));
        builder.entry(40L, true, false,
                Scenario.named("transition-complete")
                        .custom("transition-complete", context -> {
                            root.animationScale(1);
                            root.update(1);
                        }));
        return builder.build();
    }

    void verifyCoverage(long renderedFrames) {
        widgets.verifyClickAndPopupChecks();
        text.verifyInputValues();
        widgets.verifyScaleAndTabs();
        text.verifyCoverage();
        widgets.verifyWidgetChecks();
        windows.verifyCoverage(renderedFrames);
    }

    private void validateDisabledFocus() {
        showcase.overlays().showModal.set(false);
        showcase.overlays().showPopup.set(false);
        showcase.buttons().dynamicEnabled.set(true);
        interaction.showValidationSection(SECTION_BUTTONS);
        UiNode dynamic = find(root.rootNode(), UiNodeType.BUTTON, "Dynamic");
        interaction.click(dynamic);
        int before = showcase.clickCount();
        showcase.buttons().dynamicEnabled.set(false);
        interaction.settleValidationLayout();
        interaction.pressKey(Key.ENTER);
        interaction.pressKey(Key.SPACE);
        if (dynamic.focused() || showcase.clickCount() != before) {
            throw new FdxException("Disabled focused button still accepts keyboard input");
        }
        logger.info("UiKitTest verified disabled focus regression");
    }

    private void validateModalKeyboard() {
        showcase.overlays().showModal.set(false);
        showcase.buttons().dynamicEnabled.set(true);
        interaction.showValidationSection(SECTION_BUTTONS);
        UiNode background = find(root.rootNode(), UiNodeType.BUTTON, "Dynamic");
        interaction.click(background);
        showcase.overlays().showModal.set(true);
        interaction.settleValidationLayout();
        UiNode close = find(root.rootNode(), UiNodeType.BUTTON, "Close");
        if (background.focused() || close == null || !close.focused()) {
            throw new FdxException("Modal did not take keyboard focus");
        }
        interaction.pressKey(Key.TAB);
        UiNode keep = find(root.rootNode(), UiNodeType.BUTTON, "Keep");
        if (keep == null || !keep.focused()) throw new FdxException("Modal Tab escaped its keyboard scope");
        interaction.pressKey(Key.TAB);
        if (!close.focused()) throw new FdxException("Modal Tab did not wrap inside its scope");
        showcase.overlays().showModal.set(false);
        interaction.settleValidationLayout();
        if (!background.focused()) throw new FdxException("Modal did not restore background focus");
        logger.info("UiKitTest verified modal keyboard containment and restoration");
    }

    private void validateVirtualLists() {
        showcase.overlays().showModal.set(false);
        interaction.showValidationSection(SECTION_LISTS);
        showcase.lists().inventoryList.scrollToItem(0, 0);
        interaction.settleValidationLayout();
        if (showcase.lists().virtualList(0) == showcase.lists().virtualList(1) || showcase.lists().virtualList(0).listState() == showcase.lists().virtualList(1).listState()) {
            throw new FdxException("Sibling virtual lists share retained identity/state");
        }
        UiRect bounds = showcase.lists().virtualList(0).bounds();
        input.dispatchScrolled(root.displayX(bounds.x() + bounds.width() * 0.5f),
                root.displayY(bounds.y() + bounds.height() * 0.5f), 0, 1);
        interaction.settleValidationLayout();
        if (showcase.lists().inventoryList.firstVisibleIndex() == 0 && showcase.lists().inventoryList.firstVisibleOffset() == 0) {
            throw new FdxException("Virtual list wheel did not scroll");
        }
        showcase.lists().inventoryList.scrollToItem(1, 3);
        interaction.settleValidationLayout();
        if (!inventoryItem(1).equals(showcase.lists().virtualList(0).children().get(0).value())
                || Math.abs(showcase.lists().virtualList(0).children().get(0).bounds().y() - showcase.lists().virtualList(0).bounds().y() + 3) > 0.1f
                || showcase.lists().virtualList(1).listState().firstVisibleIndex() != 0) {
            throw new FdxException("Virtual list did not preserve its requested index/offset or independent state");
        }
        logger.info("UiKitTest verified virtual list wheel, index, offset and sibling identity");
    }

    private void prepareToggleSwitch(boolean checked) {
        showcase.selectTheme(0);
        interaction.showValidationSection(SECTION_TOGGLES);
        showcase.toggles().switchEnabled.set(checked);
        interaction.settleValidationLayout();
    }

    private void prepareThemeSection(int index) {
        interaction.showValidationSection(SECTION_THEMES);
        showcase.selectTheme(index);
        interaction.settleValidationLayout();
    }

    private void showValidationTabs() {
        showcase.activeSection().set(SECTION_TABS);
        showcase.tabs().activeDemoTab.set(0);
        interaction.settleValidationLayout();
    }

}
