package io.github.libfdx.ui;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.graphics.g2d.TextureRegion;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents an ui scope.
 *
 * @author xpenatan
 */
public final class UiScope {
    private static final UiModifier DEFAULT_INTERACTIVE_MODIFIER = UiModifier.none().focusable(true);
    private static final UiModifier DEFAULT_ITEM_MODIFIER = UiModifier.none().animateItemPlacement();
    private static final UiModifier DEFAULT_VIRTUAL_LIST_MODIFIER = UiModifier.none().fillWidth();
    private static final UiAnimationSpec TOOLTIP_ANIMATION = Ui.animation().durationMillis(160).fade();
    private static final int NODE_KEY_TEXT_AREA_SCROLL = 2;
    private final UiRoot root;
    private UiNode parent;
    private String path;
    private int childIndex;
    private String[] identityPaths = new String[8];
    private UiNodeType[] identityTypes = new UiNodeType[8];
    private String[] identityKeys = new String[8];
    private int[] identityChildIndices = new int[8];
    private String[] identities = new String[8];
    private int identityCount;
    private String[] scopedKeyBases = new String[8];
    private String[] scopedKeySeparators = new String[8];
    private String[] scopedKeyNames = new String[8];
    private String[] scopedKeys = new String[8];
    private int scopedKeyCount;
    private Object[] itemKeySources = new Object[8];
    private int[] itemKeyFallbackIndices = new int[8];
    private boolean[] itemKeyExplicit = new boolean[8];
    private String[] itemKeys = new String[8];
    private int itemKeyCount;
    private UiModifier[] animatedModifiers = new UiModifier[8];
    private UiModifier[] interactiveModifierSources = new UiModifier[8];
    private UiModifier[] interactiveModifiers = new UiModifier[8];
    private int interactiveModifierCount;
    private UiModifier[] contentSizeModifierSources = new UiModifier[8];
    private UiAnimationSpec[] contentSizeModifierAnimations = new UiAnimationSpec[8];
    private UiModifier[] contentSizeModifiers = new UiModifier[8];
    private int contentSizeModifierCount;

    UiScope(UiRoot root) {
        this.root = root;
    }

    UiScope reset(UiNode parent, String path) {
        this.parent = parent;
        this.path = path;
        childIndex = 0;
        identityCount = 0;
        scopedKeyCount = 0;
        itemKeyCount = 0;
        interactiveModifierCount = 0;
        contentSizeModifierCount = 0;
        return this;
    }

    void clear() {
        parent = null;
        path = null;
        childIndex = 0;
        identityCount = 0;
        scopedKeyCount = 0;
        itemKeyCount = 0;
        interactiveModifierCount = 0;
        contentSizeModifierCount = 0;
    }

    /**
     * Runs the column step.
     *
     * @param content the content
     * @return the column
     */
    public UiNode column(UiContent content) {
        return column(UiModifier.none(), content);
    }

    /**
     * Runs the column step.
     *
     * @param modifier the modifier
     * @param content the content
     * @return the column
     */
    public UiNode column(UiModifier modifier, UiContent content) {
        return container(UiNodeType.COLUMN, null, modifier, content);
    }

    /**
     * Runs the row step.
     *
     * @param content the content
     * @return the row
     */
    public UiNode row(UiContent content) {
        return row(UiModifier.none(), content);
    }

    /**
     * Runs the row step.
     *
     * @param modifier the modifier
     * @param content the content
     * @return the row
     */
    public UiNode row(UiModifier modifier, UiContent content) {
        return container(UiNodeType.ROW, null, modifier, content);
    }

    /**
     * Runs the stack step.
     *
     * @param content the content
     * @return the stack
     */
    public UiNode stack(UiContent content) {
        return stack(UiModifier.none(), content);
    }

    /**
     * Runs the stack step.
     *
     * @param modifier the modifier
     * @param content the content
     * @return the stack
     */
    public UiNode stack(UiModifier modifier, UiContent content) {
        return container(UiNodeType.STACK, null, modifier, content);
    }

    /**
     * Runs the grid step.
     *
     * @param columns the columns
     * @param content the content
     * @return the grid
     */
    public UiNode grid(int columns, UiContent content) {
        return grid(columns, UiModifier.none(), content);
    }

    /**
     * Runs the grid step.
     *
     * @param columns the columns
     * @param modifier the modifier
     * @param content the content
     * @return the grid
     */
    public UiNode grid(int columns, UiModifier modifier, UiContent content) {
        UiNode node = addNode(UiNodeType.GRID, null, modifier);
        node.intValue(Math.max(1, columns));
        build(node, content);
        return node;
    }

    /**
     * Runs the panel step.
     *
     * @param content the content
     * @return the panel
     */
    public UiNode panel(UiContent content) {
        return panel(UiModifier.none(), content);
    }

