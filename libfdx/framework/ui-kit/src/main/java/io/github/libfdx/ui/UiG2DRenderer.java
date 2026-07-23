package io.github.libfdx.ui;

import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.graphics.g2d.BitmapFont;
import io.github.libfdx.graphics.g2d.BitmapFontGlyph;
import io.github.libfdx.graphics.g2d.BitmapFontLayout;
import io.github.libfdx.graphics.g2d.ShapeRenderer2D;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an ui G2 d renderer.
 *
 * @author xpenatan
 */
public final class UiG2DRenderer implements UiRenderer {
    private static final int FALLBACK_GLYPH_COLUMNS = 5;
    private static final int FALLBACK_GLYPH_ROWS = 7;
    private static final long FALLBACK_GLYPH_SPACE = 0L;
    private static final long FALLBACK_GLYPH_NEWLINE = Long.MIN_VALUE;
    private static final long UNKNOWN_FALLBACK_GLYPH = rows(31, 17, 1, 6, 4, 0, 4);
    private static final UiDrawable BUTTON_DISABLED_BACKGROUND =
            UiDrawable.color(UiColor.rgba8888(0x222a33bb));
    private static final UiDrawable BUTTON_PRESSED_BACKGROUND =
            UiDrawable.color(UiColor.rgba8888(0x2f7edcff));
    private static final UiDrawable BUTTON_HOVERED_BACKGROUND =
            UiDrawable.color(UiColor.rgba8888(0x395064ff));
    private static final UiDrawable BUTTON_BACKGROUND =
            UiDrawable.color(UiColor.rgba8888(0x303843ff));
    private static final UiDrawable PANEL_BACKGROUND =
            UiDrawable.color(UiColor.rgba8888(0x18202aff));
    private static final UiDrawable WINDOW_BACKGROUND =
            UiDrawable.color(UiColor.rgba8888(0x18212bff));
    private static final UiDrawable TEXT_INPUT_FOCUSED_BACKGROUND =
            UiDrawable.color(UiColor.rgba8888(0x202a36ff));
    private static final UiDrawable TEXT_INPUT_BACKGROUND =
            UiDrawable.color(UiColor.rgba8888(0x121820ff));
    private static final UiDrawable TABS_BACKGROUND =
            UiDrawable.color(UiColor.rgba8888(0x101820ff));
    private static final UiColor DEBUG_INVALID = UiColor.rgba8888(0xff3b30ff);
    private static final UiColor DEBUG_FOCUSED = UiColor.rgba8888(0x7bd88fff);
    private static final UiColor DEBUG_HOVERED = UiColor.rgba8888(0xffffffff);
    private static final UiColor DEBUG_WINDOW = UiColor.rgba8888(0xffd166cc);
    private static final UiColor DEBUG_CONTROL = UiColor.rgba8888(0x4cc9f0cc);
    private static final UiColor DEBUG_OVERLAY = UiColor.rgba8888(0xf72585cc);
    private static final UiColor DEBUG_DEFAULT = UiColor.rgba8888(0xa9b6c599);
    private static final UiColor DEBUG_WINDOW_TITLE = UiColor.rgba8888(0xffd166ee);
    private static final UiColor DEBUG_WINDOW_RESIZE = UiColor.rgba8888(0xff4fd8ff);

    private final GraphicsContext graphics;
    private final ShapeRenderer2D shapes;
    private final RenderPassDescriptor renderPassDescriptor = new RenderPassDescriptor().label("ui g2d pass");
    private final List<CustomTextDraw> customTextDraws = new ArrayList<CustomTextDraw>();
    private final List<CustomImageDraw> customImageDraws = new ArrayList<CustomImageDraw>();
    private final List<UiNode> overlayNodes = new ArrayList<UiNode>();
    private final CustomDrawContext customDrawContext = new CustomDrawContext();
    private int customTextDrawCount;
    private int customImageDrawCount;
    private Batch2D batch;
    private UiRect currentClip;
    private boolean disposed;

    /**
     * Creates an UI G2 d renderer.
     *
     * @param graphics the graphics context
     */
    public UiG2DRenderer(GraphicsContext graphics) {
        this.graphics = graphics;
        this.shapes = graphics != null ? new ShapeRenderer2D(graphics) : null;
    }

    /**
     * Renders the current content.
     *
     * @param root the root
     * @param node the node
     */
    @Override
    public void render(UiRoot root, UiNode node) {
        if (disposed || root == null || node == null || shapes == null) {
            return;
        }
        resetCustomDraws();
        overlayNodes.clear();
        prepareText(root, node);
        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(renderPassDescriptor
                .colorAttachment(frame.colorAttachment())
                .colorLoadOp(LoadOp.load())
                .colorStoreOp(StoreOp.store()));
        try {
            renderSubtree(root, node, pass);
            renderOverlayQueue(root, pass);
            if (root.debugLines()) {
                shapes.begin(pass);
                renderDebugLines(root, node);
                shapes.end();
            }
        } finally {
            pass.end();
            resetCustomDraws();
            overlayNodes.clear();
        }
    }

    private void renderSubtree(UiRoot root, UiNode node, RenderPass pass) {
        if (!node.visible()) {
            return;
        }
        shapes.begin(pass);
        renderShapes(root, node, 1.0f, null);
        shapes.end();

        boolean renderBatch = needsBatch(root, node);
        if (renderBatch) {
            Batch2D activeBatch = batch();
            activeBatch.begin(pass);
            renderImages(root, node, 1.0f, null);
            activeBatch.end();
        }
    }


    private void renderOverlayQueue(UiRoot root, RenderPass pass) {
        for (int i = 0; i < overlayNodes.size(); i++) {
            renderSubtree(root, overlayNodes.get(i), pass);
        }
    }

    private void prepareText(UiRoot root, UiNode node) {
        if (!node.visible()) {
            return;
        }
        if (node.text() != null || isTextInput(node)) {
            if (hasBitmapFont(root, node)) {
                root.textFont(nodeTextStyle(root, node));
            } else {
                node.cacheFallbackGlyphRows(nodeText(node));
            }
        } else if (node.type() == UiNodeType.TABS) {
            if (hasBitmapFont(root, node)) {
                root.textFont(nodeTextStyle(root, node));
            } else {
                int count = root.tabCount(node);
                for (int i = 0; i < count; i++) {
                    node.cacheFallbackTabGlyphRows(i, root.tabLabel(node, i));
                }
            }
        }
        List<UiNode> children = root.renderChildren(node);
        for (int i = 0; i < children.size(); i++) {
            prepareText(root, children.get(i));
        }
    }

