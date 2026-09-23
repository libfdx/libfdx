package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g2d.SpriteBatchConfig;
import io.github.libfdx.graphics.g2d.SpriteShaderPlan;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.shader.runtime.*;
import io.github.libfdx.math.Color;
import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;

/** Exercises real packed-depth shadows with one readiness snapshot shared by both renderers. */
public final class ShadowShaderPreparationTest extends GraphicsParityTest {
    private final Camera camera = new Camera().projection(CameraProjection.PERSPECTIVE)
            .fieldOfView(52).nearFar(.1f, 40).position(6, 6, 9).lookAt(0, .5f, 0);
    private final DirectionalLight light = new DirectionalLight().direction(-1, -1, -.5f).intensity(2);
    private final Model[] content = new Model[3];
    private final DefaultModelInstance[] instances = new DefaultModelInstance[3];
    private final ModelInstance[] active = new ModelInstance[3];
    private final RenderPassDescriptor overlay = new RenderPassDescriptor().label("shadow preparation HUD")
            .colorLoadOp(LoadOp.load()).colorStoreOp(StoreOp.store());
    private ShaderPreparation shaders;
    private ShaderPreparationScope bootstrap, level;
    private FdxFuture<ShaderPreparationReport> bootstrapReady, levelReady;
    private ModelShaderPlan worldPlan;
    private SpriteShaderPlan spritePlan;
    private ModelShaderGroup group;
    private DirectionalShadowMap3D shadow;
    private CascadedShadowMap3D cascades;
    private ModelBatch world;
    private ShowcaseHud hud;
    private DefaultAssetManager assets;
    private ShowcaseFont font;
    private ShaderPreloadCapture capture;
    private RenderTargetLayout worldTarget;
    private int tick, gameplayFrames, pendingFrames, skippedShadowDraws;
    private boolean created, complete, pendingCaptured;
    private String status = "PREPARING LOADING UI";