    /**
     * Runs the panel step.
     *
     * @param modifier the modifier
     * @param content the content
     * @return the panel
     */
    public UiNode panel(UiModifier modifier, UiContent content) {
        return container(UiNodeType.PANEL, null, modifier, content);
    }

    /**
     * Runs the scroll step.
     *
     * @param content the content
     * @return the scroll
     */
    public UiNode scroll(UiContent content) {
        return scroll(UiModifier.none(), null, content);
    }

    /**
     * Runs the scroll step.
     *
     * @param modifier the modifier
     * @param content the content
     * @return the scroll
     */
    public UiNode scroll(UiModifier modifier, UiContent content) {
        return scroll(modifier, null, content);
    }

    /**
     * Runs the scroll step.
     *
     * @param modifier the modifier
     * @param state the state
     * @param content the content
     * @return the scroll
     */
    public UiNode scroll(UiModifier modifier, UiScrollState state, UiContent content) {
        UiNode node = addNode(UiNodeType.SCROLL, null, modifier);
        node.scrollState(state != null ? state : root.scrollState(node.identity()));
        build(node, content);
        return node;
    }

    /**
     * Runs the scroll view step.
     *
     * @param content the content
     * @return the scroll view
     */
    public UiNode scrollView(UiContent content) {
        return scroll(content);
    }

    /**
     * Runs the scroll view step.
     *
     * @param modifier the modifier
     * @param content the content
     * @return the scroll view
     */
    public UiNode scrollView(UiModifier modifier, UiContent content) {
        return scroll(modifier, content);
    }

    /**
     * Runs the scroll view step.
     *
     * @param modifier the modifier
     * @param state the state
     * @param content the content
     * @return the scroll view
     */
    public UiNode scrollView(UiModifier modifier, UiScrollState state, UiContent content) {
        return scroll(modifier, state, content);
    }

    /**
     * Runs the text step.
     *
     * @param text the text
     * @return the text
     */
    public UiNode text(String text) {
        return text(text, UiModifier.none());
    }

    /**
     * Runs the text step.
     *
     * @param text the text
     * @param modifier the modifier
     * @return the text
     */
    public UiNode text(String text, UiModifier modifier) {
        UiNode node = addNode(UiNodeType.TEXT, null, modifier);
        node.text(text != null ? text : "");
        return node;
    }

    /**
     * Runs the button step.
     *
     * @param text the text
     * @param action the action
     * @return the button
     */
    public UiNode button(String text, Runnable action) {
        return button(text, UiModifier.none(), action);
    }

    /**
     * Runs the button step.
     *
     * @param text the text
     * @param modifier the modifier
     * @param action the action
     * @return the button
     */
    public UiNode button(String text, UiModifier modifier, Runnable action) {
        UiNode node = addNode(UiNodeType.BUTTON, null, interactive(modifier));
        node.text(text);
        node.action(action);
        return node;
    }

    /**
     * Runs the checkbox step.
     *
     * @param text the text
     * @param state the state
     * @return the checkbox
     */
    public UiNode checkbox(String text, UiBooleanState state) {
        return checkbox(text, UiModifier.none(), state);
    }

    /**
     * Runs the checkbox step.
     *
     * @param state the state
     * @return the checkbox
     */
    public UiNode checkbox(UiBooleanState state) {
        return checkbox(UiModifier.none(), state);
    }

    /**
     * Runs the checkbox step.
     *
     * @param modifier the modifier
     * @param state the state
     * @return the checkbox
     */
    public UiNode checkbox(UiModifier modifier, UiBooleanState state) {
        return checkbox("", modifier, state);
    }

    /**
     * Runs the checkbox step.
     *
     * @param text the text
     * @param modifier the modifier
     * @param state the state
     * @return the checkbox
     */
    public UiNode checkbox(String text, UiModifier modifier, final UiBooleanState state) {
        UiNode node = addNode(UiNodeType.CHECKBOX, null, interactive(modifier));
        boolean hasLabel = text != null && text.length() > 0;
        node.text(hasLabel ? text : " ");
        node.checkboxLabel(hasLabel);
        boolean checked = state != null && state.get();
        node.checked(checked);
        node.descriptor(state);
        return node;
    }

    /**
     * Creates a toggle switch.
     *
     * @param state the checked state
     * @return the switch node
     */
    public UiNode toggleSwitch(UiBooleanState state) {
        return toggleSwitch("", UiModifier.none(), state);
    }

    /**
     * Creates a labeled toggle switch.
     *
     * @param text the label
     * @param state the checked state
     * @return the switch node
     */
    public UiNode toggleSwitch(String text, UiBooleanState state) {
        return toggleSwitch(text, UiModifier.none(), state);
    }

