package io.github.libfdx.graphics.g3d;

/**
 * Represents a morph target.
 *
 * @author xpenatan
 */
public final class MorphTarget {
    private final String id;
    private final float weight;
    private final float[] positions, normals, tangents;

    /**
     * Creates a morph target.
     *
     * @param id the identifier
     * @param weight the weight
     */
    public MorphTarget(String id, float weight) {
        this(id, weight, null, null, null);
    }

    /** Immutable copied XYZ deltas, applied before skinning. Null attributes have zero delta.
     * Tangent handedness is inherited from the base and is never displaced. */
    public MorphTarget(String id, float weight, float[] positions, float[] normals, float[] tangents) {
        if (!Float.isFinite(weight)) throw new IllegalArgumentException("Morph weight must be finite");
        this.id = id != null ? id : "";
        this.weight = weight;
        this.positions = copy(positions);
        this.normals = copy(normals);
        this.tangents = copy(tangents);
        int count = vertexCount();
        for (float[] data : new float[][] {this.positions, this.normals, this.tangents})
            if (data != null && data.length != count * 3) throw new IllegalArgumentException("Morph attribute counts differ");
    }

    private static float[] copy(float[] data) {
        if (data == null) return null;
        if (data.length == 0 || data.length % 3 != 0) throw new IllegalArgumentException("Morph deltas require XYZ vertices");
        for (float value : data) if (!Float.isFinite(value)) throw new IllegalArgumentException("Morph deltas must be finite");
        return data.clone();
    }
    public int vertexCount() { return positions != null ? positions.length/3 : normals != null ? normals.length/3 : tangents != null ? tangents.length/3 : 0; }
    /** Whether this target supplies a POSITION(0), NORMAL(1), or TANGENT(2) delta stream. */
    public boolean hasAttribute(int attribute) { return data(attribute) != null; }
    /** Reads one immutable delta; absent streams return zero. */
    public float delta(int attribute, int vertex, int axis) {
        if (axis < 0 || axis > 2 || vertex < 0 || vertexCount() != 0 && vertex >= vertexCount()) throw new IndexOutOfBoundsException();
        float[] data = data(attribute); return data == null ? 0 : data[vertex*3+axis];
    }
    private float[] data(int attribute) { return switch(attribute) { case 0 -> positions; case 1 -> normals; case 2 -> tangents; default -> throw new IndexOutOfBoundsException(); }; }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * Returns the weight.
     *
     * @return the weight
     */
    public float weight() {
        return weight;
    }
}
