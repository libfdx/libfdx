package io.github.libfdx.tests.graphics;

import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.Fdx;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.MultipleShadersFixture;
import io.github.libfdx.testsupport.graphics.MultipleShadersPreparation;
import io.github.libfdx.testsupport.graphics.ShaderFrameTimings;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Creates and draws distinct WGSL programs to expose runtime preparation costs.
 * Set libfdx.test.shaderCount (1–128, default 128) and libfdx.test.shaderSeed
 * (default 0) to vary the batch. Repeated seeds may benefit from driver caches.
 * Pipelines are prepared serially by default; set libfdx.test.shaderBatch=true to measure batching separately.
 * Set libfdx.test.shaderAsync=true to prepare complete pipelines while the loading HUD renders.
 * Explicit libfdx.test.shaderLoadingOnly=true permits owner-thread source/native work during loading.
 * Timings measure CPU calls, not GPU execution or first presentation.
 */
public final class MultipleShadersTest extends GraphicsParityTest {
    private static final int VERTICES_PER_TILE = 6;
    private static final int STRIDE = 16;
    private static final VertexLayout LAYOUT = VertexLayout.of(STRIDE,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8));
    private final RenderPassDescriptor passDescriptor = new RenderPassDescriptor()
            .label("multiple shader grid").colorLoadOp(LoadOp.clear(0.008f, 0.012f, 0.02f, 1))
            .colorStoreOp(StoreOp.store());
    private ShaderModule[] modules;
    private final RenderPassDescriptor hudPassDescriptor = new RenderPassDescriptor()
            .label("shader preparation timing").colorLoadOp(LoadOp.load()).colorStoreOp(StoreOp.store());
    private DefaultAssetManager assets;
    private ShowcaseFont font;
    private ShowcaseHud hud;
    private String preparationText, breakdownText;
    private boolean timerShown;
    private RenderPipeline[] pipelines;
    private boolean[] drawn;
    private Buffer vertices;
    private int count, columns, rows;
    private boolean firstDraw = true;
    private boolean failed;
    private long preparationStart, maximumRenderNanos;
    private MultipleShadersPreparation asyncPreparation;
    private boolean loadingOnlyPreparation;
    private int previousReady = -1;
    private boolean asyncReported;
    private long pendingFrames;
    private boolean pendingCaptured;
    private boolean pixelsChecked;
    private final ShaderFrameTimings preparingBody = new ShaderFrameTimings();
    private final ShaderFrameTimings readyBody = new ShaderFrameTimings();
    private final ShaderFrameTimings preparingInterval = new ShaderFrameTimings();
    private final ShaderFrameTimings readyInterval = new ShaderFrameTimings();
    private final ShaderFrameTimings preparationUpdates = new ShaderFrameTimings();
    private long previousFrameStart;
    private boolean previousFramePreparing;

    public MultipleShadersTest(long exitAfterFrames) { super(exitAfterFrames); }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "MultipleShadersTest");
        try {
            preparePrograms();
            assets = new DefaultAssetManager(fdx.files());
            G2DAssetLoaders.register(assets, graphics);
            font = new ShowcaseFont(assets.createScope(), false);
            hud = new ShowcaseHud(graphics);
        } catch (RuntimeException | Error failure) {
            failed = true;
            logger.error("MultipleShadersTest preparation failed", failure);
            throw failure;
        }
    }

    private void preparePrograms() {
        count = Integer.parseInt(System.getProperty("libfdx.test.shaderCount", "128"));
        if (count < 1 || count > 128) throw new FdxException("shaderCount must be between 1 and 128");
        int seed = Integer.parseInt(System.getProperty("libfdx.test.shaderSeed", "0"));
        columns = (int) Math.ceil(Math.sqrt(count));
        rows = (count + columns - 1) / columns;
        modules = new ShaderModule[count];
        pipelines = new RenderPipeline[count];
        drawn = new boolean[count];
        vertices = graphics.device().createBuffer(BufferDescriptor.staticVertex("multiple shader tiles",
                count * VERTICES_PER_TILE * STRIDE));
        updateVertices();
        long moduleTotal = 0, pipelineTotal = 0;
        if (Boolean.getBoolean("libfdx.test.shaderAsync")) {
            loadingOnlyPreparation = Boolean.getBoolean("libfdx.test.shaderLoadingOnly");
            preparationStart = System.nanoTime();
            int invalid = Integer.parseInt(System.getProperty("libfdx.test.shaderInvalidIndex", "-1"));
            if (invalid < -1 || invalid >= count) throw new FdxException("shaderInvalidIndex is outside the shader batch");
            asyncPreparation = new MultipleShadersPreparation(
                    graphics, LAYOUT, count, seed, invalid);
            preparationText = loadingOnlyPreparation ? "LOADING SHADERS QUEUED" : "ASYNC SHADERS QUEUED";
            breakdownText = loadingOnlyPreparation ? "EXPLICIT LOADING / NATIVE COMPILATION MAY BLOCK"
                    : "READY CONTENT CONTINUES WHILE MISSING PIPELINES ARE PREPARED";
            logger.info("MultipleShadersTest ASYNC_QUEUED shaders=" + count + " seed=" + seed
                    + " workers=" + graphics.device().shaderPreparationCapabilities().workerLimit()
                    + " loading_only=" + loadingOnlyPreparation);
            markCreated();
            return;
        }
        boolean batch = Boolean.parseBoolean(System.getProperty("libfdx.test.shaderBatch", "false"));
        RenderPipelineDescriptor[] descriptors = new RenderPipelineDescriptor[count];
        preparationStart = System.nanoTime();
        logger.info("MultipleShadersTest PREPARE provider=" + graphics.providerId().value()
                + " shaders=" + count + " seed=" + seed + " batch=" + batch);
        for (int i = 0; i < count; i++) {
            String label = "multiple shader " + (i + 1);
            String source = MultipleShadersFixture.source(i, seed);
            long start = System.nanoTime();
            try {
                modules[i] = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl(label, source));
                long moduleNanos = System.nanoTime() - start;
                descriptors[i] = RenderPipelineDescriptor
                        .shader(modules[i], graphics.surfaceFormat()).label(label)
                        .vertexLayout(LAYOUT).depthWriteEnabled(false);
                moduleTotal += moduleNanos;
                logger.info("MultipleShadersTest shader=" + (i + 1) + "/" + count
                        + " module_ms=" + moduleNanos / 1_000_000.0);
            } catch (RuntimeException failure) {
                throw new FdxException("MultipleShadersTest could not prepare shader " + (i + 1) + "/" + count, failure);
            }
        }
        long pipelineStart = System.nanoTime();
        if (batch) {
            pipelines = graphics.device().createRenderPipelines(descriptors);
        } else {
            for (int i = 0; i < count; i++) pipelines[i] = graphics.device().createRenderPipeline(descriptors[i]);
        }
        pipelineTotal = System.nanoTime() - pipelineStart;
        long preparationNanos = System.nanoTime() - preparationStart;
        preparationText = count + " SHADERS PREPARED IN " + milliseconds(preparationNanos) + " MS";
        breakdownText = (batch ? "CPU BATCH: MODULES " : "CPU SERIAL: MODULES ") + milliseconds(moduleTotal) + " MS / PIPELINES "
                + milliseconds(pipelineTotal) + " MS";
        logger.info("MultipleShadersTest READY shaders=" + count
                + " module_total_ms=" + moduleTotal / 1_000_000.0
                + " pipeline_total_ms=" + pipelineTotal / 1_000_000.0
                + " prepare_wall_ms=" + preparationNanos / 1_000_000.0);
        markCreated();
    }

    @Override
    public void resize(int width, int height) {
        if (vertices != null) updateVertices();
    }

    private void updateVertices() {
        ByteBuffer data = ByteBuffer.allocateDirect(count * VERTICES_PER_TILE * STRIDE).order(ByteOrder.nativeOrder());
        float gridHeight = framebufferHeight() - 76 * hudScale();
        float gridSpan = 2 * gridHeight / framebufferHeight();
        float aspect = framebufferWidth() * rows / (gridHeight * columns);
        for (int i = 0; i < count; i++) {
            float left = -1 + 2f * (i % columns) / columns + 0.015f / columns;
            float right = -1 + 2f * (i % columns + 1) / columns - 0.015f / columns;
            float top = -1 + gridSpan - gridSpan * (i / columns) / rows - 0.015f / rows;
            float bottom = -1 + gridSpan - gridSpan * (i / columns + 1) / rows + 0.015f / rows;
            vertex(data, left, bottom, -aspect, -1); vertex(data, right, bottom, aspect, -1);
            vertex(data, right, top, aspect, 1); vertex(data, left, bottom, -aspect, -1);
            vertex(data, right, top, aspect, 1); vertex(data, left, top, -aspect, 1);
        }
        data.flip();
        graphics.device().writeBuffer(vertices, data);
    }

    private static void vertex(ByteBuffer data, float x, float y, float u, float v) {
        data.putFloat(x).putFloat(y).putFloat(u).putFloat(v);
    }

    private static String milliseconds(long nanos) {
        long tenths = Math.round(nanos / 100_000.0);
        return tenths / 10 + "." + tenths % 10;
    }

    private float hudScale() {
        return Math.min(1.5f, Math.min(framebufferWidth() / 960f, framebufferHeight() / 720f));
    }

    @Override
    public void render() {
        long frameStart = System.nanoTime();
        boolean preparing = asyncPreparation != null && !asyncReported;
        if (previousFrameStart != 0) {
            (previousFramePreparing ? preparingInterval : readyInterval).record(frameStart - previousFrameStart);
        }
        previousFrameStart = frameStart;
        previousFramePreparing = preparing;
        if (asyncPreparation != null) {
            long updateStart = System.nanoTime();
            asyncPreparation.update();
            if (preparing) preparationUpdates.record(System.nanoTime() - updateStart);
            int ready = asyncPreparation.readyCount();
            if (ready != previousReady) {
                previousReady = ready;
                preparationText = (loadingOnlyPreparation ? "LOADING READY " : "ASYNC READY ") + ready + " / " + count;
                logger.info("MultipleShadersTest ASYNC_PROGRESS ready=" + ready + "/" + count
                        + " failed=" + asyncPreparation.failedCount());
            }
            for (int i = 0; i < count; i++) pipelines[i] = asyncPreparation.pipeline(i);
            if (!asyncPreparation.settled()) pendingFrames++;
            else if (!asyncReported) {
                asyncPreparation.verify();
                asyncReported = true;
                long elapsed = System.nanoTime() - preparationStart;
                breakdownText = (loadingOnlyPreparation ? "LOADING PREPARE " : "ASYNC PREPARE ")
                        + milliseconds(elapsed) + " MS / PENDING FRAMES " + pendingFrames;
                logger.info("MultipleShadersTest ASYNC_READY prepare_wall_ms=" + elapsed / 1_000_000.0
                        + " pending_frames=" + pendingFrames + " ready=" + ready + " failed=" + asyncPreparation.failedCount()
                        + " loading_only=" + loadingOnlyPreparation);
            }
        }
        assets.update(2, 1_000_000);
        hud.font(font.poll());
        long start = System.nanoTime();
        var frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(passDescriptor.colorAttachment(frame.colorAttachment()));
        int recordedDraws = 0;
        try {
            for (int i = 0; i < count; i++) {
                if (pipelines[i] == null) continue;
                pass.setPipeline(pipelines[i]);
                pass.setVertexBuffer(vertices);
                pass.draw(VERTICES_PER_TILE, 1, i * VERTICES_PER_TILE, 0);
                if (asyncPreparation != null) asyncPreparation.recordDraw(i);
                recordedDraws++;
                drawn[i] = true;
            }
        } catch (RuntimeException | Error failure) {
            failed = true;
            logger.error("MultipleShadersTest draw failed", failure);
            throw failure;
        } finally { pass.end(); }
        long elapsed = System.nanoTime() - start;
        maximumRenderNanos = Math.max(maximumRenderNanos, elapsed);
        if (firstDraw && recordedDraws > 0) {
            firstDraw = false;
            logger.info("MultipleShadersTest FIRST_DRAW shaders=" + recordedDraws + " record_ms=" + elapsed / 1_000_000.0
                    + " since_prepare_ms=" + (System.nanoTime() - preparationStart) / 1_000_000.0);
        }
        RenderPass hudPass = frame.commandEncoder().beginRenderPass(
                hudPassDescriptor.colorAttachment(frame.colorAttachment()));
        try {
            hud.begin(hudPass, framebufferWidth(), framebufferHeight(), hudScale(), 0, 0);
            hud.text(preparationText, 20, 16, 1.8f, .85f, .95f, 1);
            hud.text(breakdownText, 20, 46, 1.2f, .55f, .75f, .85f);
            if (asyncPreparation != null && !asyncReported) {
                float position = (System.nanoTime() - preparationStart) % 2_000_000_000L / 2_000_000_000f;
                hud.rect(20 + position * 600, 68, 28, 4, .2f, .9f, .7f, 1);
            }
            hud.end();
            timerShown |= hud.hasFont();
        } finally { hudPass.end(); }
        // Exclude optional test readback/file diagnostics from the application callback measurement.
        // Entry-to-entry intervals still include backend submission, presentation, pacing and diagnostics.
        (preparing ? preparingBody : readyBody).record(System.nanoTime() - frameStart);
        if (asyncReported && !pixelsChecked && requiresCompletion()
                && Boolean.parseBoolean(System.getProperty("libfdx.test.shaderVerifyPixels", "true"))) {
            try {
                if (graphics.currentFrame().frameBuffer().supportsReadPixelsRgba8()) {
                    MultipleShadersFixture.verifyPixels(FramebufferCapture.readPixelsRgba8(graphics),
                            framebufferWidth(), framebufferHeight(), framebufferHeight() - 76 * hudScale(),
                            count, columns, rows, Integer.parseInt(System.getProperty("libfdx.test.shaderInvalidIndex", "-1")));
                    logger.info("MultipleShadersTest PIXELS_VERIFIED ready=" + previousReady);
                } else {
                    logger.info("MultipleShadersTest PIXELS_UNSUPPORTED: framebuffer readback is unavailable; "
                            + "shader readiness is checked, pixel output is not verified");
                }
                pixelsChecked = true;
            } catch (RuntimeException | Error failure) {
                failed = true;
                throw failure;
            }
        }
        String pendingCapture = System.getProperty("libfdx.test.shaderPendingCapture", "");
        if (asyncPreparation != null && !asyncReported && !pendingCaptured && previousReady > 0
                && !pendingCapture.isEmpty()) {
            try {
                FramebufferCapture.writePpm(pendingCapture,
                        framebufferWidth(), framebufferHeight(),
                        FramebufferCapture.readPixelsRgba8(graphics));
            } catch (Exception failure) {
                failed = true;
                throw new FdxException("Could not capture pending shader preparation", failure);
            }
            pendingCaptured = true;
        }
        if (timerShown && (asyncPreparation == null || asyncReported)) finishFrame();
    }

    @Override
    public void dispose() {
        dispose(hud);
        dispose(font);
        dispose(assets);
        if (asyncPreparation != null) {
            if (!failed && requiresCompletion()) logger.info(asyncPreparation.timingReport());
            dispose(asyncPreparation);
        }
        else if (pipelines != null) for (RenderPipeline pipeline : pipelines) dispose(pipeline);
        if (modules != null) for (ShaderModule module : modules) dispose(module);
        dispose(vertices);
        if (failed) return; // Preserve the original exception instead of masking it during shutdown.
        verifyDisposed();
        if (requiresCompletion()) {
            for (int i = 0; i < count; i++) if (!drawn[i]
                    && (asyncPreparation == null || !asyncPreparation.expectedFailure(i))) {
                throw new FdxException("MultipleShadersTest never drew shader " + (i + 1));
            }
        }
        logger.info("MultipleShadersTest COMPLETE shaders=" + count
                + " max_render_cpu_ms=" + maximumRenderNanos / 1_000_000.0);
        logger.info("MultipleShadersTest FRAME_BODY preparing=" + preparingBody.snapshot() + " ready=" + readyBody.snapshot());
        logger.info("MultipleShadersTest FRAME_INTERVAL preparing=" + preparingInterval.snapshot() + " ready=" + readyInterval.snapshot());
        logger.info("MultipleShadersTest PREPARATION_UPDATE " + preparationUpdates.snapshot());
    }
}