    /**
     * Creates a labeled toggle switch.
     *
     * @param text the label
     * @param modifier the modifier
     * @param state the checked state
     * @return the switch node
     */
    public UiNode toggleSwitch(String text, UiModifier modifier, UiBooleanState state) {
        UiNode node = addNode(UiNodeType.SWITCH, null, interactive(modifier));
        boolean hasLabel = text != null && text.length() > 0;
        node.text(hasLabel ? text : " ");
        node.checkboxLabel(hasLabel);
        node.checked(state != null && state.get());
        node.descriptor(state);
        return node;
    }

    /**
     * Creates one radio-button choice.
     *
     * @param text the label
     * @param selectedValue the shared selected value
     * @param value the value represented by this radio button
     * @return the radio-button node
     */
    public UiNode radioButton(String text, UiIntState selectedValue, int value) {
        return radioButton(text, UiModifier.none(), selectedValue, value);
    }

    /**
     * Creates one radio-button choice.
     *
     * @param text the label
     * @param modifier the modifier
     * @param selectedValue the shared selected value
     * @param value the value represented by this radio button
     * @return the radio-button node
     */
    public UiNode radioButton(String text, UiModifier modifier, UiIntState selectedValue, int value) {
        UiNode node = addNode(UiNodeType.RADIO_BUTTON, null, interactive(modifier));
        boolean hasLabel = text != null && text.length() > 0;
        node.text(hasLabel ? text : " ");
        node.checkboxLabel(hasLabel);
        UiRadioModel model = node.descriptor() instanceof UiRadioModel
                ? (UiRadioModel) node.descriptor()
                : new UiRadioModel(selectedValue, value);
        model.update(selectedValue, value);
        node.checked(model.selected());
        node.intValue(value);
        node.descriptor(model);
        return node;
    }

    /**
     * Runs the slider step.
     *
     * @param state the state
     * @param minimum the minimum
     * @param maximum the maximum
     * @return the slider
     */
    public UiNode slider(UiFloatState state, float minimum, float maximum) {
        return slider(UiModifier.none(), state, minimum, maximum);
    }

    /**
     * Runs the slider step.
     *
     * @param modifier the modifier
     * @param state the state
     * @param minimum the minimum
     * @param maximum the maximum
     * @return the slider
     */
    public UiNode slider(UiModifier modifier, UiFloatState state, float minimum, float maximum) {
        UiNode node = addNode(UiNodeType.SLIDER, null, interactive(modifier));
        UiSliderModel model = node.descriptor() instanceof UiSliderModel
                ? (UiSliderModel) node.descriptor()
                : null;
        if (model == null || !model.matches(state, minimum, maximum)) {
            model = new UiSliderModel(new UiRange(minimum, maximum), state);
        }
        UiRange range = model.range();
        float value = state != null ? state.get() : minimum;
        node.floatValue(range.clamp(value));
        node.descriptor(model);
        return node;
    }

    /**
     * Runs the progress bar step.
     *
     * @param value the value
     * @return the progress bar
     */
    public UiNode progressBar(float value) {
        return progressBar(UiModifier.none(), value, 0.0f, 1.0f);
    }

    /**
     * Runs the progress bar step.
     *
     * @param modifier the modifier
     * @param value the value
     * @return the progress bar
     */
    public UiNode progressBar(UiModifier modifier, float value) {
        return progressBar(modifier, value, 0.0f, 1.0f);
    }

    /**
     * Runs the progress bar step.
     *
     * @param state the state
     * @param minimum the minimum
     * @param maximum the maximum
     * @return the progress bar
     */
    public UiNode progressBar(UiFloatState state, float minimum, float maximum) {
        return progressBar(UiModifier.none(), state, minimum, maximum);
    }

    /**
     * Runs the progress bar step.
     *
     * @param modifier the modifier
     * @param state the state
     * @param minimum the minimum
     * @param maximum the maximum
     * @return the progress bar
     */
    public UiNode progressBar(UiModifier modifier, UiFloatState state, float minimum, float maximum) {
        float value = state != null ? state.get() : minimum;
        return progressBar(modifier, value, minimum, maximum, state);
    }

    /**
     * Runs the progress bar step.
     *
     * @param modifier the modifier
     * @param value the value
     * @param minimum the minimum
     * @param maximum the maximum
     * @return the progress bar
     */
    public UiNode progressBar(UiModifier modifier, float value, float minimum, float maximum) {
        return progressBar(modifier, value, minimum, maximum, null);
    }

    private UiNode progressBar(UiModifier modifier, float value, float minimum, float maximum, UiFloatState state) {
        UiNode node = addNode(UiNodeType.PROGRESS_BAR, null, modifier);
        UiProgressBarModel model = node.descriptor() instanceof UiProgressBarModel
                ? (UiProgressBarModel) node.descriptor()
                : null;
        if (model == null || !model.matches(state, minimum, maximum)) {
            model = new UiProgressBarModel(new UiRange(minimum, maximum), state);
        }
        UiRange range = model.range();
        node.floatValue(range.clamp(value));
        node.descriptor(model);
        return node;
    }

