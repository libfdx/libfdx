package io.github.libfdx.graphics.g3d;

/**
 * Represents a morph target.
 *
 * @author xpenatan
 */
public final class MorphTarget {
    private final String id;
    private final float weight;

    /**
     * Creates a morph target.
     *
     * @param id the identifier
     * @param weight the weight
     */
    public MorphTarget(String id, float weight) {
        this.id = id != null ? id : "";
        this.weight = weight;
    }

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
