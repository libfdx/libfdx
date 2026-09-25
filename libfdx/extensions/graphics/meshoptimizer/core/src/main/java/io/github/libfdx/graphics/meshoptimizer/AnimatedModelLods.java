package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.g3d.lod.*;
import io.github.libfdx.graphics.camera.ProjectedBounds;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Vector3;

/** Owns per-instance CPU morph geometry for a generated chain. Borrows generated/source models.
 * One controller animates binding().base(); select() computes current bounds and deforms the
 * selected level before submitting draws. updateBase() supports full-detail comparison rendering.
 * Construction/upload and disposal belong on the graphics thread outside queued draws.
 * Dispose this binding before the generated chain and source lease. */
public final class AnimatedModelLods implements Disposable {
    private final CpuMorphModelAnimator baseMorph;
    private final boolean ownsBaseMorph;
    private final CpuMorphModelAnimator[] reducedMorphs;
    private final ModelLodBinding binding;
    private final BoundingBox bounds=new BoundingBox(new Vector3(),new Vector3());
    private boolean disposed;
    AnimatedModelLods(GraphicsContext graphics,GeneratedModelLods generated,DefaultModelInstance base,CpuMorphModelAnimator borrowedBaseMorph) {
        ownsBaseMorph=borrowedBaseMorph==null;
        baseMorph=ownsBaseMorph ? new CpuMorphModelAnimator(graphics,base) : borrowedBaseMorph;
        if (baseMorph.instance()!=base || baseMorph.isDisposed()) throw new IllegalArgumentException("Morph animator must belong to the live base instance");
        reducedMorphs=new CpuMorphModelAnimator[generated.levelCount()];ModelInstance[] instances=new ModelInstance[reducedMorphs.length];
        try {
            for (int i=0;i<instances.length;i++) {
                DefaultModelInstance instance=new DefaultModelInstance(generated.model(i+1));instances[i]=instance;
                reducedMorphs[i]=new CpuMorphModelAnimator(graphics,instance);
            }
            binding=new ModelLodBinding(generated.settings().runtimeConfig(),new ModelLodModels(base,instances)
                    .synchronizer(new LodGeneratedPoseSynchronizer(reducedMorphs)));
        } catch (RuntimeException | Error failure) {
            if (ownsBaseMorph) close(baseMorph,failure);
            for (CpuMorphModelAnimator animator : reducedMorphs) if (animator!=null) close(animator,failure);
            throw failure;
        }
    }
    public ModelLodBinding binding() { requireLive();return binding; }
    public void updateBase() { requireLive();baseMorph.update(); }
    /** Computes conservative current-pose bounds and deforms only the selected level. Unknown
     * bounds use full detail. Call after updating the base pose, before queuing draws. */
    public ModelInstance select(ModelLodView view,ProjectedBounds projection) {
        requireLive();if(projection==null)throw new IllegalArgumentException("LOD projection is required");baseMorph.updateBounds();
        ModelInstance selected=baseMorph.instance().calculatePoseBounds(bounds)
                ? binding.select(view,projection,bounds) : binding.selectLevel(view,0);
        if(selected==binding.base())baseMorph.update();return selected;
    }
    /** Explicit level choice that deforms only the resolved level, including base fallback. */
    public ModelInstance selectLevel(ModelLodView view,int level) {
        requireLive();ModelInstance selected=binding.selectLevel(view,level);
        if(selected==binding.base())baseMorph.update();return selected;
    }
    private void requireLive() { if (disposed) throw new IllegalStateException("Animated LOD binding is disposed"); }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) return;disposed=true;Throwable failure=null;
        for (CpuMorphModelAnimator animator : reducedMorphs) try { animator.dispose(); } catch (RuntimeException | Error next) {
            if (failure==null) failure=next;else failure.addSuppressed(next);
        }
        if (ownsBaseMorph) try { baseMorph.dispose(); } catch (RuntimeException | Error next) {
            if (failure==null) failure=next;else failure.addSuppressed(next);
        }
        if (failure instanceof RuntimeException next) throw next;
        if (failure instanceof Error next) throw next;
    }
    private static void close(Disposable value,Throwable failure) { try { value.dispose(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); } }
}