    /**
     * Creates an indeterminate linear loading indicator.
     *
     * @return the loading-bar node
     */
    public UiNode loadingBar() {
        return loadingBar(UiModifier.none());
    }

    /**
     * Creates an indeterminate linear loading indicator.
     *
     * @param modifier the modifier
     * @return the loading-bar node
     */
    public UiNode loadingBar(UiModifier modifier) {
        return addNode(UiNodeType.LOADING_BAR, null, modifier);
    }

    /**
     * Creates an indeterminate circular loading indicator.
     *
     * @return the spinner node
     */
    public UiNode loadingSpinner() {
        return loadingSpinner(UiModifier.none());
    }

    /**
     * Creates an indeterminate circular loading indicator.
     *
     * @param modifier the modifier
     * @return the spinner node
     */
    public UiNode loadingSpinner(UiModifier modifier) {
        return addNode(UiNodeType.LOADING_SPINNER, null, modifier);
    }

    /**
     * Creates a visual divider.
     *
     * @return the divider node
     */
    public UiNode divider() {
        return divider(UiModifier.none());
    }

    /**
     * Creates a visual divider.
     *
     * @param modifier the modifier
     * @return the divider node
     */
    public UiNode divider(UiModifier modifier) {
        return addNode(UiNodeType.DIVIDER, null, modifier);
    }

    /**
     * Creates an expandable disclosure bar and composes its content while expanded.
     *
     * @param title the header title
     * @param expanded the expansion state
     * @param content the expanded content
     * @return the collapse-bar node
     */
    public UiNode collapseBar(String title, UiBooleanState expanded, UiContent content) {
        return collapseBar(title, UiModifier.none(), expanded, content);
    }

    /**
     * Creates an expandable disclosure bar and composes its content while expanded.
     *
     * @param title the header title
     * @param modifier the modifier
     * @param expanded the expansion state
     * @param content the expanded content
     * @return the collapse-bar node
     */
    public UiNode collapseBar(String title, UiModifier modifier, UiBooleanState expanded, UiContent content) {
        UiNode node = addNode(UiNodeType.COLLAPSE_BAR, null, interactive(modifier));
        node.text(title != null ? title : "");
        node.checked(expanded != null && expanded.get());
        node.descriptor(expanded);
        if (node.checked()) {
            build(node, content);
        }
        return node;
    }

    /**
     * Runs the tabs step.
     *
     * @param activeIndex the active index
     * @param labels the labels
     * @return the tabs
     */
    public UiNode tabs(UiIntState activeIndex, String... labels) {
        return tabs(UiModifier.none(), activeIndex, labels);
    }

    /**
     * Runs the tabs step.
     *
     * @param modifier the modifier
     * @param activeIndex the active index
     * @param labels the labels
     * @return the tabs
     */
    public UiNode tabs(UiModifier modifier, UiIntState activeIndex, String... labels) {
        UiNode node = addNode(UiNodeType.TABS, null, interactive(modifier));
        UiTabsModel model = node.descriptor() instanceof UiTabsModel
                ? (UiTabsModel) node.descriptor()
                : new UiTabsModel(activeIndex, labels);
        model.update(activeIndex, labels);
        int active = model.clamp(activeIndex != null ? activeIndex.get() : node.intValue());
        if (active >= 0 && activeIndex != null && activeIndex.get() != active) {
            activeIndex.set(active);
        }
        node.intValue(active);
        node.descriptor(model);
        return node;
    }

    /**
     * Runs the text field step.
     *
     * @param state the state
     * @return the text field
     */
    public UiNode textField(UiState<String> state) {
        return textField(UiModifier.none(), state);
    }

    /**
     * Runs the text field step.
     *
     * @param modifier the modifier
     * @param state the state
     * @return the text field
     */
    public UiNode textField(UiModifier modifier, UiState<String> state) {
        return textField(modifier, state, UiTextInputFilter.STRING);
    }

    /**
     * Runs the text field step.
     *
     * @param state the state
     * @param inputFilter the input filter
     * @return the text field
     */
    public UiNode textField(UiState<String> state, UiTextInputFilter inputFilter) {
        return textField(UiModifier.none(), state, inputFilter);
    }

    /**
     * Runs the text field step.
     *
     * @param modifier the modifier
     * @param state the state
     * @param inputFilter the input filter
     * @return the text field
     */
    public UiNode textField(UiModifier modifier, UiState<String> state, UiTextInputFilter inputFilter) {
        return textField(modifier, state, inputFilter, null);
    }

