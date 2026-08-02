package io.github.libfdx.json;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.collections.ObjectMap;

/**
 * Represents a json.
 *
 * @author xpenatan
 */
public final class Json {
    private final JsonReader reader = new JsonReader();
    private final ObjectMap<Class<?>, JsonCodec<?>> codecs = new ObjectMap<Class<?>, JsonCodec<?>>();

    /**
     * Runs the read step.
     *
     * @param text the text
     * @return the read
     */
    public JsonValue read(String text) {
        return reader.parse(text);
    }

    /**
     * Runs the read step.
     *
     * @param bytes the bytes
     * @return the read
     */
    public JsonValue read(byte[] bytes) {
        return reader.parse(bytes);
    }

    /**
     * Runs the write step.
     *
     * @param value the value
     * @return the write
     */
    public String write(JsonValue value) {
        return JsonWriter.compact(value);
    }

    /**
     * Runs the write pretty step.
     *
     * @param value the value
     * @return the write pretty
     */
    public String writePretty(JsonValue value) {
        return JsonWriter.pretty(value);
    }

    /**
     * Sets the register and returns this JSON.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @param codec the codec
     * @return this JSON for chaining
     */
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

    /**
     * Runs the from JSON step.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @param text the text
     * @return the from JSON
     */
    public <T> T fromJson(Class<T> type, String text) {
        return read(type, read(text));
    }

    /**
     * Runs the from JSON step.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @param bytes the bytes
     * @return the from JSON
     */
    public <T> T fromJson(Class<T> type, byte[] bytes) {
        return read(type, read(bytes));
    }

    /**
     * Runs the read step.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @param value the value
     * @return the read
     */
    public <T> T read(Class<T> type, JsonValue value) {
        return codec(type).read(this, value);
    }

    /**
     * Runs the to JSON step.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @param value the value
     * @return the to JSON
     */
    public <T> String toJson(Class<T> type, T value) {
        JsonWriter writer = new JsonWriter();
        write(type, writer, value);
        return writer.toString();
    }

    /**
     * Runs the to JSON pretty step.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @param value the value
     * @return the to JSON pretty
     */
    public <T> String toJsonPretty(Class<T> type, T value) {
        JsonWriter writer = JsonWriter.prettyWriter();
        write(type, writer, value);
        return writer.toString();
    }

    /**
     * Runs the write step.
     *
     * @param <T> the value type
     * @param type the expected Java type
     * @param writer the writer
     * @param value the value
     */
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
