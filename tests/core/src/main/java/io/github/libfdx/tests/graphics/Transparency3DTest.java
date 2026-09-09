package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MaterialAlphaMode;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBuilder;
import io.github.libfdx.graphics.g3d.ModelVertexUsage;
import io.github.libfdx.graphics.g3d.ShadingModel;
import io.github.libfdx.math.Color;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;

import java.util.ArrayList;

/**
 * Orbits a furnished glasshouse with cyan front windows, amber rear windows,
 * and pale blue side windows. Furniture and opaque window frames establish
 * depth while overlapping panes reveal transparent draw ordering.
 *
 * <p>Glass is submitted before opaque geometry deliberately. Bounded runs
 * travel halfway around the house, from the front to the rear; select either
 * endpoint with {@code libfdx.test.captureFrame} for provider comparisons.
 * Glass uses alpha blending, without refraction or transmission.</p>
 */
public final class Transparency3DTest extends GraphicsParityTest {
    private final ArrayList<Model> models = new ArrayList<Model>();
    private final ArrayList<DefaultModelInstance> instances = new ArrayList<DefaultModelInstance>();
    private final Camera camera = new Camera()
            .projection(CameraProjection.ORTHOGRAPHIC).nearFar(0.1f, 40.0f);
    private ModelBatch batch;
    private long frameIndex;
    private float orbitSeconds;