    public ShadowShaderPreparationTest(long frames) { super(frames); }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "ShadowShaderPreparationTest");
        shaders = new ShaderPreparation(graphics);
        if (!shaders.capabilities().runtimeNonblocking()) throw new FdxException(
                "Graphics device does not support feature 'nonblocking shader preparation': " + shaders.capabilities());
        worldPlan = new ModelShaderPlan(graphics);
        group = new ModelShaderGroup(shaders, worldPlan, "forward and directional shadow");
        int cascadeCount = Integer.getInteger("libfdx.test.shaderCascades", 0);
        if (cascadeCount == 0) shadow = new DirectionalShadowMap3D(graphics, 1024, 1024, shaders, group)
                .bounds(0, 0, 0, 8, .1f, 30).shadowFadeFraction(0).strength(.9f);
        else {
            cascades = new CascadedShadowMap3D(graphics, cascadeCount, 1024, 1024, shaders, group).maxDistance(24);
            shadow = cascades.cascade(0);
        }
        group.usePlan(ShaderPassId.SHADOW, shadow.shaderPlan());
        Environment environment = new Environment().ambientColor(new Color(.2f, .23f, .28f, 1)).add(light);
        if (cascades != null) environment.cascadedShadowMap(cascades); else environment.directionalShadowMap(shadow);
        world = new ModelBatch(graphics, new ModelBatchConfig().shaderPlan(worldPlan).shaderGroup(group))
                .environment(environment);
        spritePlan = new SpriteShaderPlan(graphics);
        hud = new ShowcaseHud(graphics, new SpriteBatchConfig().preparation(shaders).shaderPlan(spritePlan));
        assets = new DefaultAssetManager(fdx.files());
        G2DAssetLoaders.register(assets, graphics);
        font = new ShowcaseFont(assets.createScope(), false);
        ModelBuilder builder = new ModelBuilder(graphics);
        content[0] = builder.material(new Material("ground", MaterialAttributes.baseColor(.7f, .73f, .76f, 1)))
                .plane("shadow receiver", 12, 12, ModelVertexUsage.STANDARD_PBR);
        content[1] = builder.material(new Material("ready caster", MaterialAttributes.baseColor(.12f, .65f, .86f, 1)))
                .cube("ready caster", 2, ModelVertexUsage.STANDARD_PBR);
        content[2] = builder.material(new Material("runtime masked caster", MaterialAttributes.baseColor(.94f, .4f, .13f, 1))
                        .alphaMode(MaterialAlphaMode.MASK))
                .cube("runtime caster", 1.8f, ModelVertexUsage.STANDARD_PBR);
        for (int i = 0; i < content.length; i++) instances[i] = new DefaultModelInstance(content[i]);
        instances[1].transform().setToTranslation(-1.8f, 1, 0);
        instances[2].transform().setToTranslation(1.8f, .9f, 0);
        created = true;
        markCreated();
    }

    @Override
    public void render() {
        tick++;
        if (bootstrap == null) {
            bootstrap = shaders.createScope("shadow example loading UI");
            spritePlan.include(bootstrap, RenderTargetLayout.color(graphics.surfaceFormat()));
            bootstrapReady = shaders.prepareAsync(bootstrap.seal());
            worldTarget = worldPlan.surfaceTarget(graphics.currentFrame());
        }
        shaders.update();
        assets.update();
        if (shaders.failedCount() != 0 || shaders.unsupportedCount() != 0) {
            throw new FdxException("Shadow preparation failed: " + shaders.failures());
        }
        camera.viewport(framebufferWidth(), framebufferHeight());
        boolean uiReady = ready(bootstrapReady);
        if (uiReady && level == null) {
            level = shaders.createScope("initial shadow scene");
            for (int i = 0; i < 2; i++) {
                worldPlan.include(level, instances[i], ShaderPassId.FORWARD, worldTarget);
                shadow.shaderPlan().include(level, instances[i], ShaderPassId.SHADOW, shadow.preparationTarget());
                declare(instances[i]);
            }
            levelReady = shaders.prepareAsync(level.seal());
            status = "LOADING WORLD AND SHADOWS / HUD READY";
        }
        boolean worldReady = ready(levelReady);
        if (worldReady) {
            gameplayFrames++;
            if (capture == null) capture = shaders.captureRuntime("shadow runtime miss");
            active[0] = instances[0]; active[1] = instances[1];
            if (gameplayFrames == 20) { declare(instances[2]); active[2] = instances[2]; }
            group.beginFrame();
            instances[1].transform().setToTranslation(-1.8f, 1, 0).rotateY(gameplayFrames * .009f);
            if (cascades != null) cascades.render(light, camera, active); else shadow.render(light, active);
        }
        world.begin(LoadOp.clear(.08f, .12f, .18f, 1), camera);
        try { if (worldReady) for (ModelInstance instance : active) if (instance != null) world.render(instance); }
        finally { world.end(); }
        if (worldReady) {
            int forwardSkips = world.skippedDrawsLastFrame().total();
            int shadowSkips = shadow.skippedDrawsLastFrame().total();
            if (cascades != null) for (int i = 1; i < cascades.cascadeCount(); i++) shadowSkips += cascades.cascade(i).skippedDrawsLastFrame().total();
            if ((cascades == null && forwardSkips != shadowSkips) || forwardSkips > 1
                    || forwardSkips == 0 && shadowSkips != 0) throw new FdxException(
                    "Forward/shadow readiness diverged: " + forwardSkips + "/" + shadowSkips);
            skippedShadowDraws += shadowSkips;
            if (forwardSkips != 0) { pendingFrames++; status = "NEW CASTER PENDING IN BOTH PASSES / READY SCENE CONTINUES"; }
            else status = "WORLD AND SHADOWS READY";
            if (gameplayFrames > 20 && forwardSkips == 0 && !shaders.hasPendingWork() && !complete) {
                if (pendingFrames == 0 || skippedShadowDraws == 0) throw new FdxException("Pending shadow/forward group was not exercised");
                long misses = capture.snapshot().discoveries().stream()
                        .filter(item -> item.cause() != ShaderPreloadDiscovery.Cause.NONE).count();
                if (misses != 2) throw new FdxException("Expected one forward and one shadow discovery, got " + misses);
                logger.info("ShadowShaderPreparationTest GROUP_READY pending_frames=" + pendingFrames
                        + " shadow_skips=" + skippedShadowDraws + " runtime_misses=" + misses
                        + " cascades=" + (cascades == null ? 1 : cascades.cascadeCount()));
                complete = true;
            }
        }
        if (uiReady) drawHud();
        String path = System.getProperty("libfdx.test.shaderCapturePending", "");
        if (pendingFrames > 0 && !complete && !pendingCaptured && !path.isEmpty()) {
            try { FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), FramebufferCapture.readPixelsRgba8(graphics)); }
            catch (Exception error) { throw new FdxException("Could not capture pending shadow frame", error); }
            pendingCaptured = true;
        }
        if (complete && hud.hasFont()) finishFrame();
    }

    private void declare(ModelInstance instance) {
        group.include(instance, new ShaderPassId[]{ShaderPassId.FORWARD, ShaderPassId.SHADOW},
                new RenderTargetLayout[]{worldTarget, shadow.preparationTarget()});
    }
    private static boolean ready(FdxFuture<ShaderPreparationReport> report) {
        if (report == null || !report.isDone()) return false;
        if (!report.get().allReady()) throw new FdxException("Required shadow preparation did not succeed");
        return true;
    }
    private void drawHud() {
        hud.font(font.poll());
        RenderPass pass = graphics.currentFrame().commandEncoder().beginRenderPass(
                overlay.colorAttachment(graphics.currentFrame().colorAttachment()));
        try {
            hud.begin(pass, framebufferWidth(), framebufferHeight(), 1, 18, 18);
            hud.text(status, 0, 0, 1.1f, .9f, .97f, 1);
            hud.rect(tick % 260, 28, 32, 4, .12f, .8f, .65f, 1);
            hud.end();
        } finally { pass.end(); }
        if (hud.skippedDrawsLastFrame().total() != 0) throw new FdxException("Preloaded shadow example HUD skipped a draw");
    }
    @Override
    public void dispose() {
        dispose(capture); dispose(world); dispose(group);
        if (cascades != null) dispose(cascades); else dispose(shadow);
        dispose(hud);
        dispose(level); dispose(bootstrap); dispose(shaders); dispose(worldPlan);
        for (Model model : content) dispose(model);
        dispose(font); dispose(assets);
        if (created && requiresCompletion()) {
            if (!complete) throw new FdxException("Shadow preparation scenario did not complete");
            verifyDisposed();
        }
    }
}
