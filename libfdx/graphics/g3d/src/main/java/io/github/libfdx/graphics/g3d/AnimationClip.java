package io.github.libfdx.graphics.g3d;

/**
 * Represents an animation clip.
 *
 * @author xpenatan
 */
public final class AnimationClip {
    private final String id;
    private final float durationSeconds;

    /**
     * Creates an animation clip.
     *
     * @param id the identifier
     * @param durationSeconds the duration seconds
     */
    public AnimationClip(String id, float durationSeconds) {
        this.id = id != null ? id : "";
        this.durationSeconds = durationSeconds;
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
     * Returns the duration seconds.
     *
     * @return the duration seconds
     */
    public float durationSeconds() {
        return durationSeconds;
    }
}