    /**
     * Runs the text field step with an action invoked when Enter is pressed.
     *
     * @param modifier the modifier
     * @param state the state
     * @param inputFilter the input filter
     * @param submitAction the Enter-key submit action
     * @return the text field
     */
    public UiNode textField(UiModifier modifier, UiState<String> state, UiTextInputFilter inputFilter,
            Runnable submitAction) {
        UiNode node = addNode(UiNodeType.TEXT_FIELD, null, interactive(modifier));
        node.value(state != null ? state.get() : "");
        UiTextFieldModel model = node.descriptor() instanceof UiTextFieldModel
                ? (UiTextFieldModel) node.descriptor()
                : new UiTextFieldModel(state);
        model.state(state);
        model.multiline(false);
        model.readOnly(false);
        model.inputFilter(inputFilter);
        model.submitAction(submitAction);
        node.descriptor(model);
        return node;
    }

    /**
     * Runs the text area step.
     *
     * @param state the state
     * @return the text area
     */
    public UiNode textArea(UiState<String> state) {
        return textArea(UiModifier.none(), state, UiTextAreaOptions.defaults());
    }

    /**
     * Runs the text area step.
     *
     * @param modifier the modifier
     * @param state the state
     * @return the text area
     */
    public UiNode textArea(UiModifier modifier, UiState<String> state) {
        return textArea(modifier, state, UiTextAreaOptions.defaults());
    }

    /**
     * Runs the text area step.
     *
     * @param state the state
     * @param options the options
     * @return the text area
     */
    public UiNode textArea(UiState<String> state, UiTextAreaOptions options) {
        return textArea(UiModifier.none(), state, options);
    }

    /**
     * Runs the text area step.
     *
     * @param modifier the modifier
     * @param state the state
     * @param options the options
     * @return the text area
     */
    public UiNode textArea(UiModifier modifier, UiState<String> state, UiTextAreaOptions options) {
        UiNode node = addNode(UiNodeType.TEXT_AREA, null, interactive(modifier));
        node.value(state != null ? state.get() : "");
        UiTextFieldModel model = node.descriptor() instanceof UiTextFieldModel
                ? (UiTextFieldModel) node.descriptor()
                : new UiTextFieldModel(state);
        model.state(state);
        model.multiline(true);
        model.readOnly(options != null && options.readOnly());
        model.inputFilter(UiTextInputFilter.STRING);
        model.textAreaOptions(options);
        UiScrollState scrollState = root.scrollState(node.derivedKey(NODE_KEY_TEXT_AREA_SCROLL, ":text-area"));
        model.scrollState(scrollState);
        node.scrollState(scrollState);
        node.descriptor(model);
        return node;
    }

    /**
     * Runs the int field step.
     *
     * @param state the state
     * @return the int field
     */
    public UiNode intField(UiState<String> state) {
        return textField(UiModifier.none(), state, UiTextInputFilter.INTEGER);
    }

    /**
     * Runs the int field step.
     *
     * @param modifier the modifier
     * @param state the state
     * @return the int field
     */
    public UiNode intField(UiModifier modifier, UiState<String> state) {
        return textField(modifier, state, UiTextInputFilter.INTEGER);
    }

    /**
     * Runs the float field step.
     *
     * @param state the state
     * @return the float field
     */
    public UiNode floatField(UiState<String> state) {
        return textField(UiModifier.none(), state, UiTextInputFilter.FLOAT);
    }

    /**
     * Runs the float field step.
     *
     * @param modifier the modifier
     * @param state the state
     * @return the float field
     */
    public UiNode floatField(UiModifier modifier, UiState<String> state) {
        return textField(modifier, state, UiTextInputFilter.FLOAT);
    }

    /**
     * Runs the image step.
     *
     * @param region the region
     * @return the image
     */
    public UiNode image(TextureRegion region) {
        return image(region, UiModifier.none());
    }

    /**
     * Runs the image step.
     *
     * @param region the region
     * @param modifier the modifier
     * @return the image
     */
    public UiNode image(TextureRegion region, UiModifier modifier) {
        UiNode node = addNode(UiNodeType.IMAGE, null, modifier);
        node.image(region);
        return node;
    }

    /**
     * Returns the spacer.
     *
     * @return the spacer
     */
    public UiNode spacer() {
        return spacer(UiModifier.none());
    }

    /**
     * Runs the spacer step.
     *
     * @param modifier the modifier
     * @return the spacer
     */
    public UiNode spacer(UiModifier modifier) {
        return addNode(UiNodeType.SPACER, null, modifier);
    }

    /**
     * Runs the animated visibility step.
     *
     * @param visible the visible
     * @param animation the animation
     * @param content the content
     * @return the animated visibility
     */
    public UiNode animatedVisibility(boolean visible, UiAnimationSpec animation, UiContent content) {
        UiAnimationSpec spec = animation != null ? animation : UiAnimationSpec.defaultSpec();
        String identity = nextIdentity(UiNodeType.ANIMATED_VISIBILITY, null);
        UiFloatAnimatable progress = root.floatAnimatable(
                scopedKey(identity, ":visibility", ""), visible ? 1.0f : 0.0f);
        progress.animateTo(visible ? 1.0f : 0.0f, spec);
        float value = progress.get();
        float remaining = 1.0f - value;
        UiModifier modifier = animatedModifier(spec.isFade() ? value : 1.0f,
                spec.slideX() * remaining, spec.slideY() * remaining);
        UiNode node = addNode(identity, UiNodeType.ANIMATED_VISIBILITY, null, modifier);
        node.visible(visible || progress.isRunning() || value > 0.001f);
        node.animationSpec(spec);
        if (node.visible()) {
            build(node, content);
        }
        return node;
    }

