package io.github.libfdx.ui;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.g2d.BitmapFontLayout;
import io.github.libfdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final ArrayList<UiNode> orderedChildren = new ArrayList<UiNode>();
    private final List<UiNode> readOnlyOrderedChildren = Collections.unmodifiableList(orderedChildren);
    private final ArrayList<UiNode> reverseOrderedChildren = new ArrayList<UiNode>();
    private final List<UiNode> readOnlyReverseOrderedChildren = Collections.unmodifiableList(reverseOrderedChildren);
    private long childOrderRevision = Long.MIN_VALUE;
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
    private UiRect[] cachedRects;
    private UiSize[] cachedSizes;
    private String[] derivedKeySuffixes;
    private String[] derivedKeys;
    private UiLayoutConstraints[] cachedLayoutConstraints;
    private int nextLayoutConstraint;
    private UiInsets cachedEffectivePadding;
    private long compositionPass = Long.MIN_VALUE;
    private String maskedTextSource;
    private String maskedText;
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
        this.visible = true;
        this.hovered = false;
        this.pressed = false;
        this.checked = false;
        this.checkboxLabel = false;
        this.invalid = false;
        this.children.clear();
        this.orderedChildren.clear();
        this.reverseOrderedChildren.clear();
        this.childOrderRevision = Long.MIN_VALUE;
    }

    void addChild(UiNode child) {
        if (child != null) {
            child.parent = this;
            children.add(child);
            childOrderRevision = Long.MIN_VALUE;
        }
    }

    void usedInComposition(long pass) {
        compositionPass = pass;
    }

    boolean wasUsedInComposition(long pass) {
        return compositionPass == pass;
    }

    UiRect rendererRect(int slot, float x, float y, float width, float height) {
        if (slot < 0) {
            throw new IllegalArgumentException("UI node rectangle cache slot cannot be negative");
        }
        if (cachedRects == null) {
            cachedRects = new UiRect[Math.max(16, slot + 1)];
        } else if (slot >= cachedRects.length) {
            int next = cachedRects.length;
            while (next <= slot) {
                next *= 2;
            }
            cachedRects = Arrays.copyOf(cachedRects, next);
        }
        UiRect cached = cachedRects[slot];
        float clampedWidth = Math.max(0.0f, width);
        float clampedHeight = Math.max(0.0f, height);
        if (cached != null
                && Float.compare(cached.x(), x) == 0
                && Float.compare(cached.y(), y) == 0
                && Float.compare(cached.width(), clampedWidth) == 0
                && Float.compare(cached.height(), clampedHeight) == 0) {
            return cached;
        }
        UiRect value = new UiRect(x, y, clampedWidth, clampedHeight);
        cachedRects[slot] = value;
        return value;
    }

    UiSize layoutSize(int slot, float width, float height) {
        if (slot < 0) {
            throw new IllegalArgumentException("UI node size cache slot cannot be negative");
        }
        if (cachedSizes == null) {
            cachedSizes = new UiSize[Math.max(8, slot + 1)];
        } else if (slot >= cachedSizes.length) {
            int next = cachedSizes.length;
            while (next <= slot) {
                next *= 2;
            }
            cachedSizes = Arrays.copyOf(cachedSizes, next);
        }
        float clampedWidth = Math.max(0.0f, width);
        float clampedHeight = Math.max(0.0f, height);
        UiSize cached = cachedSizes[slot];
        if (cached != null
                && Float.compare(cached.width(), clampedWidth) == 0
                && Float.compare(cached.height(), clampedHeight) == 0) {
            return cached;
        }
        UiSize value = new UiSize(clampedWidth, clampedHeight);
        cachedSizes[slot] = value;
        return value;
    }

    String derivedKey(int slot, String suffix) {
        if (slot < 0) {
            throw new IllegalArgumentException("UI node derived-key cache slot cannot be negative");
        }
        if (derivedKeys == null) {
            derivedKeySuffixes = new String[Math.max(4, slot + 1)];
            derivedKeys = new String[Math.max(4, slot + 1)];
        } else if (slot >= derivedKeys.length) {
            int next = derivedKeys.length;
            while (next <= slot) {
                next *= 2;
            }
            derivedKeySuffixes = Arrays.copyOf(derivedKeySuffixes, next);
            derivedKeys = Arrays.copyOf(derivedKeys, next);
        }
        String actualSuffix = suffix != null ? suffix : "";
        if (derivedKeys[slot] != null && sameText(derivedKeySuffixes[slot], actualSuffix)) {
            return derivedKeys[slot];
        }
        derivedKeySuffixes[slot] = actualSuffix;
        derivedKeys[slot] = identity + actualSuffix;
        return derivedKeys[slot];
    }

    UiLayoutConstraints layoutConstraints(float minWidth, float minHeight, float maxWidth, float maxHeight) {
        float actualMinWidth = Math.max(0.0f, minWidth);
        float actualMinHeight = Math.max(0.0f, minHeight);
        float actualMaxWidth = Math.max(actualMinWidth, maxWidth);
        float actualMaxHeight = Math.max(actualMinHeight, maxHeight);
        if (cachedLayoutConstraints == null) {
            cachedLayoutConstraints = new UiLayoutConstraints[4];
        }
        for (int i = 0; i < cachedLayoutConstraints.length; i++) {
            UiLayoutConstraints cached = cachedLayoutConstraints[i];
            if (cached != null
                    && Float.compare(cached.minWidth(), actualMinWidth) == 0
                    && Float.compare(cached.minHeight(), actualMinHeight) == 0
                    && Float.compare(cached.maxWidth(), actualMaxWidth) == 0
                    && Float.compare(cached.maxHeight(), actualMaxHeight) == 0) {
                return cached;
            }
        }
        int index = nextLayoutConstraint;
        for (int i = 0; i < cachedLayoutConstraints.length; i++) {
            if (cachedLayoutConstraints[i] == null) {
                index = i;
                break;
            }
        }
        UiLayoutConstraints value = new UiLayoutConstraints(
                actualMinWidth, actualMinHeight, actualMaxWidth, actualMaxHeight);
        cachedLayoutConstraints[index] = value;
        nextLayoutConstraint = (index + 1) % cachedLayoutConstraints.length;
        return value;
    }

    String maskedText(String value) {
        String actual = value != null ? value : "";
        if (sameText(maskedTextSource, actual)) {
            return maskedText;
        }
        maskedTextSource = actual;
        int codePointCount = actual.codePointCount(0, actual.length());
        maskedText = codePointCount == 0 ? "" : "*".repeat(codePointCount);
        return maskedText;
    }

    UiInsets effectivePadding(UiInsets modifierPadding, UiInsets stylePadding) {
        UiInsets modifier = modifierPadding != null ? modifierPadding : UiInsets.ZERO;
        UiInsets style = stylePadding != null ? stylePadding : UiInsets.ZERO;
        float left = modifier.left() + style.left();
        float top = modifier.top() + style.top();
        float right = modifier.right() + style.right();
        float bottom = modifier.bottom() + style.bottom();
        if (cachedEffectivePadding != null
                && Float.compare(cachedEffectivePadding.left(), left) == 0
                && Float.compare(cachedEffectivePadding.top(), top) == 0
                && Float.compare(cachedEffectivePadding.right(), right) == 0
                && Float.compare(cachedEffectivePadding.bottom(), bottom) == 0) {
            return cachedEffectivePadding;
        }
        if (style == UiInsets.ZERO) {
            cachedEffectivePadding = modifier;
        } else if (modifier == UiInsets.ZERO) {
            cachedEffectivePadding = style;
        } else {
            cachedEffectivePadding = UiInsets.of(left, top, right, bottom);
        }
        return cachedEffectivePadding;
    }

    boolean hasChildOrderRevision(long revision) {
        return childOrderRevision == revision;
    }

    ArrayList<UiNode> mutableOrderedChildren() {
        return orderedChildren;
    }

    ArrayList<UiNode> mutableReverseOrderedChildren() {
        return reverseOrderedChildren;
    }

    void childOrderRevision(long revision) {
        childOrderRevision = revision;
    }

    List<UiNode> orderedChildren(boolean frontToBack) {
        return frontToBack ? readOnlyReverseOrderedChildren : readOnlyOrderedChildren;
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

    void bounds(float x, float y, float width, float height) {
        this.bounds = rendererRect(12, x, y, width, height);
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
        int textLength = text != null ? text.length() : 0;
        int length = text != null ? text.codePointCount(0, textLength) : 0;
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
        int index = 0;
        for (int i = 0; i < textLength;) {
            int codePoint = text.codePointAt(i);
            fallbackGlyphRows[index++] = UiG2DRenderer.fallbackGlyphRows(codePoint);
            i += Character.charCount(codePoint);
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
        int textLength = text != null ? text.length() : 0;
        int length = text != null ? text.codePointCount(0, textLength) : 0;
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
        int rowIndex = 0;
        for (int i = 0; i < textLength;) {
            int codePoint = text.codePointAt(i);
            rows[rowIndex++] = UiG2DRenderer.fallbackGlyphRows(codePoint);
            i += Character.charCount(codePoint);
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
        if ((type == UiNodeType.CHECKBOX || type == UiNodeType.SWITCH || type == UiNodeType.COLLAPSE_BAR)
                && descriptor instanceof UiBooleanState) {
            UiBooleanState state = (UiBooleanState) descriptor;
            state.toggle();
            checked = state.get();
            return;
        }
        if (type == UiNodeType.RADIO_BUTTON && descriptor instanceof UiRadioModel) {
            UiRadioModel model = (UiRadioModel) descriptor;
            model.select();
            checked = model.selected();
        }
    }

    boolean activatable() {
        return action != null
                || type == UiNodeType.CHECKBOX
                || type == UiNodeType.SWITCH
                || type == UiNodeType.RADIO_BUTTON
                || type == UiNodeType.COLLAPSE_BAR;
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
        modifier = UiModifier.none();
        text = null;
        value = null;
        action = null;
        image = null;
        animationSpec = null;
        scrollState = null;
        listState = null;
        customContext = null;
        descriptor = null;
        children.clear();
        orderedChildren.clear();
        reverseOrderedChildren.clear();
        childOrderRevision = Long.MIN_VALUE;
        cachedRects = null;
        cachedSizes = null;
        derivedKeySuffixes = null;
        derivedKeys = null;
        cachedLayoutConstraints = null;
        nextLayoutConstraint = 0;
        cachedEffectivePadding = null;
        maskedTextSource = null;
        maskedText = null;
        preferredSize = null;
        textLayoutText = null;
        textLayoutStyle = null;
        textLayout = null;
        fallbackGlyphText = null;
        fallbackGlyphRows = null;
        fallbackTabGlyphTexts = null;
        fallbackTabGlyphRows = null;
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
