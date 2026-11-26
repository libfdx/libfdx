package io.github.libfdx.tests.utils.shared;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.Fdx;
import io.github.libfdx.input.Input;
import io.github.libfdx.ui.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.graphics.shader.runtime.ShaderSkippedDraws;
import java.nio.ByteBuffer;

/** Small application-owned overlay; the font is borrowed from the scene's asset scope. */
public final class ShowcaseHud implements Disposable {
    private final SpriteBatch batch;
    private final UiRoot root;
    private RenderPass pass;
    private final List<Rectangle> panels = new ArrayList<>(), marks = new ArrayList<>();
    private int panelCount, markCount, previousPanelCount;
    private static final class Rectangle {
        float x, y, width, height;
        UiColor color;

        boolean set(float x, float y, float w, float h, float r, float g, float b, float a) {
            boolean changed = this.x != x || this.y != y || width != w || height != h;
            this.x = x;
            this.y = y;
            width = w;
            height = h;
            if (color == null || color.red() != r || color.green() != g || color.blue() != b || color.alpha() != a) {
                color = UiColor.rgba(r, g, b, a);
                changed = true;
            }
            return changed;
        }

    }

    private static final class Meter {
        float x, y, width, height;
        final UiFloatState value = Ui.state(0f);
    }

    private int meterCount, previousMeterCount;
    private final List<Meter> meters = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private int labelCount, previousCount, width, height;
    private float scale, insetX, insetY;
    private UiContent controls;
    private UiFont uiFont;
    private int controlsVersion;
    private static final class Label {
        String text;
        float x, y, size, red, green, blue;
    }

    private Texture white;
    private BitmapFont font;
    private float sx, sy, left, top;
    private boolean disposed;

    public ShowcaseHud(Fdx fdx) {
        this(fdx, null);
    }

