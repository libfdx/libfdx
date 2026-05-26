package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;

public final class AnimationController {
    private final ModelInstance instance;
    private AnimationClip clip;
    private float timeSeconds;
    private boolean looping;

    public AnimationController(ModelInstance instance) {
        if (instance == null) {
            throw new FdxException("AnimationController instance cannot be null");
        }
        this.instance = instance;
    }

    public AnimationController play(AnimationClip clip, boolean looping) {
        this.clip = clip;
        this.looping = looping;
        this.timeSeconds = 0.0f;
        return this;
    }

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

    public ModelInstance instance() {
        return instance;
    }

    public AnimationClip clip() {
        return clip;
    }

    public float timeSeconds() {
        return timeSeconds;
    }
}
