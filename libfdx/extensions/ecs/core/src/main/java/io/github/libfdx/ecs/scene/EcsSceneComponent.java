package io.github.libfdx.ecs.scene;

import io.github.libfdx.json.JsonValue;

/** One typed component payload in a core ECS scene document. */
public final class EcsSceneComponent {
    private final String typeId;
    private final JsonValue data;

    public EcsSceneComponent(String typeId, JsonValue data) {
        if (typeId == null || typeId.trim().length() == 0 || data == null) {
            throw new IllegalArgumentException("typeId and data cannot be blank or null.");
        }
        this.typeId = typeId.trim();
        this.data = data;
    }

    public String typeId() {
        return typeId;
    }

    public JsonValue data() {
        return data;
    }
}
