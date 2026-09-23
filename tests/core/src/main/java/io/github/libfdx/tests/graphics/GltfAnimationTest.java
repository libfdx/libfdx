package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.AnimationDroneGeometry;
import io.github.libfdx.testsupport.graphics.ShowcaseHud;
import io.github.libfdx.testsupport.graphics.ShowcaseFont;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.*;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.*;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Color;

/** Three interpolation stations comparing imported glTF poses with independent analytic transforms. */
public final class GltfAnimationTest extends GraphicsParityTest {
    private static final String[] PATHS = {"gltf-animation/step.gltf", "gltf-animation/mixed.gltf", "gltf-animation/cubic-skin.glb"};
    private final DefaultModelInstance[][] instances = new DefaultModelInstance[2][3];
    private final Model[] referenceModels = new Model[3];
    private final AnimationController[] controllers = new AnimationController[3];
    private final ModelBatch[] batches = new ModelBatch[2];
    private final OffscreenTarget[] targets = new OffscreenTarget[6];
    private final Matrix4 actual = new Matrix4();
    private final float[] actualValues = new float[16], expectedValues = new float[16];
    private final Camera camera = new Camera().projection(CameraProjection.PERSPECTIVE).fieldOfView(44)
            .nearFar(.1f, 60).position(4, 2, 13).lookAt(0, 0, 0);
    private final Environment environment = new Environment().ambientColor(new Color(.22f,.27f,.36f,1))
            .add(new DirectionalLight().direction(-.5f,-.7f,-1).intensity(3));
    private final DefaultModelInstance[] rails = new DefaultModelInstance[3];
    private Model railModel;
    private Model guideModel;
    private final DefaultModelInstance[][] guides = new DefaultModelInstance[3][];
    private ShowcaseHud hud;
    private ShowcaseFont font;
    private float playbackTime;
    private static final String[] TITLES = {"01   STEP TRANSLATION", "02   MIXED CHANNELS", "03   CUBIC JOINT ROTATION"};
    private static final String[] DETAILS = {"HOLD, THEN JUMP AT EACH KEY", "CURVED PATH / TURN / SIZE STEPS", "SMOOTH ROTATION THROUGH A SKIN"};
    private static final String[] TIME_LABELS = {"0 S", "1 S", "2 S", "3 S", "4 S"};
    private static final float[] CENTERS = {1.7f, -.05f, -1.9f};
    private float elapsed;
    private final RenderPassDescriptor descriptor = new RenderPassDescriptor().label("glTF animation comparison")
            .colorLoadOp(LoadOp.clear(12f/255, 18f/255, 28f/255, 1)).colorStoreOp(StoreOp.store());
    private DefaultAssetManager assets;
    private TextureBlitter blitter;
    private int frames;
    private boolean ready;

