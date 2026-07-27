package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.G3DAssetLoaders;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBatchConfig;
import io.github.libfdx.graphics.g3d.ShaderGraphPbrTestSupport;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Runs the model batch test scenario.
 *
 * @author xpenatan
 */
public final class ModelBatchTest extends ApplicationAdapter {
    public static final String DEFAULT_GLTF_ASSET = "data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf";

    private final long exitAfterFrames;
    private final String gltfAsset;
    private Application application;
    private Display display;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private AssetManager assets;
    private GraphicsContext graphics;
    private ModelBatch batch;
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
        this.exitAfterFrames = exitAfterFrames;
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
        assets = new DefaultAssetManager(fdx.files());
        G3DAssetLoaders.register(assets, graphics);

        Environment3D environment = new Environment3D()
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
        if (Boolean.getBoolean("libfdx.test.shaderGraphPbr")) {
            graphShaderProvider = ShaderGraphPbrTestSupport.provider(graphics);
            batch = new ModelBatch(graphics, new ModelBatchConfig()
                    .shaderProvider(graphShaderProvider)).environment(environment);
        } else {
            batch = new ModelBatch(graphics).environment(environment);
        }
        assets.load(AssetDescriptor.of(gltfAsset, Model.class));
        assets.finishLoading();
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
        float deltaSeconds = application.deltaTime();
        assets.update();
        camera.viewport(framebufferWidth(), framebufferHeight());
        cameraInput.update(deltaSeconds);
        float seconds = renderedFrames / 60.0f;
        instance.transform(Matrix4.rotationY(seconds * 0.45f));
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
            application.requestExit();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        ShaderGraphPbrTestSupport.dispose(graphShaderProvider);
        graphShaderProvider = null;
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        if (model != null) {
            model.dispose();
            model = null;
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
