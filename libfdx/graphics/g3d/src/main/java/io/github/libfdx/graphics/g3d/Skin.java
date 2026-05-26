package io.github.libfdx.graphics.g3d;

public final class Skin {
    private final String id;
    private final Skeleton skeleton;

    public Skin(String id, Skeleton skeleton) {
        this.id = id != null ? id : "";
        this.skeleton = skeleton;
    }

    public String id() {
        return id;
    }

    public Skeleton skeleton() {
        return skeleton;
    }
}
