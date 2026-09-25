package io.github.libfdx.testsupport.graphics.lodoptimizer;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.ProjectedBounds;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.g3d.lod.*;
import io.github.libfdx.math.*;

/** Two lit views, sharing source materials and a camera. No mesh work happens during drawing. */
public final class LodOptimizerPreview implements Disposable {
    private final GraphicsContext graphics;
    private final ModelBatch batch;
    private final OffscreenTarget[] targets = new OffscreenTarget[2];
    private final TextureRegion[] images = new TextureRegion[2];
    private final Camera camera = new Camera().projection(CameraProjection.PERSPECTIVE).fieldOfView(45).nearFar(.05f, 300);
    private final ProjectedBounds projected = new ProjectedBounds();
    private final ModelLodView view = new ModelLodView();
    private final Matrix4 fit = new Matrix4();
    private DefaultModelInstance fitted;
    private BoundingBox bounds;
    private boolean ready, disposed;
    private int width = 1, height = 1;
    private float pixels;

    public LodOptimizerPreview(GraphicsContext graphics) {
        this.graphics = graphics;
        batch = new ModelBatch(graphics).environment(new Environment().ambientColor(new Color(.25f,.28f,.32f,1))
                .add(new DirectionalLight().direction(-.6f,-.8f,-1).intensity(2.8f))
                .add(new DirectionalLight().direction(.5f,.3f,.8f).intensity(.9f)).neutralToneMapping(1));
        for (int i=0;i<2;i++) targets[i] = new OffscreenTarget(graphics.device(),true).clearColor(.055f,.075f,.105f,1);
        resize(480,400);
    }
    public boolean resize(int width, int height) {
        this.width = Math.max(1,width); this.height = Math.max(1,height);
        boolean changed = false;
        for (int i=0;i<2;i++) if (targets[i].resize(this.width,this.height)) {
            images[i] = TextureRegion.rendered(targets[i]); changed = true;
        }
        return changed;
    }
    public TextureRegion image(int side) { return images[side]; }
    public boolean ready() { return ready; }
    public int level() { return hasLods ? view.renderedLevel() : 0; }
    public float pixels() { return pixels; }
    public ModelLodFallback fallback() { return view.fallback(); }
    private boolean hasLods;

    public void render(LodOptimizerSession session, float distance, int mode, float yaw) {
        DefaultModelInstance base = session.base();
        if (base != fitted && base != null) fit(base);
        if (base != null) base.transform().setToRotationY(yaw).mul(fit);
        boolean knownBounds=base!=null && base.calculatePoseBounds(bounds);
        camera.viewport(width,height).position(0,0,distance).lookAt(0,0,0).update();
        projected.update(camera,width,height);
        pixels = base == null ? 0 : knownBounds ? projected.diameterPixels(bounds,base.transform()) : Float.POSITIVE_INFINITY;
        ModelInstance selected = base;
        hasLods = session.binding() != null;
        if (session.binding() != null) selected = mode < 0 ? session.binding().select(view,pixels)
                : session.binding().selectLevel(view,Math.min(mode,session.generated().levelCount()));
        ready = base != null;
        for (int i=0;i<2;i++) {
            RenderPass pass = targets[i].begin(graphics.currentFrame(),true);
            if (base != null) {
                batch.begin(pass,camera); batch.render(i == 0 ? base : selected); batch.end();
                ready &= batch.skippedDrawsLastFrame().total() == 0;
            }
            pass.end();
        }
        graphics.clear(.022f,.029f,.047f,1);
    }
    private void fit(DefaultModelInstance instance) {
        DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
        instance.collectRenderables(queue);
        Vector3 min = new Vector3(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY);
        Vector3 max = new Vector3(Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY);
        Vector3 p = new Vector3();
        for (int i=0;i<queue.size();i++) {
            Renderable3D renderable = queue.get(i);
            BoundingBox b = renderable.bounds();
            for (int corner=0;corner<8;corner++) {
                p.set((corner&1)==0?b.min().x():b.max().x(),(corner&2)==0?b.min().y():b.max().y(),(corner&4)==0?b.min().z():b.max().z());
                renderable.worldTransform().transformPosition(p,p);
                min.set(Math.min(min.x(),p.x()),Math.min(min.y(),p.y()),Math.min(min.z(),p.z()));
                max.set(Math.max(max.x(),p.x()),Math.max(max.y(),p.y()),Math.max(max.z(),p.z()));
            }
        }
        bounds = new BoundingBox(min,max);
        float extent = Math.max(max.x()-min.x(),Math.max(max.y()-min.y(),max.z()-min.z()));
        float scale = extent > 0 ? 2 / extent : 1;
        fit.idt().scale(scale,scale,scale).translate(-(min.x()+max.x())/2,-(min.y()+max.y())/2,-(min.z()+max.z())/2);
        fitted = instance;
    }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) return; disposed = true;
        batch.dispose(); for (OffscreenTarget target : targets) target.dispose();
    }
}
