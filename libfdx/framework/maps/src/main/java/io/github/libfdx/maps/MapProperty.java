package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;

/** Immutable, typed authoring metadata. Colors use packed RGBA; object values are stable IDs (0 means unset). */
public final class MapProperty {
    public enum Type { STRING, INT, FLOAT, BOOL, COLOR, FILE, OBJECT }
    private final String name;
    private final Type type;
    private final Object value;
    public MapProperty(String name, Type type, Object value) {
        if (name == null || type == null || value == null) { throw new FdxException("Property fields cannot be null"); }
        boolean valid = switch (type) {
            case STRING, FILE -> value instanceof String;
            case INT, OBJECT -> value instanceof Long;
            case FLOAT -> value instanceof Double && Double.isFinite((Double)value);
            case BOOL -> value instanceof Boolean;
            case COLOR -> value instanceof Integer;
        };
        if (!valid) { throw new FdxException("Invalid value for property " + name + " (" + type + ")"); }
        this.name = name; this.type = type; this.value = value;
    }
    public String name() { return name; }
    public Type type() { return type; }
    public String stringValue() { require(type == Type.STRING || type == Type.FILE); return (String)value; }
    public long longValue() { require(type == Type.INT || type == Type.OBJECT); return (Long)value; }
    public double doubleValue() { require(type == Type.FLOAT); return (Double)value; }
    public boolean booleanValue() { require(type == Type.BOOL); return (Boolean)value; }
    public int rgba() { require(type == Type.COLOR); return (Integer)value; }
    private void require(boolean matches) {
        if (!matches) { throw new FdxException("Wrong accessor for property " + name + " (" + type + ")"); }
    }
}
