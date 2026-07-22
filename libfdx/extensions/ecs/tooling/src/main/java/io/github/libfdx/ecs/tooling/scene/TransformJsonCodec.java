package io.github.libfdx.ecs.tooling.scene;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.ecs.transform.Transform;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonCodec;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

/** Persists transform TRS data while excluding the derived matrix. */
public final class TransformJsonCodec implements JsonCodec<Transform> {
    @Override
    public Transform read(Json json, JsonValue value) {
        JsonValue position = requireArray(value, "position", 3);
        JsonValue rotation = requireArray(value, "rotation", 4);
        JsonValue scale = requireArray(value, "scale", 3);

        return new Transform()
            .position(
                position.require(0).floatValue(),
                position.require(1).floatValue(),
                position.require(2).floatValue())
            .rotation(
                rotation.require(0).floatValue(),
                rotation.require(1).floatValue(),
                rotation.require(2).floatValue(),
                rotation.require(3).floatValue())
            .scale(
                scale.require(0).floatValue(),
                scale.require(1).floatValue(),
                scale.require(2).floatValue());
    }

    @Override
    public void write(Json json, JsonWriter writer, Transform transform) {
        if (transform == null) {
            throw new FdxException("Transform cannot be null");
        }
        float x = transform.rotation().x();
        float y = transform.rotation().y();
        float z = transform.rotation().z();
        float w = transform.rotation().w();
        float length = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (length == 0.0f) {
            x = 0.0f;
            y = 0.0f;
            z = 0.0f;
            w = 1.0f;
        } else {
            float inverse = 1.0f / length;
            x *= inverse;
            y *= inverse;
            z *= inverse;
            w *= inverse;
        }

        writer.object()
                .name("position").array()
                    .value(transform.x()).value(transform.y()).value(transform.z())
                .endArray()
                .name("rotation").array()
                    .value(x).value(y).value(z).value(w)
                .endArray()
                .name("scale").array()
                    .value(transform.scaleX()).value(transform.scaleY()).value(transform.scaleZ())
                .endArray()
            .endObject();
    }

    private static JsonValue requireArray(JsonValue object, String name, int size) {
        JsonValue value = object.require(name);
        if (!value.isArray() || value.size() != size) {
            throw new FdxException("Transform '" + name + "' must contain " + size + " numbers");
        }
        return value;
    }
}
