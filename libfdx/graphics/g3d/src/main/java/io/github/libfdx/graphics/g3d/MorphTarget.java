package io.github.libfdx.graphics.g3d;

public final class MorphTarget {
    private final String id;
    private final float weight;

    public MorphTarget(String id, float weight) {
        this.id = id != null ? id : "";
        this.weight = weight;
    }

    public String id() {
        return id;
    }

    public float weight() {
        return weight;
    }
}
