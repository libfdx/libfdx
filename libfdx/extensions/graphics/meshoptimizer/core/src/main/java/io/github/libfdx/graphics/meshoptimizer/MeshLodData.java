package io.github.libfdx.graphics.meshoptimizer;

/** Detached standard PBR mesh data. Constructor copies arrays; returned arrays are borrowed read-only. */
public final class MeshLodData {
    public static final int POSITION = 0, COLOR = 1, BAKED_COLOR = 2, NORMAL = 3, UV0 = 4, UV1 = 5,
            TANGENT = 6, PBR = 7, BAKED_PBR = 8, EMISSIVE = 9, BAKED_EMISSIVE = 10, CHANNEL_COUNT = 11;
    private static final int[] COMPONENTS = {3,4,4,3,2,2,4,3,3,3,3};
    private final float[][] channels;
    private final int[] indices;
    private final int vertexCount;
    final boolean compact;
    final boolean[] locked;
    private final MeshLodDeformation deformation;
    private final int[] origins;

    public MeshLodData(float[][] channels, int[] indices) {
        this(channels,indices,null);
    }
    public MeshLodData(float[][] channels,int[] indices,MeshLodDeformation deformation) {
        compact = false;
        locked = null;
        this.deformation=deformation; origins=null;
        if (channels == null || channels.length != CHANNEL_COUNT || channels[POSITION] == null
                || channels[POSITION].length == 0 || channels[POSITION].length % 3 != 0)
            throw new IllegalArgumentException("LOD mesh requires xyz positions and standard attribute channels");
        vertexCount = channels[POSITION].length / 3;
        if (deformation != null && deformation.vertexCount() != vertexCount) throw new IllegalArgumentException("Deformation vertex domain mismatch");
        this.channels = new float[CHANNEL_COUNT][];
        for (int c = 0; c < CHANNEL_COUNT; c++) if (channels[c] != null) {
            if (channels[c].length != (long)vertexCount * COMPONENTS[c])
                throw new IllegalArgumentException("LOD attribute length does not match vertices: channel " + c);
            for (float value : channels[c]) if (!Float.isFinite(value))
                throw new IllegalArgumentException("LOD attributes must be finite: channel " + c);
            this.channels[c] = channels[c].clone();
        }
        if (indices == null || indices.length == 0 || indices.length % 3 != 0)
            throw new IllegalArgumentException("LOD mesh requires a nonempty triangle list");
        for (int index : indices) if (index < 0 || index >= vertexCount)
            throw new IllegalArgumentException("LOD index outside vertex range");
        this.indices = indices.clone();
    }
    /** Internal ownership transfer: arrays populated from validated immutable input. */
    static MeshLodData owned(float[][] channels, int[] indices, boolean[] locked, boolean compact) {
        return owned(channels,indices,locked,compact,null,null);
    }
    static MeshLodData owned(float[][] channels,int[] indices,boolean[] locked,boolean compact,MeshLodDeformation deformation,int[] origins) {
        return new MeshLodData(channels,indices,locked,compact,deformation,origins);
    }
    private MeshLodData(float[][] channels, int[] indices, boolean[] locked, boolean compact,MeshLodDeformation deformation,int[] origins) {
        this.channels = channels; this.indices = indices; this.locked = locked; this.compact = compact;
        this.deformation=deformation;this.origins=origins;
        vertexCount = channels[POSITION].length / 3;
    }
    /** Explicit constraints supplied by shared-boundary preparation. */
    public boolean vertexLocked(int vertex) { return locked != null && locked[vertex]; }
    MeshLodData withLocks(boolean[] constraints) { return owned(channels,indices,constraints,compact,deformation,origins); }
    /** Immutable original-domain data, or null for static geometry. */
    public MeshLodDeformation deformation() { return deformation; }
    /** Source-domain vertex for preserved influences, targets and sample positions. */
    public int sourceVertex(int vertex) { return origins == null ? vertex : origins[vertex]; }
    public static int components(int channel) { return COMPONENTS[channel]; }
    public float[] channel(int channel) { return channels[channel]; }
    public int[] indices() { return indices; }
    public int vertexCount() { return vertexCount; }
    public int triangleCount() { return indices.length / 3; }
}
