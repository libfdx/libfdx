package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLease;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment;
import io.github.libfdx.graphics.g3d.G3DAssetLoaders;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.math.Color;
import io.github.libfdx.testsupport.graphics.GltfLoadingObserver;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;

/** Requests just a glTF path; its loader discovers the external buffer and texture. */
public final class GltfLoadingTest extends GraphicsParityTest {
    public static final String MODEL_PATH = "data/g3d/gltf/Ducky/ducky.gltf";
    public static final String BUFFER_PATH = "data/g3d/gltf/Ducky/ducky.bin";
    public static final String IMAGE_PATH = "data/g3d/gltf/Ducky/textures/palette.png";
    private final GltfLoadingObserver observer;
    private final RenderPassDescriptor overlay = new RenderPassDescriptor()
            .colorLoadOp(LoadOp.load()).colorStoreOp(StoreOp.store());
    private DefaultAssetManager assets;
    private AssetLease<Model> model;
    private DefaultModelInstance instance;
    private ModelBatch batch;
    private Camera camera;
    private ShowcaseHud hud;
    private ShowcaseFont font;
    private float seconds;
    private long loadingFrames, readyFrames;

    public GltfLoadingTest(long exitAfterFrames) { this(exitAfterFrames, GltfLoadingObserver.NONE); }

    public GltfLoadingTest(long exitAfterFrames, GltfLoadingObserver observer) {
        super(exitAfterFrames);
        this.observer = observer;
    }

    @Override public void create(Fdx fdx) {
        initialize(fdx, "GltfLoadingTest");
        assets = new DefaultAssetManager(fdx.files());
        G3DAssetLoaders.register(assets, graphics);
        G2DAssetLoaders.register(assets, graphics);
        font = new ShowcaseFont(assets.createScope(), false);
        hud = new ShowcaseHud(graphics);
        batch = new ModelBatch(graphics).environment(new Environment()
                .ambientColor(new Color(.32f, .34f, .4f, 1))
                .add(new DirectionalLight().direction(-.4f, -.7f, -1).intensity(1.8f)));
        camera = new Camera().projection(CameraProjection.PERSPECTIVE).fieldOfView(45)
                .nearFar(.1f, 30).position(3, 2.1f, 5).lookAt(0, .8f, 0);
        observer.beforeRequest();
        // No manual buffer/image loads: the glTF loader discovers both from this file.
        model = assets.acquire(AssetDescriptor.of(MODEL_PATH, Model.class));
        markCreated();
    }

    @Override public void render() {
        seconds += Math.max(0, Math.min(.1f, application.deltaTime()));
        assets.update(4, 1_000_000L);
        if (model.future().isFailed()) model.future().get();
        observer.frame(model.isLoaded());
        if (model.isLoaded() && instance == null) {
            if (assets.find(BUFFER_PATH, byte[].class) == null || assets.find(IMAGE_PATH, ImageData.class) == null) {
                throw new FdxException("Model became ready without its external dependencies");
            }
            instance = new DefaultModelInstance(model.asset());
            logger.info("GltfLoadingTest ready: requested only " + MODEL_PATH
                    + ", external buffer and image ready, loadingFrames=" + loadingFrames);
        }
        if (instance == null) {
            loadingFrames++;
            graphics.clear(.025f, .04f, .07f, 1);
        } else {
            readyFrames++;
            camera.viewport(framebufferWidth(), framebufferHeight()).update();
            instance.transform().setToRotationY(seconds * .25f);
            batch.begin(LoadOp.clear(.025f, .04f, .07f, 1), camera);
            batch.render(instance);
            batch.end();
        }
        drawStatus();
        finishFrame();
    }

    private void drawStatus() {
        int width = framebufferWidth(), height = framebufferHeight();
        float scale = Math.min(width / 960f, height / 640f);
        RenderPass pass = graphics.currentFrame().commandEncoder().beginRenderPass(
                overlay.colorAttachment(graphics.currentFrame().colorAttachment()));
        hud.font(font.poll());
        hud.begin(pass, width, height, scale, (width - 960 * scale) / 2, (height - 640 * scale) / 2);
        hud.text("GLTF / LOAD BY PATH", 28, 24, 2, .88f, .95f, 1);
        hud.text("ROOT DOCUMENT  >  BINARY + IMAGE  >  MODEL", 28, 66, 1.3f, .6f, .76f, .9f);
        hud.text(instance == null ? "DOWNLOADING / PREPARING DEPENDENCIES" : "READY / MODEL AND DEPENDENCIES LOADED",
                28, 565, 1.4f, .3f, .9f, .7f);
        hud.rect(28 + seconds * 110 % 880, 610, 24, 5, .95f, .76f, .38f, 1);
        hud.end();
        pass.end();
    }

    @Override public void dispose() {
        try {
            if (requiresCompletion() && (instance == null || readyFrames == 0)) {
                throw new FdxException("Deferred glTF never reached rendering");
            }
        } finally {
            try { dispose(batch); dispose(hud); dispose(font); dispose(assets); }
            finally { observer.dispose(); }
        }
        verifyDisposed();
    }
}