    public Transparency3DTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "Transparency3DTest");
        batch = new ModelBatch(graphics).environment(new Environment3D()
                .ambientColor(new Color(0.32f, 0.37f, 0.43f, 1.0f))
                .add(new DirectionalLight().direction(-0.5f, -1.0f, -0.3f)
                        .color(new Color(1.0f, 0.92f, 0.80f, 1.0f)).intensity(2.0f)));
        ModelBuilder builder = new ModelBuilder(graphics);
        Model cyan = mesh(builder, "a-cyan-glass", 0.20f, 0.76f, 0.92f, 0.30f);
        Model amber = mesh(builder, "z-amber-glass", 1.0f, 0.57f, 0.20f, 0.34f);
        Model blue = mesh(builder, "side-glass", 0.40f, 0.70f, 0.86f, 0.22f);
        Model plaster = mesh(builder, "warm-plaster", 0.86f, 0.79f, 0.65f, 1.0f);
        Model wood = mesh(builder, "oak", 0.38f, 0.20f, 0.10f, 1.0f);
        Model roof = mesh(builder, "slate-roof", 0.12f, 0.21f, 0.27f, 1.0f);
        Model stone = mesh(builder, "limestone", 0.58f, 0.63f, 0.62f, 1.0f);
        Model grass = mesh(builder, "lawn", 0.24f, 0.39f, 0.27f, 1.0f);
        Model leaf = mesh(builder, "foliage", 0.18f, 0.46f, 0.26f, 1.0f);
        Model terracotta = mesh(builder, "terracotta", 0.69f, 0.28f, 0.15f, 1.0f);
        Model fabric = mesh(builder, "coral-upholstery", 0.83f, 0.31f, 0.22f, 1.0f);

        // Separate single-surface panes avoid the self-sorting ambiguity of
        // transparent boxes. Opaque walls leave actual openings behind them.
        for (int side = -1; side <= 1; side += 2) {
            pane(cyan, side * 1.5f, 1.8f, 2.0f, 2.2f, 1.9f, false);
            pane(amber, side * 1.5f, 1.8f, -2.0f, 2.2f, 1.9f, false);
            pane(blue, side * 2.75f, 1.8f, 0.0f, 3.7f, 1.9f, true);
        }
        pane(cyan, 0.0f, 1.5f, 2.015f, 0.72f, 2.4f, false);
        pane(amber, 0.0f, 1.8f, -2.0f, 0.72f, 1.9f, false);

        box(grass, 0, -0.25f, 0, 10, 0.3f, 9);
        box(stone, 0, 0, 0, 6.1f, 0.25f, 4.7f);
        box(wood, 0, 0.17f, 0, 5.5f, 0.10f, 4.0f);
        box(stone, 0, -0.02f, 2.65f, 1.6f, 0.16f, 0.6f);
        for (int i = 0; i < 3; i++) {
            box(stone, 0, -0.07f, 3.25f + i * 0.48f, 1.15f, 0.08f, 0.34f);
        }
        for (int side = -1; side <= 1; side += 2) {
            float z = side * 2.0f;
            // Low plaster walls and a continuous lintel around the glazing.
            box(plaster, -1.5f, 0.52f, z, 2.5f, 0.6f, 0.18f);
            box(plaster, 1.5f, 0.52f, z, 2.5f, 0.6f, 0.18f);
            box(wood, 0, 2.87f, z, 5.6f, 0.20f, 0.22f);
            for (int column = 0; column < 4; column++) {
                float x = column == 0 ? -2.75f : column == 1 ? -0.45f
                        : column == 2 ? 0.45f : 2.75f;
                box(wood, x, 1.5f, z, 0.14f, 2.7f, 0.22f);
            }
            for (int window = -1; window <= 1; window += 2) {
                float x = window * 1.5f;
                box(wood, x, 0.86f, z, 2.25f, 0.10f, 0.25f);
                box(wood, x, 1.8f, z, 0.065f, 1.9f, 0.12f);
                box(wood, x, 2.12f, z, 2.2f, 0.065f, 0.12f);
            }
            float x = side * 2.75f;
            box(plaster, x, 0.52f, 0, 0.18f, 0.6f, 4);
            box(wood, x, 2.87f, 0, 0.22f, 0.20f, 4.2f);
            box(wood, x, 0.86f, 0, 0.25f, 0.10f, 4);
            box(wood, x, 1.8f, 0, 0.14f, 1.9f, 0.10f);
            box(wood, x, 2.12f, 0, 0.12f, 0.065f, 4);
            // Two roof slopes meet along the ridge, with open gables.
            DefaultModelInstance slope = box(roof, side * 1.48f, 3.47f, 0,
                    1, 1, 1);
            slope.transform().rotateZ(side * -0.38f).scale(3.2f, 0.16f, 4.7f);
        }
        box(wood, 0, 4.07f, 0, 0.18f, 0.18f, 4.8f);
        box(wood, 0.26f, 1.45f, 2.09f, 0.06f, 0.25f, 0.08f);
        box(plaster, 0, 0.52f, -2, 0.76f, 0.6f, 0.18f);

        // Solid furniture is visible through the front, side and rear glass.
        box(fabric, -1.5f, 0.65f, -0.65f, 1.65f, 0.40f, 0.85f);
        box(fabric, -1.5f, 1.02f, -1.02f, 1.65f, 0.85f, 0.18f);
        for (int side = -1; side <= 1; side += 2) {
            box(wood, -1.5f + side * 0.7f, 0.35f, -0.65f, 0.12f, 0.3f, 0.7f);
            box(fabric, -1.5f + side * 0.8f, 0.90f, -0.65f, 0.16f, 0.35f, 0.85f);
        }
        box(wood, 0.7f, 0.92f, 0.1f, 1.45f, 0.12f, 1.05f);
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                box(wood, 0.7f + x * 0.58f, 0.55f, 0.1f + z * 0.4f,
                        0.09f, 0.7f, 0.09f);
            }
        }
        box(terracotta, 0.7f, 1.10f, 0.1f, 0.24f, 0.24f, 0.24f);
        box(leaf, 0.7f, 1.35f, 0.1f, 0.40f, 0.30f, 0.35f);
        for (int side = -1; side <= 1; side += 2) {
            box(terracotta, side * 2.15f, 0.3f, 2.85f, 0.85f, 0.65f, 0.65f);
            box(leaf, side * 2.15f, 0.78f, 2.85f, 1.0f, 0.40f, 0.75f);
        }
        markCreated();
    }

    @Override
    public void render() {
        double angle = 0.48 + (exitAfterFrames > 1L
                ? Math.PI * frameIndex / (exitAfterFrames - 1L)
                : exitAfterFrames == 1L ? 0.0 : orbitSeconds * 0.18);
        camera.viewport(11.5f * framebufferWidth() / framebufferHeight(), 11.5f)
                .position((float)Math.sin(angle) * 12.0f, 6.4f,
                        (float)Math.cos(angle) * 12.0f)
                .lookAt(0.0f, 1.35f, 0.0f).update();
        batch.begin(LoadOp.clear(0.075f, 0.11f, 0.15f, 1.0f), camera);
        for (int i = 0; i < instances.size(); i++) {
            batch.render(instances.get(i));
        }
        batch.end();
        finishFrame();
        frameIndex++;
        orbitSeconds += application.deltaTime();
    }

    @Override
    public void dispose() {
        dispose(batch);
        for (int i = 0; i < models.size(); i++) {
            dispose(models.get(i));
        }
        verifyDisposed();
    }

    private Model mesh(ModelBuilder builder, String id, float r, float g, float b, float alpha) {
        boolean glass = alpha < 1.0f;
        Material material = new Material(id)
                .shadingModel(glass ? ShadingModel.UNLIT : ShadingModel.PBR)
                .set(MaterialAttributes.baseColor(r, g, b, alpha))
                .set(MaterialAttributes.alphaCutoff(0.0f))
                .alphaMode(glass ? MaterialAlphaMode.BLEND : MaterialAlphaMode.OPAQUE)
                .doubleSided(glass);
        builder.material(material);
        Model model = glass ? builder.plane(id, 1, 1, ModelVertexUsage.STANDARD_PBR)
                : builder.box(id, 1, 1, 1, ModelVertexUsage.STANDARD_PBR);
        models.add(model);
        return model;
    }

    private DefaultModelInstance box(Model model, float x, float y, float z,
            float width, float height, float depth) {
        DefaultModelInstance instance = new DefaultModelInstance(model);
        instance.transform().setToTranslation(x, y, z).scale(width, height, depth);
        instances.add(instance);
        return instance;
    }

    private void pane(Model model, float x, float y, float z,
            float width, float height, boolean side) {
        DefaultModelInstance instance = new DefaultModelInstance(model);
        instance.transform().setToTranslation(x, y, z)
                .rotateY(side ? (float)(Math.PI * 0.5) : 0.0f)
                .rotateX((float)(Math.PI * 0.5)).scale(width, 1, height);
        instances.add(instance);
    }
}