    /**
     * Runs the animate content size step.
     *
     * @param content the content
     * @return the animate content size
     */
    public UiNode animateContentSize(UiContent content) {
        return animateContentSize(UiModifier.none(), UiAnimationSpec.defaultSpec(), content);
    }

    /**
     * Runs the animate content size step.
     *
     * @param animation the animation
     * @param content the content
     * @return the animate content size
     */
    public UiNode animateContentSize(UiAnimationSpec animation, UiContent content) {
        return animateContentSize(UiModifier.none(), animation, content);
    }

    /**
     * Runs the animate content size step.
     *
     * @param modifier the modifier
     * @param animation the animation
     * @param content the content
     * @return the animate content size
     */
    public UiNode animateContentSize(UiModifier modifier, UiAnimationSpec animation, UiContent content) {
        UiModifier value = modifier != null ? modifier : UiModifier.none();
        UiAnimationSpec spec = animation != null ? animation : UiAnimationSpec.defaultSpec();
        return container(UiNodeType.COLUMN, null, contentSizeModifier(value, spec), content);
    }

    /**
     * Runs the window step.
     *
     * @param title the title
     * @param state the state
     * @param content the content
     * @return the window
     */
    public UiNode window(String title, UiWindowState state, UiContent content) {
        return window(title, UiModifier.none(), state, content);
    }

    /**
     * Runs the window step.
     *
     * @param title the title
     * @param modifier the modifier
     * @param state the state
     * @param content the content
     * @return the window
     */
    public UiNode window(String title, UiModifier modifier, UiWindowState state, UiContent content) {
        UiNode node = addNode(UiNodeType.WINDOW, title, interactive(modifier));
        node.text(title);
        UiWindowModel model = node.descriptor() instanceof UiWindowModel
                ? (UiWindowModel) node.descriptor()
                : null;
        if (model == null || !model.matches(state)) {
            model = new UiWindowModel(state);
        }
        node.descriptor(model);
        build(node, content);
        return node;
    }

    /**
     * Runs the modal step.
     *
     * @param modal the modal
     * @param content the content
     * @return the modal
     */
    public UiNode modal(UiModal modal, UiContent content) {
        UiNode node = addNode(UiNodeType.MODAL, modal != null ? modal.id() : null, UiModifier.none());
        node.descriptor(modal);
        build(node, content);
        return node;
    }

    /**
     * Runs the popup step.
     *
     * @param popup the popup
     * @param content the content
     * @return the popup
     */
    public UiNode popup(UiPopup popup, UiContent content) {
        UiNode node = addNode(UiNodeType.POPUP, popup != null ? popup.id() : null, UiModifier.none());
        node.descriptor(popup);
        build(node, content);
        return node;
    }

    /**
     * Runs the tooltip step.
     *
     * @param tooltip the tooltip
     * @param content the content
     * @return the tooltip
     */
    public UiNode tooltip(UiTooltip tooltip, UiContent content) {
        String identity = nextIdentity(UiNodeType.TOOLTIP, tooltip != null ? tooltip.text() : null);
        boolean active = root.tooltipActive(tooltip);
        UiFloatAnimatable alpha = root.floatAnimatable(scopedKey(identity, ":alpha", ""), active ? 1.0f : 0.0f);
        alpha.animateTo(active ? 1.0f : 0.0f, TOOLTIP_ANIMATION);
        UiNode node = addNode(identity, UiNodeType.TOOLTIP, tooltip != null ? tooltip.text() : null,
                animatedModifier(alpha.get(), 0.0f, 0.0f));
        node.descriptor(tooltip);
        node.visible(active || alpha.isRunning() || alpha.get() > 0.001f);
        if (node.visible()) {
            build(node, content);
        }
        return node;
    }

    /**
     * Runs the items step.
     *
     * @param <T> the value type
     * @param items the items
     * @param key the key
     * @param content the content
     */
    public <T> void items(Iterable<T> items, UiKey<T> key, UiItemContent<T> content) {
        if (items == null || content == null) {
            return;
        }
        ArrayView<T> values = root.materialize(items);
        for (int index = 0; index < values.size(); index++) {
            T item = values.get(index);
            String itemKey = itemKey(key != null ? key.key(item) : null, index, key != null);
            UiNode node = addNode(UiNodeType.ITEM, itemKey, DEFAULT_ITEM_MODIFIER);
            node.value(item);
            content.build(root.obtainScope(node, node.identity()), item);
        }
    }

