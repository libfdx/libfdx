package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.effects.*;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.testsupport.graphics.EffectsMaterialScene;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;
import java.util.Arrays;

/** Visual comparison of normal lighting, scene resolution, bloom and HDR; checks target reuse/disposal. */
public final class EffectsTest extends GraphicsParityTest {
    private static final int PANEL_COUNT = 6;
    private final PostProcessor[] post = new PostProcessor[PANEL_COUNT];
    private final OffscreenTarget[] lit = new OffscreenTarget[PANEL_COUNT];
    private final EffectQuality[] qualities = new EffectQuality[PANEL_COUNT];
    private final String[] labels = new String[PANEL_COUNT];
    private final long[][] timings = new long[PANEL_COUNT][1024];
    private final EffectsMaterialScene scene = new EffectsMaterialScene();
    private final RenderPassDescriptor screen = new RenderPassDescriptor().label("effects comparison")
            .colorLoadOp(LoadOp.clear(.025f, .035f, .055f, 1));
    private Lighting2D lighting;
    private DefaultAssetManager assets;
    private ShowcaseFont font;
    private ShowcaseHud hud;
    private int frames, samples, panelWidth, panelHeight, windowWidth, windowHeight;
    private int margin, gap, header, caption;
    private float uiScale;

    public EffectsTest(long frames) { super(frames); }

    @Override public void create(Fdx fdx) {
        initialize(fdx, "EffectsTest");
        assets = new DefaultAssetManager(fdx.files());
        G2DAssetLoaders.register(assets, graphics);
        font = new ShowcaseFont(assets.createScope(), false);
        hud = new ShowcaseHud(graphics);
        lighting = new Lighting2D(graphics.device()).bounds(0, 0, 1, 1).ambient(.13f, .16f, .22f)
                .light(0, .23f, .73f, .20f, .85f, 1, .36f, .10f, 12)
                .light(1, .78f, .60f, .24f, .85f, .18f, .48f, 1, 10)
                .light(2, .50f, .12f, .16f, .55f, .18f, 1, .75f, 6).lightCount(3);
        EffectQuality highest = EffectQuality.bestSupported(graphics.device().capabilities());
        if (highest != EffectQuality.HIGH)
            logger.info("EffectsTest HIGH NOT_RUN: selected " + highest + "; HDR filtering/blending unavailable");
        for (int i = 0; i < PANEL_COUNT; i++) {
            EffectQuality quality = i % 3 == 0 ? EffectQuality.LOW : i % 3 == 1 ? EffectQuality.BALANCED : highest;
            qualities[i] = quality;
            post[i] = new PostProcessor(graphics.device(), quality);
            lit[i] = new OffscreenTarget(graphics.device(), quality.sceneFormat(), null, 1, TextureFilter.LINEAR);
        }
        resizePanels();
        markCreated();
    }

    private void resizePanels() {
        int width = framebufferWidth(), height = framebufferHeight();
        if (width == windowWidth && height == windowHeight) return;
        windowWidth = width;
        windowHeight = height;
        uiScale = Math.max(.35f, Math.min(1.7f, Math.min(width / 1050f, height / 720f)));
        margin = Math.max(2, Math.round(22 * uiScale));
        gap = Math.max(2, Math.round(14 * uiScale));
        header = Math.round(112 * uiScale);
        caption = Math.round(49 * uiScale);
        panelWidth = Math.max(1, (width - margin * 2 - gap * 2) / 3);
        panelHeight = Math.max(1, (height - header - margin - gap) / 2 - caption);
        scene.resize(graphics.device(), panelWidth, panelHeight);
        for (int i = 0; i < PANEL_COUNT; i++) {
            EffectQuality quality = qualities[i];
            post[i].resize(panelWidth, panelHeight);
            int w = quality.sceneDimension(panelWidth), h = quality.sceneDimension(panelHeight);
            lit[i].resize(w, h);
            labels[i] = w + " X " + h + "  /  " + post[i].passCount()
                    + (post[i].passCount() == 1 ? " PASS" : " PASSES");
            logger.info("EffectsTest panel=" + i + " quality=" + quality + " scene=" + w + "x" + h
                    + " presentation=" + panelWidth + "x" + panelHeight
                    + " bytes=" + (post[i].estimatedBytes() + lit[i].estimatedBytes()));
        }
    }

    private int panelX(int i) { return margin + (i % 3) * (panelWidth + gap); }
    private int panelTop(int i) { return header + (i / 3) * (panelHeight + caption + gap); }

    private void checkResize() {
        if (frames != 25 && frames != 85) return;
        for (int i = 0; i < PANEL_COUNT; i++) {
            Texture old = post[i].color();
            post[i].resize(Math.max(1, panelWidth / 2), Math.max(1, panelHeight / 2));
            post[i].resize(panelWidth, panelHeight);
            if ((panelWidth > 1 || panelHeight > 1) && !old.isDisposed())
                throw new FdxException("Effects resize leaked its prior output");
        }
    }

