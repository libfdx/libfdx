package io.github.libfdx.graphics.g3d;

public final class AnimationClip {
    private final String id;
    private final float durationSeconds;

    public AnimationClip(String id, float durationSeconds) {
        this.id = id != null ? id : "";
        this.durationSeconds = durationSeconds;
    }

    public String id() {
        return id;
    }

    public float durationSeconds() {
        return durationSeconds;
    }
}
