package io.github.libfdx.json;

import io.github.libfdx.core.FdxException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a json value.
 *
 * @author xpenatan
 */
public final class JsonValue {
    /**
     * Lists the supported type values.
     *
     * @author xpenatan
     */
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

    /**
     * Creates a JSON value.
     *
     * @return a new JSON value
     */
    public static JsonValue object() {
        return new JsonValue(Type.OBJECT, new LinkedHashMap<String, JsonValue>(), null, null, null, false);
    }

    /**
     * Creates a JSON value.
     *
     * @return a new JSON value
     */
    public static JsonValue array() {
        return new JsonValue(Type.ARRAY, null, new ArrayList<JsonValue>(), null, null, false);
    }

    /**
     * Creates a JSON value.
     *
     * @param value the value
     * @return a new JSON value
     */
    public static JsonValue value(String value) {
        return value == null ? NULL : new JsonValue(Type.STRING, null, null, value, null, false);
    }

    /**
     * Creates a JSON value.
     *
     * @param value the value
     * @return a new JSON value
     */
    public static JsonValue value(int value) {
        return numberLiteral(Integer.toString(value));
    }

    /**
     * Creates a JSON value.
     *
     * @param value the value
     * @return a new JSON value
     */
    public static JsonValue value(long value) {
        return numberLiteral(Long.toString(value));
    }

