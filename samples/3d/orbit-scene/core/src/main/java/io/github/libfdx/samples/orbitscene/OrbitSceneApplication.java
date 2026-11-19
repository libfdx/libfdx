package io.github.libfdx.samples.orbitscene;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.CameraInputBindings3D;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBuilder;
import io.github.libfdx.graphics.g3d.ModelVertexUsage;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.math.Color;

/** A camera orbiting a tabletop sculpture built entirely from mesh primitives. */
public final class OrbitSceneApplication extends ApplicationAdapter {
    private static final LoadOp BACKGROUND = LoadOp.clear(0.055f, 0.075f, 0.11f, 1f);
    private final Camera camera = new Camera().projection(CameraProjection.PERSPECTIVE)
            .fieldOfView(42).nearFar(0.1f, 40);
    private final long exitAfterFrames;
    private Fdx fdx;
    private OrbitCameraController3D orbit;
    private ModelBatch batch;
    private Model baseModel;
    private Model ringModel;
    private Model sphereModel;
    private DefaultModelInstance base;
    private DefaultModelInstance ring;
    private DefaultModelInstance sphere;
    private long frames;

    public OrbitSceneApplication(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        GraphicsContext graphics = fdx.graphics().main();
        batch = new ModelBatch(graphics).environment(new Environment()
                .ambientColor(new Color(0.30f, 0.34f, 0.40f, 1f))
                .add(new DirectionalLight().direction(-0.5f, -0.8f, -0.4f).intensity(1.5f)));
        orbit = new OrbitCameraController3D(fdx.input(), camera)
                .inputBindings(CameraInputBindings3D.defaults().lookButton(MouseButton.LEFT))
                .radiusRange(2.5f, 10f).position(2.5f, 2.0f, 4.0f, 0, 0.65f, 0);

        ModelBuilder builder = new ModelBuilder(graphics);
        baseModel = builder.material(material("Slate", 0.18f, 0.24f, 0.34f))
                .cylinder(1.2f, 0.28f, 48, ModelVertexUsage.STANDARD_PBR);
        ringModel = builder.material(material("Copper", 0.94f, 0.38f, 0.12f))
                .torus(0.88f, 0.09f, 48, ModelVertexUsage.STANDARD_PBR);
        sphereModel = builder.material(material("Jade", 0.08f, 0.56f, 0.43f))
                .sphere(0.50f, 32, ModelVertexUsage.STANDARD_PBR);
        base = new DefaultModelInstance(baseModel);
        ring = new DefaultModelInstance(ringModel);
        ring.transform().setToTranslation(0, 0.92f, 0).rotateZ(0.98f);
        sphere = new DefaultModelInstance(sphereModel);
        sphere.transform().setToTranslation(0, 0.92f, 0);
    }

    private static Material material(String name, float red, float green, float blue) {
        return new Material(name, MaterialAttributes.baseColor(red, green, blue, 1),
                PbrAttributes.roughnessFactor(0.55f));
    }

    @Override
    public void render() {
        var display = fdx.displays().main();
        camera.viewport(Math.max(1, display.framebufferWidth()), Math.max(1, display.framebufferHeight()));
        orbit.update(fdx.app().deltaTime());
        batch.begin(BACKGROUND, camera);
        batch.render(base);
        batch.render(ring);
        batch.render(sphere);
        batch.end();
        if (exitAfterFrames > 0 && ++frames >= exitAfterFrames) {
            fdx.app().requestExit();
        }
    }

    @Override
    public void dispose() {
        if (orbit != null) orbit.dispose();
        if (batch != null) batch.dispose();
        if (sphereModel != null) sphereModel.dispose();
        if (ringModel != null) ringModel.dispose();
        if (baseModel != null) baseModel.dispose();
    }

}
