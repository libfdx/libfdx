package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import io.github.libfdx.testsupport.graphics.TestCameraControllers;
import io.github.libfdx.testsupport.graphics.SpotLightGallery;
import io.github.libfdx.Fdx;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.graphics.g3d.DirectionalShadowMap3D;
import io.github.libfdx.graphics.g3d.G3DAssetLoaders;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.math.Color;
import io.github.libfdx.testsupport.TestFpsLogger;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Renders a sculpture gallery that exposes spotlight cone falloff on curved and flat surfaces.
 *
 * @author xpenatan
 */
public final class SpotLight3DTest extends ApplicationAdapter {
    private static final Color CLEAR_COLOR = new Color(0.018f, 0.022f, 0.032f, 1.0f);

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private GraphicsContext graphics;
    private ModelBatch batch;
    private Camera camera;
    private OrbitCameraController3D cameraInput;
    private SpotLightGallery gallery;
    private AssetManager assets;
    private Runnable assetSetup;
    private DirectionalLight mainLight;
    private DirectionalShadowMap3D shadows;
    private boolean created;
    private String capturePath;
    private int captureEvery;
    private int capturedFrames;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a 3D spotlight test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public SpotLight3DTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
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
        fpsLogger = TestFpsLogger.create(logger, "SpotLight3DTest");

        assets = new DefaultAssetManager(fdx.files());
        G3DAssetLoaders.register(assets, graphics);
        String dragonPath = "data/g3d/gltf/StanfordDragon/stanfordDragon.gltf";
        assets.load(AssetDescriptor.of(dragonPath, Model.class));
        assetSetup = () -> createLoadedAssets(fdx, dragonPath);
    }

    private void createLoadedAssets(Fdx fdx, String dragonPath) {
        mainLight = new DirectionalLight().direction(-.6f, -1, -.35f)
                .color(new Color(.72f, .82f, 1, 1)).intensity(1.6f);
        // The existing renderer shadows this directional key; spotlights add local pools.
        shadows = new DirectionalShadowMap3D(graphics, 2048, 2048)
                .bounds(0, 1, 0, 10, .1f, 35).autoBias(true).strength(1);
        Environment environment = new Environment()
                .ambientColor(new Color(.055f, .065f, .08f, 1))
                .fog(CLEAR_COLOR, 30, 55).neutralToneMapping(1)
                .add(mainLight).directionalShadowMap(shadows);
        gallery = new SpotLightGallery(graphics, environment, assets.get(dragonPath, Model.class));
        batch = new ModelBatch(graphics).environment(environment);
        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(46.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 60.0f);
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .position(7.0f, 6.8f, 14.5f, 0.0f, 1.0f, 0.0f)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), 0.75f, exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(), TestCameraControllers.autoOrbitDegrees());
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureEvery = Integer.parseInt(System.getProperty("libfdx.test.captureEvery", "0"));

        created = true;
        logger.info("SpotLight3DTest created courtyard with spotlights and directional shadows for provider "
                + graphics.providerId());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        boolean assetsFinished = assets.update(4, 1_000_000L);
        if (assetSetup != null) {
            if (!assetsFinished) {
                graphics.clear(0.02f, 0.025f, 0.04f, 1.0f);
                return;
            }
            Runnable setup = assetSetup;
            assetSetup = null;
            setup.run();
        }
        float deltaSeconds = application.deltaTime();
        camera.viewport(framebufferWidth(), framebufferHeight());
        cameraInput.update(deltaSeconds);
        shadows.render(mainLight, gallery.shadowCasters());
        batch.begin(LoadOp.clear(CLEAR_COLOR.red(), CLEAR_COLOR.green(), CLEAR_COLOR.blue(), 1.0f), camera);
        gallery.render(batch);
        batch.end();
        if (capturePath != null && capturePath.length() > 0) {
            if (captureEvery > 0 && capturePath.indexOf('%') >= 0) {
                if (renderedFrames % captureEvery == 0) {
                    captureFrame(String.format(Locale.ROOT, capturePath, capturedFrames));
                    capturedFrames++;
                }
            }
            else if (!captured && renderedFrames >= 10) {
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
        boolean cancelledLoading = assetSetup != null;
        assetSetup = null;
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (shadows != null) {
            shadows.dispose();
            shadows = null;
        }
        if (gallery != null) {
            gallery.dispose();
            gallery = null;
        }
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        if (cancelledLoading && exitAfterFrames == 0L) {
            return;
        }
        if (!created) {
            throw new FdxException("SpotLight3DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("SpotLight3DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        logger.info("SpotLight3DTest rendered " + renderedFrames + " frames");
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
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("SpotLight3DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture SpotLight3DTest framebuffer", e);
        }
    }
}
