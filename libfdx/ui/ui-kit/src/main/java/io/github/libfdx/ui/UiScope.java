package io.github.libfdx.ui;

import io.github.libfdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.List;

public final class UiScope {
    private final UiRoot root;
    private final UiNode parent;
    private final String path;
    private int childIndex;

    UiScope(UiRoot root, UiNode parent, String path) {
        this.root = root;
        this.parent = parent;
        this.path = path;
    }

    public UiNode column(UiContent content) {
        return column(UiModifier.none(), content);
    }

    public UiNode column(UiModifier modifier, UiContent content) {
        return container(UiNodeType.COLUMN, null, modifier, content);
    }

    public UiNode row(UiContent content) {
        return row(UiModifier.none(), content);
    }

    public UiNode row(UiModifier modifier, UiContent content) {
        return container(UiNodeType.ROW, null, modifier, content);
    }

    public UiNode stack(UiContent content) {
        return stack(UiModifier.none(), content);
    }

    public UiNode stack(UiModifier modifier, UiContent content) {
        return container(UiNodeType.STACK, null, modifier, content);
    }

    public UiNode grid(int columns, UiContent content) {
        return grid(columns, UiModifier.none(), content);
    }

    public UiNode grid(int columns, UiModifier modifier, UiContent content) {
        UiNode node = addNode(UiNodeType.GRID, null, modifier);
        node.intValue(Math.max(1, columns));
        build(node, content);
        return node;
    }

    public UiNode panel(UiContent content) {
        return panel(UiModifier.none(), content);
    }

    public UiNode panel(UiModifier modifier, UiContent content) {
        return container(UiNodeType.PANEL, null, modifier, content);
    }

    public UiNode scroll(UiContent content) {
        return scroll(UiModifier.none(), null, content);
    }

    public UiNode scroll(UiModifier modifier, UiContent content) {
        return scroll(modifier, null, content);
    }

    public UiNode scroll(UiModifier modifier, UiScrollState state, UiContent content) {
        UiNode node = addNode(UiNodeType.SCROLL, null, modifier);
        node.scrollState(state != null ? state : root.scrollState(node.identity()));
        build(node, content);
        return node;
    }

    public UiNode scrollView(UiContent content) {
        return scroll(content);
    }

    public UiNode scrollView(UiModifier modifier, UiContent content) {
        return scroll(modifier, content);
    }

    public UiNode scrollView(UiModifier modifier, UiScrollState state, UiContent content) {
        return scroll(modifier, state, content);
    }

    public UiNode text(String text) {
        return text(text, UiModifier.none());
    }

    public UiNode text(String text, UiModifier modifier) {
        UiNode node = addNode(UiNodeType.TEXT, null, modifier);
        node.text(text != null ? text : "");
        return node;
    }

    public UiNode button(String text, Runnable action) {
        return button(text, UiModifier.none(), action);
    }

    public UiNode button(String text, UiModifier modifier, Runnable action) {
        UiNode node = addNode(UiNodeType.BUTTON, null, interactive(modifier));
        node.text(text);
        node.action(action);
        return node;
    }

    public UiNode checkbox(String text, UiBooleanState state) {
        return checkbox(text, UiModifier.none(), state);
    }

    public UiNode checkbox(UiBooleanState state) {
        return checkbox(UiModifier.none(), state);
    }

    public UiNode checkbox(UiModifier modifier, UiBooleanState state) {
        return checkbox("", modifier, state);
    }

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

    public UiNode slider(UiFloatState state, float minimum, float maximum) {
        return slider(UiModifier.none(), state, minimum, maximum);
    }

    public UiNode slider(UiModifier modifier, UiFloatState state, float minimum, float maximum) {
        UiNode node = addNode(UiNodeType.SLIDER, null, interactive(modifier));
        UiRange range = new UiRange(minimum, maximum);
        float value = state != null ? state.get() : minimum;
        node.floatValue(range.clamp(value));
        node.descriptor(new UiSliderModel(range, state));
        return node;
    }

