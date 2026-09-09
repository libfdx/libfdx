package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.MaterialInspectionBay;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.*;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.*;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;
import io.github.libfdx.input.*;

/** Resizable inspection bay for imported UV transforms and normals versus CPU-baked fixtures. */
public final class GltfMaterialsTest extends GraphicsParityTest {
    private static final String[] LABELS = {"01 COLOR / UV1", "02 EMISSION", "03 METAL / AO",
            "04 NORMAL SCALE", "05 TANGENTS", "06 MIRROR UV",
            "07 FLAT NORMAL", "08 MIRROR NODE", "09 SKIN NORMAL",
            "10 GRID", "11 FILTERING", "12 MIP FILTER",
            "13 NORMAL / UV1"};
    private static final String[] COMPACT_LABELS = {"01 UV1", "02 GLOW", "03 METAL",
            "04 NORM", "05 TAN", "06 UV", "07 FLAT", "08 NODE", "09 SKIN",
            "10 GRID", "11 TEX", "12 MIP", "13 N-UV"};
    private final String[] paths;
    private final DefaultModelInstance[] instances = new DefaultModelInstance[2];
    private final OffscreenTarget[] targets = new OffscreenTarget[2];
    private final ModelBatch[] batches = new ModelBatch[2];
    private final Camera camera = new Camera().projection(CameraProjection.PERSPECTIVE).fieldOfView(44)
            .nearFar(.1f, 80);
    private final Environment3D environment = new Environment3D().ambientColor(new Color(.07f,.09f,.13f,1))
            .add(new DirectionalLight().direction(-.6f,-.4f,-1).intensity(2));
    private final RenderPassDescriptor screen = new RenderPassDescriptor().label("glTF material comparison")
            .colorLoadOp(LoadOp.clear(12f/255,18f/255,28f/255,1)).colorStoreOp(StoreOp.store());
    private DefaultAssetManager assets;
    private TextureBlitter blitter;
    private MaterialInspectionBay bay;
    private ShowcaseHud hud;
    private BitmapFont font;
    private Input input;
    private final Vector3 world = new Vector3(), projected = new Vector3();
    private float angle = .18f;
    private boolean ready;

    public GltfMaterialsTest(long frames) {
        super(frames);
        paths = new String[] {"gltf-materials/authored.gltf", "gltf-materials/reference.gltf"};
    }

