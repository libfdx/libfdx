package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;

/**
 * Represents an animation controller.
 *
 * @author xpenatan
 */
public final class AnimationController {
    private final ModelInstance instance;
    private AnimationClip clip;
    private float timeSeconds;
    private boolean looping;

    /**
     * Creates an animation controller.
     *
     * @param instance the instance
     */
    public AnimationController(ModelInstance instance) {
        if (instance == null) {
            throw new FdxException("AnimationController instance cannot be null");
        }
        this.instance = instance;
    }

    /**
     * Sets the play and returns this animation controller.
     *
     * @param clip the clip
     * @param looping the looping
     * @return this animation controller for chaining
     */
    public AnimationController play(AnimationClip clip, boolean looping) {
        this.clip = clip;
        this.looping = looping;
        this.timeSeconds = 0.0f;
        return this;
    }

    /**
     * Updates this instance.
     *
     * @param deltaSeconds the delta seconds
     * @return this animation controller for chaining
     */
    public AnimationController update(float deltaSeconds) {
        if (clip == null) {
            return this;
        }
        timeSeconds += deltaSeconds;
        if (looping && clip.durationSeconds() > 0.0f) {
            timeSeconds = timeSeconds % clip.durationSeconds();
        }
        return this;
    }

    /**
     * Returns the instance.
     *
     * @return the instance
     */
    public ModelInstance instance() {
        return instance;
    }

    /**
     * Returns the clip.
     *
     * @return the clip
     */
    public AnimationClip clip() {
        return clip;
    }

    /**
     * Returns the time seconds.
     *
     * @return the time seconds
     */
    public float timeSeconds() {
        return timeSeconds;
    }
}