    public UiNode progressBar(float value) {
        return progressBar(UiModifier.none(), value, 0.0f, 1.0f);
    }

    public UiNode progressBar(UiModifier modifier, float value) {
        return progressBar(modifier, value, 0.0f, 1.0f);
    }

    public UiNode progressBar(UiFloatState state, float minimum, float maximum) {
        return progressBar(UiModifier.none(), state, minimum, maximum);
    }

    public UiNode progressBar(UiModifier modifier, UiFloatState state, float minimum, float maximum) {
        UiRange range = new UiRange(minimum, maximum);
        float value = state != null ? state.get() : minimum;
        return progressBar(modifier, value, range, state);
    }

    public UiNode progressBar(UiModifier modifier, float value, float minimum, float maximum) {
        return progressBar(modifier, value, new UiRange(minimum, maximum), null);
    }

    private UiNode progressBar(UiModifier modifier, float value, UiRange range, UiFloatState state) {
        UiNode node = addNode(UiNodeType.PROGRESS_BAR, null, modifier);
        node.floatValue(range.clamp(value));
        node.descriptor(new UiProgressBarModel(range, state));
        return node;
    }

    public UiNode tabs(UiIntState activeIndex, String... labels) {
        return tabs(UiModifier.none(), activeIndex, labels);
    }

    public UiNode tabs(UiModifier modifier, UiIntState activeIndex, String... labels) {
        UiNode node = addNode(UiNodeType.TABS, null, interactive(modifier));
        UiTabsModel model = new UiTabsModel(activeIndex, labels);
        int active = model.clamp(activeIndex != null ? activeIndex.get() : node.intValue());
        if (active >= 0 && activeIndex != null && activeIndex.get() != active) {
            activeIndex.set(active);
        }
        node.intValue(active);
        node.descriptor(model);
        return node;
    }

    public UiNode textField(UiState<String> state) {
        return textField(UiModifier.none(), state);
    }

    public UiNode textField(UiModifier modifier, UiState<String> state) {
        return textField(modifier, state, UiTextInputFilter.STRING);
    }

    public UiNode textField(UiState<String> state, UiTextInputFilter inputFilter) {
        return textField(UiModifier.none(), state, inputFilter);
    }

    public UiNode textField(UiModifier modifier, UiState<String> state, UiTextInputFilter inputFilter) {
        UiNode node = addNode(UiNodeType.TEXT_FIELD, null, interactive(modifier));
        node.value(state != null ? state.get() : "");
        UiTextFieldModel model = node.descriptor() instanceof UiTextFieldModel
                ? (UiTextFieldModel) node.descriptor()
                : new UiTextFieldModel(state);
        model.state(state);
        model.multiline(false);
        model.inputFilter(inputFilter);
        node.descriptor(model);
        return node;
    }

    public UiNode textArea(UiState<String> state) {
        return textArea(UiModifier.none(), state, UiTextAreaOptions.defaults());
    }

    public UiNode textArea(UiModifier modifier, UiState<String> state) {
        return textArea(modifier, state, UiTextAreaOptions.defaults());
    }

    public UiNode textArea(UiState<String> state, UiTextAreaOptions options) {
        return textArea(UiModifier.none(), state, options);
    }

    public UiNode textArea(UiModifier modifier, UiState<String> state, UiTextAreaOptions options) {
        UiNode node = addNode(UiNodeType.TEXT_AREA, null, interactive(modifier));
        node.value(state != null ? state.get() : "");
        UiTextFieldModel model = node.descriptor() instanceof UiTextFieldModel
                ? (UiTextFieldModel) node.descriptor()
                : new UiTextFieldModel(state);
        model.state(state);
        model.multiline(true);
        model.inputFilter(UiTextInputFilter.STRING);
        model.textAreaOptions(options);
        UiScrollState scrollState = root.scrollState(node.identity() + ":text-area");
        model.scrollState(scrollState);
        node.scrollState(scrollState);
        node.descriptor(model);
        return node;
    }

