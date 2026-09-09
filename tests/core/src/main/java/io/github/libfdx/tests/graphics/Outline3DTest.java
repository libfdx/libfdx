package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import io.github.libfdx.testsupport.graphics.OutlineControls;
import io.github.libfdx.testsupport.graphics.TestCameraControllers;

import io.github.libfdx.Fdx;
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
import io.github.libfdx.graphics.g3d.ModelBuilder;
import io.github.libfdx.graphics.g3d.ModelVertexUsage;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.EdgeDetectionOutlineRenderer3D;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.math.Color;
import io.github.libfdx.testsupport.TestFpsLogger;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Runs the screen-space edge outline test scenario.
 *
 * @author xpenatan
 */
public final class Outline3DTest extends ApplicationAdapter {
    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private GraphicsContext graphics;
    private final EdgeDetectionOutlineRenderer3D[] outlines = new EdgeDetectionOutlineRenderer3D[3];
    private OutlineControls controls;
    private final ArrayList<Model> models = new ArrayList<>();
    private final ArrayList<DefaultModelInstance> scenery = new ArrayList<>();
    private ModelBatch batch;
    private Camera camera;
    private OrbitCameraController3D cameraInput;
    private DefaultModelInstance[] instances;
    private boolean created;
    private String capturePath;
    private long captureFrame;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates an outline3 d test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public Outline3DTest(long exitAfterFrames) {
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
        fpsLogger = TestFpsLogger.create(logger, "Outline3DTest");

        Environment3D environment = new Environment3D()
                .ambientColor(new Color(0.10f, 0.11f, 0.14f, 1.0f))
                .add(new DirectionalLight()
                        .direction(-0.35f, -0.75f, -0.42f)
                        .color(new Color(1.0f, 0.94f, 0.84f, 1.0f))
                        .intensity(1.6f));
        controls = new OutlineControls(fdx, "RELIC COURTYARD / 3D OUTLINES",
                "Cyan sentinel  /  amber cache  /  violet relic    -    Drag the scene to orbit");
        outlines[0] = new EdgeDetectionOutlineRenderer3D(graphics).outlineColor(.18f,.85f,1,1);
        outlines[1] = new EdgeDetectionOutlineRenderer3D(graphics).outlineColor(1,.69f,.16f,1);
        outlines[2] = new EdgeDetectionOutlineRenderer3D(graphics).outlineColor(.77f,.40f,1,1);
        batch = new ModelBatch(graphics).environment(environment);
        createScene();
        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(48.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 80.0f);
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .position(8.0f, 7.0f, 13.5f, 0.0f, 1.0f, 0.0f)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), 0.75f, exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(), TestCameraControllers.autoOrbitDegrees());
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "10"));

        created = true;
        logger.info("Outline3DTest created WGSL edge-detection outline renderer for provider "
                + graphics.providerId().value());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        controls.update(deltaSeconds);
        camera.viewport(framebufferWidth(), framebufferHeight());
        cameraInput.update(deltaSeconds);
        batch.begin(LoadOp.clear(0.018f, 0.022f, 0.032f, 1.0f), camera);
        for (int i = 0; i < scenery.size(); i++) batch.render(scenery.get(i));
        for (int i = 0; i < instances.length; i++) {
            batch.render(instances[i]);
        }
        batch.end();
        for (int i = 0; i < outlines.length; i++) {
            outlines[i].outlineWidth(controls.width.get());
            outlines[i].begin(camera);
            outlines[i].render(instances[i]);
            outlines[i].end();
        }
        controls.render();

        if (capturePath != null && capturePath.length() > 0 && !captured && renderedFrames >= captureFrame) {
            captureFrame(capturePath);
            captured = true;
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
        for (EdgeDetectionOutlineRenderer3D outline : outlines) if (outline != null) outline.dispose();
        if (controls != null) controls.dispose();
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        for (Model model : models) model.dispose();
        if (!created) {
            throw new FdxException("Outline3DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("Outline3DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("Outline3DTest did not capture framebuffer to " + capturePath);
        }
        logger.info("Outline3DTest rendered " + renderedFrames + " frames");
    }

    @Override
    public void resize(int width, int height) {
        if (controls != null) controls.resize(width, height);
    }

    private ModelBuilder material(float r, float g, float b, float metal) {
        return new ModelBuilder(graphics).material(new Material("courtyard material",
                MaterialAttributes.baseColor(r,g,b,1), PbrAttributes.roughnessFactor(.48f),
                PbrAttributes.metallicFactor(metal)));
    }

    private Model own(Model model) { models.add(model); return model; }

    private DefaultModelInstance place(Model model, float x, float y, float z) {
        DefaultModelInstance instance = new DefaultModelInstance(model);
        instance.transform().setToTranslation(x,y,z);
        return instance;
    }

    private void createScene() {
        long usage = ModelVertexUsage.STANDARD_PBR;
        Model ground = own(material(.13f,.21f,.24f,0).box("courtyard foundation",13,.3f,10,usage));
        scenery.add(place(ground,0,-.3f,0));
        Model tile = own(material(.27f,.35f,.37f,0).box("stone paver",1.45f,.12f,1.45f,usage));
        for (int z=0;z<6;z++) for(int x=0;x<8;x++) scenery.add(place(tile,(x-3.5f)*1.5f,-.08f,(z-2.5f)*1.5f));
        Model plinth = own(material(.32f,.40f,.45f,.15f).cylinder(.99f,.4f,48,usage));
        Model rim = own(material(.56f,.63f,.67f,.6f).cylinder(1.06f,.10f,48,usage));
        for(int i=0;i<3;i++) {
            scenery.add(place(plinth,(i-1)*3.3f,.2f,0));
            scenery.add(place(rim,(i-1)*3.3f,.45f,0));
        }
        Model sentinel = own(material(.32f,.66f,.72f,.35f).capsule(.62f,1.8f,40,usage));
        Model cache = own(material(.71f,.38f,.14f,.2f).box("treasure cache",1.3f,1.1f,1.1f,usage));
        Model relic = own(material(.52f,.31f,.78f,.65f).torus(.66f,.22f,48,usage));
        instances = new DefaultModelInstance[] {place(sentinel,-3.3f,1.6f,0),place(cache,0,1.1f,0),place(relic,3.3f,1.55f,0)};
        instances[1].transform().rotateY(.25f);
        instances[2].transform().rotateX(1.1f);
        Model visor = own(material(.035f,.10f,.16f,.45f).box("sentinel visor",.82f,.25f,.18f,usage));
        scenery.add(place(visor,-3.3f,1.98f,.56f));
        Model latch = own(material(.94f,.73f,.30f,.65f).box("cache latch",.23f,.37f,.13f,usage));
        scenery.add(place(latch,.14f,1.13f,.59f));
        Model column = own(material(.25f,.32f,.37f,0).box("ruined pillar",.65f,2.8f,.65f,usage));
        Model cap = own(material(.42f,.49f,.50f,.1f).box("pillar cap",.94f,.22f,.94f,usage));
        for(int i=0;i<5;i++) {
            scenery.add(place(column,(i-2)*2.65f,1.25f,-3.7f));
            scenery.add(place(cap,(i-2)*2.65f,2.76f,-3.7f));
        }
        Model boulder = own(material(.20f,.32f,.29f,0).sphere(.65f,8,usage));
        for(int i=0;i<6;i++) scenery.add(place(boulder,-5.4f+i*2.15f,.25f,3.6f));
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
            logger.info("Outline3DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture Outline3DTest framebuffer", e);
        }
    }

}
