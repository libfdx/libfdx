package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.graphics.GraphicsContext;

/** Completed CPU preparation. Publish across threads before beginning its single graphics-thread upload. */
public final class PreparedModelLods {
    final ModelLodInput input;
    final ModelLodSettings settings;
    final LodPreparedMesh[][][] meshes;
    final ModelLodReport[] reports;
    private boolean uploading;
    PreparedModelLods(ModelLodInput input, ModelLodSettings settings, LodPreparedMesh[][][] meshes, ModelLodReport[] reports) {
        this.input = input; this.settings = settings; this.meshes = meshes; this.reports = reports;
    }
    /** Immutable generation statistics, numbered 1 through settings().levelCount(). */
    public ModelLodReport report(int level) { return reports[level - 1]; }
    public ModelLodSettings settings() { return settings; }

    /** Owns any partial GPU resources until take succeeds; dispose on cancellation or failure. */
    public ModelLodUpload beginUpload(GraphicsContext graphics) {
        if (uploading) throw new IllegalStateException("Prepared LODs already consumed");
        if (graphics == null || input.source.isDisposed()) throw new IllegalStateException("Graphics and a live base model are required");
        uploading = true;
        return new ModelLodUpload(graphics,this);
    }
}
