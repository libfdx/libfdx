package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g2d.SpriteBatchConfig;
import io.github.libfdx.graphics.g2d.SpriteShaderPlan;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MaterialAlphaMode;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBatchConfig;
import io.github.libfdx.graphics.g3d.ModelBuilder;
import io.github.libfdx.graphics.g3d.ModelShaderPlan;
import io.github.libfdx.graphics.g3d.ModelVertexUsage;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadCapture;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadDiscovery;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadManifest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationReport;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationScope;
import io.github.libfdx.math.Color;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;

/**
 * Executable game-side example: preload the HUD, preload a world, stream another model while the
 * world renders, then discover one unplanned material at runtime. All batches share preparation
 * and their loading/runtime selectors. No batch here uses synchronous shader construction.
 */
public final class ShaderPreloadingTest extends GraphicsParityTest {
    private final ShaderPreloadCapture.Destination destination;
    private final String manifest;
    private final Camera camera = new Camera().projection(CameraProjection.PERSPECTIVE)
            .fieldOfView(52).nearFar(.1f, 40).position(0, 2.6f, 8).lookAt(0, 0, 0);
    private final RenderPassDescriptor hudPass = new RenderPassDescriptor().label("preloaded HUD")
            .colorLoadOp(LoadOp.load()).colorStoreOp(StoreOp.store());
    private ShaderPreparation shaders;
    private SpriteShaderPlan spritePlan;
    private ModelShaderPlan modelPlan;
    private ShaderPreparationScope bootstrap, level, stream;
    private FdxFuture<ShaderPreparationReport> bootstrapReady, levelReady, streamReady;
    private FdxFuture<Void> exported;
    private ShaderPreloadCapture capture;
    private DefaultAssetManager assets;
    private ShowcaseFont font;
    private ShowcaseHud hud;
    private ModelBatch models;
    private final Model[] content = new Model[3];
    private final DefaultModelInstance[] instances = new DefaultModelInstance[3];
    private RenderTargetLayout modelTarget;
    private int frameTick, gameplayFrames, streamingFrames, runtimeSkipped;
    private boolean finished, created, pendingCaptured;
    private String status = "PREPARING LOADING UI";

    public ShaderPreloadingTest(long frames) { this(frames, null, null); }

    /** Platform injection: owns a disposable destination; manifest text is acquired before startup. */
    public ShaderPreloadingTest(long frames, ShaderPreloadCapture.Destination destination, String manifest) {
        super(frames);
        this.destination = destination;
        this.manifest = manifest;
    }

    @Override public void create(Fdx fdx) {
        initialize(fdx, "ShaderPreloadingTest");
        shaders = new ShaderPreparation(graphics);
        if (!shaders.capabilities().runtimeNonblocking()) {
            throw new FdxException("Graphics device does not support feature 'nonblocking shader preparation': " + shaders.capabilities());
        }
        spritePlan = new SpriteShaderPlan(graphics);
        modelPlan = new ModelShaderPlan(graphics);
        hud = new ShowcaseHud(graphics, new SpriteBatchConfig().preparation(shaders).shaderPlan(spritePlan));
        models = new ModelBatch(graphics, new ModelBatchConfig().preparation(shaders).shaderPlan(modelPlan))
                .environment(new Environment3D().ambientColor(new Color(.25f, .25f, .3f, 1))
                        .add(new DirectionalLight().direction(-.3f, -.8f, -1).intensity(1.4f)));
        assets = new DefaultAssetManager(fdx.files());
        G2DAssetLoaders.register(assets, graphics);
        font = new ShowcaseFont(assets.createScope(), false);
        ModelBuilder builder = new ModelBuilder(graphics);
        content[0] = builder.material(new Material("world", MaterialAttributes.baseColor(.2f, .75f, .9f, 1)))
                .cube("world cube", 1.5f, ModelVertexUsage.DEFAULT);
        content[1] = builder.material(new Material("streamed", MaterialAttributes.baseColor(.9f, .5f, .16f, 1)))
                .sphere("streamed sphere", .8f, 24, 16, ModelVertexUsage.STANDARD_PBR);
        content[2] = builder.material(new Material("unexpected alpha", MaterialAttributes.baseColor(.6f, .28f, .95f, .8f))
                        .alphaMode(MaterialAlphaMode.BLEND))
                .cube("runtime material", 1.25f, ModelVertexUsage.STANDARD_PBR);
        for (int i = 0; i < instances.length; i++) instances[i] = new DefaultModelInstance(content[i]);
        created = true;
        markCreated();
    }