    public GltfAnimationTest(long frames) { super(frames); }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "GltfAnimationTest");
        assets = new DefaultAssetManager(fdx.files());
        G3DAssetLoaders.register(assets, graphics);
        G2DAssetLoaders.register(assets, graphics);
        font = new ShowcaseFont(assets.createScope(), false);
        hud = new ShowcaseHud(graphics);
        for (String path : PATHS) assets.load(AssetDescriptor.of(path, Model.class));
        float[] positions = AnimationDroneGeometry.positions();
        float[][] colors = {{.9f,.55f,.06f}, {.1f,.8f,.25f}, {.75f,.12f,.5f}};
        for (int i = 0; i < 3; i++) {
            var material = new Material("reference-"+i)
                    .set(PbrAttributes.metallicFactor(.25f)).set(PbrAttributes.roughnessFactor(.4f))
                    .set(MaterialAttributes.baseColor(colors[i][0], colors[i][1], colors[i][2], 1));
            referenceModels[i] = new ModelBuilder(graphics).material(material).triangles("reference-"+i,
                    positions, null, null, ModelVertexUsage.STANDARD_PBR);
            instances[1][i] = new DefaultModelInstance(referenceModels[i]);
        }
        for (int i = 0; i < 2; i++) {
            batches[i] = new ModelBatch(graphics).environment(environment);
        }
        for (int i = 0; i < targets.length; i++)
            targets[i] = new OffscreenTarget(graphics.device(), true).clearColor(.047f, .071f, .11f, 1);
        blitter = new TextureBlitter(graphics.device());
        railModel = new ModelBuilder(graphics).material(new Material("comparison platform")
                .set(MaterialAttributes.baseColor(.08f,.14f,.22f,1))).box(4.8f,.035f,1.6f);
        for (int i = 0; i < 3; i++) {
            rails[i] = new DefaultModelInstance(railModel);
            rails[i].transform().setToTranslation(0, CENTERS[i] - 1.1f, 0);
        }
        guideModel = new ModelBuilder(graphics).material(new Material("motion guide")
                .set(MaterialAttributes.baseColor(.32f,.48f,.62f,1))).box(.045f,.045f,.045f);
        for (int i = 0; i < 3; i++) {
            guides[i] = new DefaultModelInstance[i == 0 ? 4 : 49];
            for (int n = 0; n < guides[i].length; n++) {
                float t = n * 4f / (guides[i].length - 1);
                float x, y;
                if (i == 0) {
                    x = n == 0 ? -1.5f : n == 1 ? -.5f : n == 2 ? 1 : .5f;
                    y = CENTERS[i];
                } else if (i == 1) {
                    x = -1.2f + .6f * t;
                    y = .1f * t * (4 - t) - .25f;
                } else {
                    double angle = n * Math.PI * 2 / (guides[i].length - 1);
                    x = .95f * (float)Math.cos(angle);
                    y = CENTERS[i] + .95f * (float)Math.sin(angle);
                }
                guides[i][n] = new DefaultModelInstance(guideModel);
                guides[i][n].transform().setToTranslation(x, y, -.45f);
            }
        }
        markCreated();
    }

    @Override
    public void render() {
        if (!ready) {
            boolean loaded = assets.update(2, 1_000_000);
            hud.font(font.poll());
            ready = loaded && hud.hasFont();
            if (ready) {
                for (int i = 0; i < 3; i++) {
                    Model model = assets.get(PATHS[i], Model.class);
                    instances[0][i] = new DefaultModelInstance(model);
                    controllers[i] = new AnimationController(instances[0][i]).play(model.animations().get(0), false);
                }
                logger.info("GltfAnimationTest assets ready: STEP, mixed timelines, cubic GPU skin, sparse, external BIN, GLB");
            }
        }
        GraphicsFrame frame = graphics.currentFrame();
        int width = framebufferWidth(), height = framebufferHeight();
        float uiScale = Math.min(width / 1100f, height / 760f);
        float uiWidth = width / uiScale, uiHeight = height / uiScale;
        int margin = Math.max(1, Math.round(20 * uiScale));
        int gap = Math.max(1, Math.round(12 * uiScale));
        int viewWidth = Math.max(1, (width - margin * 2 - gap * 2) / 3);
        int top = Math.round(156 * uiScale);
        int viewHeight = Math.max(1, (height - top - Math.round(112 * uiScale)) / 2);
        camera.viewport(viewWidth, viewHeight);
        if (ready) {
            // Revisit exact STEP boundaries, their neighbors and both clamped endpoints.
            elapsed += Math.min(.1f, application.deltaTime());
            float time = requiresCompletion() ? (frames%301-30)/60f : elapsed % 5 - .5f;
            playbackTime = Math.max(0, Math.min(4, time));
            for (int i = 0; i < 3; i++) {
                controllers[i].time(time);
                reference(i, Math.max(0, Math.min(4, time)), instances[1][i].transform());
                instances[0][i].copyNodeModelTransform("animated", actual).copyValues(actualValues, 0);
                instances[1][i].transform().copyValues(expectedValues, 0);
                for (int c = 0; c < 16; c++) {
                    if (Math.abs(actualValues[c]-expectedValues[c]) > .00001f) {
                        throw new FdxException("glTF transform mismatch: case="+i+" time="+time+" component="+c);
                    }
                }
            }
            for (int method = 0; method < 2; method++) {
                for (int i = 0; i < 3; i++) {
                    float distance = Math.max(5.5f, 6.6f * viewHeight / viewWidth);
                    camera.position(.65f, CENTERS[i] + .65f, distance).lookAt(0, CENTERS[i], 0);
                    OffscreenTarget target = targets[method * 3 + i];
                    target.resize(viewWidth, viewHeight);
                    RenderPass scene = target.begin(frame, true);
                    batches[method].begin(scene, camera);
                    batches[method].render(rails[i]);
                    for (var guide : guides[i]) batches[method].render(guide);
                    batches[method].render(instances[method][i]);
                    batches[method].end();
                    scene.end();
                }
            }
        }
        RenderPass pass = frame.commandEncoder().beginRenderPass(descriptor.colorAttachment(frame.colorAttachment()));
        if (ready) for (int method = 0; method < 2; method++) {
            for (int i = 0; i < 3; i++) {
                int x = margin + i * (viewWidth + gap);
                int y = height - top - method * (viewHeight + gap) - viewHeight;
                pass.setViewport(x, y, viewWidth, viewHeight);
                pass.setScissor(x, y, viewWidth, viewHeight);
                OffscreenTarget target = targets[method * 3 + i];
                blitter.draw(pass, target.color(), target.origin(), false);
            }
        }
        pass.setViewport(0, 0, width, height);
        pass.setScissor(0, 0, width, height);
        hud.begin(pass, width, height, uiScale, 0, 0);
        hud.text("GLTF ANIMATION",20,22,2.25f,.9f,.95f,1);
        hud.text("THREE INTERPOLATION CASES. SAME TIME. INDEPENDENT REFERENCE.",20,62,1,.52f,.66f,.8f);
        for (int i = 0; i < 3; i++) {
            float x = (margin + i * (viewWidth + gap)) / uiScale;
            float w = viewWidth / uiScale;
            float r = i == 0 ? .95f : i == 1 ? .3f : .85f;
            float g = i == 0 ? .7f : i == 1 ? .85f : .4f;
            float b = i == 0 ? .2f : i == 1 ? .65f : .8f;
            hud.rect(x,100,w,3,r,g,b,1);
            hud.text(TITLES[i],x,116,1.25f,r,g,b);
            hud.text(DETAILS[i],x,139,.95f,.58f,.68f,.8f);
            for (int method = 0; method < 2; method++) {
                float y = (top + method * (viewHeight + gap)) / uiScale;
                hud.text(method == 0 ? "IMPORTED GLTF" : "ANALYTIC REFERENCE",x+12,y+12,1f,.63f,.75f,.86f);
                hud.rect(x,y+viewHeight/uiScale-1,w,1,.16f,.23f,.32f,1);
            }
        }
        float timelineY = uiHeight - 66;
        float timelineWidth = uiWidth - 40;
        hud.rect(20,timelineY,timelineWidth,3,.18f,.27f,.36f,1);
        hud.rect(20,timelineY,timelineWidth * playbackTime / 4,3,.3f,.83f,.7f,1);
        for (int tick = 0; tick <= 4; tick++) {
            float x = 20 + timelineWidth * tick / 4;
            hud.rect(x-1,timelineY-3,2,9,.6f,.73f,.83f,1);
            hud.text(TIME_LABELS[tick],x-(tick==4 ? 15 : 0),timelineY+14,1f,.6f,.73f,.83f);
        }
        hud.text(ready ? "POSE CHECKS PASSING   /   TOP AND BOTTOM SHOULD MATCH" : "LOADING MODELS AND TRUETYPE FONT",
                20,uiHeight-22,1f,.4f,.9f,.7f);
        hud.end();
        pass.end();
        if (ready) { frames++; finishFrame(); }
    }

    private static void reference(int index, float t, Matrix4 out) {
        if (index == 0) {
            out.setToTranslation(t < 1 ? -1.5f : t < 2 ? -.5f : t < 4 ? 1 : .5f, 1.7f, 0);
        } else if (index == 1) {
            float scale = t < 1.5f ? .5f : t < 3 ? .8f : .6f;
            double halfAngle = Math.PI*t/8;
            out.setToTrs(-1.2f+.6f*t, .1f*t*(4-t)-.25f, 0,
                    0, 0, (float)Math.sin(halfAngle), (float)Math.cos(halfAngle), scale, scale, scale);
        } else {
            // Independent cubic Bezier conversion of glTF's Hermite curve; retain its negative endpoint sign.
            double u = t/4, v = 1-u, endZ = -(float)Math.sqrt(.75);
            double z = 3*v*v*u*(4*.2f/3) + 3*v*u*u*endZ + u*u*u*endZ;
            double w = v*v*v + 3*v*v*u + 3*v*u*u*(-.5-4*(-.2f)/3) - .5*u*u*u;
            double norm = Math.sqrt(z*z+w*w);
            out.setToTrs(0, -1.9f, 0, 0, 0, (float)(z/norm), (float)(w/norm), 1, 1, 1);
        }
    }

    @Override
    public void dispose() {
        for (var batch : batches) dispose(batch);
        for (var model : referenceModels) dispose(model);
        dispose(blitter);
        dispose(hud);
        dispose(font);
        dispose(assets);
        dispose(railModel);
        dispose(guideModel);
        for (var target : targets) dispose(target);
        if (!ready) throw new FdxException("glTF assets never became ready");
        logger.info("GltfAnimationTest analytic transform comparisons="+frames*3);
        verifyDisposed();
    }
}
