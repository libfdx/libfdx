package io.github.libfdx.json;

import io.github.libfdx.core.FdxException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonValue {
    public enum Type {
        OBJECT,
        ARRAY,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    private static final JsonValue NULL = new JsonValue(Type.NULL, null, null, null, null, false);

    private final Type type;
    private final LinkedHashMap<String, JsonValue> object;
    private final ArrayList<JsonValue> array;
    private final String string;
    private final String number;
    private final boolean bool;

    private JsonValue(Type type, LinkedHashMap<String, JsonValue> object, ArrayList<JsonValue> array, String string,
            String number, boolean bool) {
        this.type = type;
        this.object = object;
        this.array = array;
        this.string = string;
        this.number = number;
        this.bool = bool;
    }

    public static JsonValue object() {
        return new JsonValue(Type.OBJECT, new LinkedHashMap<String, JsonValue>(), null, null, null, false);
    }

    public static JsonValue array() {
        return new JsonValue(Type.ARRAY, null, new ArrayList<JsonValue>(), null, null, false);
    }

    public static JsonValue value(String value) {
        return value == null ? NULL : new JsonValue(Type.STRING, null, null, value, null, false);
    }

    public static JsonValue value(int value) {
        return numberLiteral(Integer.toString(value));
    }

    public static JsonValue value(long value) {
        return numberLiteral(Long.toString(value));
    }

    public static JsonValue value(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new FdxException("JSON number must be finite: " + value);
        }
        return numberLiteral(Float.toString(value));
    }

    public static JsonValue value(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new FdxException("JSON number must be finite: " + value);
        }
        return numberLiteral(Double.toString(value));
    }

    public static JsonValue value(boolean value) {
        return new JsonValue(Type.BOOLEAN, null, null, null, null, value);
    }

    public static JsonValue nullValue() {
        return NULL;
    }

    static JsonValue numberLiteral(String value) {
        if (value == null || value.length() == 0) {
            throw new FdxException("JSON number literal cannot be empty");
        }
        return new JsonValue(Type.NUMBER, null, null, null, value, false);
    }

    public Type type() {
        return type;
    }

    public boolean isObject() {
        return type == Type.OBJECT;
    }

    public boolean isArray() {
        return type == Type.ARRAY;
    }

    public boolean isString() {
        return type == Type.STRING;
    }

    public boolean isNumber() {
        return type == Type.NUMBER;
    }

    public boolean isBoolean() {
        return type == Type.BOOLEAN;
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public int size() {
        if (type == Type.OBJECT) {
            return object.size();
        }
        if (type == Type.ARRAY) {
            return array.size();
        }
        return 0;
    }

    public Map<String, JsonValue> objectMembers() {
        expect(Type.OBJECT);
        return Collections.unmodifiableMap(object);
    }

    public List<JsonValue> arrayValues() {
        expect(Type.ARRAY);
        return Collections.unmodifiableList(array);
    }

    public JsonValue put(String name, JsonValue value) {
        expect(Type.OBJECT);
        if (name == null) {
            throw new FdxException("JSON object member name cannot be null");
        }
        object.put(name, value != null ? value : NULL);
        return this;
    }

    public JsonValue put(String name, String value) {
        return put(name, JsonValue.value(value));
    }

    public JsonValue put(String name, int value) {
        return put(name, JsonValue.value(value));
    }

    public JsonValue put(String name, long value) {
        return put(name, JsonValue.value(value));
    }

    public JsonValue put(String name, float value) {
        return put(name, JsonValue.value(value));
    }

    public JsonValue put(String name, double value) {
        return put(name, JsonValue.value(value));
    }

    public JsonValue put(String name, boolean value) {
        return put(name, JsonValue.value(value));
    }

    public JsonValue add(JsonValue value) {
        expect(Type.ARRAY);
        array.add(value != null ? value : NULL);
        return this;
    }

    public JsonValue add(String value) {
        return add(JsonValue.value(value));
    }

    public JsonValue add(int value) {
        return add(JsonValue.value(value));
    }

    public JsonValue add(long value) {
        return add(JsonValue.value(value));
    }

    public JsonValue add(float value) {
        return add(JsonValue.value(value));
    }

    public JsonValue add(double value) {
        return add(JsonValue.value(value));
    }

    public JsonValue add(boolean value) {
        return add(JsonValue.value(value));
    }

    public JsonValue get(String name) {
        expect(Type.OBJECT);
        return object.get(name);
    }

    public JsonValue require(String name) {
        JsonValue value = get(name);
        if (value == null) {
            throw new FdxException("Missing JSON object member '" + name + "'");
        }
        return value;
    }

    public JsonValue get(int index) {
        expect(Type.ARRAY);
        if (index < 0 || index >= array.size()) {
            return null;
        }
        return array.get(index);
    }

    public JsonValue require(int index) {
        JsonValue value = get(index);
        if (value == null) {
            throw new FdxException("Missing JSON array element " + index);
        }
        return value;
    }

    public String stringValue() {
        expect(Type.STRING);
        return string;
    }

    public String stringValue(String fallback) {
        return type == Type.STRING ? string : fallback;
    }

    public String stringValue(String name, String fallback) {
        JsonValue value = optional(name);
        return value != null ? value.stringValue(fallback) : fallback;
    }

    public String requireString(String name) {
        return require(name).stringValue();
    }

    public String numberLiteral() {
        expect(Type.NUMBER);
        return number;
    }

    public double doubleValue() {
        expect(Type.NUMBER);
        return parseDouble();
    }

    public double doubleValue(double fallback) {
        return type == Type.NUMBER ? parseDouble() : fallback;
    }

    public double doubleValue(String name, double fallback) {
        JsonValue value = optional(name);
        return value != null ? value.doubleValue(fallback) : fallback;
    }

    public float floatValue() {
        return (float)doubleValue();
    }

    public float floatValue(float fallback) {
        return type == Type.NUMBER ? (float)parseDouble() : fallback;
    }

    public float floatValue(String name, float fallback) {
        JsonValue value = optional(name);
        return value != null ? value.floatValue(fallback) : fallback;
    }

    public int intValue() {
        expect(Type.NUMBER);
        return parseDecimal().intValue();
    }

    public int intValue(int fallback) {
        return type == Type.NUMBER ? parseDecimal().intValue() : fallback;
    }

    public int intValue(String name, int fallback) {
        JsonValue value = optional(name);
        return value != null ? value.intValue(fallback) : fallback;
    }

    public long longValue() {
        expect(Type.NUMBER);
        return parseDecimal().longValue();
    }

    public long longValue(long fallback) {
        return type == Type.NUMBER ? parseDecimal().longValue() : fallback;
    }

    public long longValue(String name, long fallback) {
        JsonValue value = optional(name);
        return value != null ? value.longValue(fallback) : fallback;
    }

    public boolean booleanValue() {
        expect(Type.BOOLEAN);
        return bool;
    }

    public boolean booleanValue(boolean fallback) {
        return type == Type.BOOLEAN ? bool : fallback;
    }

    public boolean booleanValue(String name, boolean fallback) {
        JsonValue value = optional(name);
        return value != null ? value.booleanValue(fallback) : fallback;
    }

    public String toJson() {
        return JsonWriter.compact(this);
    }

    public String toPrettyJson() {
        return JsonWriter.pretty(this);
    }

    @Override
    public String toString() {
        return toJson();
    }

    private JsonValue optional(String name) {
        expect(Type.OBJECT);
        return object.get(name);
    }

    private double parseDouble() {
        return Double.parseDouble(number);
    }

    private BigDecimal parseDecimal() {
        return new BigDecimal(number);
    }

    private void expect(Type expected) {
        if (type != expected) {
            throw new FdxException("Expected JSON " + expected.name().toLowerCase() + " but was "
                    + type.name().toLowerCase());
        }
    }
}