    public UiNode intField(UiState<String> state) {
        return textField(UiModifier.none(), state, UiTextInputFilter.INTEGER);
    }

    public UiNode intField(UiModifier modifier, UiState<String> state) {
        return textField(modifier, state, UiTextInputFilter.INTEGER);
    }

    public UiNode floatField(UiState<String> state) {
        return textField(UiModifier.none(), state, UiTextInputFilter.FLOAT);
    }

    public UiNode floatField(UiModifier modifier, UiState<String> state) {
        return textField(modifier, state, UiTextInputFilter.FLOAT);
    }

    public UiNode image(TextureRegion region) {
        return image(region, UiModifier.none());
    }

    public UiNode image(TextureRegion region, UiModifier modifier) {
        UiNode node = addNode(UiNodeType.IMAGE, null, modifier);
        node.image(region);
        return node;
    }

    public UiNode spacer() {
        return spacer(UiModifier.none());
    }

    public UiNode spacer(UiModifier modifier) {
        return addNode(UiNodeType.SPACER, null, modifier);
    }

    public UiNode animatedVisibility(boolean visible, UiAnimationSpec animation, UiContent content) {
        UiAnimationSpec spec = animation != null ? animation : UiAnimationSpec.defaultSpec();
        String identity = nextIdentity(UiNodeType.ANIMATED_VISIBILITY, null);
        UiFloatAnimatable progress = root.floatAnimatable(identity + ":visibility", visible ? 1.0f : 0.0f);
        progress.animateTo(visible ? 1.0f : 0.0f, spec);
        float value = progress.get();
        UiModifier modifier = UiModifier.none();
        if (spec.isFade()) {
            modifier = modifier.alpha(value);
        }
        if (spec.slideX() != 0.0f || spec.slideY() != 0.0f) {
            float remaining = 1.0f - value;
            modifier = modifier.offset(spec.slideX() * remaining, spec.slideY() * remaining);
        }
        UiNode node = addNode(identity, UiNodeType.ANIMATED_VISIBILITY, null, modifier);
        node.visible(visible || progress.isRunning() || value > 0.001f);
        node.animationSpec(spec);
        if (node.visible()) {
            build(node, content);
        }
        return node;
    }

    public UiNode animateContentSize(UiContent content) {
        return animateContentSize(UiModifier.none(), UiAnimationSpec.defaultSpec(), content);
    }

    public UiNode animateContentSize(UiAnimationSpec animation, UiContent content) {
        return animateContentSize(UiModifier.none(), animation, content);
    }

    public UiNode animateContentSize(UiModifier modifier, UiAnimationSpec animation, UiContent content) {
        UiModifier value = modifier != null ? modifier : UiModifier.none();
        return container(UiNodeType.COLUMN, null, value.animateContentSize(animation), content);
    }

    public UiNode window(String title, UiWindowState state, UiContent content) {
        return window(title, UiModifier.none(), state, content);
    }

    public UiNode window(String title, UiModifier modifier, UiWindowState state, UiContent content) {
        UiNode node = addNode(UiNodeType.WINDOW, title, interactive(modifier));
        node.text(title);
        node.descriptor(new UiWindowModel(state));
        build(node, content);
        return node;
    }

    public UiNode modal(UiModal modal, UiContent content) {
        UiNode node = addNode(UiNodeType.MODAL, modal != null ? modal.id() : null, UiModifier.none());
        node.descriptor(modal);
        build(node, content);
        return node;
    }

    public UiNode popup(UiPopup popup, UiContent content) {
        UiNode node = addNode(UiNodeType.POPUP, popup != null ? popup.id() : null, UiModifier.none());
        node.descriptor(popup);
        build(node, content);
        return node;
    }

