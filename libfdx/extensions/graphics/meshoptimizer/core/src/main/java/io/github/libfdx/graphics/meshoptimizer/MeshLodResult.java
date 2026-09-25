package io.github.libfdx.graphics.meshoptimizer;

/**
 * Actual simplifier output. Error includes the weighted attributes and is relative to mesh extent.
 * {@code unchanged} certifies identical source triangles, winding and vertex attributes, allowing
 * only reindexing/ordering and unused-vertex removal. The generator may then borrow the source
 * GPU mesh instead of uploading this result. A triangle-count match alone is insufficient.
 */
public record MeshLodResult(MeshLodData mesh, float error, boolean unchanged) {
    /** Custom simplifiers conservatively opt out of source-mesh reuse. */
    public MeshLodResult(MeshLodData mesh,float error) { this(mesh,error,false); }
    public MeshLodResult {
        if (mesh == null || !Float.isFinite(error) || error < 0)
            throw new IllegalArgumentException("LOD result needs geometry and finite nonnegative error");
    }
}
