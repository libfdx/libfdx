package io.github.libfdx.samples.g2d.spritemovement.component;

import io.github.libfdx.ecs.component.Component;

/** Runtime-only bridge between ECS handles and stable scene identity. */
public final class SceneEntityComponent implements Component {
    public long id;
    public String name = "Entity";
    public long parentId;

    public SceneEntityComponent() {
    }

    public SceneEntityComponent(long id, String name) {
        if (id <= 0L) {
            throw new IllegalArgumentException("id must be positive.");
        }
        this.id = id;
        this.name = normalizeName(name);
    }

    public static String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "Entity" : normalized;
    }
}