    public UiNode tooltip(UiTooltip tooltip, UiContent content) {
        String identity = nextIdentity(UiNodeType.TOOLTIP, tooltip != null ? tooltip.text() : null);
        boolean active = root.tooltipActive(tooltip);
        UiFloatAnimatable alpha = root.floatAnimatable(identity + ":alpha", active ? 1.0f : 0.0f);
        alpha.animateTo(active ? 1.0f : 0.0f, Ui.animation().durationMillis(160).fade());
        UiNode node = addNode(identity, UiNodeType.TOOLTIP, tooltip != null ? tooltip.text() : null,
                UiModifier.none().alpha(alpha.get()));
        node.descriptor(tooltip);
        node.visible(active || alpha.isRunning() || alpha.get() > 0.001f);
        if (node.visible()) {
            build(node, content);
        }
        return node;
    }

    public <T> void items(Iterable<T> items, UiKey<T> key, UiItemContent<T> content) {
        if (items == null || content == null) {
            return;
        }
        int index = 0;
        for (T item : items) {
            String itemKey = key != null ? String.valueOf(key.key(item)) : String.valueOf(index);
            UiNode node = addNode(UiNodeType.ITEM, itemKey, UiModifier.none().animateItemPlacement());
            node.value(item);
            content.build(new UiScope(root, node, node.identity()), item);
            index++;
        }
    }

    public <T> UiNode virtualList(Iterable<T> items, UiListState state, int visibleCount, UiKey<T> key,
            UiItemContent<T> content) {
        UiListState listState = state != null ? state : listState(null);
        UiNode node = addNode(UiNodeType.SCROLL, "virtual-list", UiModifier.none().fillWidth());
        node.listState(listState);
        if (items == null || content == null) {
            return node;
        }
        List<T> values = new ArrayList<T>();
        for (T item : items) {
            values.add(item);
        }
        int first = Math.max(0, Math.min(listState.firstVisibleIndex(), values.size()));
        int count = Math.max(0, visibleCount);
        int last = Math.min(values.size(), first + count);
        UiScope childScope = new UiScope(root, node, node.identity());
        for (int index = first; index < last; index++) {
            T item = values.get(index);
            String itemKey = key != null ? String.valueOf(key.key(item)) : String.valueOf(index);
            UiNode itemNode = childScope.addNode(UiNodeType.ITEM, itemKey,
                    UiModifier.none().animateItemPlacement());
            itemNode.value(item);
            content.build(new UiScope(root, itemNode, itemNode.identity()), item);
        }
        return node;
    }

    public UiNode custom(String type, UiModifier modifier, UiCustomContent content) {
        UiNode node = addNode(UiNodeType.CUSTOM, type, modifier);
        if (content != null) {
            UiCustomContext context = new UiCustomContext();
            content.build(context);
            node.customContext(context);
        }
        return node;
    }

    public <T> UiAnimatable<T> animatable(String key, T value) {
        return root.animatable(path + ":animatable:" + key, value);
    }

    public UiFloatAnimatable floatAnimatable(String key, float value) {
        return root.floatAnimatable(path + ":float-animatable:" + key, value);
    }

    public UiScrollState scrollState(String key) {
        return root.scrollState(path + ":scroll:" + key);
    }

    public UiListState listState(String key) {
        return root.listState(path + ":list:" + key);
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
            content.build(new UiScope(root, node, node.identity()));
        }
    }

    private String nextIdentity(UiNodeType type, String key) {
        if (key != null) {
            return path + "/" + type.name() + ":key:" + key;
        }
        String identity = path + "/" + childIndex + ":" + type.name();
        childIndex++;
        return identity;
    }

    private UiModifier interactive(UiModifier modifier) {
        UiModifier value = modifier != null ? modifier : UiModifier.none();
        return value.focusable(true);
    }
}
