package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLease;
import io.github.libfdx.assets.AssetExecutor;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.shader.runtime.*;
import io.github.libfdx.math.Color;
import io.github.libfdx.testsupport.graphics.ConcurrentGltfFixtures;
import io.github.libfdx.testsupport.graphics.ConcurrentGltfObserver;
import io.github.libfdx.testsupport.graphics.AssetLoadingMetrics;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;
import java.util.ArrayList;

/** Loads distinct roots in one burst and draws each scene as soon as it is ready. */
public final class ConcurrentGltfLoadingTest extends GraphicsParityTest {
    private final ConcurrentGltfObserver observer;
    private final AssetExecutor executor;
    private final ArrayList<AssetLease<Model>> models = new ArrayList<>();
    private final ArrayList<ShaderPreparationScope> scopes = new ArrayList<>();
    private final ArrayList<FdxFuture<ShaderPreparationReport>> prepared = new ArrayList<>();
    private final DefaultModelInstance[] instances = new DefaultModelInstance[ConcurrentGltfFixtures.count()];
    private final int[] draws = new int[instances.length];
    private final RenderPassDescriptor overlay = new RenderPassDescriptor()
            .colorLoadOp(LoadOp.load()).colorStoreOp(StoreOp.store());
    private DefaultAssetManager assets;
    private final AssetLoadingMetrics assetMetrics = new AssetLoadingMetrics();
    private ModelBatch batch;
    private ShaderPreparation shaders;
    private ModelShaderPlan plan;
    private Camera camera;
    private ShowcaseHud hud;
    private ShowcaseFont font;
    private boolean loadingOnly, reported;
    private int readyCount;
    private float seconds;
    private long loadingFrames, previousFrame, maxLoadingGap, maxAssetUpdate, maxShaderUpdate, maxRender;
    private String status = "REQUESTING 12 MODELS TOGETHER";

    public ConcurrentGltfLoadingTest(long frames) { this(frames, ConcurrentGltfObserver.NONE); }
    public ConcurrentGltfLoadingTest(long frames, ConcurrentGltfObserver observer) {
        this(frames, observer, null);
    }
    /** Takes ownership of an optional platform preparation executor. */
    public ConcurrentGltfLoadingTest(long frames, ConcurrentGltfObserver observer, AssetExecutor executor) {
        super(frames);
        this.observer = observer;
        this.executor = executor;
    }

    @Override public void create(Fdx fdx) {
        initialize(fdx, "ConcurrentGltfLoadingTest");
        assets = new DefaultAssetManager(fdx.files(), executor);
        G2DAssetLoaders.register(assets, graphics);
        G3DAssetLoaders.register(assets, graphics);
        assets.registerLoader(Model.class, assetMetrics.measure(G3DAssetLoaders.modelLoader(graphics)));
        font = new ShowcaseFont(assets.createScope(), false);
        hud = new ShowcaseHud(graphics);
        shaders = new ShaderPreparation(graphics);
        plan = new ModelShaderPlan(graphics);
        loadingOnly = !graphics.device().shaderPreparationCapabilities().runtimeNonblocking();
        batch = new ModelBatch(graphics, new ModelBatchConfig().preparation(shaders).shaderPlan(plan))
                .environment(new Environment().ambientColor(new Color(.4f, .4f, .44f, 1))
                        .add(new DirectionalLight().direction(-.4f, -.7f, -1).intensity(2)));
        camera = new Camera().projection(CameraProjection.PERSPECTIVE).fieldOfView(45)
                .nearFar(.1f, 60).position(0, 0, 13).lookAt(0, 0, 0);
        observer.beforeRequests();
        for (int i = 0; i < instances.length; i++) {
            models.add(assets.acquire(AssetDescriptor.of(ConcurrentGltfFixtures.path(i), Model.class)));
            scopes.add(null);
            prepared.add(null);
        }
        markCreated();
    }