    /**
     * Creates a JSON value.
     *
     * @param value the value
     * @return a new JSON value
     */
    public static JsonValue value(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new FdxException("JSON number must be finite: " + value);
        }
        return numberLiteral(Float.toString(value));
    }

    /**
     * Creates a JSON value.
     *
     * @param value the value
     * @return a new JSON value
     */
    public static JsonValue value(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new FdxException("JSON number must be finite: " + value);
        }
        return numberLiteral(Double.toString(value));
    }

    /**
     * Creates a JSON value.
     *
     * @param value the value
     * @return a new JSON value
     */
    public static JsonValue value(boolean value) {
        return new JsonValue(Type.BOOLEAN, null, null, null, null, value);
    }

    /**
     * Creates a JSON value.
     *
     * @return a new JSON value
     */
    public static JsonValue nullValue() {
        return NULL;
    }

    static JsonValue numberLiteral(String value) {
        if (value == null || value.length() == 0) {
            throw new FdxException("JSON number literal cannot be empty");
        }
        return new JsonValue(Type.NUMBER, null, null, null, value, false);
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public Type type() {
        return type;
    }

    /**
     * Returns whether object is enabled or true.
     *
     * @return true if object is enabled or true; false otherwise
     */
    public boolean isObject() {
        return type == Type.OBJECT;
    }

    /**
     * Returns whether array is enabled or true.
     *
     * @return true if array is enabled or true; false otherwise
     */
    public boolean isArray() {
        return type == Type.ARRAY;
    }

    /**
     * Returns whether string is enabled or true.
     *
     * @return true if string is enabled or true; false otherwise
     */
    public boolean isString() {
        return type == Type.STRING;
    }

    /**
     * Returns whether number is enabled or true.
     *
     * @return true if number is enabled or true; false otherwise
     */
    public boolean isNumber() {
        return type == Type.NUMBER;
    }

    /**
     * Returns whether boolean is enabled or true.
     *
     * @return true if boolean is enabled or true; false otherwise
     */
    public boolean isBoolean() {
        return type == Type.BOOLEAN;
    }

    /**
     * Returns whether null is enabled or true.
     *
     * @return true if null is enabled or true; false otherwise
     */
    public boolean isNull() {
        return type == Type.NULL;
    }

    /**
     * Returns the size.
     *
     * @return the size
     */
    public int size() {
        if (type == Type.OBJECT) {
            return object.size();
        }
        if (type == Type.ARRAY) {
            return array.size();
        }
        return 0;
    }

    /**
     * Returns the object members.
     *
     * @return the object members
     */
    public Map<String, JsonValue> objectMembers() {
        expect(Type.OBJECT);
        return Collections.unmodifiableMap(object);
    }

    /**
     * Returns the array values.
     *
     * @return the array values
     */
    public List<JsonValue> arrayValues() {
        expect(Type.ARRAY);
        return Collections.unmodifiableList(array);
    }

    /**
     * Sets the put and returns this JSON value.
     *
     * @param name the name
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue put(String name, JsonValue value) {
        expect(Type.OBJECT);
        if (name == null) {
            throw new FdxException("JSON object member name cannot be null");
        }
        object.put(name, value != null ? value : NULL);
        return this;
    }

    /**
     * Sets the put and returns this JSON value.
     *
     * @param name the name
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue put(String name, String value) {
        return put(name, JsonValue.value(value));
    }

    /**
     * Sets the put and returns this JSON value.
     *
     * @param name the name
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue put(String name, int value) {
        return put(name, JsonValue.value(value));
    }

    /**
     * Sets the put and returns this JSON value.
     *
     * @param name the name
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue put(String name, long value) {
        return put(name, JsonValue.value(value));
    }

    /**
     * Sets the put and returns this JSON value.
     *
     * @param name the name
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue put(String name, float value) {
        return put(name, JsonValue.value(value));
    }

    /**
     * Sets the put and returns this JSON value.
     *
     * @param name the name
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue put(String name, double value) {
        return put(name, JsonValue.value(value));
    }

    /**
     * Sets the put and returns this JSON value.
     *
     * @param name the name
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue put(String name, boolean value) {
        return put(name, JsonValue.value(value));
    }

    /**
     * Sets the add and returns this JSON value.
     *
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue add(JsonValue value) {
        expect(Type.ARRAY);
        array.add(value != null ? value : NULL);
        return this;
    }

    /**
     * Sets the add and returns this JSON value.
     *
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue add(String value) {
        return add(JsonValue.value(value));
    }

    /**
     * Sets the add and returns this JSON value.
     *
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue add(int value) {
        return add(JsonValue.value(value));
    }

    /**
     * Sets the add and returns this JSON value.
     *
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue add(long value) {
        return add(JsonValue.value(value));
    }

    /**
     * Sets the add and returns this JSON value.
     *
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue add(float value) {
        return add(JsonValue.value(value));
    }

    /**
     * Sets the add and returns this JSON value.
     *
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue add(double value) {
        return add(JsonValue.value(value));
    }

    /**
     * Sets the add and returns this JSON value.
     *
     * @param value the value
     * @return this JSON value for chaining
     */
    public JsonValue add(boolean value) {
        return add(JsonValue.value(value));
    }

    /**
     * Sets the get and returns this JSON value.
     *
     * @param name the name
     * @return this JSON value for chaining
     */
    public JsonValue get(String name) {
        expect(Type.OBJECT);
        return object.get(name);
    }

    /**
     * Sets the require and returns this JSON value.
     *
     * @param name the name
     * @return this JSON value for chaining
     */
    public JsonValue require(String name) {
        JsonValue value = get(name);
        if (value == null) {
            throw new FdxException("Missing JSON object member '" + name + "'");
        }
        return value;
    }

    /**
     * Sets the get and returns this JSON value.
     *
     * @param index the index
     * @return this JSON value for chaining
     */
    public JsonValue get(int index) {
        expect(Type.ARRAY);
        if (index < 0 || index >= array.size()) {
            return null;
        }
        return array.get(index);
    }

    /**
     * Sets the require and returns this JSON value.
     *
     * @param index the index
     * @return this JSON value for chaining
     */
    public JsonValue require(int index) {
        JsonValue value = get(index);
        if (value == null) {
            throw new FdxException("Missing JSON array element " + index);
        }
        return value;
    }

    /**
     * Returns the string value.
     *
     * @return the string value
     */
    public String stringValue() {
        expect(Type.STRING);
        return string;
    }

    /**
     * Runs the string value step.
     *
     * @param fallback the fallback
     * @return the string value
     */
    public String stringValue(String fallback) {
        return type == Type.STRING ? string : fallback;
    }

    /**
     * Runs the string value step.
     *
     * @param name the name
     * @param fallback the fallback
     * @return the string value
     */
    public String stringValue(String name, String fallback) {
        JsonValue value = optional(name);
        return value != null ? value.stringValue(fallback) : fallback;
    }

    /**
     * Runs the require string step.
     *
     * @param name the name
     * @return the require string
     */
    public String requireString(String name) {
        return require(name).stringValue();
    }

    /**
     * Returns the number literal.
     *
     * @return the number literal
     */
    public String numberLiteral() {
        expect(Type.NUMBER);
        return number;
    }

    /**
     * Returns the double value.
     *
     * @return the double value
     */
    public double doubleValue() {
        expect(Type.NUMBER);
        return parseDouble();
    }

    /**
     * Runs the double value step.
     *
     * @param fallback the fallback
     * @return the double value
     */
    public double doubleValue(double fallback) {
        return type == Type.NUMBER ? parseDouble() : fallback;
    }

    /**
     * Runs the double value step.
     *
     * @param name the name
     * @param fallback the fallback
     * @return the double value
     */
    public double doubleValue(String name, double fallback) {
        JsonValue value = optional(name);
        return value != null ? value.doubleValue(fallback) : fallback;
    }

    /**
     * Returns the float value.
     *
     * @return the float value
     */
    public float floatValue() {
        return (float)doubleValue();
    }

    /**
     * Runs the float value step.
     *
     * @param fallback the fallback
     * @return the float value
     */
    public float floatValue(float fallback) {
        return type == Type.NUMBER ? (float)parseDouble() : fallback;
    }

    /**
     * Runs the float value step.
     *
     * @param name the name
     * @param fallback the fallback
     * @return the float value
     */
    public float floatValue(String name, float fallback) {
        JsonValue value = optional(name);
        return value != null ? value.floatValue(fallback) : fallback;
    }

    /**
     * Returns the int value.
     *
     * @return the int value
     */
    public int intValue() {
        expect(Type.NUMBER);
        return parseDecimal().intValue();
    }

    /**
     * Runs the int value step.
     *
     * @param fallback the fallback
     * @return the int value
     */
    public int intValue(int fallback) {
        return type == Type.NUMBER ? parseDecimal().intValue() : fallback;
    }

    /**
     * Runs the int value step.
     *
     * @param name the name
     * @param fallback the fallback
     * @return the int value
     */
    public int intValue(String name, int fallback) {
        JsonValue value = optional(name);
        return value != null ? value.intValue(fallback) : fallback;
    }

    /**
     * Returns the long value.
     *
     * @return the long value
     */
    public long longValue() {
        expect(Type.NUMBER);
        return parseDecimal().longValue();
    }

    /**
     * Runs the long value step.
     *
     * @param fallback the fallback
     * @return the long value
     */
    public long longValue(long fallback) {
        return type == Type.NUMBER ? parseDecimal().longValue() : fallback;
    }

    /**
     * Runs the long value step.
     *
     * @param name the name
     * @param fallback the fallback
     * @return the long value
     */
    public long longValue(String name, long fallback) {
        JsonValue value = optional(name);
        return value != null ? value.longValue(fallback) : fallback;
    }

    /**
     * Returns the boolean value.
     *
     * @return true if boolean value succeeds or is active; false otherwise
     */
    public boolean booleanValue() {
        expect(Type.BOOLEAN);
        return bool;
    }

    /**
     * Runs the boolean value step.
     *
     * @param fallback the fallback
     * @return true if boolean value succeeds or is active; false otherwise
     */
    public boolean booleanValue(boolean fallback) {
        return type == Type.BOOLEAN ? bool : fallback;
    }

    /**
     * Runs the boolean value step.
     *
     * @param name the name
     * @param fallback the fallback
     * @return true if boolean value succeeds or is active; false otherwise
     */
    public boolean booleanValue(String name, boolean fallback) {
        JsonValue value = optional(name);
        return value != null ? value.booleanValue(fallback) : fallback;
    }

    /**
     * Returns the to JSON.
     *
     * @return the to JSON
     */
    public String toJson() {
        return JsonWriter.compact(this);
    }

    /**
     * Returns the to pretty JSON.
     *
     * @return the to pretty JSON
     */
    public String toPrettyJson() {
        return JsonWriter.pretty(this);
    }

    /**
     * Returns a readable string representation of this instance.
     *
     * @return the to string
     */
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
