package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;

/**
 * Represents an animation controller.
 *
 * @author xpenatan
 */
public final class AnimationController {
    private final ModelInstance instance;
    private final Matrix4 sampledTransform = new Matrix4();
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
        timeSeconds = 0.0f;
        applyClip();
        return this;
    }

    /**
     * Sets the animation time and returns this controller.
     *
     * @param timeSeconds the time seconds
     * @return this animation controller for chaining
     */
    public AnimationController time(float timeSeconds) {
        if (Float.isNaN(timeSeconds)) {
            throw new FdxException("Animation time cannot be NaN");
        }
        this.timeSeconds = normalizedTime(timeSeconds);
        applyClip();
        return this;
    }

    /**
     * Updates this instance.
     *
     * @param deltaSeconds the delta seconds
     * @return this animation controller for chaining
     */
    public AnimationController update(float deltaSeconds) {
        if (Float.isNaN(deltaSeconds)) {
            throw new FdxException("Animation delta cannot be NaN");
        }
        if (clip == null) {
            return this;
        }
        timeSeconds = normalizedTime(timeSeconds + deltaSeconds);
        applyClip();
        return this;
    }

    /**
     * Clears the active clip and returns this controller.
     *
     * @return this animation controller for chaining
     */
    public AnimationController stop() {
        clip = null;
        timeSeconds = 0.0f;
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

    private float normalizedTime(float timeSeconds) {
        if (clip == null) {
            return timeSeconds;
        }
        float duration = clip.durationSeconds();
        if (duration <= 0.0f) {
            return 0.0f;
        }
        if (!looping) {
            return Math.max(0.0f, Math.min(duration, timeSeconds));
        }
        float wrapped = timeSeconds % duration;
        return wrapped < 0.0f ? wrapped + duration : wrapped;
    }

    private void applyClip() {
        if (clip == null) {
            return;
        }
        AnimationClip.NodeTransformChannel[] channels = clip.nodeTransformChannelsUnsafe();
        if (channels.length == 0) {
            return;
        }
        if (!(instance instanceof DefaultModelInstance)) {
            throw new FdxException("Node transform animation requires DefaultModelInstance");
        }
        DefaultModelInstance defaultInstance = (DefaultModelInstance)instance;
        for (int i = 0; i < channels.length; i++) {
            AnimationClip.NodeTransformChannel channel = channels[i];
            defaultInstance.nodeTransform(channel.nodeId(), channel.sample(timeSeconds, sampledTransform));
        }
    }
}