    @Override public void render() {
        long now = System.nanoTime();
        if (!reported && previousFrame != 0) maxLoadingGap = Math.max(maxLoadingGap, now - previousFrame);
        previousFrame = now;
        seconds += Math.max(0, Math.min(.1f, application.deltaTime()));
        assets.update(4, 1_000_000L);
        if (!reported) maxAssetUpdate = Math.max(maxAssetUpdate, assets.lastUpdateNanos());
        for (int i = 0; i < models.size(); i++) {
            var lease = models.get(i);
            if (lease.future().isFailed()) lease.future().get();
            if (lease.isLoaded() && instances[i] == null) {
                for (int j = 0; j < i; j++) {
                    if (models.get(j).isLoaded() && models.get(j).asset() == lease.asset())
                        throw new FdxException("Distinct paths reused the same model");
                }
                observer.modelReady(i);
                instances[i] = new DefaultModelInstance(lease.asset());
                ConcurrentGltfFixtures.position(instances[i], i);
                var scope = shaders.createScope(ConcurrentGltfFixtures.path(i));
                scopes.set(i, scope);
                plan.include(scope, instances[i], ShaderPassId.FORWARD, plan.surfaceTarget(graphics.currentFrame()));
                prepared.set(i, shaders.prepareAsync(scope.seal()));
                break; // Admit one newly loaded scene per frame, including its shader-plan traversal.
            }
        }
        long shaderStart = System.nanoTime();
        if (loadingOnly) shaders.updateLoading(1_000_000L); else shaders.update(1_000_000L);
        if (!reported) maxShaderUpdate = Math.max(maxShaderUpdate, System.nanoTime() - shaderStart);
        if (shaders.failedCount() > 0 || shaders.unsupportedCount() > 0)
            throw new FdxException("Concurrent model shader preparation failed: " + shaders.failures());
        int ready = 0;
        camera.viewport(framebufferWidth(), framebufferHeight()).update();
        long renderStart = System.nanoTime();
        batch.begin(LoadOp.clear(.025f, .04f, .07f, 1), camera);
        for (int i = 0; i < instances.length; i++) {
            var completion = prepared.get(i);
            if (completion == null || !completion.isDone()) continue;
            if (!completion.get().allReady()) throw new FdxException("Model shader scope did not complete");
            batch.render(instances[i]);
            draws[i]++;
            ready++;
        }
        batch.end();
        if (!reported) maxRender = Math.max(maxRender, System.nanoTime() - renderStart);
        if (batch.skippedDrawsLastFrame().total() != 0) throw new FdxException("Prepared model draws were skipped");
        if (ready != readyCount) {
            readyCount = ready;
            status = ready + " / " + instances.length + " MODELS READY";
        }
        observer.frame(ready == instances.length);
        if (ready < instances.length) loadingFrames++;
        else if (!reported) {
            reported = true;
            assetMetrics.report(logger);
            observer.loadingComplete(maxAssetUpdate, maxShaderUpdate, maxRender, maxLoadingGap);
            logger.info("CONCURRENT_GLTF_READY models=" + ready + " loadingFrames=" + loadingFrames
                    + " maxAssetUpdateMs=" + maxAssetUpdate / 1e6 + " maxShaderUpdateMs=" + maxShaderUpdate / 1e6
                    + " maxRenderMs=" + maxRender / 1e6 + " maxLoadingFrameGapMs=" + maxLoadingGap / 1e6);
        }
        drawStatus();
        finishFrame();
    }

    private void drawStatus() {
        int width = framebufferWidth(), height = framebufferHeight();
        float scale = Math.min(width / 960f, height / 640f);
        var pass = graphics.currentFrame().commandEncoder().beginRenderPass(
                overlay.colorAttachment(graphics.currentFrame().colorAttachment()));
        hud.font(font.poll());
        hud.begin(pass, width, height, scale, (width - 960 * scale) / 2, (height - 640 * scale) / 2);
        hud.text("GLTF / CONCURRENT LOADING", 28, 22, 2, .88f, .95f, 1);
        hud.text(status, 28, 574, 1.5f, .3f, .9f, .7f);
        hud.rect(28 + seconds * 110 % 880, 610, 24, 5, .95f, .76f, .38f, 1);
        hud.end();
        pass.end();
    }

    @Override public void dispose() {
        try {
            if (requiresCompletion()) {
                if (!reported || loadingFrames < 2) throw new FdxException("Concurrent models never completed");
                for (int count : draws) if (count < 2) throw new FdxException("A concurrent model was not rendered");
            }
        } finally {
            try {
                dispose(batch); dispose(hud); dispose(font);
                for (var scope : scopes) dispose(scope);
                if (shaders != null) { shaders.disposeAsync(); shaders.update(); }
                dispose(plan); dispose(assets);
                for (var lease : models) if (lease.isLoaded()) throw new FdxException("Disposed manager retained a model");
            } finally { try { dispose(executor); } finally { observer.dispose(); } }
        }
        verifyDisposed();
    }
}