    /** The optional preparation/plan in this configuration is borrowed by the HUD batch. */
    public ShowcaseHud(Fdx fdx, SpriteBatchConfig configuration) {
        GraphicsContext graphics = fdx.graphics().main();
        batch = configuration == null ? null : new SpriteBatch(graphics, configuration);
        root = new UiToolkit(fdx.files()).root(fdx.displays().main(), graphics).autoUiScale(false);
        root.setContent(this::build);

        if (batch == null) return;
        try {
            white=graphics.device().createTexture(TextureDescriptor.rgba8("gallery solid",1,1));
            ByteBuffer pixel=ByteBuffer.allocateDirect(4);
            pixel.putInt(-1).flip();
            graphics.device().writeTexture(white,pixel);
        } catch (RuntimeException | Error failure) {
            try { dispose(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public void font(BitmapFont font) {
        if (this.font == font) return;
        this.font = font;
        uiFont = font == null ? null : UiFont.bitmap(font);
        UiTheme theme = root.theme();
        UiStyle toggle = theme.style("switch");
        root.theme(theme.toggleSwitch(toggle.text(toggle.textStyle().font(uiFont))));
        root.requestCompose();
    }

    public void controls(Input input, UiContent controls) {
        this.controls = controls;
        root.input(input);
        root.requestCompose();
    }

    public void controlsVersion(int version) {
        if (controlsVersion != version) {
            controlsVersion = version;
            root.requestCompose();
        }
    }

    public UiModifier box(float x, float y, float w, float h) {
        return Ui.modifier().size(w * scale, h * scale).offset(insetX + x * scale, insetY + y * scale);
    }

    public UiModifier button(float x, float y, float w, float h, boolean active) {
        UiStyle style = UiStyle.button().padding(2).text(textStyle(14, .85f, .94f, .91f).align(UiTextAlign.CENTER));
        style = style.background(UiDrawable.color(active ? UiColor.rgb(.19f, .49f, .40f) : UiColor.rgb(.10f, .15f, .18f)));
        style = style.hover(style.background(UiDrawable.color(UiColor.rgb(.21f, .31f, .34f))))
                .focused(style.background(UiDrawable.color(UiColor.rgb(.23f, .37f, .39f))));
        return box(x, y, w, h).style(style);
    }

    private UiTextStyle textStyle(float size, float r, float g, float b) {
        return UiTextStyle.text().font(uiFont).size(size * scale).color(UiColor.rgb(r, g, b)).wrap(false);
    }

    private void build(UiScope ui) {
        ui.stack(Ui.modifier().fill(), layer -> {
            for (int i = 0; i < panelCount; i++) {
                Rectangle panel = panels.get(i);
                layer.panel(box(panel.x, panel.y, panel.width, panel.height)
                        .style(UiStyle.style().background(UiDrawable.color(panel.color))), null);
            }
            for (int i = 0; i < meterCount; i++) {
                Meter meter = meters.get(i);
                layer.progressBar(box(meter.x, meter.y, meter.width, meter.height), meter.value, 0, 1);
            }
            if (batch == null) layer.custom("chart-marks", Ui.modifier().fill(), surface -> surface.draw(this::drawMarks));
            for (int i = 0; i < labelCount; i++) {
                Label label = labels.get(i);
                float remaining = (width - insetX) / scale - label.x;
                layer.text(label.text, box(label.x, label.y, Math.max(1, remaining), label.size * 1.6f)
                        .style(UiStyle.style().text(textStyle(label.size, label.red, label.green, label.blue))));
            }
            if (controls != null) controls.build(layer);
        });
    }

    public boolean hasFont() {
        return font != null;
    }

    public void begin(RenderPass pass, int width, int height, float scale, float left, float top) {
        this.pass = pass;
        panelCount = markCount = meterCount = labelCount = 0;
        if (this.width != width || this.height != height || this.scale != scale || insetX != left || insetY != top) {
            this.width = width;
            this.height = height;
            this.scale = scale;
            insetX = left;
            insetY = top;
            root.resize(width, height);
            root.requestCompose();
        }
        sx=2*scale/width; sy=2*scale/height;
        this.left=2*left/width-1; this.top=1-2*top/height;
        if (batch != null) {
            batch.viewport(width, height);
            batch.begin(pass);
        }
    }

    private static Rectangle slot(List<Rectangle> storage, int index) {
        if (storage.size() == index) storage.add(new Rectangle());
        return storage.get(index);
    }

    public void panel(float x, float y, float w, float h, float r, float g, float b, float a) {
        if (slot(panels, panelCount++).set(x, y, w, h, r, g, b, a)) root.requestCompose();
    }

    public void progress(float x, float y, float width, float height, float value) {
        if (meterCount == meters.size()) meters.add(new Meter());
        Meter meter = meters.get(meterCount++);
        if (meter.x != x || meter.y != y || meter.width != width || meter.height != height) {
            meter.x = x;
            meter.y = y;
            meter.width = width;
            meter.height = height;
            root.requestCompose();
        }
        meter.value.set(value);
    }

    /** Chart marks and texture test patterns keep their own drawing coordinates. */
    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        if (batch == null) {
            slot(marks, markCount++).set(x, y, w, h, r, g, b, a);
        } else {
            batch.color(r, g, b, a);
            batch.draw(white, left + x * sx, top - (y + h) * sy, w * sx, h * sy);
        }
    }

    private void drawMarks(UiDrawContext draw, UiRect bounds) {
        for (int i = 0; i < markCount; i++) {
            Rectangle mark = marks.get(i);
            draw.rect(bounds.x() + insetX + mark.x * scale, bounds.y() + insetY + mark.y * scale,
                    mark.width * scale, mark.height * scale, mark.color);
        }
    }

    public void text(String text, float x, float y, float scale, float r, float g, float b) {
        if (font == null) return;
        if (labelCount == labels.size()) labels.add(new Label());
        Label label = labels.get(labelCount++);
        float size = 12 * scale;
        if (Objects.equals(label.text, text) && label.x == x && label.y == y && label.size == size
                && label.red == r && label.green == g && label.blue == b) return;
        label.text = text;
        label.x = x;
        label.y = y;
        label.size = size;
        label.red = r;
        label.green = g;
        label.blue = b;
        root.requestCompose();
    }

    public void end() {
        if (batch != null) batch.end();
        if (labelCount != previousCount || panelCount != previousPanelCount || meterCount != previousMeterCount) root.requestCompose();
        previousCount = labelCount;
        previousPanelCount = panelCount;
        previousMeterCount = meterCount;
        root.update(0);
        ((UiG2DRenderer) root.renderer()).renderInto(root, pass);
        pass = null;
    }

    public ShaderSkippedDraws skippedDrawsLastFrame() {
        if (batch == null) throw new IllegalStateException("Shader counters require a prepared HUD batch");
        return batch.skippedDrawsLastFrame();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        try {
            root.dispose();
        } finally {
            font = null;
            try {
                if (batch != null) batch.dispose();
            } finally {
                if (white != null) white.dispose();
            }
        }
    }
}