    @Override public void render() {
        assets.update(2, 1_000_000);
        hud.font(font.poll());
        resizePanels();
        checkResize();
        GraphicsFrame frame = graphics.currentFrame();
        for (int i = 0; i < PANEL_COUNT; i++) {
            if (post[i].resize(panelWidth, panelHeight))
                throw new FdxException("Effects repeated equal-size allocation");
            if (lit[i].resize(qualities[i].sceneDimension(panelWidth), qualities[i].sceneDimension(panelHeight)))
                throw new FdxException("Lighting repeated equal-size allocation");
            long start = System.nanoTime();
            RenderPass pass = lit[i].begin(frame, false);
            lighting.draw(pass, scene.albedo(), TextureOrigin.TOP_LEFT, ColorEncoding.SRGB,
                    i >= 3 ? scene.normals() : null, TextureOrigin.TOP_LEFT);
            pass.end();
            post[i].process(frame, lit[i].color(), lit[i].origin(),
                    lit[i].color().format().isSrgb() ? ColorEncoding.SRGB : ColorEncoding.LINEAR);
            if (frames >= 100 && samples < 1024) timings[i][samples] = System.nanoTime() - start;
        }
        if (frames >= 100 && samples < 1024) samples++;
        RenderPass pass = frame.commandEncoder().beginRenderPass(screen.colorAttachment(frame.colorAttachment()));
        for (int i = 0; i < PANEL_COUNT; i++) {
            int x = panelX(i), y = windowHeight - panelTop(i) - caption - panelHeight;
            pass.setViewport(x, y, panelWidth, panelHeight);
            pass.setScissor(x, y, panelWidth, panelHeight);
            post[i].present(pass);
        }
        pass.setViewport(0, 0, windowWidth, windowHeight);
        pass.setScissor(0, 0, windowWidth, windowHeight);
        drawLabels(pass);
        pass.end();
        frames++;
        // Captures and finite runs include the labels even if the font takes several frames to load.
        if (hud.hasFont()) finishFrame();
    }

    private void drawLabels(RenderPass pass) {
        hud.begin(pass, windowWidth, windowHeight, uiScale, 0, 0);
        float left = margin / uiScale;
        hud.text("LIGHT / MATERIAL", left, 18, 2.4f, .91f, .95f, 1);
        hud.text("SAME SCENE, THREE EFFECT BUDGETS", left, 55, 1.15f, .40f, .79f, .86f);
        hud.text("COMPARE CURVED SURFACES, FINE EDGES AND THE GLOW AROUND BRIGHT INLAYS.",
                left, 80, 1, .57f, .65f, .75f);
        for (int i = 0; i < PANEL_COUNT; i++) {
            float x = panelX(i) / uiScale, y = panelTop(i) / uiScale;
            float w = panelWidth / uiScale;
            hud.rect(x, y, w, caption / uiScale, .055f, .075f, .105f, 1);
            hud.rect(x, y, w, 2, i >= 3 ? .32f : .85f, i >= 3 ? .77f : .56f, i >= 3 ? .83f : .30f, 1);
            String title = switch (i % 3) {
                case 0 -> "LOW / NO BLOOM";
                case 1 -> "BALANCED / BLOOM + AA";
                default -> qualities[i] == EffectQuality.HIGH ? "HIGH / HDR + BLOOM + AA" : "HIGH UNAVAILABLE / FALLBACK";
            };
            hud.text(title, x + 10, y + 10, 1.03f, .87f, .93f, .98f);
            hud.text(i < 3 ? "FLAT NORMALS" : "SURFACE NORMALS", x + 10, y + 30, .85f, .56f, .71f, .78f);
            hud.text(labels[i], x + w - 154, y + 30, .70f, .49f, .59f, .69f);
        }
        hud.end();
    }

    @Override public void dispose() {
        for (int i = 0; i < PANEL_COUNT; i++) {
            if (samples > 0) {
                Arrays.sort(timings[i], 0, samples);
                logger.info("EffectsTest CPU_RECORD panel=" + i + " samples=" + samples
                        + " median_us=" + timings[i][samples / 2] / 1000
                        + " p95_us=" + timings[i][(samples - 1) * 95 / 100] / 1000
                        + " p99_us=" + timings[i][(samples - 1) * 99 / 100] / 1000
                        + " max_us=" + timings[i][samples - 1] / 1000 + " GPU_NOT_MEASURED");
            }
            dispose(post[i]);
            dispose(lit[i]);
        }
        dispose(lighting);
        dispose(scene);
        dispose(hud);
        dispose(font);
        dispose(assets);
        verifyDisposed();
    }
}
