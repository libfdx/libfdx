package io.github.libfdx.ecs.tooling.scene;

import io.github.libfdx.ecs.component.TransformComponent;
import io.github.libfdx.ecs.transform.Transform;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonCodec;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

/** JSON codec for the standard ECS {@link TransformComponent}. */
public final class TransformComponentJsonCodec implements JsonCodec<TransformComponent> {
    private final TransformJsonCodec transformCodec = new TransformJsonCodec();

    @Override
    public TransformComponent read(Json json, JsonValue value) {
        Transform transform = transformCodec.read(json, value);
        return new TransformComponent(transform);
    }

    @Override
    public void write(Json json, JsonWriter writer, TransformComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("component cannot be null.");
        }
        transformCodec.write(json, writer, component.transform);
    }
}
