package io.github.libfdx.json;

/**
 * Defines the contract for json codec implementations.
 *
 * @param <T> the value type
 *
 * @author xpenatan
 */
public interface JsonCodec<T> {
    /**
     * Runs the read step.
     *
     * @param json the JSON
     * @param value the value
     * @return the read
     */
    T read(Json json, JsonValue value);

    /**
     * Runs the write step.
     *
     * @param json the JSON
     * @param writer the writer
     * @param value the value
     */
    void write(Json json, JsonWriter writer, T value);
}