    /**
     * Runs the virtual list step.
     *
     * @param <T> the value type
     * @param items the items
     * @param state the state
     * @param visibleCount the visible count
     * @param key the key
     * @param content the content
     * @return the virtual list
     */
    public <T> UiNode virtualList(Iterable<T> items, UiListState state, int visibleCount, UiKey<T> key,
            UiItemContent<T> content) {
        UiListState listState = state != null ? state : listState(null);
        UiNode node = addNode(UiNodeType.SCROLL, "virtual-list", DEFAULT_VIRTUAL_LIST_MODIFIER);
        node.listState(listState);
        if (items == null || content == null) {
            return node;
        }
        ArrayView<T> values = root.materialize(items);
        int first = Math.max(0, Math.min(listState.firstVisibleIndex(), values.size()));
        int count = Math.max(0, visibleCount);
        int last = Math.min(values.size(), first + count);
        UiScope childScope = root.obtainScope(node, node.identity());
        for (int index = first; index < last; index++) {
            T item = values.get(index);
            String itemKey = childScope.itemKey(key != null ? key.key(item) : null, index, key != null);
            UiNode itemNode = childScope.addNode(UiNodeType.ITEM, itemKey,
                    DEFAULT_ITEM_MODIFIER);
            itemNode.value(item);
            content.build(root.obtainScope(itemNode, itemNode.identity()), item);
        }
        return node;
    }

    /**
     * Runs the custom step.
     *
     * @param type the expected Java type
     * @param modifier the modifier
     * @param content the content
     * @return the custom
     */
    public UiNode custom(String type, UiModifier modifier, UiCustomContent content) {
        UiNode node = addNode(UiNodeType.CUSTOM, type, modifier);
        if (content != null) {
            UiCustomContext context = node.customContext();
            if (context == null) {
                context = new UiCustomContext();
            } else {
                context.reset();
            }
            content.build(context);
            node.customContext(context);
        } else {
            node.customContext(null);
        }
        return node;
    }

    /**
     * Runs the animatable step.
     *
     * @param <T> the value type
     * @param key the key
     * @param value the value
     * @return the animatable
     */
    public <T> UiAnimatable<T> animatable(String key, T value) {
        return root.animatable(scopedKey(path, ":animatable:", String.valueOf(key)), value);
    }

    /**
     * Runs the float animatable step.
     *
     * @param key the key
     * @param value the value
     * @return the float animatable
     */
    public UiFloatAnimatable floatAnimatable(String key, float value) {
        return root.floatAnimatable(scopedKey(path, ":float-animatable:", String.valueOf(key)), value);
    }

    /**
     * Runs the scroll state step.
     *
     * @param key the key
     * @return the scroll state
     */
    public UiScrollState scrollState(String key) {
        return root.scrollState(scopedKey(path, ":scroll:", String.valueOf(key)));
    }

    /**
     * Runs the list state step.
     *
     * @param key the key
     * @return the list state
     */
    public UiListState listState(String key) {
        return root.listState(scopedKey(path, ":list:", String.valueOf(key)));
    }

    private UiNode container(UiNodeType type, String key, UiModifier modifier, UiContent content) {
        UiNode node = addNode(type, key, modifier);
        build(node, content);
        return node;
    }

    private UiNode addNode(UiNodeType type, String key, UiModifier modifier) {
        String identity = nextIdentity(type, key);
        return addNode(identity, type, key, modifier);
    }

    private UiNode addNode(String identity, UiNodeType type, String key, UiModifier modifier) {
        UiNode node = root.retainNode(identity, type, key, modifier);
        parent.addChild(node);
        return node;
    }

    private void build(UiNode node, UiContent content) {
        if (content != null) {
            content.build(root.obtainScope(node, node.identity()));
        }
    }

    private String nextIdentity(UiNodeType type, String key) {
        int index = identityCount++;
        if (index >= identities.length) {
            int capacity = identities.length * 2;
            identityPaths = Arrays.copyOf(identityPaths, capacity);
            identityTypes = Arrays.copyOf(identityTypes, capacity);
            identityKeys = Arrays.copyOf(identityKeys, capacity);
            identityChildIndices = Arrays.copyOf(identityChildIndices, capacity);
            identities = Arrays.copyOf(identities, capacity);
            animatedModifiers = Arrays.copyOf(animatedModifiers, capacity);
        }
        int unkeyedIndex = key == null ? childIndex++ : -1;
        String identity = identities[index];
        if (identity != null
                && Objects.equals(identityPaths[index], path)
                && identityTypes[index] == type
                && Objects.equals(identityKeys[index], key)
                && identityChildIndices[index] == unkeyedIndex) {
            return identity;
        }
        identity = key != null
                ? path + "/" + type.name() + ":key:" + key
                : path + "/" + unkeyedIndex + ":" + type.name();
        identityPaths[index] = path;
        identityTypes[index] = type;
        identityKeys[index] = key;
        identityChildIndices[index] = unkeyedIndex;
        identities[index] = identity;
        return identity;
    }

