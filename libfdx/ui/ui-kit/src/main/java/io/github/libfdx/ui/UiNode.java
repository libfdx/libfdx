package io.github.libfdx.ui;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.g2d.BitmapFontLayout;
import io.github.libfdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public UiNodeType type() {
        return type;
    }

    public String identity() {
        return identity;
    }

    public String key() {
        return key;
    }

    UiNode parent() {
        return parent;
    }

    public UiModifier modifier() {
        return modifier;
    }

    public String text() {
        return text;
    }

    public Object value() {
        return value;
    }

    public int intValue() {
        return intValue;
    }

    public float floatValue() {
        return floatValue;
    }

    public Runnable action() {
        return action;
    }

    public TextureRegion image() {
        return image;
    }

    public UiAnimationSpec animationSpec() {
        return animationSpec;
    }

    public UiScrollState scrollState() {
        return scrollState;
    }

    public UiListState listState() {
        return listState;
    }

    public UiCustomContext customContext() {
        return customContext;
    }

    public Object descriptor() {
        return descriptor;
    }

    public boolean visible() {
        return visible;
    }

    public boolean hovered() {
        return hovered;
    }

    public boolean pressed() {
        return pressed;
    }

    public boolean focused() {
        return focused;
    }

    public boolean checked() {
        return checked;
    }

    boolean checkboxLabel() {
        return checkboxLabel;
    }

    public boolean invalid() {
        return invalid;
    }

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

    public List<UiNode> children() {
        return readOnlyChildren;
    }

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

    public int tabCount() {
        return type == UiNodeType.TABS && descriptor instanceof UiTabsModel
                ? ((UiTabsModel) descriptor).count()
                : 0;
    }

    public String tabLabel(int index) {
        return type == UiNodeType.TABS && descriptor instanceof UiTabsModel
                ? ((UiTabsModel) descriptor).label(index)
                : "";
    }

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

    public float sliderMinimum() {
        if (type != UiNodeType.SLIDER || !(descriptor instanceof UiSliderModel)) {
            return 0.0f;
        }
        UiRange range = ((UiSliderModel) descriptor).range();
        return range != null ? range.minimum() : 0.0f;
    }

    public float sliderMaximum() {
        if (type != UiNodeType.SLIDER || !(descriptor instanceof UiSliderModel)) {
            return 1.0f;
        }
        UiRange range = ((UiSliderModel) descriptor).range();
        return range != null ? range.maximum() : 1.0f;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        parent = null;
        children.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