    @Override public void create(Fdx fdx) {
        initialize(fdx, getClass().getSimpleName());
        input = fdx.input();
        assets = new DefaultAssetManager(fdx.files());
        G3DAssetLoaders.register(assets, graphics);
        G2DAssetLoaders.register(assets, graphics);
        assets.load(TextureLoadOptions.PIXEL_ART.descriptor("showcase/hud.png", Texture.class));
        for (String path : paths) assets.load(AssetDescriptor.of(path, Model.class));
        for (int i = 0; i < 2; i++) {
            batches[i] = new ModelBatch(graphics).environment(environment);
            targets[i] = new OffscreenTarget(graphics.device(), true).clearColor(12f/255,18f/255,28f/255,1);
        }
        blitter = new TextureBlitter(graphics.device());
        bay = new MaterialInspectionBay(graphics);
        hud = new ShowcaseHud(graphics);
        markCreated();
    }
    @Override public void render() {
        if (!ready) {
            ready = assets.update(2, 1_000_000);
            if (ready) {
                StringBuilder characters = new StringBuilder();
                for (char c = 32; c < 96; c++) characters.append(c);
                font = BitmapFont.fromGrid(assets.get("showcase/hud.png", Texture.class), characters.toString(),6,8);
                hud.font(font);
                for (int i = 0; i < 2; i++) {
                    Model model = assets.get(paths[i], Model.class);
                    instances[i] = new DefaultModelInstance(model);
                }
                logger.info("glTF material fixtures ready: " + paths[0]);
            }
        }
        GraphicsFrame frame = graphics.currentFrame();
        int width = framebufferWidth(), height = framebufferHeight();
        float uiScale = Math.min(width / 1100f, height / 760f);
        int header = Math.round(110 * uiScale), footer = Math.round(76 * uiScale);
        int viewWidth = Math.max(1,width / 2), viewHeight = Math.max(1,height-header-footer);
        camera.viewport(viewWidth,viewHeight);
        if (!requiresCompletion()) {
            float delta = Math.min(.1f,application.deltaTime());
            if (input.isKeyPressed(Key.LEFT)) angle -= delta * .45f;
            if (input.isKeyPressed(Key.RIGHT)) angle += delta * .45f;
            if (input.isKeyPressed(Key.R)) angle = .18f;
            angle = Math.max(-.45f,Math.min(.45f,angle));
        }
        float distance = Math.max(14.6f, 9.5f * viewHeight / viewWidth);
        camera.position((float)Math.sin(angle)*distance,.9f,(float)Math.cos(angle)*distance).lookAt(0,0,0);
        if (ready) for (int i = 0; i < 2; i++) {
            targets[i].resize(viewWidth,viewHeight);
            RenderPass pass = targets[i].begin(frame, true);
            batches[i].begin(pass, camera);
            bay.render(batches[i]);
            batches[i].render(instances[i]);
            batches[i].end(); pass.end();
        }
        RenderPass pass = frame.commandEncoder().beginRenderPass(screen.colorAttachment(frame.colorAttachment()));
        if (ready) for (int i = 0; i < 2; i++) {
            pass.setViewport(i*viewWidth,footer,viewWidth,viewHeight);
            pass.setScissor(i*viewWidth,footer,viewWidth,viewHeight);
            blitter.draw(pass,targets[i].color(),targets[i].origin(),false);
        }
        pass.setViewport(0,0,width,height); pass.setScissor(0,0,width,height);
        hud.begin(pass,width,height,uiScale,0,0);
        float uiWidth = width/uiScale, uiHeight = height/uiScale;
        boolean compact = viewWidth < 480;
        hud.rect(0,0,uiWidth,110,.025f,.045f,.075f,1);
        hud.text("SURFACE INSPECTION BAY",24,17,2.2f,.86f,.95f,1);
        hud.text(compact ? "13 MATERIAL PROBES / IDENTICAL LIGHTING" :
                "13 MATERIAL PROBES / COMPARE THE SAME SURFACES UNDER THE SAME LIGHT",24,53,1.1f,.5f,.7f,.85f);
        hud.text("IMPORTED GLTF MATERIALS",24,86,1.15f,.3f,.85f,1);
        hud.text("BAKED REFERENCE",viewWidth/uiScale+24,86,1.15f,.3f,.85f,1);
        hud.rect(viewWidth/uiScale,110,1,viewHeight/uiScale,.16f,.27f,.37f,1);
        if (ready) for (int side = 0; side < 2; side++) for (int i = 0; i < LABELS.length; i++) {
            world.set((i%3-1)*2-.77f,4-i/3*2-.82f,.03f);
            camera.project(world,projected);
            hud.text(compact ? COMPACT_LABELS[i] : LABELS[i],(side*viewWidth+projected.x())/uiScale,
                    (header+projected.y())/uiScale,compact ? .65f/uiScale : .65f,.72f,.85f,.92f);
        }
        hud.rect(0,uiHeight-76,uiWidth,76,.025f,.045f,.075f,1);
        hud.text(ready ? (compact ? "COMPARE PATTERNS, SHADING AND SURFACE RELIEF" :
                "LOOK FOR MATCHING PATTERNS, REFLECTIONS AND SURFACE RELIEF") : "LOADING MATERIAL PROBES...",
                24,uiHeight-57,1.15f,.5f,.9f,.75f);
        hud.text("LEFT / RIGHT: ROTATE VIEW     R: RESET",24,uiHeight-28,1.1f,.55f,.7f,.82f);
        hud.end();
        pass.end();
        if (ready) finishFrame();
    }
    @Override public void dispose() {
        for (var batch : batches) dispose(batch);
        dispose(hud); dispose(font); dispose(bay);
        dispose(assets); dispose(blitter);
        for (var target : targets) dispose(target);
        if (!ready) throw new FdxException("glTF materials did not finish loading");
        verifyDisposed();
    }
}
