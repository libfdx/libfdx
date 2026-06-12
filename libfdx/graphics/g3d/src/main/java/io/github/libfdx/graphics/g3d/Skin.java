package io.github.libfdx.graphics.g3d;

/**
 * Represents a skin.
 *
 * @author xpenatan
 */
public final class Skin {
    private final String id;
    private final Skeleton skeleton;

    /**
     * Creates a skin.
     *
     * @param id the identifier
     * @param skeleton the skeleton
     */
    public Skin(String id, Skeleton skeleton) {
        this.id = id != null ? id : "";
        this.skeleton = skeleton;
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
     * Returns the skeleton.
     *
     * @return the skeleton
     */
    public Skeleton skeleton() {
        return skeleton;
    }
}
