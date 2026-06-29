package io.github.libfdx.ui;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.g2d.BitmapFontLayout;
import io.github.libfdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an ui node.
 *
 * @author xpenatan
 */
public final class UiNode implements Disposable {
    private final List<UiNode> children = new ArrayList<UiNode>();
    private final List<UiNode> readOnlyChildren = Collections.unmodifiableList(children);
    private UiNodeType type;
    private String identity;
    private String key;
    private UiNode parent;
    private UiModifier modifier = UiModifier.none();
    private String text;
    private Object value;
    private int intValue;
    private float floatValue;
    private Runnable action;
    private TextureRegion image;
    private UiAnimationSpec animationSpec;
    private UiScrollState scrollState;
    private UiListState listState;
    private UiCustomContext customContext;
    private Object descriptor;
    private boolean visible = true;
    private boolean hovered;
    private boolean pressed;
    private boolean focused;
    private boolean checked;
    private boolean checkboxLabel;
    private boolean invalid;
    private UiRect bounds = UiRect.ZERO;
    private boolean disposed;
    private int preferredSizePass = -1;
    private float preferredSizeAvailableWidth;
    private float preferredSizeAvailableHeight;
    private UiSize preferredSize;
    private int textLayoutPass = -1;
    private String textLayoutText;
    private UiTextStyle textLayoutStyle;
    private float textLayoutMaxWidth;
    private BitmapFontLayout textLayout;
    private String fallbackGlyphText;
    private long[] fallbackGlyphRows;
    private String[] fallbackTabGlyphTexts;
    private long[][] fallbackTabGlyphRows;

    UiNode(UiNodeType type, String identity) {
        this.type = type;
        this.identity = identity;
    }

    void begin(String key, UiModifier modifier) {
        this.disposed = false;
        this.key = key;
        this.parent = null;
        this.modifier = modifier != null ? modifier : UiModifier.none();
        this.text = null;
        this.value = null;
        this.intValue = 0;
        this.floatValue = 0.0f;
        this.action = null;
        this.image = null;
        this.animationSpec = null;
        this.customContext = null;
        this.descriptor = null;
        this.visible = true;
        this.hovered = false;
        this.pressed = false;
        this.checked = false;
        this.checkboxLabel = false;
        this.invalid = false;
        this.children.clear();
    }

    void addChild(UiNode child) {
        if (child != null) {
            child.parent = this;
            children.add(child);
        }
    }

    void text(String text) {
        this.text = text;
    }

    void value(Object value) {
        this.value = value;
    }

    void intValue(int value) {
        this.intValue = value;
    }

    void floatValue(float value) {
        this.floatValue = value;
    }

    void action(Runnable action) {
        this.action = action;
    }

    void image(TextureRegion image) {
        this.image = image;
    }

    void animationSpec(UiAnimationSpec animationSpec) {
        this.animationSpec = animationSpec;
    }

    void scrollState(UiScrollState scrollState) {
        this.scrollState = scrollState;
    }

    void listState(UiListState listState) {
        this.listState = listState;
    }

    void customContext(UiCustomContext customContext) {
        this.customContext = customContext;
    }

    void descriptor(Object descriptor) {
        this.descriptor = descriptor;
    }

    void visible(boolean visible) {
        this.visible = visible;
    }

    void hovered(boolean hovered) {
        this.hovered = hovered;
    }

    void pressed(boolean pressed) {
        this.pressed = pressed;
    }

    void focused(boolean focused) {
        this.focused = focused;
    }

    void checked(boolean checked) {
        this.checked = checked;
    }

    void checkboxLabel(boolean checkboxLabel) {
        this.checkboxLabel = checkboxLabel;
    }

    void invalid(boolean invalid) {
        this.invalid = invalid;
    }

