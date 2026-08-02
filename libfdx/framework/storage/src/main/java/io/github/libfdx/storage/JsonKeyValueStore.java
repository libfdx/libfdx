package io.github.libfdx.storage;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.collections.ObjectMapEntry;
import io.github.libfdx.collections.OrderedMap;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonValue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class JsonKeyValueStore implements KeyValueStore {
    private static final String TYPE_STRING = "string";
    private static final String TYPE_INT = "int";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_FLOAT = "float";
    private static final String TYPE_DOUBLE = "double";
    private static final String TYPE_BOOLEAN = "boolean";
    private static final String TYPE_BYTES = "bytes";
    private static final String TYPE_JSON = "json";

    private final StorageBackend backend;
    private final StorageScope scope;
    private final String name;
    private final String path;
    private final StorageCodec codec;
    private final OrderedMap<String, Entry> entries = new OrderedMap<String, Entry>();
    private final Json json = new Json();
    private boolean loaded;
    private boolean dirty;

    JsonKeyValueStore(StorageBackend backend, StorageScope scope, String name, String path, StorageCodec codec) {
        if (backend == null) {
            throw new FdxException("StorageBackend cannot be null");
        }
        if (scope == null) {
            throw new FdxException("StorageScope cannot be null");
        }
        this.backend = backend;
        this.scope = scope;
        this.name = name;
        this.path = path;
        this.codec = codec != null ? codec : StorageCodecs.identity();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public StorageScope scope() {
        return scope;
    }

    @Override
    public boolean loaded() {
        return loaded;
    }

    @Override
    public boolean dirty() {
        return dirty;
    }

    @Override
    public KeyValueStore load() {
        entries.clear();
        byte[] storedBytes = backend.read(scope, path);
        if (storedBytes != null && storedBytes.length > 0) {
            byte[] decoded = codec.decode(storedBytes);
            JsonValue root = json.read(decoded);
            JsonValue storedEntries = root.get("entries");
            if (storedEntries != null && storedEntries.isObject()) {
                ObjectIterator<? extends ObjectMapEntry<String, JsonValue>> iterator =
                        storedEntries.objectMembers().entries().iterator();
                while (iterator.hasNext()) {
                    ObjectMapEntry<String, JsonValue> entry = iterator.next();
                    Entry storedEntry = readEntry(entry.value());
                    if (storedEntry != null) {
                        entries.put(entry.key(), storedEntry);
                    }
                }
            }
        }
        loaded = true;
        dirty = false;
        return this;
    }

    @Override
    public KeyValueStore flush() {
        ensureLoaded();
        byte[] bytes = json.write(writeRoot()).getBytes(StandardCharsets.UTF_8);
        backend.write(scope, path, codec.encode(bytes));
        dirty = false;
        return this;
    }

    @Override
    public boolean contains(String key) {
        ensureLoaded();
        return entries.containsKey(requiredKey(key));
    }

    @Override
    public String[] keys() {
        ensureLoaded();
        String[] keys = new String[entries.size()];
        int index = 0;
        ObjectIterator<String> iterator = entries.keys().iterator();
        while (iterator.hasNext()) {
            keys[index++] = iterator.next();
        }
        return keys;
    }

    @Override
    public KeyValueStore remove(String key) {
        ensureLoaded();
        if (entries.remove(requiredKey(key)) != null) {
            dirty = true;
        }
        return this;
    }

    @Override
    public KeyValueStore clear() {
        ensureLoaded();
        if (!entries.isEmpty()) {
            entries.clear();
            dirty = true;
        }
        return this;
    }

    @Override
    public String getString(String key, String fallback) {
        Entry entry = entry(key);
        return entry != null && TYPE_STRING.equals(entry.type) ? (String) entry.value : fallback;
    }

    @Override
    public KeyValueStore putString(String key, String value) {
        return put(key, TYPE_STRING, value);
    }

    @Override
    public int getInt(String key, int fallback) {
        Entry entry = entry(key);
        return entry != null && TYPE_INT.equals(entry.type) ? ((Integer) entry.value).intValue() : fallback;
    }

    @Override
    public KeyValueStore putInt(String key, int value) {
        return put(key, TYPE_INT, Integer.valueOf(value));
    }

    @Override
    public long getLong(String key, long fallback) {
        Entry entry = entry(key);
        return entry != null && TYPE_LONG.equals(entry.type) ? ((Long) entry.value).longValue() : fallback;
    }

    @Override
    public KeyValueStore putLong(String key, long value) {
        return put(key, TYPE_LONG, Long.valueOf(value));
    }

    @Override
    public float getFloat(String key, float fallback) {
        Entry entry = entry(key);
        return entry != null && TYPE_FLOAT.equals(entry.type) ? ((Float) entry.value).floatValue() : fallback;
    }

    @Override
    public KeyValueStore putFloat(String key, float value) {
        return put(key, TYPE_FLOAT, Float.valueOf(value));
    }

    @Override
    public double getDouble(String key, double fallback) {
        Entry entry = entry(key);
        return entry != null && TYPE_DOUBLE.equals(entry.type) ? ((Double) entry.value).doubleValue() : fallback;
    }

    @Override
    public KeyValueStore putDouble(String key, double value) {
        return put(key, TYPE_DOUBLE, Double.valueOf(value));
    }

    @Override
    public boolean getBoolean(String key, boolean fallback) {
        Entry entry = entry(key);
        return entry != null && TYPE_BOOLEAN.equals(entry.type) ? ((Boolean) entry.value).booleanValue() : fallback;
    }

    @Override
    public KeyValueStore putBoolean(String key, boolean value) {
        return put(key, TYPE_BOOLEAN, Boolean.valueOf(value));
    }

    @Override
    public byte[] getBytes(String key, byte[] fallback) {
        Entry entry = entry(key);
        if (entry == null || !TYPE_BYTES.equals(entry.type)) {
            return StorageCodecs.copy(fallback);
        }
        return StorageCodecs.copy((byte[]) entry.value);
    }

    @Override
    public KeyValueStore putBytes(String key, byte[] value) {
        return put(key, TYPE_BYTES, StorageCodecs.copy(value));
    }

    @Override
    public JsonValue getJson(String key, JsonValue fallback) {
        Entry entry = entry(key);
        return entry != null && TYPE_JSON.equals(entry.type) ? (JsonValue) entry.value : fallback;
    }

    @Override
    public KeyValueStore putJson(String key, JsonValue value) {
        return put(key, TYPE_JSON, value != null ? value : JsonValue.nullValue());
    }

    @Override
    public <T> T getJson(String key, Class<T> type, Json json, T fallback) {
        JsonValue value = getJson(key, null);
        return value != null ? requiredJson(json).read(type, value) : fallback;
    }

    @Override
    public <T> KeyValueStore putJson(String key, Class<T> type, Json json, T value) {
        Json serializer = requiredJson(json);
        return putJson(key, serializer.read(serializer.toJson(type, value)));
    }

    private KeyValueStore put(String key, String type, Object value) {
        ensureLoaded();
        entries.put(requiredKey(key), new Entry(type, value));
        dirty = true;
        return this;
    }

    private Entry entry(String key) {
        ensureLoaded();
        return entries.get(requiredKey(key));
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private String requiredKey(String key) {
        if (key == null || key.trim().length() == 0) {
            throw new FdxException("Storage key cannot be empty");
        }
        return key;
    }

    private Json requiredJson(Json json) {
        if (json == null) {
            throw new FdxException("Json cannot be null");
        }
        return json;
    }

    private JsonValue writeRoot() {
        JsonValue root = JsonValue.object();
        JsonValue storedEntries = JsonValue.object();
        ObjectIterator<OrderedMap.Entry<String, Entry>> iterator = entries.entries().iterator();
        while (iterator.hasNext()) {
            OrderedMap.Entry<String, Entry> mapEntry = iterator.next();
            storedEntries.put(mapEntry.key(), writeEntry(mapEntry.value()));
        }
        root.put("version", 1);
        root.put("entries", storedEntries);
        return root;
    }

    private JsonValue writeEntry(Entry entry) {
        JsonValue value = JsonValue.object();
        value.put("type", entry.type);
        if (TYPE_STRING.equals(entry.type)) {
            value.put("value", (String) entry.value);
        } else if (TYPE_INT.equals(entry.type)) {
            value.put("value", ((Integer) entry.value).intValue());
        } else if (TYPE_LONG.equals(entry.type)) {
            value.put("value", ((Long) entry.value).longValue());
        } else if (TYPE_FLOAT.equals(entry.type)) {
            value.put("value", ((Float) entry.value).floatValue());
        } else if (TYPE_DOUBLE.equals(entry.type)) {
            value.put("value", ((Double) entry.value).doubleValue());
        } else if (TYPE_BOOLEAN.equals(entry.type)) {
            value.put("value", ((Boolean) entry.value).booleanValue());
        } else if (TYPE_BYTES.equals(entry.type)) {
            value.put("value", Base64.getEncoder().encodeToString((byte[]) entry.value));
        } else if (TYPE_JSON.equals(entry.type)) {
            value.put("value", (JsonValue) entry.value);
        }
        return value;
    }

    private Entry readEntry(JsonValue value) {
        if (value == null || !value.isObject()) {
            return null;
        }
        String type = value.stringValue("type", "");
        JsonValue storedValue = value.get("value");
        if (TYPE_STRING.equals(type)) {
            return new Entry(type, storedValue != null ? storedValue.stringValue(null) : null);
        }
        if (TYPE_INT.equals(type)) {
            return new Entry(type, Integer.valueOf(storedValue != null ? storedValue.intValue(0) : 0));
        }
        if (TYPE_LONG.equals(type)) {
            return new Entry(type, Long.valueOf(storedValue != null ? storedValue.longValue(0L) : 0L));
        }
        if (TYPE_FLOAT.equals(type)) {
            return new Entry(type, Float.valueOf(storedValue != null ? storedValue.floatValue(0.0f) : 0.0f));
        }
        if (TYPE_DOUBLE.equals(type)) {
            return new Entry(type, Double.valueOf(storedValue != null ? storedValue.doubleValue(0.0) : 0.0));
        }
        if (TYPE_BOOLEAN.equals(type)) {
            return new Entry(type, Boolean.valueOf(storedValue != null && storedValue.booleanValue(false)));
        }
        if (TYPE_BYTES.equals(type)) {
            String encoded = storedValue != null ? storedValue.stringValue("") : "";
            return new Entry(type, Base64.getDecoder().decode(encoded));
        }
        if (TYPE_JSON.equals(type)) {
            return new Entry(type, storedValue != null ? storedValue : JsonValue.nullValue());
        }
        return null;
    }

    private static final class Entry {
        private final String type;
        private final Object value;

        private Entry(String type, Object value) {
            this.type = type;
            this.value = value;
        }
    }
}
