package io.github.libfdx.json;

public interface JsonCodec<T> {
    T read(Json json, JsonValue value);

    void write(Json json, JsonWriter writer, T value);
}