    void bounds(UiRect bounds) {
        this.bounds = bounds != null ? bounds : UiRect.ZERO;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public UiNodeType type() {
        return type;
    }

    /**
     * Returns the identity.
     *
     * @return the identity
     */
    public String identity() {
        return identity;
    }

    /**
     * Returns the key.
     *
     * @return the key
     */
    public String key() {
        return key;
    }

    UiNode parent() {
        return parent;
    }

    /**
     * Returns the modifier.
     *
     * @return the modifier
     */
    public UiModifier modifier() {
        return modifier;
    }

    /**
     * Returns the text.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    /**
     * Returns the value.
     *
     * @return the value
     */
    public Object value() {
        return value;
    }

    /**
     * Returns the int value.
     *
     * @return the int value
     */
    public int intValue() {
        return intValue;
    }

    /**
     * Returns the float value.
     *
     * @return the float value
     */
    public float floatValue() {
        return floatValue;
    }

    /**
     * Returns the action.
     *
     * @return the action
     */
    public Runnable action() {
        return action;
    }

    /**
     * Returns the image.
     *
     * @return the image
     */
    public TextureRegion image() {
        return image;
    }

    /**
     * Returns the animation spec.
     *
     * @return the animation spec
     */
    public UiAnimationSpec animationSpec() {
        return animationSpec;
    }

    /**
     * Returns the scroll state.
     *
     * @return the scroll state
     */
    public UiScrollState scrollState() {
        return scrollState;
    }

    /**
     * Returns the list state.
     *
     * @return the list state
     */
    public UiListState listState() {
        return listState;
    }

    /**
     * Returns the custom context.
     *
     * @return the custom context
     */
    public UiCustomContext customContext() {
        return customContext;
    }

    /**
     * Returns the descriptor.
     *
     * @return the descriptor
     */
    public Object descriptor() {
        return descriptor;
    }

    /**
     * Returns the visible.
     *
     * @return true if visible succeeds or is active; false otherwise
     */
    public boolean visible() {
        return visible;
    }

    /**
     * Returns the hovered.
     *
     * @return true if hovered succeeds or is active; false otherwise
     */
    public boolean hovered() {
        return hovered;
    }

    /**
     * Returns the pressed.
     *
     * @return true if pressed succeeds or is active; false otherwise
     */
    public boolean pressed() {
        return pressed;
    }

    /**
     * Returns the focused.
     *
     * @return true if focused succeeds or is active; false otherwise
     */
    public boolean focused() {
        return focused;
    }

    /**
     * Returns the checked.
     *
     * @return true if checked succeeds or is active; false otherwise
     */
    public boolean checked() {
        return checked;
    }

    boolean checkboxLabel() {
        return checkboxLabel;
    }

    /**
     * Returns the invalid.
     *
     * @return true if invalid succeeds or is active; false otherwise
     */
    public boolean invalid() {
        return invalid;
    }

    /**
     * Returns the bounds.
     *
     * @return the bounds
     */
    public UiRect bounds() {
        return bounds;
    }

    UiSize cachedPreferredSize(int pass, float availableWidth, float availableHeight) {
        if (preferredSizePass == pass
                && Float.compare(preferredSizeAvailableWidth, availableWidth) == 0
                && Float.compare(preferredSizeAvailableHeight, availableHeight) == 0) {
            return preferredSize;
        }
        return null;
    }

    void cachePreferredSize(int pass, float availableWidth, float availableHeight, UiSize size) {
        preferredSizePass = pass;
        preferredSizeAvailableWidth = availableWidth;
        preferredSizeAvailableHeight = availableHeight;
        preferredSize = size;
    }

    BitmapFontLayout cachedTextLayout(int pass, String text, UiTextStyle style, float maxWidth) {
        if (textLayoutPass == pass
                && sameText(textLayoutText, text)
                && textLayoutStyle == style
                && Float.compare(textLayoutMaxWidth, maxWidth) == 0) {
            return textLayout;
        }
        return null;
    }

    void cacheTextLayout(int pass, String text, UiTextStyle style, float maxWidth, BitmapFontLayout layout) {
        textLayoutPass = pass;
        textLayoutText = text;
        textLayoutStyle = style;
        textLayoutMaxWidth = maxWidth;
        textLayout = layout;
    }

    void cacheFallbackGlyphRows(String text) {
        int length = text != null ? text.length() : 0;
        if (length <= 0) {
            fallbackGlyphText = text;
            return;
        }
        if (sameText(fallbackGlyphText, text)
                && fallbackGlyphRows != null
                && fallbackGlyphRows.length >= length) {
            return;
        }
        if (fallbackGlyphRows == null || fallbackGlyphRows.length < length) {
            fallbackGlyphRows = new long[length];
        }
        for (int i = 0; i < length; i++) {
            fallbackGlyphRows[i] = UiG2DRenderer.fallbackGlyphRows(text.charAt(i));
        }
        fallbackGlyphText = text;
    }

    long[] fallbackGlyphRows(String text) {
        return sameText(fallbackGlyphText, text) ? fallbackGlyphRows : null;
    }

    void cacheFallbackTabGlyphRows(int index, String text) {
        if (index < 0) {
            return;
        }
        if (fallbackTabGlyphTexts == null || fallbackTabGlyphTexts.length <= index) {
            int length = fallbackTabGlyphTexts == null ? 4 : fallbackTabGlyphTexts.length;
            while (length <= index) {
                length *= 2;
            }
            String[] texts = new String[length];
            long[][] rows = new long[length][];
            if (fallbackTabGlyphTexts != null) {
                for (int i = 0; i < fallbackTabGlyphTexts.length; i++) {
                    texts[i] = fallbackTabGlyphTexts[i];
                }
                for (int i = 0; i < fallbackTabGlyphRows.length; i++) {
                    rows[i] = fallbackTabGlyphRows[i];
                }
            }
            fallbackTabGlyphTexts = texts;
            fallbackTabGlyphRows = rows;
        }
        int length = text != null ? text.length() : 0;
        long[] rows = fallbackTabGlyphRows[index];
        if (sameText(fallbackTabGlyphTexts[index], text)
                && rows != null
                && rows.length >= length) {
            return;
        }
        if (rows == null || rows.length < length) {
            rows = new long[length];
            fallbackTabGlyphRows[index] = rows;
        }
        for (int i = 0; i < length; i++) {
            rows[i] = UiG2DRenderer.fallbackGlyphRows(text.charAt(i));
        }
        fallbackTabGlyphTexts[index] = text;
    }

    long[] fallbackTabGlyphRows(int index, String text) {
        if (index < 0 || fallbackTabGlyphTexts == null || index >= fallbackTabGlyphTexts.length) {
            return null;
        }
        return sameText(fallbackTabGlyphTexts[index], text) ? fallbackTabGlyphRows[index] : null;
    }

    private boolean sameText(String a, String b) {
        return a == b || (a != null && a.equals(b));
    }

    /**
     * Returns the children.
     *
     * @return the children
     */
    public List<UiNode> children() {
        return readOnlyChildren;
    }

    /**
     * Runs the activate step.
     */
    public void activate() {
        if (action != null) {
            action.run();
            return;
        }
        if (type == UiNodeType.CHECKBOX && descriptor instanceof UiBooleanState) {
            UiBooleanState state = (UiBooleanState) descriptor;
            state.toggle();
            checked = state.get();
        }
    }

    boolean activatable() {
        return action != null || type == UiNodeType.CHECKBOX;
    }

    /**
     * Runs the select tab step.
     *
     * @param index the index
     * @return true if select tab succeeds or is active; false otherwise
     */
    public boolean selectTab(int index) {
        if (type != UiNodeType.TABS || !(descriptor instanceof UiTabsModel)) {
            return false;
        }
        UiTabsModel model = (UiTabsModel) descriptor;
        int selected = model.clamp(index);
        if (selected < 0) {
            return false;
        }
        model.select(selected);
        intValue = selected;
        return true;
    }

    /**
     * Returns the tab count.
     *
     * @return the tab count
     */
    public int tabCount() {
        return type == UiNodeType.TABS && descriptor instanceof UiTabsModel
                ? ((UiTabsModel) descriptor).count()
                : 0;
    }

    /**
     * Runs the tab label step.
     *
     * @param index the index
     * @return the tab label
     */
    public String tabLabel(int index) {
        return type == UiNodeType.TABS && descriptor instanceof UiTabsModel
                ? ((UiTabsModel) descriptor).label(index)
                : "";
    }

    /**
     * Sets the slider value.
     *
     * @param value the value
     * @return true if set slider value succeeds or is active; false otherwise
     */
    public boolean setSliderValue(float value) {
        if (type != UiNodeType.SLIDER || !(descriptor instanceof UiSliderModel)) {
            return false;
        }
        UiSliderModel model = (UiSliderModel) descriptor;
        float actual = model.range() != null ? model.range().clamp(value) : value;
        if (model.state() != null) {
            model.state().set(actual);
        }
        floatValue = actual;
        return true;
    }

    /**
     * Returns the slider minimum.
     *
     * @return the slider minimum
     */
    public float sliderMinimum() {
        if (type != UiNodeType.SLIDER || !(descriptor instanceof UiSliderModel)) {
            return 0.0f;
        }
        UiRange range = ((UiSliderModel) descriptor).range();
        return range != null ? range.minimum() : 0.0f;
    }

    /**
     * Returns the slider maximum.
     *
     * @return the slider maximum
     */
    public float sliderMaximum() {
        if (type != UiNodeType.SLIDER || !(descriptor instanceof UiSliderModel)) {
            return 1.0f;
        }
        UiRange range = ((UiSliderModel) descriptor).range();
        return range != null ? range.maximum() : 1.0f;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        parent = null;
        children.clear();
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
