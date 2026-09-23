package io.github.libfdx.tests.graphics;

import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.assets.AssetExecutor;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.Fdx;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment;
import io.github.libfdx.graphics.g3d.G3DAssetLoaders;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBatchConfig;
import io.github.libfdx.graphics.g3d.ModelShaderPlan;
import io.github.libfdx.graphics.g3d.ShaderGraphPbrTestSupport;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadCapture;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadDiscovery;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadManifest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationReport;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationScope;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.math.Color;
import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import io.github.libfdx.testsupport.graphics.TestCameraControllers;
import io.github.libfdx.testsupport.TestFpsLogger;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Runs the model batch test scenario.
 *
 * @author xpenatan
 */
public final class ModelBatchTest extends ApplicationAdapter {
    private final AssetExecutor executor;
    public static final String DEFAULT_GLTF_ASSET = "data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf";

    private final long exitAfterFrames;
    private final String gltfAsset;
    private final ShaderPreloadCapture.Destination exportDestination;
    private final String preloadManifest;
    private ShaderPreloadCapture shaderCapture;
    private FdxFuture<Void> exportCompletion;
    private FdxFuture<ShaderPreparationReport> preloadCompletion;
    private Application application;
    private Display display;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private DefaultAssetManager assets;
    private long loadingFrames, maxLoadingUpdateNanos;
    private boolean loadingOnlyPreparation;
    private Runnable assetSetup;
    private GraphicsContext graphics;
    private ModelBatch batch;
    private ShaderPreparation preparation;
    private ModelShaderPlan shaderPlan;
    private ShaderPreparationScope preload;
    private boolean preparationReported;
    private long preparationStart, pendingFrames;
    private ShaderProvider graphShaderProvider;
    private Camera camera;
    private OrbitCameraController3D cameraInput;
    private Model model;
    private DefaultModelInstance instance;
    private boolean created;
    private String capturePath;
    private int captureEvery;
    private int capturedFrames;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a model batch test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public ModelBatchTest(long exitAfterFrames) {
        this(exitAfterFrames, System.getProperty("libfdx.test.modelAsset", DEFAULT_GLTF_ASSET));
    }

    /**
     * Creates a model batch test.
     *
     * @param exitAfterFrames the exit after frames
     * @param gltfAsset the glTF asset
     */
    public ModelBatchTest(long exitAfterFrames, String gltfAsset) {
        this(exitAfterFrames, gltfAsset, null, null);
    }

    /** Desktop/platform harness injection. Owns a disposable export destination; manifest text
     * has already been loaded by the platform before shader recipe import. */
    public ModelBatchTest(long exitAfterFrames, String gltfAsset, ShaderPreloadCapture.Destination exportDestination, String preloadManifest) {
        this(exitAfterFrames, gltfAsset, exportDestination, preloadManifest, null);
    }

    /** Takes ownership of the optional preparation executor and export destination. */
    public ModelBatchTest(long exitAfterFrames, String gltfAsset, ShaderPreloadCapture.Destination exportDestination,
            String preloadManifest, AssetExecutor executor) {
        this.executor = executor;
        this.exitAfterFrames = exitAfterFrames;
        this.exportDestination = exportDestination;
        this.preloadManifest = preloadManifest;
        this.gltfAsset = gltfAsset != null && gltfAsset.trim().length() > 0
                ? gltfAsset.trim()
                : DEFAULT_GLTF_ASSET;
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "ModelBatchTest");
        assets = new DefaultAssetManager(fdx.files(), executor);
        G3DAssetLoaders.register(assets, graphics);

