package io.github.libfdx.json;

import io.github.libfdx.core.FdxException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Json {
    private final JsonReader reader = new JsonReader();
    private final Map<Class<?>, JsonCodec<?>> codecs = new LinkedHashMap<Class<?>, JsonCodec<?>>();

    public JsonValue read(String text) {
        return reader.parse(text);
    }

    public JsonValue read(byte[] bytes) {
        return reader.parse(bytes);
    }

    public String write(JsonValue value) {
        return JsonWriter.compact(value);
    }

    public String writePretty(JsonValue value) {
        return JsonWriter.pretty(value);
    }

    public <T> Json register(Class<T> type, JsonCodec<T> codec) {
        if (type == null) {
            throw new FdxException("JSON codec type cannot be null");
        }
        if (codec == null) {
            throw new FdxException("JSON codec cannot be null for " + type.getName());
        }
        codecs.put(type, codec);
        return this;
    }

    public <T> T fromJson(Class<T> type, String text) {
        return read(type, read(text));
    }

    public <T> T fromJson(Class<T> type, byte[] bytes) {
        return read(type, read(bytes));
    }

    public <T> T read(Class<T> type, JsonValue value) {
        return codec(type).read(this, value);
    }

    public <T> String toJson(Class<T> type, T value) {
        JsonWriter writer = new JsonWriter();
        write(type, writer, value);
        return writer.toString();
    }

    public <T> String toJsonPretty(Class<T> type, T value) {
        JsonWriter writer = JsonWriter.prettyWriter();
        write(type, writer, value);
        return writer.toString();
    }

    public <T> void write(Class<T> type, JsonWriter writer, T value) {
        if (writer == null) {
            throw new FdxException("JSON writer cannot be null");
        }
        codec(type).write(this, writer, value);
    }

    @SuppressWarnings("unchecked")
    private <T> JsonCodec<T> codec(Class<T> type) {
        if (type == null) {
            throw new FdxException("JSON codec type cannot be null");
        }
        JsonCodec<?> codec = codecs.get(type);
        if (codec == null) {
            throw new FdxException("No JSON codec registered for " + type.getName());
        }
        return (JsonCodec<T>)codec;
    }
}
