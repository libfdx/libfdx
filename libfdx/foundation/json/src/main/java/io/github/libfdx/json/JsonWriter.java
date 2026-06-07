package io.github.libfdx.json;

import io.github.libfdx.core.FdxException;

import java.util.ArrayList;
import java.util.Map;

public final class JsonWriter {
    private final StringBuilder builder = new StringBuilder();
    private final ArrayList<Context> stack = new ArrayList<Context>();
    private final boolean pretty;
    private int indent;
    private boolean rootWritten;

    public JsonWriter() {
        this(false);
    }

    public JsonWriter(boolean pretty) {
        this.pretty = pretty;
    }

    public static JsonWriter prettyWriter() {
        return new JsonWriter(true);
    }

    public static String compact(JsonValue value) {
        JsonWriter writer = new JsonWriter();
        writer.value(value);
        return writer.toString();
    }

    public static String pretty(JsonValue value) {
        JsonWriter writer = prettyWriter();
        writer.value(value);
        return writer.toString();
    }

    public JsonWriter object() {
        beforeValue();
        builder.append('{');
        stack.add(new Context(true));
        indent++;
        return this;
    }

    public JsonWriter endObject() {
        Context context = current();
        if (!context.object) {
            throw new FdxException("JSON writer is not inside an object");
        }
        if (context.expectingValue) {
            throw new FdxException("JSON object member is missing a value");
        }
        stack.remove(stack.size() - 1);
        indent--;
        if (!context.first) {
            newline();
        }
        builder.append('}');
        return this;
    }

    public JsonWriter array() {
        beforeValue();
        builder.append('[');
        stack.add(new Context(false));
        indent++;
        return this;
    }

    public JsonWriter endArray() {
        Context context = current();
        if (context.object) {
            throw new FdxException("JSON writer is not inside an array");
        }
        stack.remove(stack.size() - 1);
        indent--;
        if (!context.first) {
            newline();
        }
        builder.append(']');
        return this;
    }

    public JsonWriter name(String name) {
        Context context = current();
        if (!context.object) {
            throw new FdxException("JSON member names can only be written inside an object");
        }
        if (name == null) {
            throw new FdxException("JSON object member name cannot be null");
        }
        if (context.expectingValue) {
            throw new FdxException("JSON object member is missing a value");
        }
        if (!context.first) {
            builder.append(',');
        }
        newline();
        context.first = false;
        appendQuoted(builder, name);
        builder.append(':');
        if (pretty) {
            builder.append(' ');
        }
        context.expectingValue = true;
        return this;
    }

    public JsonWriter value(JsonValue value) {
        writeTree(value != null ? value : JsonValue.nullValue());
        return this;
    }

    public JsonWriter value(String value) {
        if (value == null) {
            return nullValue();
        }
        beforeValue();
        appendQuoted(builder, value);
        return this;
    }

    public JsonWriter value(int value) {
        return number(Integer.toString(value));
    }

    public JsonWriter value(long value) {
        return number(Long.toString(value));
    }

    public JsonWriter value(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new FdxException("JSON number must be finite: " + value);
        }
        return number(Float.toString(value));
    }

    public JsonWriter value(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new FdxException("JSON number must be finite: " + value);
        }
        return number(Double.toString(value));
    }

    public JsonWriter value(boolean value) {
        beforeValue();
        builder.append(value ? "true" : "false");
        return this;
    }

    public JsonWriter nullValue() {
        beforeValue();
        builder.append("null");
        return this;
    }

    @Override
    public String toString() {
        if (!stack.isEmpty()) {
            throw new FdxException("JSON writer has unclosed containers");
        }
        return builder.toString();
    }

    private JsonWriter number(String value) {
        beforeValue();
        builder.append(value);
        return this;
    }

    private void writeTree(JsonValue value) {
        if (value.isObject()) {
            object();
            for (Map.Entry<String, JsonValue> entry : value.objectMembers().entrySet()) {
                name(entry.getKey()).value(entry.getValue());
            }
            endObject();
        }
        else if (value.isArray()) {
            array();
            for (JsonValue child : value.arrayValues()) {
                value(child);
            }
            endArray();
        }
        else if (value.isString()) {
            value(value.stringValue());
        }
        else if (value.isNumber()) {
            number(value.numberLiteral());
        }
        else if (value.isBoolean()) {
            value(value.booleanValue());
        }
        else {
            nullValue();
        }
    }

    private void beforeValue() {
        if (stack.isEmpty()) {
            if (rootWritten) {
                throw new FdxException("JSON writer root value was already written");
            }
            rootWritten = true;
            return;
        }
        Context context = current();
        if (context.object) {
            if (!context.expectingValue) {
                throw new FdxException("JSON object value must be preceded by a member name");
            }
            context.expectingValue = false;
        }
        else {
            if (!context.first) {
                builder.append(',');
            }
            newline();
            context.first = false;
        }
    }

    private Context current() {
        if (stack.isEmpty()) {
            throw new FdxException("JSON writer is not inside a container");
        }
        return stack.get(stack.size() - 1);
    }

    private void newline() {
        if (!pretty) {
            return;
        }
        builder.append('\n');
        for (int i = 0; i < indent; i++) {
            builder.append("  ");
        }
    }

    private static void appendQuoted(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                builder.append('\\').append(c);
            }
            else if (c == '\b') {
                builder.append("\\b");
            }
            else if (c == '\f') {
                builder.append("\\f");
            }
            else if (c == '\n') {
                builder.append("\\n");
            }
            else if (c == '\r') {
                builder.append("\\r");
            }
            else if (c == '\t') {
                builder.append("\\t");
            }
            else if (c < 0x20) {
                String hex = Integer.toHexString(c);
                builder.append("\\u");
                for (int pad = hex.length(); pad < 4; pad++) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            else {
                builder.append(c);
            }
        }
        builder.append('"');
    }

    private static final class Context {
        private final boolean object;
        private boolean first = true;
        private boolean expectingValue;

        Context(boolean object) {
            this.object = object;
        }
    }
}