    private String scopedKey(String base, String separator, String name) {
        int index = scopedKeyCount++;
        if (index >= scopedKeys.length) {
            int capacity = scopedKeys.length * 2;
            scopedKeyBases = Arrays.copyOf(scopedKeyBases, capacity);
            scopedKeySeparators = Arrays.copyOf(scopedKeySeparators, capacity);
            scopedKeyNames = Arrays.copyOf(scopedKeyNames, capacity);
            scopedKeys = Arrays.copyOf(scopedKeys, capacity);
        }
        String cached = scopedKeys[index];
        if (cached != null
                && Objects.equals(scopedKeyBases[index], base)
                && Objects.equals(scopedKeySeparators[index], separator)
                && Objects.equals(scopedKeyNames[index], name)) {
            return cached;
        }
        scopedKeyBases[index] = base;
        scopedKeySeparators[index] = separator;
        scopedKeyNames[index] = name;
        scopedKeys[index] = base + separator + name;
        return scopedKeys[index];
    }

    private String itemKey(Object source, int fallbackIndex, boolean explicit) {
        int index = itemKeyCount++;
        if (index >= itemKeys.length) {
            int capacity = itemKeys.length * 2;
            itemKeySources = Arrays.copyOf(itemKeySources, capacity);
            itemKeyFallbackIndices = Arrays.copyOf(itemKeyFallbackIndices, capacity);
            itemKeyExplicit = Arrays.copyOf(itemKeyExplicit, capacity);
            itemKeys = Arrays.copyOf(itemKeys, capacity);
        }
        String cached = itemKeys[index];
        if (cached != null
                && itemKeyExplicit[index] == explicit
                && (explicit ? Objects.equals(itemKeySources[index], source)
                        : itemKeyFallbackIndices[index] == fallbackIndex)) {
            return cached;
        }
        itemKeySources[index] = source;
        itemKeyFallbackIndices[index] = fallbackIndex;
        itemKeyExplicit[index] = explicit;
        itemKeys[index] = explicit ? String.valueOf(source) : String.valueOf(fallbackIndex);
        return itemKeys[index];
    }

    private UiModifier animatedModifier(float alpha, float offsetX, float offsetY) {
        int index = Math.max(0, identityCount - 1);
        UiModifier cached = animatedModifiers[index];
        if (cached != null
                && Float.compare(cached.alpha(), alpha) == 0
                && Float.compare(cached.offsetX(), offsetX) == 0
                && Float.compare(cached.offsetY(), offsetY) == 0) {
            return cached;
        }
        UiModifier modifier = UiModifier.none().alpha(alpha).offset(offsetX, offsetY);
        animatedModifiers[index] = modifier;
        return modifier;
    }

    private UiModifier contentSizeModifier(UiModifier source, UiAnimationSpec animation) {
        int index = contentSizeModifierCount++;
        if (index >= contentSizeModifiers.length) {
            int capacity = contentSizeModifiers.length * 2;
            contentSizeModifierSources = Arrays.copyOf(contentSizeModifierSources, capacity);
            contentSizeModifierAnimations = Arrays.copyOf(contentSizeModifierAnimations, capacity);
            contentSizeModifiers = Arrays.copyOf(contentSizeModifiers, capacity);
        }
        if (contentSizeModifiers[index] != null
                && contentSizeModifierSources[index] == source
                && contentSizeModifierAnimations[index] == animation) {
            return contentSizeModifiers[index];
        }
        contentSizeModifierSources[index] = source;
        contentSizeModifierAnimations[index] = animation;
        contentSizeModifiers[index] = source.animateContentSize(animation);
        return contentSizeModifiers[index];
    }

    private UiModifier interactive(UiModifier modifier) {
        UiModifier value = modifier != null ? modifier : UiModifier.none();
        if (value == UiModifier.none()) {
            return DEFAULT_INTERACTIVE_MODIFIER;
        }
        if (value.focusable()) {
            return value;
        }
        int index = interactiveModifierCount++;
        if (index >= interactiveModifiers.length) {
            int capacity = interactiveModifiers.length * 2;
            interactiveModifierSources = Arrays.copyOf(interactiveModifierSources, capacity);
            interactiveModifiers = Arrays.copyOf(interactiveModifiers, capacity);
        }
        if (interactiveModifierSources[index] == value && interactiveModifiers[index] != null) {
            return interactiveModifiers[index];
        }
        interactiveModifierSources[index] = value;
        interactiveModifiers[index] = value.focusable(true);
        return interactiveModifiers[index];
    }
}
