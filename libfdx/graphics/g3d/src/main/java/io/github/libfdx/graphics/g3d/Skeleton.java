package io.github.libfdx.graphics.g3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Skeleton {
    private final ArrayList<Bone> bones;

    public Skeleton(List<Bone> bones) {
        this.bones = bones != null ? new ArrayList<Bone>(bones) : new ArrayList<Bone>();
    }

    public List<Bone> bones() {
        return Collections.unmodifiableList(bones);
    }
}