    @Override public void render() {
        frameTick++;
        // One update boundary publishes readiness for both batches before any render pass.
        if (bootstrap == null) {
            bootstrap = shaders.createScope("loading UI");
            spritePlan.include(bootstrap, RenderTargetLayout.color(graphics.surfaceFormat()));
            bootstrapReady = shaders.prepareAsync(bootstrap.seal());
            modelTarget = modelPlan.surfaceTarget(graphics.currentFrame());
        }
        shaders.update();
        assets.update();
        if (shaders.failedCount() != 0 || shaders.unsupportedCount() != 0) {
            throw new FdxException("Shader preload failed: " + shaders.failures());
        }
        camera.viewport(framebufferWidth(), framebufferHeight());
        if (!ready(bootstrapReady)) {
            drawWorld(false, false, false);
            return;
        }
        if (level == null) {
            capture = shaders.captureRuntime("startup and streaming");
            level = shaders.createScope("world");
            modelPlan.include(level, instances[0], ShaderPassId.FORWARD, modelTarget);
            if (manifest != null) {
                var imported = level.include(ShaderPreloadManifest.fromJson(manifest), recipe ->
                        recipe.factory().equals("libfdx.sprite") ? spritePlan.resolve(recipe, spritePlan.targets())
                                : modelPlan.resolve(recipe, modelPlan.targets()));
                if (imported.hasUnresolvedEntries()) throw new FdxException("Preload recipe import failed: " + imported.items());
            }
            levelReady = shaders.prepareAsync(level.seal());
            status = "LOADING WORLD / HUD READY";
        }
        if (!ready(levelReady)) {
            drawWorld(false, false, false);
            drawHud();
            return;
        }

        gameplayFrames++;
        if (gameplayFrames == 15) {
            stream = shaders.createScope("next area");
            modelPlan.include(stream, instances[1], ShaderPassId.FORWARD, modelTarget);
            streamReady = shaders.prepareAsync(stream.seal());
            status = "STREAMING NEXT AREA / WORLD AND HUD READY";
        }
        boolean streaming = stream != null && !ready(streamReady);
        if (streaming) streamingFrames++;
        boolean streamed = stream != null && !streaming;
        boolean runtime = streamed && gameplayFrames >= 30;
        int previousSkips = runtimeSkipped;
        drawWorld(true, streamed, runtime);
        int skips = models.skippedDrawsLastFrame().total();
        if (runtime) runtimeSkipped += skips;
        else if (skips != 0) throw new FdxException("Preloaded world or streamed content requested another shader");
        if (runtime && skips != 0) status = "NEW MATERIAL COMPILING / READY CONTENT CONTINUES";
        else if (!streaming && !runtime) status = "WORLD AND HUD READY";
        if (runtime && skips == 0 && !shaders.hasPendingWork() && !finished) {
            if (manifest == null && (previousSkips == 0 || streamingFrames == 0)) {
                throw new FdxException("Streaming/runtime phases were not exercised");
            }
            var discovery = capture.snapshot();
            long misses = discovery.discoveries().stream().filter(item -> item.cause() != ShaderPreloadDiscovery.Cause.NONE).count();
            long expectedMisses = manifest == null ? 1 : 0;
            if (misses != expectedMisses || manifest != null && runtimeSkipped != 0) {
                throw new FdxException("Preload coverage mismatch: misses=" + misses + ", runtime skipped=" + runtimeSkipped);
            }
            logger.info("ShaderPreloadingTest READY_CONTENT_CONTINUED streaming_frames=" + streamingFrames
                    + " runtime_skipped=" + runtimeSkipped + " runtime_misses=" + misses);
            logger.info(discovery.markdown());
            exported = destination == null ? FdxFuture.completed(null) : capture.exportAsync(destination);
            capture.dispose();
            capture = null;
            status = manifest == null ? "ALL READY / 1 RUNTIME MISS RECORDED FOR PRELOADING"
                    : "REPLAY READY / ZERO RUNTIME MISSES AND ZERO SKIPS";
            finished = true;
        }
        drawHud();
        if (streaming && streamingFrames >= 5 && !pendingCaptured) {
            String path = System.getProperty("libfdx.test.shaderCapturePending", "");
            if (!path.isBlank()) {
                try { FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), FramebufferCapture.readPixelsRgba8(graphics)); }
                catch (Exception failure) { throw new FdxException("Could not capture streaming frame", failure); }
            }
            pendingCaptured = true;
        }
        if (finished && exported.isDone()) { exported.get(); finishFrame(); }
    }

    private boolean ready(FdxFuture<ShaderPreparationReport> future) {
        if (future == null || !future.isDone()) return false;
        if (!future.get().allReady()) throw new FdxException("Preload failed: " + future.get().items());
        return true;
    }

    private void drawWorld(boolean world, boolean streamed, boolean runtime) {
        float angle = gameplayFrames * .008f;
        for (int i = 0; i < instances.length; i++) {
            instances[i].transform().setToTranslation((i - 1) * 2.25f, 0, 0).rotateY(angle);
        }
        models.begin(LoadOp.clear(.025f, .035f, .055f, 1), camera);
        try {
            if (world) models.render(instances[0]);
            if (streamed) models.render(instances[1]);
            if (runtime) models.render(instances[2]);
        } finally { models.end(); }
    }

    private void drawHud() {
        hud.font(font.poll());
        var frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(hudPass.colorAttachment(frame.colorAttachment()));
        try {
            hud.begin(pass, framebufferWidth(), framebufferHeight(), 1, 18, 18);
            hud.text(status, 0, 0, 1.2f, .85f, .95f, 1);
            hud.rect(0, 28, 260, 5, .12f, .18f, .25f, 1);
            hud.rect(frameTick % 220, 28, 40, 5, .15f, .8f, .7f, 1);
            hud.end();
        } finally { pass.end(); }
        if (hud.skippedDrawsLastFrame().total() != 0) throw new FdxException("Preloaded HUD skipped a draw");
    }

    @Override public void dispose() {
        dispose(capture);
        dispose(models); dispose(hud);
        dispose(stream); dispose(level); dispose(bootstrap);
        dispose(shaders); dispose(modelPlan);
        for (Model model : content) dispose(model);
        dispose(font); dispose(assets);
        if (destination instanceof Disposable disposable) dispose(disposable);
        if (created && requiresCompletion()) {
            if (!finished) throw new FdxException("ShaderPreloadingTest did not complete startup/streaming/runtime phases");
            verifyDisposed();
        }
    }
}
