package io.github.libfdx.testsupport.graphics.lod;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.ProjectedBounds;
import io.github.libfdx.graphics.g2d.TextureBlitter;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.g3d.lod.*;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;

/** Three immutable prepared meshes, rendered through the public LOD API. */
public final class ModelLodScene implements Disposable {
    private final GraphicsContext graphics;
    private final Model[] models = new Model[3];
    private final DefaultModelInstance[] instances = new DefaultModelInstance[3];
    private final int[] triangles = new int[3];
    private final OffscreenTarget[] targets = new OffscreenTarget[2];
    private final ModelBatch batch;
    private final TextureBlitter blitter;
    private final Camera camera = new Camera().fieldOfView(48).nearFar(.1f, 200);
    private final ProjectedBounds projected = new ProjectedBounds();
    private final BoundingBox bounds = new BoundingBox(new Vector3(-1.5f,-.4f,-1.5f), new Vector3(1.5f,.4f,1.5f));
    private final ModelLodModels levels;
    private final ModelLodBinding binding;
    private final ModelLodView referenceView = new ModelLodView(), selectedView = new ModelLodView();
    private final RenderPassDescriptor screen = new RenderPassDescriptor().label("LOD comparison")
            .colorLoadOp(LoadOp.clear(.025f,.04f,.065f,1)).colorStoreOp(StoreOp.store());
    private int width, height;
    private float pixels, angle;
    private boolean missing, ready, disposed;

    public ModelLodScene(GraphicsContext graphics) {
        this.graphics = graphics;
        Material material = new Material("blue ceramic", MaterialAttributes.baseColor(.12f,.64f,.88f,1),
                PbrAttributes.metallicFactor(.1f), PbrAttributes.roughnessFactor(.32f));
        ModelBuilder builder = new ModelBuilder(graphics).material(material);
        int[] divisions = {64,24,8};
        for (int i = 0; i < 3; i++) {
            models[i] = builder.torus("ring", 1.1f,.38f,divisions[i],ModelVertexUsage.STANDARD_PBR);
            instances[i] = new DefaultModelInstance(models[i]);
            MeshPart part = models[i].nodes().get(0).parts().get(0).meshPart();
            triangles[i] = (part.indexCount() > 0 ? part.indexCount() : part.vertexCount()) / 3;
        }
        levels = new ModelLodModels(instances[0],instances[1],instances[2]);
        binding = new ModelLodBinding(new ModelLodConfig(220,80),levels);
        Environment environment = new Environment().ambientColor(new Color(.16f,.19f,.25f,1))
                .add(new DirectionalLight().direction(-.4f,-.65f,-1).intensity(2.6f))
                .add(new DirectionalLight().direction(.6f,.2f,.7f).color(new Color(.4f,.7f,1,1)).intensity(.8f))
                .neutralToneMapping(1);
        batch = new ModelBatch(graphics).environment(environment);
        blitter = new TextureBlitter(graphics.device());
        for (int i = 0; i < 2; i++) targets[i] = new OffscreenTarget(graphics.device(),true).clearColor(.055f,.075f,.105f,1);
    }

    public void render(ModelLodControls controls, ModelLodLayout layout, float delta) {
        if (width != layout.viewWidth || height != layout.viewHeight) {
            width = layout.viewWidth; height = layout.viewHeight;
            for (OffscreenTarget target : targets) target.resize(width,height);
        }
        ModelLodConfig config = binding.config();
        if (config.maxScreenPixels(1) != controls.mediumPixels.get() || config.maxScreenPixels(2) != controls.lowPixels.get()
                || config.hysteresis() != controls.hysteresis.get()) {
            binding.config(new ModelLodConfig(controls.mediumPixels.get(), controls.lowPixels.get()).withHysteresis(controls.hysteresis.get()));
        }
        if (missing != controls.missing.get()) {
            missing = controls.missing.get(); levels.level(2, missing ? null : instances[2]);
        }
        if (controls.spin.get()) angle += Math.min(.1f,delta) * .65f;
        else angle = 0;
        instances[0].transform().setToRotationX(.85f).rotateY(angle + .2f);
        float distance = controls.distance.get();
        camera.nearFar(.1f,Math.max(200,controls.maximumDistance() * 2));
        if (controls.orthographic.get()) {
            float span = distance * .72f;
            camera.projection(CameraProjection.ORTHOGRAPHIC).viewport(span * width / height,span).position(0,0,12);
        } else camera.projection(CameraProjection.PERSPECTIVE).viewport(width,height).position(0,0,distance);
        camera.lookAt(0,0,0).update();
        projected.update(camera,width,height);
        pixels = projected.diameterPixels(bounds,instances[0].transform());
        int mode = controls.mode.get();
        binding.enabled(mode != 0);
        ModelInstance selected = mode > 0 ? binding.selectLevel(selectedView,mode) : binding.select(selectedView,pixels);
        int expectedSelected = selectedView.renderedLevel();
        ModelInstance reference = binding.selectLevel(referenceView,0);
        if (selectedView.renderedLevel() != expectedSelected || referenceView.renderedLevel() != 0)
            throw new FdxException("Reference and selected LOD view state leaked");
        GraphicsFrame frame = graphics.currentFrame();
        ready = true;
        for (int i = 0; i < 2; i++) {
            RenderPass pass = targets[i].begin(frame,true);
            batch.begin(pass,camera);
            batch.render(i == 0 ? reference : selected);
            batch.end(); pass.end();
            ready &= batch.skippedDrawsLastFrame().total() == 0;
        }
        RenderPass pass = frame.commandEncoder().beginRenderPass(screen.colorAttachment(frame.colorAttachment()));
        for (int i = 0; i < 2; i++) {
            pass.setViewport(layout.x[i],layout.y,width,height);
            pass.setScissor(layout.x[i],layout.y,width,height);
            blitter.draw(pass,targets[i].color(),targets[i].origin(),false);
        }
        pass.end();
    }

    public int renderedLevel() { return selectedView.renderedLevel(); }
    public int desiredLevel() { return selectedView.desiredLevel(); }
    public int triangles() { return triangles[renderedLevel()]; }
    public int referenceTriangles() { return triangles[0]; }
    public float projectedPixels() { return pixels; }
    public ModelLodFallback fallback() { return selectedView.fallback(); }
    public boolean ready() { return ready; }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        batch.dispose(); blitter.dispose();
        for (OffscreenTarget target : targets) target.dispose();
        for (Model model : models) model.dispose();
    }
}
