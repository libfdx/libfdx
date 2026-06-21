package io.github.libfdx.storage;

import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonValue;

/**
 * Represents a named persistent key/value store.
 *
 * @author xpenatan
 */
public interface KeyValueStore {
    String name();

    StorageScope scope();

    boolean loaded();

    boolean dirty();

    KeyValueStore load();

    KeyValueStore flush();

    boolean contains(String key);

    String[] keys();

    KeyValueStore remove(String key);

    KeyValueStore clear();

    String getString(String key, String fallback);

    KeyValueStore putString(String key, String value);

    int getInt(String key, int fallback);

    KeyValueStore putInt(String key, int value);

    long getLong(String key, long fallback);

    KeyValueStore putLong(String key, long value);

    float getFloat(String key, float fallback);

    KeyValueStore putFloat(String key, float value);

    double getDouble(String key, double fallback);

    KeyValueStore putDouble(String key, double value);

    boolean getBoolean(String key, boolean fallback);

    KeyValueStore putBoolean(String key, boolean value);

    byte[] getBytes(String key, byte[] fallback);

    KeyValueStore putBytes(String key, byte[] value);

    JsonValue getJson(String key, JsonValue fallback);

    KeyValueStore putJson(String key, JsonValue value);

    <T> T getJson(String key, Class<T> type, Json json, T fallback);

    <T> KeyValueStore putJson(String key, Class<T> type, Json json, T value);
}
