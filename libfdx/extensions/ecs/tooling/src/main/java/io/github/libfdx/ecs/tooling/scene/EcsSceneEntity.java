package io.github.libfdx.ecs.tooling.scene;

import java.util.List;

/** Stable entity data in a scene document. */
public final class EcsSceneEntity {
    private final long id;
    private final String name;
    private final long parentId;
    private final List<EcsSceneComponent> components;

    public EcsSceneEntity(long id, String name, long parentId, List<EcsSceneComponent> components) {
        if (id <= 0L) {
            throw new IllegalArgumentException("Entity id must be positive.");
        }
        if (parentId < 0L || components == null) {
            throw new IllegalArgumentException("parentId must be non-negative and components cannot be null.");
        }
        this.id = id;
        this.name = name == null ? "" : name;
        this.parentId = parentId;
        this.components = List.copyOf(components);
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long parentId() {
        return parentId;
    }

    public int componentCount() {
        return components.size();
    }

    public EcsSceneComponent component(int index) {
        return components.get(index);
    }

    public List<EcsSceneComponent> components() {
        return components;
    }
}