    private void renderShapes(UiRoot root, UiNode node, float parentAlpha, UiRect inheritedClip) {
        if (!node.visible()) {
            return;
        }
        UiRect previousClip = currentClip;
        currentClip = nodeClip(node, inheritedClip);
        float alpha = combinedAlpha(parentAlpha, node.modifier().alpha());
        drawBackground(root, node, alpha);
        if (node.type() == UiNodeType.CUSTOM) {
            drawCustom(root, node, alpha);
        }
        if (node.type() == UiNodeType.CHECKBOX) {
            drawCheckbox(root, node, alpha);
        } else if (node.type() == UiNodeType.SLIDER) {
            drawSlider(root, node, alpha);
        } else if (node.type() == UiNodeType.PROGRESS_BAR) {
            drawProgressBar(root, node, alpha);
        } else if (node.type() == UiNodeType.TABS) {
            drawTabs(root, node, alpha);
        }
        if (isTextInput(node)) {
            drawTextSelection(root, node, alpha);
            UiTextFieldModel model = node.descriptor() instanceof UiTextFieldModel
                    ? (UiTextFieldModel) node.descriptor()
                    : null;
            if (node.focused() && model != null && !model.readOnly()) {
                drawTextInputCaret(root, node, alpha);
            }
        }
        if ((node.text() != null || isTextInput(node)) && !hasBitmapFont(root, node)) {
            drawNodeFallbackText(root, node, alpha);
        } else if (node.type() == UiNodeType.TABS && !hasBitmapFont(root, node)) {
            drawTabsFallbackText(root, node, alpha);
        }
        List<UiNode> children = root.renderChildren(node);
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (root.isOverlayNode(child)) {
                overlayNodes.add(child);
            } else {
                renderShapes(root, child, alpha, currentClip);
            }
        }
        if (node.type() == UiNodeType.SCROLL || node.type() == UiNodeType.TEXT_AREA) {
            drawScrollbars(root, node, alpha);
        }
        currentClip = previousClip;
    }

    private void renderImages(UiRoot root, UiNode node, float parentAlpha, UiRect inheritedClip) {
        if (!node.visible()) {
            return;
        }
        UiRect previousClip = currentClip;
        currentClip = nodeClip(node, inheritedClip);
        float alpha = combinedAlpha(parentAlpha, node.modifier().alpha());
        drawBackgroundImage(root, node, alpha);
        drawImage(root, node, alpha);
        if (node.type() == UiNodeType.CUSTOM) {
            renderCustomImages(root, node);
            renderCustomText(root, node);
        }
        if (node.text() != null || isTextInput(node)) {
            drawNodeBitmapText(root, node, alpha);
        } else if (node.type() == UiNodeType.TABS) {
            drawTabsBitmapText(root, node, alpha);
        }
        List<UiNode> children = root.renderChildren(node);
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!root.isOverlayNode(child)) {
                renderImages(root, child, alpha, currentClip);
            }
        }
        currentClip = previousClip;
    }

    private void drawBackground(UiRoot root, UiNode node, float alpha) {
        if (node.type() == UiNodeType.MODAL && node.descriptor() instanceof UiModal) {
            UiColor scrim = ((UiModal) node.descriptor()).scrimColor();
            if (scrim != null) {
                drawRect(root, node.bounds(), scrim.red(), scrim.green(), scrim.blue(), scrim.alpha() * alpha);
            }
            return;
        }
        UiStyle style = root.styleFor(node);
        UiDrawable background = style != null ? stateStyle(node, style).background() : defaultBackground(node);
        if (background == null || background.type() == UiDrawableType.NONE) {
            return;
        }
        if (background.type() == UiDrawableType.COLOR && background.color() != null) {
            UiColor color = background.color();
            drawRect(root, node.bounds(), color.red(), color.green(), color.blue(), color.alpha() * alpha);
        } else if (background.type() == UiDrawableType.NINE_PATCH && background.ninePatch() != null
                && background.ninePatch().region() == null) {
            drawRect(root, node.bounds(), 0.12f, 0.14f, 0.18f, alpha);
        }
        if (node.type() == UiNodeType.WINDOW) {
            drawWindowChrome(root, node, alpha);
        }
    }

    private Batch2D batch() {
        if (batch == null) {
            batch = new SpriteBatch(graphics);
        }
        return batch;
    }

    private void drawCheckbox(UiRoot root, UiNode node, float alpha) {
        UiRect box = checkboxBox(root, node);
        drawRect(root, box, 0.07f, 0.08f, 0.10f, alpha);
        drawRect(root, box.x(), box.y(), box.width(), 1.0f, 0.32f, 0.40f, 0.50f, alpha);
        drawRect(root, box.x(), box.bottom() - 1.0f, box.width(), 1.0f, 0.32f, 0.40f, 0.50f, alpha);
        drawRect(root, box.x(), box.y(), 1.0f, box.height(), 0.32f, 0.40f, 0.50f, alpha);
        drawRect(root, box.right() - 1.0f, box.y(), 1.0f, box.height(), 0.32f, 0.40f, 0.50f, alpha);
        if (node.checked()) {
            float inset = Math.max(2.0f, box.width() * 0.16f);
            drawRect(root, box.x() + inset, box.y() + inset,
                    Math.max(0.0f, box.width() - inset * 2.0f), Math.max(0.0f, box.height() - inset * 2.0f),
                    0.28f, 0.68f, 1.0f, alpha);
        }
    }

    private boolean needsBatch(UiRoot root, UiNode node) {
        if (node == null || !node.visible()) {
            return false;
        }
        if (node.image() != null || textureBackground(root, node)) {
            return true;
        }
        if (node.type() == UiNodeType.CUSTOM && node.customContext() != null
                && node.customContext().drawFunction() != null) {
            return true;
        }
        if ((node.text() != null || isTextInput(node) || node.type() == UiNodeType.TABS)
                && hasBitmapFont(root, node)) {
            return true;
        }
        List<UiNode> children = root.renderChildren(node);
        for (int i = 0; i < children.size(); i++) {
            UiNode child = children.get(i);
            if (!root.isOverlayNode(child) && needsBatch(root, child)) {
                return true;
            }
        }
        return false;
    }

    private boolean textureBackground(UiRoot root, UiNode node) {
        UiStyle style = root.styleFor(node);
        UiDrawable background = style != null ? stateStyle(node, style).background() : defaultBackground(node);
        if (node.type() == UiNodeType.MODAL && node.descriptor() instanceof UiModal) {
            return false;
        }
        return background != null
                && ((background.type() == UiDrawableType.TEXTURE && background.region() != null)
                || (background.type() == UiDrawableType.NINE_PATCH && background.ninePatch() != null
                && background.ninePatch().region() != null));
    }

    private UiRect checkboxBox(UiRoot root, UiNode node) {
        UiRect bounds = node.bounds();
        UiInsets padding = root.effectivePadding(node);
        float contentX = bounds.x() + padding.left();
        float contentY = bounds.y() + padding.top();
        float contentWidth = Math.max(0.0f, bounds.width() - padding.horizontal());
        float contentHeight = Math.max(0.0f, bounds.height() - padding.vertical());
        float boxSize = Math.max(0.0f, Math.min(contentWidth, contentHeight));
        float x = node.checkboxLabel()
                ? contentX
                : contentX + Math.max(0.0f, contentWidth - boxSize) * 0.5f;
        return node.rendererRect(0, x, contentY + Math.max(0.0f, contentHeight - boxSize) * 0.5f,
                boxSize, boxSize);
    }

    private void drawSlider(UiRoot root, UiNode node, float alpha) {
        UiRect bounds = node.bounds();
        float y = bounds.y() + bounds.height() * 0.5f - 2.0f;
        float trackX = bounds.x() + 8.0f;
        float trackWidth = Math.max(0.0f, bounds.width() - 16.0f);
        float trackHeight = 4.0f;
        boolean enabled = node.modifier().enabled();
        float sliderAlpha = enabled ? alpha : alpha * 0.55f;
        drawRect(root, trackX, y, trackWidth, trackHeight, 0.20f, 0.23f, 0.28f, sliderAlpha);
        float progress = sliderProgress(node);
        if (enabled) {
            drawRect(root, trackX, y, trackWidth * progress, trackHeight, 0.28f, 0.68f, 1.0f, sliderAlpha);
        } else {
            drawRect(root, trackX, y, trackWidth * progress, trackHeight, 0.36f, 0.43f, 0.50f, sliderAlpha);
        }
        float knobX = trackX + trackWidth * progress - 5.0f;
        if (enabled) {
            drawRect(root, knobX, y - 5.0f, 10.0f, 14.0f, 0.86f, 0.90f, 0.96f, sliderAlpha);
        } else {
            drawRect(root, knobX, y - 5.0f, 10.0f, 14.0f, 0.48f, 0.55f, 0.62f, sliderAlpha);
        }
    }

    private void drawProgressBar(UiRoot root, UiNode node, float alpha) {
        UiRect bounds = node.bounds().inset(root.effectivePadding(node));
        float trackHeight = Math.max(6.0f, Math.min(bounds.height(), 14.0f));
        float trackX = bounds.x();
        float trackY = bounds.y() + Math.max(0.0f, (bounds.height() - trackHeight) * 0.5f);
        float trackWidth = bounds.width();
        boolean enabled = node.modifier().enabled();
        float localAlpha = enabled ? alpha : alpha * 0.55f;
        drawRect(root, trackX, trackY, trackWidth, trackHeight, 0.18f, 0.22f, 0.28f, localAlpha);
        float progress = progressValue(node);
        if (enabled) {
            drawRect(root, trackX, trackY, trackWidth * progress, trackHeight, 0.39f, 0.80f, 0.58f, localAlpha);
        } else {
            drawRect(root, trackX, trackY, trackWidth * progress, trackHeight, 0.36f, 0.43f, 0.50f, localAlpha);
        }
        drawRect(root, trackX, trackY, trackWidth, 1.0f, 0.42f, 0.50f, 0.60f, localAlpha * 0.8f);
    }

    private void drawTabs(UiRoot root, UiNode node, float alpha) {
        int count = root.tabCount(node);
        if (count <= 0) {
            return;
        }
        UiRect content = node.bounds().inset(root.effectivePadding(node));
        boolean enabled = node.modifier().enabled();
        float localAlpha = enabled ? alpha : alpha * 0.55f;
        int active = root.tabActiveIndex(node);
        drawRect(root, content.x(), content.bottom() - 1.0f, content.width(), 1.0f,
                0.30f, 0.37f, 0.46f, localAlpha);
        for (int i = 0; i < count; i++) {
            UiRect tab = root.tabBounds(node, i);
            boolean selected = i == active;
            if (selected) {
                drawRect(root, tab, 0.16f, 0.28f, 0.36f, localAlpha);
            } else {
                drawRect(root, tab, 0.10f, 0.14f, 0.18f, localAlpha * 0.88f);
            }
            drawRect(root, tab.right() - 1.0f, tab.y() + 5.0f, 1.0f, Math.max(0.0f, tab.height() - 10.0f),
                    0.25f, 0.31f, 0.39f, localAlpha * 0.72f);
            if (selected) {
                drawRect(root, tab.x(), tab.y(), tab.width(), 2.0f,
                        0.31f, 0.70f, 1.0f, localAlpha);
                drawRect(root, tab.x(), tab.bottom() - 1.0f, tab.width(), 1.0f,
                        0.16f, 0.28f, 0.36f, localAlpha);
            }
        }
        if (node.focused()) {
            float focusAlpha = localAlpha * 0.9f;
            drawRect(root, content.x(), content.y(), content.width(), 1.0f, 0.50f, 0.72f, 1.0f, focusAlpha);
            drawRect(root, content.x(), content.bottom() - 1.0f, content.width(), 1.0f,
                    0.50f, 0.72f, 1.0f, focusAlpha);
            drawRect(root, content.x(), content.y(), 1.0f, content.height(), 0.50f, 0.72f, 1.0f, focusAlpha);
            drawRect(root, content.right() - 1.0f, content.y(), 1.0f, content.height(),
                    0.50f, 0.72f, 1.0f, focusAlpha);
        }
    }

    private void drawScrollbars(UiRoot root, UiNode node, float alpha) {
        UiScrollState state = node.scrollState();
        if (state == null) {
            return;
        }
        UiRect bounds = node.bounds().inset(root.effectivePadding(node));
        float trackAlpha = alpha * 0.72f;
        float thumbAlpha = alpha * 0.92f;
        if (state.canScrollY() && state.contentHeight() > 0.0f) {
            float width = 4.0f;
            float trackHeight = Math.max(0.0f, bounds.height());
            float thumbHeight = Math.max(22.0f, trackHeight * state.viewportHeight() / state.contentHeight());
            thumbHeight = Math.min(trackHeight, thumbHeight);
            float travel = Math.max(0.0f, trackHeight - thumbHeight);
            float y = bounds.y() + travel * state.y() / Math.max(1.0f, state.maxY());
            drawRect(root, bounds.right() - width, bounds.y(), width, trackHeight,
                    0.13f, 0.17f, 0.22f, trackAlpha);
            drawRect(root, bounds.right() - width, y, width, thumbHeight,
                    0.42f, 0.50f, 0.60f, thumbAlpha);
        }
        if (state.canScrollX() && state.contentWidth() > 0.0f) {
            float height = 4.0f;
            float trackWidth = Math.max(0.0f, bounds.width());
            float thumbWidth = Math.max(22.0f, trackWidth * state.viewportWidth() / state.contentWidth());
            thumbWidth = Math.min(trackWidth, thumbWidth);
            float travel = Math.max(0.0f, trackWidth - thumbWidth);
            float x = bounds.x() + travel * state.x() / Math.max(1.0f, state.maxX());
            drawRect(root, bounds.x(), bounds.bottom() - height, trackWidth, height,
                    0.13f, 0.17f, 0.22f, trackAlpha);
            drawRect(root, x, bounds.bottom() - height, thumbWidth, height,
                    0.42f, 0.50f, 0.60f, thumbAlpha);
        }
    }

    private void drawWindowChrome(UiRoot root, UiNode node, float alpha) {
        UiRect title = root.windowTitleBar(node);
        drawRect(root, title, 0.10f, 0.14f, 0.19f, alpha);
        drawRect(root, title.x(), title.bottom() - 1.0f, title.width(), 1.0f,
                0.25f, 0.33f, 0.42f, alpha);
        UiRect handle = root.windowResizeHandle(node);
        drawRect(root, handle.right() - 12.0f, handle.bottom() - 4.0f, 9.0f, 2.0f,
                0.42f, 0.54f, 0.68f, alpha);
        drawRect(root, handle.right() - 8.0f, handle.bottom() - 8.0f, 5.0f, 2.0f,
                0.42f, 0.54f, 0.68f, alpha);
        drawRect(root, handle.right() - 4.0f, handle.bottom() - 12.0f, 1.5f, 9.0f,
                0.42f, 0.54f, 0.68f, alpha);
    }

    private void drawTextSelection(UiRoot root, UiNode node, float alpha) {
        if (!(node.descriptor() instanceof UiTextFieldModel)) {
            return;
        }
        UiTextFieldModel model = (UiTextFieldModel) node.descriptor();
        if (!model.hasSelection()) {
            return;
        }
        UiTextStyle style = nodeTextStyle(root, node);
        UiRect bounds = nodeTextBounds(root, node);
        int start = model.selectionMin();
        int end = model.selectionMax();
        if (node.type() == UiNodeType.TEXT_AREA) {
            drawMultilineSelection(root, model.value(), start, end, bounds, style,
                    textAreaScrollX(node), textAreaScrollY(node), alpha);
        } else {
            String text = nodeText(node);
            int safeStart = Math.max(0, Math.min(start, text.length()));
            int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
            float startX = textWidth(root, text, 0, safeStart, style);
            float endX = textWidth(root, text, 0, safeEnd, style);
            float x = bounds.x() + startX;
            float width = Math.max(0.0f, endX - startX);
            float height = Math.min(bounds.height(), Math.max(12.0f, textLineHeight(root, style)));
            float y = bounds.y() + Math.max(0.0f, (bounds.height() - height) * 0.5f);
            drawRect(root, x, y, width, height, 0.25f, 0.50f, 0.90f, alpha * 0.65f);
        }
    }

    private void drawMultilineSelection(UiRoot root, String value, int start, int end, UiRect bounds,
            UiTextStyle style, float scrollX, float scrollY, float alpha) {
        String text = value != null ? value : "";
        float lineHeight = textLineHeight(root, style);
        int lineStart = 0;
        int lineIndex = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                int rangeStart = Math.max(start, lineStart);
                int rangeEnd = Math.min(end, i);
                if (rangeEnd > rangeStart || (start <= i && end > i && rangeStart == i)) {
                    int from = Math.max(lineStart, rangeStart);
                    int to = Math.max(from, Math.min(i, rangeEnd));
                    float startX = textWidth(root, text, lineStart, from, style);
                    float endX = textWidth(root, text, lineStart, to, style);
                    float x = bounds.x() + startX - scrollX;
                    float width = Math.max(3.0f, endX - startX);
                    float y = bounds.y() + lineIndex * lineHeight - scrollY;
                    drawRect(root, x, y, width, lineHeight, 0.25f, 0.50f, 0.90f, alpha * 0.65f);
                }
                lineStart = i + 1;
                lineIndex++;
            }
        }
    }

    private void drawTextInputCaret(UiRoot root, UiNode node, float alpha) {
        if (!(node.descriptor() instanceof UiTextFieldModel)) {
            return;
        }
        if (((int) (root.elapsedSeconds() * 2.0f)) % 2 != 0) {
            return;
        }
        drawRect(root, root.textCaretBounds(node, ((UiTextFieldModel) node.descriptor()).cursor()),
                0.85f, 0.92f, 1.0f, alpha);
    }

    private void drawCustom(final UiRoot root, UiNode node, float alpha) {
        UiCustomContext custom = node.customContext();
        if (custom == null || custom.drawFunction() == null) {
            return;
        }
        custom.drawFunction().draw(customDrawContext.configure(root, node, alpha), node.bounds());
    }

    private void queueCustomImage(UiNode node, TextureRegion region, float x, float y, float width, float height,
            UiColor color, float inheritedAlpha) {
        if (region == null || width <= 0.0f || height <= 0.0f || color == null || color.alpha() <= 0.0f) {
            return;
        }
        CustomImageDraw draw;
        if (customImageDrawCount == customImageDraws.size()) {
            draw = new CustomImageDraw();
            customImageDraws.add(draw);
        }
        else {
            draw = customImageDraws.get(customImageDrawCount);
        }
        customImageDrawCount++;
        draw.set(node, region, x, y, width, height, color.red(), color.green(), color.blue(),
                color.alpha() * inheritedAlpha);
    }

    private void renderCustomImages(UiRoot root, UiNode node) {
        for (int i = 0; i < customImageDrawCount; i++) {
            CustomImageDraw draw = customImageDraws.get(i);
            if (draw.node != node) {
                continue;
            }
            drawRegion(root, draw.region, draw.x, draw.y, draw.width, draw.height,
                    draw.red, draw.green, draw.blue, draw.alpha);
        }
    }

    private void renderCustomText(UiRoot root, UiNode node) {
        for (int i = 0; i < customTextDrawCount; i++) {
            CustomTextDraw draw = customTextDraws.get(i);
            if (draw.node != node) {
                continue;
            }
            BitmapFont font = root.textFont(draw.style);
            if (font != null) {
                drawBitmapText(root, font, draw.text, draw.node, draw.bounds, draw.style, draw.alpha);
            }
        }
    }

    private String nodeText(UiNode node) {
        String text = node.text();
        if (isTextInput(node)) {
            Object descriptor = node.descriptor();
            if (descriptor instanceof UiTextFieldModel) {
                UiTextFieldModel model = (UiTextFieldModel) descriptor;
                text = model.password() ? node.maskedText(model.value()) : model.value();
            } else {
                text = String.valueOf(node.value());
            }
        }
        return text;
    }

    private UiTextStyle nodeTextStyle(UiRoot root, UiNode node) {
        UiStyle style = root.styleFor(node);
        return style != null ? stateStyle(node, style).textStyle() : UiTextStyle.text();
    }

    private UiRect nodeTextBounds(UiRoot root, UiNode node) {
        UiInsets padding = root.effectivePadding(node);
        UiRect bounds = node.bounds();
        if (node.type() == UiNodeType.CHECKBOX) {
            UiRect box = checkboxBox(root, node);
            float textX = box.right() + 8.0f;
            return node.rendererRect(1, textX, bounds.y(), Math.max(0.0f, bounds.right() - textX), bounds.height());
        }
        if (node.type() == UiNodeType.WINDOW) {
            UiRect title = root.windowTitleBar(node);
            return node.rendererRect(1, title.x() + 12.0f, title.y(),
                    Math.max(0.0f, title.width() - 24.0f), title.height());
        }
        return node.rendererRect(1, bounds.x() + padding.left(), bounds.y() + padding.top(),
                bounds.width() - padding.horizontal(), bounds.height() - padding.vertical());
    }

    private boolean hasBitmapFont(UiRoot root, UiNode node) {
        return root.textFont(nodeTextStyle(root, node)) != null;
    }

    private void drawNodeBitmapText(UiRoot root, UiNode node, float alpha) {
        String text = nodeText(node);
        if (text == null || text.length() == 0) {
            return;
        }
        if (node.type() == UiNodeType.CHECKBOX && !node.checkboxLabel()) {
            return;
        }
        UiTextStyle textStyle = nodeTextStyle(root, node);
        BitmapFont font = root.textFont(textStyle);
        if (font == null) {
            return;
        }
        if (node.type() == UiNodeType.TEXT_AREA) {
            drawTextAreaBitmapText(root, font, text, nodeTextBounds(root, node), textStyle, alpha,
                    textAreaScrollX(node), textAreaScrollY(node));
            return;
        }
        drawBitmapText(root, font, text, node, nodeTextBounds(root, node), textStyle, alpha);
    }

    private void drawTabsBitmapText(UiRoot root, UiNode node, float alpha) {
        int count = root.tabCount(node);
        if (count <= 0) {
            return;
        }
        UiTextStyle style = nodeTextStyle(root, node);
        BitmapFont font = root.textFont(style);
        if (font == null) {
            return;
        }
        boolean enabled = node.modifier().enabled();
        int active = root.tabActiveIndex(node);
        float localAlpha = enabled ? alpha : alpha * 0.55f;
        float lineHeight = textLineHeight(root, style);
        for (int i = 0; i < count; i++) {
            String label = root.tabLabel(node, i);
            UiRect tab = root.tabBounds(node, i);
            float labelWidth = textWidth(root, label, style);
            float x = tab.x() + Math.max(0.0f, (tab.width() - labelWidth) * 0.5f);
            float y = tab.y() + Math.max(0.0f, (tab.height() - lineHeight) * 0.5f);
            float labelAlpha = i == active ? localAlpha : localAlpha * 0.72f;
            drawBitmapLine(root, font, label, x, y, style.size(), style.color(), labelAlpha);
        }
    }

    private void drawNodeFallbackText(UiRoot root, UiNode node, float alpha) {
        String text = nodeText(node);
        if (text == null || text.length() == 0) {
            return;
        }
        if (node.type() == UiNodeType.CHECKBOX && !node.checkboxLabel()) {
            return;
        }
        int length = text.length();
        long[] glyphRows = node.fallbackGlyphRows(text);
        if (glyphRows == null || glyphRows.length < length) {
            return;
        }
        UiTextStyle textStyle = nodeTextStyle(root, node);
        UiRect bounds = nodeTextBounds(root, node);
        UiColor color = textStyle.color() != null ? textStyle.color() : UiColor.WHITE;
        float textAlpha = color.alpha() * alpha;
        if (textAlpha <= 0.0f) {
            return;
        }
        float x = bounds.x();
        float y;
        if (node.type() == UiNodeType.TEXT_AREA) {
            x -= textAreaScrollX(node);
            y = bounds.y() - textAreaScrollY(node);
        } else {
            y = bounds.y() + Math.max(0.0f, (bounds.height() - 7.0f) * 0.5f);
        }
        float cursor = x;
        for (int i = 0; i < length; i++) {
            long rows = glyphRows[i];
            if (rows == FALLBACK_GLYPH_NEWLINE) {
                cursor = x;
                y += 10.0f;
                continue;
            }
            if (rows == FALLBACK_GLYPH_SPACE) {
                cursor += 5.0f;
                continue;
            }
            drawGlyphRows(root, rows, cursor, y, color.red(), color.green(), color.blue(), textAlpha);
            cursor += 7.0f;
        }
    }

    private void drawTabsFallbackText(UiRoot root, UiNode node, float alpha) {
        int count = root.tabCount(node);
        if (count <= 0) {
            return;
        }
        UiTextStyle style = nodeTextStyle(root, node);
        boolean enabled = node.modifier().enabled();
        int active = root.tabActiveIndex(node);
        float localAlpha = enabled ? alpha : alpha * 0.55f;
        for (int i = 0; i < count; i++) {
            String label = root.tabLabel(node, i);
            int labelLength = label != null ? label.length() : 0;
            long[] glyphRows = node.fallbackTabGlyphRows(i, label);
            if (labelLength <= 0 || glyphRows == null || glyphRows.length < labelLength) {
                continue;
            }
            UiRect tab = root.tabBounds(node, i);
            float labelWidth = labelLength * 7.0f;
            float x = tab.x() + Math.max(0.0f, (tab.width() - labelWidth) * 0.5f);
            float y = tab.y() + Math.max(0.0f, (tab.height() - 7.0f) * 0.5f);
            float labelAlpha = i == active ? localAlpha : localAlpha * 0.72f;
            UiColor color = style.color() != null ? style.color() : UiColor.WHITE;
            float textAlpha = color.alpha() * labelAlpha;
            if (textAlpha > 0.0f) {
                float cursor = x;
                for (int j = 0; j < labelLength; j++) {
                    long rows = glyphRows[j];
                    if (rows == FALLBACK_GLYPH_NEWLINE) {
                        continue;
                    }
                    if (rows == FALLBACK_GLYPH_SPACE) {
                        cursor += 5.0f;
                        continue;
                    }
                    drawGlyphRows(root, rows, cursor, y, color.red(), color.green(), color.blue(), textAlpha);
                    cursor += 7.0f;
                }
            }
        }
    }

    private void drawImage(UiRoot root, UiNode node, float alpha) {
        if (batch == null || node.image() == null) {
            return;
        }
        drawRegion(root, node.image(), node.bounds().x(), node.bounds().y(), node.bounds().width(),
                node.bounds().height(), 1.0f, 1.0f, 1.0f, alpha);
    }

    private void drawBackgroundImage(UiRoot root, UiNode node, float alpha) {
        UiStyle style = root.styleFor(node);
        UiDrawable background = style != null ? stateStyle(node, style).background() : null;
        if (background == null) {
            return;
        }
        if (background.type() == UiDrawableType.TEXTURE && background.region() != null) {
            drawRegion(root, background.region(), node.bounds().x(), node.bounds().y(), node.bounds().width(),
                    node.bounds().height(), 1.0f, 1.0f, 1.0f, alpha);
        } else if (background.type() == UiDrawableType.NINE_PATCH && background.ninePatch() != null
                && background.ninePatch().region() != null) {
            drawNinePatch(root, background.ninePatch(), node.bounds(), alpha);
        }
    }

    private void drawNinePatch(UiRoot root, UiNinePatch patch, UiRect bounds, float alpha) {
        TextureRegion source = patch.region();
        UiInsets split = patch.splits();
        int left = (int) Math.min(split.left(), bounds.width() * 0.5f);
        int right = (int) Math.min(split.right(), bounds.width() * 0.5f);
        int top = (int) Math.min(split.top(), bounds.height() * 0.5f);
        int bottom = (int) Math.min(split.bottom(), bounds.height() * 0.5f);
        int centerSourceWidth = Math.max(1, source.width() - left - right);
        int centerSourceHeight = Math.max(1, source.height() - top - bottom);
        float centerWidth = Math.max(0.0f, bounds.width() - left - right);
        float centerHeight = Math.max(0.0f, bounds.height() - top - bottom);

        drawPatchRegion(root, source, source.x(), source.y(), left, top,
                bounds.x(), bounds.y(), left, top, alpha);
        drawPatchRegion(root, source, source.x() + left, source.y(), centerSourceWidth, top,
                bounds.x() + left, bounds.y(), centerWidth, top, alpha);
        drawPatchRegion(root, source, source.x() + source.width() - right, source.y(), right, top,
                bounds.right() - right, bounds.y(), right, top, alpha);

        drawPatchRegion(root, source, source.x(), source.y() + top, left, centerSourceHeight,
                bounds.x(), bounds.y() + top, left, centerHeight, alpha);
        drawPatchRegion(root, source, source.x() + left, source.y() + top, centerSourceWidth, centerSourceHeight,
                bounds.x() + left, bounds.y() + top, centerWidth, centerHeight, alpha);
        drawPatchRegion(root, source, source.x() + source.width() - right, source.y() + top, right,
                centerSourceHeight, bounds.right() - right, bounds.y() + top, right, centerHeight, alpha);

        drawPatchRegion(root, source, source.x(), source.y() + source.height() - bottom, left, bottom,
                bounds.x(), bounds.bottom() - bottom, left, bottom, alpha);
        drawPatchRegion(root, source, source.x() + left, source.y() + source.height() - bottom, centerSourceWidth,
                bottom, bounds.x() + left, bounds.bottom() - bottom, centerWidth, bottom, alpha);
        drawPatchRegion(root, source, source.x() + source.width() - right, source.y() + source.height() - bottom,
                right, bottom, bounds.right() - right, bounds.bottom() - bottom, right, bottom, alpha);
    }

    private void drawPatchRegion(UiRoot root, TextureRegion source, int x, int y, int width, int height,
            float boundsX, float boundsY, float boundsWidth, float boundsHeight, float alpha) {
        if (width <= 0 || height <= 0 || boundsWidth <= 0.0f || boundsHeight <= 0.0f) {
            return;
        }
        drawRegion(root, source.texture(), x, y, width, height, boundsX, boundsY, boundsWidth, boundsHeight,
                1.0f, 1.0f, 1.0f, alpha);
    }

    private void drawRegion(UiRoot root, TextureRegion region, UiRect bounds) {
        drawRegion(root, region, bounds, UiColor.WHITE);
    }

    private void drawRegion(UiRoot root, TextureRegion region, UiRect bounds, UiColor color) {
        if (bounds == null) {
            return;
        }
        drawRegion(root, region, bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
    }

    private void drawRegion(UiRoot root, TextureRegion region, float x, float y, float width, float height,
            UiColor color) {
        if (region == null || width <= 0.0f || height <= 0.0f) {
            return;
        }
        UiColor finalColor = color != null ? color : UiColor.WHITE;
        if (finalColor.alpha() <= 0.0f) {
            return;
        }
        drawRegion(root, region, x, y, width, height, finalColor.red(), finalColor.green(), finalColor.blue(),
                finalColor.alpha());
    }

    private void drawRegion(UiRoot root, TextureRegion region, float x, float y, float width, float height,
            float red, float green, float blue, float alpha) {
        if (region == null) {
            return;
        }
        drawRegion(root, region.texture(), region.x(), region.y(), region.width(), region.height(),
                x, y, width, height, red, green, blue, alpha);
    }

    private void drawRegion(UiRoot root, Texture texture, int sourceX, int sourceY, int sourceWidth,
            int sourceHeight, float x, float y, float width, float height,
            float red, float green, float blue, float alpha) {
        if (texture == null || sourceWidth <= 0 || sourceHeight <= 0
                || width <= 0.0f || height <= 0.0f || alpha <= 0.0f) {
            return;
        }
        float clippedX = x;
        float clippedY = y;
        float clippedRight = x + width;
        float clippedBottom = y + height;
        if (currentClip != null) {
            clippedX = Math.max(currentClip.x(), clippedX);
            clippedY = Math.max(currentClip.y(), clippedY);
            clippedRight = Math.min(currentClip.right(), clippedRight);
            clippedBottom = Math.min(currentClip.bottom(), clippedBottom);
            if (clippedRight <= clippedX || clippedBottom <= clippedY) {
                return;
            }
        }
        int clippedSourceX = sourceX;
        int clippedSourceY = sourceY;
        int clippedSourceRight = sourceX + sourceWidth;
        int clippedSourceBottom = sourceY + sourceHeight;
        float clippedWidth = clippedRight - clippedX;
        float clippedHeight = clippedBottom - clippedY;
        if (clippedX != x || clippedY != y || clippedWidth != width || clippedHeight != height) {
            float left = Math.max(0.0f, clippedX - x);
            float top = Math.max(0.0f, clippedY - y);
            float right = Math.max(0.0f, x + width - clippedRight);
            float bottom = Math.max(0.0f, y + height - clippedBottom);
            clippedSourceX += Math.round(left / width * sourceWidth);
            clippedSourceY += Math.round(top / height * sourceHeight);
            clippedSourceRight -= Math.round(right / width * sourceWidth);
            clippedSourceBottom -= Math.round(bottom / height * sourceHeight);
        }
        batch.color(red, green, blue, alpha);
        batch.draw(texture, clippedSourceX, clippedSourceY,
                Math.max(1, clippedSourceRight - clippedSourceX),
                Math.max(1, clippedSourceBottom - clippedSourceY),
                ndcX(root, clippedX), ndcY(root, clippedY, clippedHeight),
                ndcWidth(root, clippedRight - clippedX), ndcHeight(root, clippedBottom - clippedY));
    }

    private void drawRect(UiRoot root, UiRect rect, UiColor color) {
        if (rect == null) {
            return;
        }
        drawRect(root, rect.x(), rect.y(), rect.width(), rect.height(), color);
    }

    private void drawRect(UiRoot root, UiRect rect, float red, float green, float blue, float alpha) {
        if (rect == null) {
            return;
        }
        drawRect(root, rect.x(), rect.y(), rect.width(), rect.height(), red, green, blue, alpha);
    }

    private void drawRect(UiRoot root, float x, float y, float width, float height, UiColor color) {
        if (color == null) {
            return;
        }
        drawRect(root, x, y, width, height, color.red(), color.green(), color.blue(), color.alpha());
    }

    private void drawRect(UiRoot root, float x, float y, float width, float height, float red, float green, float blue,
            float alpha) {
        if (alpha <= 0.0f || width <= 0.0f || height <= 0.0f) {
            return;
        }
        float clippedX = x;
        float clippedY = y;
        float clippedRight = x + width;
        float clippedBottom = y + height;
        if (currentClip != null) {
            clippedX = Math.max(currentClip.x(), clippedX);
            clippedY = Math.max(currentClip.y(), clippedY);
            clippedRight = Math.min(currentClip.right(), clippedRight);
            clippedBottom = Math.min(currentClip.bottom(), clippedBottom);
            if (clippedRight <= clippedX || clippedBottom <= clippedY) {
                return;
            }
        }
        float clippedWidth = clippedRight - clippedX;
        float clippedHeight = clippedBottom - clippedY;
        shapes.filledRect(ndcX(root, clippedX), ndcY(root, clippedY, clippedHeight), ndcWidth(root, clippedWidth),
                ndcHeight(root, clippedHeight),
                red, green, blue, alpha);
    }

    private UiRect nodeClip(UiNode node, UiRect inheritedClip) {
        UiRect clip = inheritedClip;
        if (node.type() == UiNodeType.SCROLL || node.type() == UiNodeType.TEXT_FIELD
                || node.type() == UiNodeType.TEXT_AREA || node.type() == UiNodeType.TABS) {
            clip = intersect(node, clip, node.bounds());
        }
        return clip;
    }

    private UiRect intersect(UiNode node, UiRect clip, UiRect rect) {
        if (rect == null || rect.width() <= 0.0f || rect.height() <= 0.0f) {
            return null;
        }
        if (clip == null) {
            return rect;
        }
        float x = Math.max(clip.x(), rect.x());
        float y = Math.max(clip.y(), rect.y());
        float right = Math.min(clip.right(), rect.right());
        float bottom = Math.min(clip.bottom(), rect.bottom());
        if (right <= x || bottom <= y) {
            return null;
        }
        return node.rendererRect(2, x, y, right - x, bottom - y);
    }

    private void renderDebugLines(UiRoot root, UiNode node) {
        if (!node.visible()) {
            return;
        }
        drawOutline(root, node.bounds(), debugColor(node));
        if (node.type() == UiNodeType.WINDOW) {
            drawOutline(root, root.windowTitleBar(node), DEBUG_WINDOW_TITLE);
            drawOutline(root, root.windowResizeHandle(node), DEBUG_WINDOW_RESIZE);
        }
        List<UiNode> children = root.renderChildren(node);
        for (int i = 0; i < children.size(); i++) {
            renderDebugLines(root, children.get(i));
        }
    }

    private void drawOutline(UiRoot root, UiRect rect, UiColor color) {
        if (rect == null || rect.width() <= 0.0f || rect.height() <= 0.0f || color == null || color.alpha() <= 0.0f) {
            return;
        }
        float scale = root.effectiveUiScale();
        float pixel = 1.0f / Math.max(0.25f, scale);
        float maxRight = root.renderWidth() / Math.max(0.25f, scale);
        float maxBottom = root.renderHeight() / Math.max(0.25f, scale);
        float edgeTolerance = pixel * 0.5f;
        float x = rect.x();
        float y = rect.y();
        float right = rect.right();
        float bottom = rect.bottom();
        if (x <= edgeTolerance) {
            x += pixel;
        }
        if (y <= edgeTolerance) {
            y += pixel;
        }
        if (right >= maxRight - edgeTolerance) {
            right -= pixel;
        }
        if (bottom >= maxBottom - edgeTolerance) {
            bottom -= pixel;
        }
        float width = Math.max(0.0f, right - x);
        float height = Math.max(0.0f, bottom - y);
        shapes.rect(ndcX(root, x), ndcY(root, y, height), ndcWidth(root, width), ndcHeight(root, height),
                color.red(), color.green(), color.blue(), color.alpha());
    }

    private UiColor debugColor(UiNode node) {
        if (node.invalid()) {
            return DEBUG_INVALID;
        }
        if (node.focused()) {
            return DEBUG_FOCUSED;
        }
        if (node.hovered()) {
            return DEBUG_HOVERED;
        }
        if (node.type() == UiNodeType.WINDOW) {
            return DEBUG_WINDOW;
        }
        if (node.type() == UiNodeType.BUTTON || node.type() == UiNodeType.CHECKBOX
                || node.type() == UiNodeType.SLIDER || node.type() == UiNodeType.PROGRESS_BAR
                || node.type() == UiNodeType.TABS || node.type() == UiNodeType.TEXT_FIELD
                || node.type() == UiNodeType.TEXT_AREA) {
            return DEBUG_CONTROL;
        }
        if (node.type() == UiNodeType.MODAL || node.type() == UiNodeType.POPUP || node.type() == UiNodeType.TOOLTIP) {
            return DEBUG_OVERLAY;
        }
        return DEBUG_DEFAULT;
    }

    private void drawTextAreaBitmapText(UiRoot root, BitmapFont font, String text, UiRect bounds, UiTextStyle style,
            float alpha, float scrollX, float scrollY) {
        float x = bounds.x() - scrollX;
        float y = bounds.y() - scrollY;
        float lineHeight = textLineHeight(root, style);
        int start = 0;
        int lineIndex = 0;
        String value = text != null ? text : "";
        for (int i = 0; i <= value.length(); i++) {
            if (i == value.length() || value.charAt(i) == '\n') {
                drawBitmapLine(root, font, value, start, i, x,
                        y + lineIndex * lineHeight, style.size(), style.color(), alpha);
                start = i + 1;
                lineIndex++;
            }
        }
    }

    private void drawBitmapText(UiRoot root, BitmapFont font, String text, UiNode node, UiRect bounds, UiTextStyle style,
            float alpha) {
        float maxWidth = style.wrap() || style.ellipsis() ? bounds.width() : 0.0f;
        BitmapFontLayout layout = root.textLayout(node, text, style, maxWidth);
        if (layout == null) {
            return;
        }
        float y = bounds.y() + Math.max(0.0f, (bounds.height() - layout.height()) * 0.5f);
        if (style.shadowColor().alpha() > 0.0f && style.shadowOffset() != 0.0f) {
            drawBitmapTextLines(root, font, layout, bounds.x() + style.shadowOffset(),
                    y + style.shadowOffset(), style, style.shadowColor(), alpha, bounds.width());
        }
        if (style.outlineColor().alpha() > 0.0f && style.outlineWidth() > 0.0f) {
            float outline = style.outlineWidth();
            drawBitmapTextLines(root, font, layout, bounds.x() - outline, y, style,
                    style.outlineColor(), alpha, bounds.width());
            drawBitmapTextLines(root, font, layout, bounds.x() + outline, y, style,
                    style.outlineColor(), alpha, bounds.width());
            drawBitmapTextLines(root, font, layout, bounds.x(), y - outline, style,
                    style.outlineColor(), alpha, bounds.width());
            drawBitmapTextLines(root, font, layout, bounds.x(), y + outline, style,
                    style.outlineColor(), alpha, bounds.width());
        }
        drawBitmapTextLines(root, font, layout, bounds.x(), y, style, style.color(), alpha, bounds.width());
    }

    private void drawBitmapTextLines(UiRoot root, BitmapFont font, BitmapFontLayout layout, float x, float y,
            UiTextStyle style, UiColor color, float alpha, float availableWidth) {
        List<String> lines = layout.lines();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            float lineX = alignedLineX(x, layout.lineWidth(lineIndex), style.align(), availableWidth);
            drawBitmapLine(root, font, line, lineX, y + lineIndex * layout.lineHeight(), style.size(), color, alpha);
        }
    }

    private void drawBitmapLine(UiRoot root, BitmapFont font, String text, float x, float y, float size,
            UiColor color, float alpha) {
        drawBitmapLine(root, font, text, 0, text != null ? text.length() : 0, x, y, size, color, alpha);
    }

    private void drawBitmapLine(UiRoot root, BitmapFont font, String text, int start, int end, float x, float y,
            float size, UiColor color, float alpha) {
        if (text == null || color == null || color.alpha() <= 0.0f || alpha <= 0.0f) {
            return;
        }
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        float scale = font.scale(size);
        float displayScale = root.effectiveUiScale();
        float cursor = x;
        int previous = -1;
        for (int i = safeStart; i < safeEnd; i++) {
            int codePoint = text.charAt(i);
            BitmapFontGlyph glyph = font.glyph(codePoint);
            if (glyph == null) {
                cursor += size * 0.5f;
                previous = -1;
                continue;
            }
            if (previous >= 0) {
                cursor += font.kerning(previous, codePoint) * scale;
            }
            TextureRegion region = glyph.region();
            float glyphX = snapToDisplayPixel(cursor + glyph.xOffset() * scale, displayScale);
            float glyphY = snapToDisplayPixel(y + glyph.yOffset() * scale, displayScale);
            float glyphWidth = snapSizeToDisplayPixel(region.width() * scale, displayScale);
            float glyphHeight = snapSizeToDisplayPixel(region.height() * scale, displayScale);
            drawRegion(root, region, glyphX, glyphY, glyphWidth, glyphHeight,
                    color.red(), color.green(), color.blue(), color.alpha() * alpha);
            cursor += glyph.xAdvance() * scale;
            previous = codePoint;
        }
    }

    private float snapToDisplayPixel(float value, float displayScale) {
        float scale = Math.max(0.25f, displayScale);
        return Math.round(value * scale) / scale;
    }

    private float snapSizeToDisplayPixel(float value, float displayScale) {
        if (value <= 0.0f) {
            return value;
        }
        float scale = Math.max(0.25f, displayScale);
        return Math.max(1.0f / scale, Math.round(value * scale) / scale);
    }

    private float alignedLineX(float x, float lineWidth, UiTextAlign align, float availableWidth) {
        if (align == UiTextAlign.CENTER) {
            return x + (availableWidth - lineWidth) * 0.5f;
        }
        if (align == UiTextAlign.END) {
            return x + availableWidth - lineWidth;
        }
        return x;
    }

    private void drawGlyphRows(UiRoot root, long rows, float x, float y, float red, float green, float blue,
            float alpha) {
        for (int row = 0; row < FALLBACK_GLYPH_ROWS; row++) {
            int bits = glyphRow(rows, row);
            int column = 0;
            while (column < FALLBACK_GLYPH_COLUMNS) {
                while (column < FALLBACK_GLYPH_COLUMNS && !glyphColumnSet(bits, column)) {
                    column++;
                }
                int start = column;
                while (column < FALLBACK_GLYPH_COLUMNS && glyphColumnSet(bits, column)) {
                    column++;
                }
                if (column > start) {
                    drawRect(root, x + start, y + row, column - start, 1.0f, red, green, blue, alpha);
                }
            }
        }
    }

    static long fallbackGlyphRows(char c) {
        char glyph = fallbackGlyphChar(c);
        if (glyph == '\n') {
            return FALLBACK_GLYPH_NEWLINE;
        }
        if (glyph == ' ') {
            return FALLBACK_GLYPH_SPACE;
        }
        return glyph(glyph);
    }

    private static char fallbackGlyphChar(char c) {
        if (c >= 'a' && c <= 'z') {
            return (char) (c - ('a' - 'A'));
        }
        return c;
    }

    private static long glyph(char c) {
        switch (c) {
            case 'A': return rows(14, 17, 17, 31, 17, 17, 17);
            case 'B': return rows(30, 17, 17, 30, 17, 17, 30);
            case 'C': return rows(14, 17, 16, 16, 16, 17, 14);
            case 'D': return rows(30, 17, 17, 17, 17, 17, 30);
            case 'E': return rows(31, 16, 16, 30, 16, 16, 31);
            case 'F': return rows(31, 16, 16, 30, 16, 16, 16);
            case 'G': return rows(14, 17, 16, 23, 17, 17, 14);
            case 'H': return rows(17, 17, 17, 31, 17, 17, 17);
            case 'I': return rows(14, 4, 4, 4, 4, 4, 14);
            case 'J': return rows(7, 2, 2, 2, 18, 18, 12);
            case 'K': return rows(17, 18, 20, 24, 20, 18, 17);
            case 'L': return rows(16, 16, 16, 16, 16, 16, 31);
            case 'M': return rows(17, 27, 21, 21, 17, 17, 17);
            case 'N': return rows(17, 25, 21, 19, 17, 17, 17);
            case 'O': return rows(14, 17, 17, 17, 17, 17, 14);
            case 'P': return rows(30, 17, 17, 30, 16, 16, 16);
            case 'Q': return rows(14, 17, 17, 17, 21, 18, 13);
            case 'R': return rows(30, 17, 17, 30, 20, 18, 17);
            case 'S': return rows(15, 16, 16, 14, 1, 1, 30);
            case 'T': return rows(31, 4, 4, 4, 4, 4, 4);
            case 'U': return rows(17, 17, 17, 17, 17, 17, 14);
            case 'V': return rows(17, 17, 17, 17, 17, 10, 4);
            case 'W': return rows(17, 17, 17, 21, 21, 21, 10);
            case 'X': return rows(17, 17, 10, 4, 10, 17, 17);
            case 'Y': return rows(17, 17, 10, 4, 4, 4, 4);
            case 'Z': return rows(31, 1, 2, 4, 8, 16, 31);
            case '0': return rows(14, 17, 19, 21, 25, 17, 14);
            case '1': return rows(4, 12, 4, 4, 4, 4, 14);
            case '2': return rows(14, 17, 1, 2, 4, 8, 31);
            case '3': return rows(30, 1, 1, 14, 1, 1, 30);
            case '4': return rows(2, 6, 10, 18, 31, 2, 2);
            case '5': return rows(31, 16, 16, 30, 1, 1, 30);
            case '6': return rows(14, 16, 16, 30, 17, 17, 14);
            case '7': return rows(31, 1, 2, 4, 8, 8, 8);
            case '8': return rows(14, 17, 17, 14, 17, 17, 14);
            case '9': return rows(14, 17, 17, 15, 1, 1, 14);
            case ':': return rows(0, 4, 4, 0, 4, 4, 0);
            case '.': return rows(0, 0, 0, 0, 0, 12, 12);
            case '-': return rows(0, 0, 0, 31, 0, 0, 0);
            case '_': return rows(0, 0, 0, 0, 0, 0, 31);
            case '/': return rows(1, 2, 2, 4, 8, 8, 16);
            default: return UNKNOWN_FALLBACK_GLYPH;
        }
    }

    private static int glyphRow(long rows, int row) {
        int shift = (FALLBACK_GLYPH_ROWS - 1 - row) * FALLBACK_GLYPH_COLUMNS;
        return (int) ((rows >> shift) & 31L);
    }

    private static boolean glyphColumnSet(int bits, int column) {
        return (bits & (1 << (FALLBACK_GLYPH_COLUMNS - 1 - column))) != 0;
    }

    private static long rows(int a, int b, int c, int d, int e, int f, int g) {
        return ((long) a << 30)
                | ((long) b << 25)
                | ((long) c << 20)
                | ((long) d << 15)
                | ((long) e << 10)
                | ((long) f << 5)
                | (long) g;
    }

    private UiDrawable defaultBackground(UiNode node) {
        if (node.type() == UiNodeType.BUTTON) {
            if (!node.modifier().enabled()) {
                return BUTTON_DISABLED_BACKGROUND;
            }
            return node.pressed() ? BUTTON_PRESSED_BACKGROUND
                    : node.hovered() ? BUTTON_HOVERED_BACKGROUND : BUTTON_BACKGROUND;
        }
        if (node.type() == UiNodeType.PANEL) {
            return PANEL_BACKGROUND;
        }
        if (node.type() == UiNodeType.WINDOW) {
            return WINDOW_BACKGROUND;
        }
        if (node.type() == UiNodeType.TEXT_FIELD || node.type() == UiNodeType.TEXT_AREA) {
            return node.focused() ? TEXT_INPUT_FOCUSED_BACKGROUND : TEXT_INPUT_BACKGROUND;
        }
        if (node.type() == UiNodeType.TABS) {
            return TABS_BACKGROUND;
        }
        return UiDrawable.none();
    }

    private UiStyle stateStyle(UiNode node, UiStyle style) {
        if (!node.modifier().enabled() && style.disabled() != null) {
            return style.disabled();
        }
        if (node.pressed() && style.pressed() != null) {
            return style.pressed();
        }
        if (node.hovered() && style.hover() != null) {
            return style.hover();
        }
        if (node.focused() && style.focused() != null) {
            return style.focused();
        }
        return style;
    }

    private float sliderProgress(UiNode node) {
        Object descriptor = node.descriptor();
        if (!(descriptor instanceof UiSliderModel)) {
            return 0.0f;
        }
        UiRange range = ((UiSliderModel) descriptor).range();
        float span = Math.max(0.0001f, range.maximum() - range.minimum());
        return (range.clamp(node.floatValue()) - range.minimum()) / span;
    }

    private float progressValue(UiNode node) {
        Object descriptor = node.descriptor();
        if (!(descriptor instanceof UiProgressBarModel)) {
            return Math.max(0.0f, Math.min(1.0f, node.floatValue()));
        }
        UiRange range = ((UiProgressBarModel) descriptor).range();
        float span = Math.max(0.0001f, range.maximum() - range.minimum());
        return (range.clamp(node.floatValue()) - range.minimum()) / span;
    }

    private float textAreaScrollY(UiNode node) {
        if (node.scrollState() != null) {
            return node.scrollState().y();
        }
        return 0.0f;
    }

    private float textAreaScrollX(UiNode node) {
        if (node.scrollState() != null) {
            return node.scrollState().x();
        }
        return 0.0f;
    }

    private int lineStart(String value, int targetLine) {
        String text = value != null ? value : "";
        if (targetLine <= 0) {
            return 0;
        }
        int line = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                if (line == targetLine) {
                    return i + 1;
                }
            }
        }
        return text.length();
    }

    private int lineEnd(String value, int start) {
        String text = value != null ? value : "";
        int index = Math.max(0, Math.min(start, text.length()));
        while (index < text.length() && text.charAt(index) != '\n') {
            index++;
        }
        return index;
    }

    private int lineIndexForOffset(String value, int offset) {
        String text = value != null ? value : "";
        int clamped = Math.max(0, Math.min(offset, text.length()));
        int line = 0;
        for (int i = 0; i < clamped; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private float textLineHeight(UiRoot root, UiTextStyle style) {
        UiTextStyle actual = style != null ? style : UiTextStyle.text();
        BitmapFont font = root.textFont(actual);
        if (font != null) {
            return Math.max(actual.lineHeight(), font.lineHeight(actual.size()));
        }
        return Math.max(10.0f, actual.lineHeight());
    }

    private float textWidth(UiRoot root, String text, UiTextStyle style) {
        return textWidth(root, text, 0, text != null ? text.length() : 0, style);
    }

    private float textWidth(UiRoot root, String text, int start, int end, UiTextStyle style) {
        UiTextStyle actual = style != null ? style : UiTextStyle.text();
        int safeStart = text != null ? Math.max(0, Math.min(start, text.length())) : 0;
        int safeEnd = text != null ? Math.max(safeStart, Math.min(end, text.length())) : 0;
        BitmapFont font = root.textFont(actual);
        if (font != null) {
            float scale = font.scale(actual.size());
            float width = 0.0f;
            int previous = -1;
            for (int i = safeStart; i < safeEnd; i++) {
                int codePoint = text.charAt(i);
                BitmapFontGlyph glyph = font.glyph(codePoint);
                if (glyph == null) {
                    width += actual.size() * 0.5f;
                    previous = -1;
                    continue;
                }
                if (previous >= 0) {
                    width += font.kerning(previous, codePoint) * scale;
                }
                width += glyph.xAdvance() * scale;
                previous = codePoint;
            }
            return width;
        }
        return (safeEnd - safeStart) * 8.0f;
    }

    private boolean isTextInput(UiNode node) {
        return node != null && (node.type() == UiNodeType.TEXT_FIELD || node.type() == UiNodeType.TEXT_AREA);
    }

    private float combinedAlpha(float parentAlpha, float localAlpha) {
        return Math.max(0.0f, Math.min(1.0f, parentAlpha * localAlpha));
    }

    private float ndcX(UiRoot root, UiRect rect) {
        return ndcX(root, rect.x());
    }

    private float ndcX(UiRoot root, float x) {
        float width = Math.max(1.0f, root.renderWidth());
        float scale = root.effectiveUiScale();
        return x * scale / width * 2.0f - 1.0f;
    }

    private float ndcY(UiRoot root, UiRect rect) {
        return ndcY(root, rect.y(), rect.height());
    }

    private float ndcY(UiRoot root, float y, float heightValue) {
        float height = Math.max(1.0f, root.renderHeight());
        float scale = root.effectiveUiScale();
        return 1.0f - (y + heightValue) * scale / height * 2.0f;
    }

    private float ndcWidth(UiRoot root, UiRect rect) {
        return ndcWidth(root, rect.width());
    }

    private float ndcWidth(UiRoot root, float widthValue) {
        float width = Math.max(1.0f, root.renderWidth());
        return widthValue * root.effectiveUiScale() / width * 2.0f;
    }

    private float ndcHeight(UiRoot root, UiRect rect) {
        return ndcHeight(root, rect.height());
    }

    private float ndcHeight(UiRoot root, float heightValue) {
        float height = Math.max(1.0f, root.renderHeight());
        return heightValue * root.effectiveUiScale() / height * 2.0f;
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
        resetCustomDraws();
        customTextDraws.clear();
        customImageDraws.clear();
        if (shapes != null) {
            shapes.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
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


    /**
     * Represents a custom image draw.
     *
     * @author xpenatan
     */
    private static final class CustomImageDraw {
        UiNode node;
        TextureRegion region;
        float x;
        float y;
        float width;
        float height;
        float red;
        float green;
        float blue;
        float alpha;

        void set(UiNode node, TextureRegion region, float x, float y, float width, float height,
                float red, float green, float blue, float alpha) {
            this.node = node;
            this.region = region;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }

        void clear() {
            node = null;
            region = null;
        }
    }

    /**
     * Represents a custom text draw.
     *
     * @author xpenatan
     */
    private static final class CustomTextDraw {
        UiNode node;
        String text;
        UiRect bounds;
        UiTextStyle style;
        float alpha;

        void set(UiNode node, String text, UiRect bounds, UiTextStyle style, float alpha) {
            this.node = node;
            this.text = text;
            this.bounds = bounds;
            this.style = style;
            this.alpha = alpha;
        }

        void clear() {
            node = null;
            text = null;
            bounds = null;
            style = null;
        }
    }

    private void queueCustomText(UiNode node, String text, UiRect bounds, UiTextStyle style, float alpha) {
        CustomTextDraw draw;
        if (customTextDrawCount == customTextDraws.size()) {
            draw = new CustomTextDraw();
            customTextDraws.add(draw);
        }
        else {
            draw = customTextDraws.get(customTextDrawCount);
        }
        customTextDrawCount++;
        draw.set(node, text, bounds, style, alpha);
    }

    private void resetCustomDraws() {
        for (int i = 0; i < customTextDrawCount; i++) {
            customTextDraws.get(i).clear();
        }
        for (int i = 0; i < customImageDrawCount; i++) {
            customImageDraws.get(i).clear();
        }
        customTextDrawCount = 0;
        customImageDrawCount = 0;
    }

    private final class CustomDrawContext implements UiDrawContext {
        private UiRoot root;
        private UiNode node;
        private float alpha;

        CustomDrawContext configure(UiRoot root, UiNode node, float alpha) {
            this.root = root;
            this.node = node;
            this.alpha = alpha;
            return this;
        }

        @Override
        public void rect(UiRect bounds, UiColor color) {
            if (color != null) {
                drawRect(root, bounds, color.red(), color.green(), color.blue(), color.alpha() * alpha);
            }
        }

        @Override
        public void rect(float x, float y, float width, float height, UiColor color) {
            if (color != null) {
                drawRect(root, x, y, width, height,
                        color.red(), color.green(), color.blue(), color.alpha() * alpha);
            }
        }

        @Override
        public void image(TextureRegion region, UiRect bounds, UiColor color) {
            if (bounds != null) {
                queueCustomImage(node, region, bounds.x(), bounds.y(), bounds.width(), bounds.height(), color, alpha);
            }
        }

        @Override
        public void image(TextureRegion region, float x, float y, float width, float height, UiColor color) {
            queueCustomImage(node, region, x, y, width, height, color, alpha);
        }

        @Override
        public void text(String text, UiRect bounds, UiTextStyle style) {
            UiTextStyle actualStyle = style != null ? style : UiTextStyle.text();
            queueCustomText(node, text, bounds, actualStyle, alpha);
        }
    }
}