        Environment environment = new Environment()
                .ambientColor(new Color(0.24f, 0.24f, 0.27f, 1.0f))
                .add(new DirectionalLight()
                        .direction(-0.35f, -0.65f, -1.0f)
                        .intensity(1.45f));
        if (Boolean.getBoolean("libfdx.test.pbrFillLight")) {
            environment.add(new DirectionalLight()
                    .direction(0.55f, -0.25f, 0.80f)
                    .color(new Color(0.42f, 0.62f, 1.0f, 1.0f))
                    .intensity(0.72f));
        }
        if (Boolean.getBoolean("libfdx.test.pbrToneMapping")) {
            environment.neutralToneMapping(Float.parseFloat(
                    System.getProperty("libfdx.test.pbrExposure", "1.35")));
        }
        var shaderCapabilities = graphics.device().shaderPreparationCapabilities();
        loadingOnlyPreparation = !shaderCapabilities.runtimeNonblocking();
        if (Boolean.getBoolean("libfdx.test.shaderAsync") || !Boolean.getBoolean("libfdx.test.shaderGraphPbr")
                && shaderCapabilities.cpuExecution() != io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities.Execution.UNAVAILABLE
                && shaderCapabilities.nativeExecution() != io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities.Execution.UNAVAILABLE) {
            preparation = new ShaderPreparation(graphics);
            if (exportDestination != null || preloadManifest != null) shaderCapture = preparation.captureRuntime("ModelBatchTest");
            shaderPlan = new ModelShaderPlan(graphics);
            batch = new ModelBatch(graphics, new ModelBatchConfig()
                    .preparation(preparation).shaderPlan(shaderPlan)).environment(environment);
            preparationStart = System.nanoTime();
        } else if (Boolean.getBoolean("libfdx.test.shaderGraphPbr")) {
            graphShaderProvider = ShaderGraphPbrTestSupport.provider(graphics);
            batch = new ModelBatch(graphics, new ModelBatchConfig()
                    .shaderProvider(graphShaderProvider)).environment(environment);
        } else {
            batch = new ModelBatch(graphics).environment(environment);
        }
        assets.load(AssetDescriptor.of(gltfAsset, Model.class));
        assetSetup = () -> createLoadedAssets(fdx);
    }

    private void createLoadedAssets(Fdx fdx) {
        model = assets.get(gltfAsset, Model.class);
        instance = new DefaultModelInstance(model);
        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(67.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 40.0f);
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .position(0.0f, 0.18f, 3.25f, 0.0f, 0.0f, 0.0f)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), 0.75f, exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(), TestCameraControllers.autoOrbitDegrees());
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureEvery = Integer.parseInt(System.getProperty("libfdx.test.captureEvery", "0"));

        created = true;
        logger.info("ModelBatchTest created with graphics provider " + graphics.providerId()
                + ", glTF asset " + gltfAsset + ", and ModelBatch");
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        boolean assetsFinished = assets.update(4, 1_000_000L);
        if (assetSetup != null) {
            loadingFrames++;
            maxLoadingUpdateNanos = Math.max(maxLoadingUpdateNanos, assets.lastUpdateNanos());
            if (!assetsFinished) {
                graphics.clear(0.02f, 0.025f, 0.04f, 1.0f);
                return;
            }
            Runnable setup = assetSetup;
            assetSetup = null;
            setup.run();
        }
        if (preparation != null) {
            if (preload == null && (preloadManifest != null || Boolean.getBoolean("libfdx.test.shaderPreload")
                    || Boolean.getBoolean("libfdx.test.shaderLoadingOnly") || loadingOnlyPreparation)) {
                preload = preparation.createScope("model level");
                shaderPlan.surfaceTarget(graphics.currentFrame());
                if (preloadManifest != null) {
                    var imported = preload.include(ShaderPreloadManifest.fromJson(preloadManifest), recipe -> shaderPlan.resolve(recipe, shaderPlan.targets()));
                    if (imported.hasUnresolvedEntries()) throw new FdxException("Shader manifest import failed: " + imported.items());
                }
                else {
                    shaderPlan.include(preload, instance, ShaderPassId.FORWARD, shaderPlan.targets().apply("surface"));
                }
                preloadCompletion = preparation.prepareAsync(preload.seal());
            }
            if (preload != null && (Boolean.getBoolean("libfdx.test.shaderLoadingOnly") || loadingOnlyPreparation)) preparation.updateLoading();
            else preparation.update();
            if (preloadCompletion != null) {
                if (!preloadCompletion.isDone()) {
                    batch.begin(LoadOp.clear(0.04f, 0.045f, 0.06f, 1.0f), camera); batch.end();
                    pendingFrames++;
                    return;
                }
                if (!preloadCompletion.get().allReady()) throw new FdxException("Model preload failed: " + preloadCompletion.get().items());
            }
        }
        float deltaSeconds = application.deltaTime();
        camera.viewport(framebufferWidth(), framebufferHeight());
        cameraInput.update(deltaSeconds);
        float seconds = renderedFrames / 60.0f;
        instance.transform().setToRotationY(seconds * 0.45f);
        batch.begin(LoadOp.clear(0.04f, 0.045f, 0.06f, 1.0f), camera);
        try {
            batch.render(instance);
        } catch (RuntimeException failure) {
            failure.printStackTrace(System.err);
            throw failure;
        } finally {
            try {
                batch.end();
            } catch (RuntimeException failure) {
                failure.printStackTrace(System.err);
                throw failure;
            }
        }
        if (preparation != null && (preparation.hasPendingWork() || batch.skippedDrawsLastFrame().total() > 0)) {
            pendingFrames++;
            if (preparation.failedCount() > 0 || preparation.unsupportedCount() > 0) {
                for (var item : preparation.failures()) if (item.failure() != null) item.failure().printStackTrace(System.err);
                throw new FdxException("Model shader preparation failed: failed=" + preparation.failedCount()
                        + " unsupported=" + preparation.unsupportedCount());
            }
            return;
        }
        if (preparation != null && !preparationReported) {
            preparationReported = true;
            logger.info("ModelBatchTest ASYNC_READY ready=" + preparation.readyCount() + " pending_frames=" + pendingFrames
                    + " prepare_wall_ms=" + (System.nanoTime() - preparationStart) / 1_000_000.0);
        }
        if (capturePath != null && capturePath.length() > 0) {
            if (captureEvery > 0 && capturePath.indexOf('%') >= 0) {
                if (renderedFrames % captureEvery == 0) {
                    captureFrame(String.format(Locale.ROOT, capturePath, capturedFrames));
                    capturedFrames++;
                }
            } else if (!captured && renderedFrames >= 30) {
                captureFrame(capturePath);
                captured = true;
            }
        }

        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            if (shaderCapture != null) {
                if (exportCompletion == null) {
                    var snapshot = shaderCapture.snapshot();
                    long misses = snapshot.discoveries().stream().filter(ShaderPreloadDiscovery::actionable).count();
                    long skipped = snapshot.discoveries().stream().mapToLong(ShaderPreloadDiscovery::skippedDraws).sum();
                    logger.info("ModelBatchTest SHADER_CAPTURE requirements=" + snapshot.discoveries().size() + " misses=" + misses + " skipped=" + skipped);
                    if (preloadManifest != null && (misses != 0 || skipped != 0)) throw new FdxException("Manifest replay had runtime shader preparation misses");
                    exportCompletion = exportDestination != null ? shaderCapture.exportAsync(exportDestination) : FdxFuture.completed(null);
                }
                if (!exportCompletion.isDone()) return;
                exportCompletion.get();
            }
            application.requestExit();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        logger.info("ModelBatchTest loading: frames=" + loadingFrames
                + ", maxUpdateMs=" + maxLoadingUpdateNanos / 1_000_000.0);
        boolean cancelledLoading = assetSetup != null;
        assetSetup = null;
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        ShaderGraphPbrTestSupport.dispose(graphShaderProvider);
        if (preload != null) preload.dispose();
        if (preparation != null) { preparation.disposeAsync(); preparation.update(); }
        if (shaderPlan != null) shaderPlan.dispose();
        if (shaderCapture != null) shaderCapture.dispose();
        if (exportDestination instanceof Disposable disposable) disposable.dispose();
        graphShaderProvider = null;
        if (assets != null) {
            try { assets.dispose(); }
            finally { if (executor != null) executor.dispose(); assets = null; }
        }
        if (model != null) {
            model.dispose();
            model = null;
        }
        if (cancelledLoading && exitAfterFrames == 0L) {
            return;
        }
        if (!created) {
            throw new FdxException("ModelBatchTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("ModelBatchTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        logger.info("ModelBatchTest rendered " + renderedFrames + " frames");
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            int width = framebufferWidth();
            int height = framebufferHeight();
            FramebufferCapture.validateSceneFrame(width, height, pixels);
            FramebufferCapture.writePpm(path, width, height, pixels);
            logger.info("ModelBatchTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture ModelBatchTest framebuffer", e);
        }
    }
}
