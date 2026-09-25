package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.g3d.lod.*;

/** Owns only generated models/meshes; borrows unchanged base meshes, materials and textures. Detach bindings before disposal. */
public final class GeneratedModelLods implements Disposable {
    private final Model source;
    private final Model[] models;
    private final ModelLodSettings settings;
    private final ModelLodReport[] reports;
    private boolean disposed;
    GeneratedModelLods(Model source, Model[] models, ModelLodSettings settings, ModelLodReport[] reports) {
        this.source = source; this.models = models.clone(); this.settings = settings; this.reports = reports.clone();
    }
    /** Number of reduced levels, excluding the borrowed full-detail source. */
    public int levelCount() { return models.length; }
    /** Borrowed generated model, numbered 1 through levelCount(); do not dispose it separately. */
    public Model model(int level) { requireLive(); return models[level - 1]; }
    /** Immutable generation statistics, numbered 1 through levelCount(). */
    public ModelLodReport report(int level) { return reports[level - 1]; }
    public ModelLodSettings settings() { return settings; }
    /** Creates independent viewable instances; the returned binding borrows this owner's geometry. */
    public ModelLodBinding bind(DefaultModelInstance base) {
        requireLive();
        if (base == null || base.model() != source) throw new IllegalArgumentException("Binding must use the captured base model");
        if (hasMorph(source.nodes())) throw new IllegalArgumentException("Morph LODs require bindAnimated(graphics, base) and updateBase() after animation");
        ModelInstance[] reduced = new ModelInstance[models.length];
        for (int i = 0; i < reduced.length; i++) reduced[i] = new DefaultModelInstance(models[i]);
        ModelLodModels instances=new ModelLodModels(base,reduced);
        if (source.animations().notEmpty() || source.skins().notEmpty() || hasSkin(source.nodes())) instances.synchronizer(new LodGeneratedPoseSynchronizer(null));
        return new ModelLodBinding(settings.runtimeConfig(),instances);
    }
    /** Creates owned deformation copies and automatic pose synchronization for this base instance. */
    public AnimatedModelLods bindAnimated(GraphicsContext graphics,DefaultModelInstance base) { return bindAnimated(graphics,base,null); }
    /** Reuses a borrowed base morph animator; dispose the returned binding before that animator. */
    public AnimatedModelLods bindAnimated(GraphicsContext graphics,DefaultModelInstance base,CpuMorphModelAnimator baseMorph) {
        requireLive();if (base==null || base.model()!=source) throw new IllegalArgumentException("Binding must use the captured base model");
        return new AnimatedModelLods(graphics,this,base,baseMorph);
    }
    private static boolean hasMorph(io.github.libfdx.collections.ArrayView<ModelNode> nodes) {
        for (int n=0;n<nodes.size();n++) {
            ModelNode node=nodes.get(n);
            for (int p=0;p<node.parts().size();p++) if (node.parts().get(p).morphTargetCount()>0) return true;
            if (hasMorph(node.children())) return true;
        }
        return false;
    }
    private void requireLive() { if (disposed || source.isDisposed()) throw new IllegalStateException("Generated LODs or their base were disposed"); }
    private static boolean hasSkin(io.github.libfdx.collections.ArrayView<ModelNode> nodes) {
        for (int n=0;n<nodes.size();n++) {
            ModelNode node=nodes.get(n);
            for (int p=0;p<node.parts().size();p++) if (node.parts().get(p).skin()!=null) return true;
            if (hasSkin(node.children())) return true;
        }
        return false;
    }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        Throwable failure = null;
        for (Model model : models) try { model.dispose(); } catch (RuntimeException | Error next) {
            if (failure == null) failure = next; else if (failure != next) failure.addSuppressed(next);
        }
        if (failure instanceof RuntimeException e) throw e;
        if (failure instanceof Error e) throw e;
    }
}
