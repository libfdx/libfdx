package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GltfJson {
    private final String text;
    private int index;

    private GltfJson(String text) {
        this.text = text != null ? text : "";
    }

    static Object parse(String text) {
        GltfJson parser = new GltfJson(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.end()) {
            throw new FdxException("Unexpected JSON content at " + parser.index);
        }
        return value;
    }

    private Object readValue() {
        skipWhitespace();
        if (end()) {
            throw new FdxException("Unexpected end of JSON");
        }
        char c = text.charAt(index);
        if (c == '{') {
            return readObject();
        }
        if (c == '[') {
            return readArray();
        }
        if (c == '"') {
            return readString();
        }
        if (c == 't') {
            expect("true");
            return Boolean.TRUE;
        }
        if (c == 'f') {
            expect("false");
            return Boolean.FALSE;
        }
        if (c == 'n') {
            expect("null");
            return null;
        }
        return readNumber();
    }

    private Map<String, Object> readObject() {
        expect('{');
        LinkedHashMap<String, Object> object = new LinkedHashMap<String, Object>();
        skipWhitespace();
        if (peek('}')) {
            index++;
            return object;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            object.put(key, readValue());
            skipWhitespace();
            if (peek('}')) {
                index++;
                return object;
            }
            expect(',');
        }
    }

    private List<Object> readArray() {
        expect('[');
        ArrayList<Object> array = new ArrayList<Object>();
        skipWhitespace();
        if (peek(']')) {
            index++;
            return array;
        }
        while (true) {
            array.add(readValue());
            skipWhitespace();
            if (peek(']')) {
                index++;
                return array;
            }
            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (!end()) {
            char c = text.charAt(index++);
            if (c == '"') {
                return builder.toString();
            }
            if (c == '\\') {
                if (end()) {
                    throw new FdxException("Unterminated JSON escape");
                }
                char escaped = text.charAt(index++);
                if (escaped == '"' || escaped == '\\' || escaped == '/') {
                    builder.append(escaped);
                }
                else if (escaped == 'b') {
                    builder.append('\b');
                }
                else if (escaped == 'f') {
                    builder.append('\f');
                }
                else if (escaped == 'n') {
                    builder.append('\n');
                }
                else if (escaped == 'r') {
                    builder.append('\r');
                }
                else if (escaped == 't') {
                    builder.append('\t');
                }
                else if (escaped == 'u') {
                    builder.append((char) Integer.parseInt(readHex(4), 16));
                }
                else {
                    throw new FdxException("Unsupported JSON escape: " + escaped);
                }
            }
            else {
                builder.append(c);
            }
        }
        throw new FdxException("Unterminated JSON string");
    }

    private String readHex(int count) {
        if (index + count > text.length()) {
            throw new FdxException("Unterminated JSON unicode escape");
        }
        String value = text.substring(index, index + count);
        index += count;
        return value;
    }

    private Number readNumber() {
        int start = index;
        if (peek('-')) {
            index++;
        }
        while (!end() && Character.isDigit(text.charAt(index))) {
            index++;
        }
        if (!end() && text.charAt(index) == '.') {
            index++;
            while (!end() && Character.isDigit(text.charAt(index))) {
                index++;
            }
        }
        if (!end() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
            index++;
            if (!end() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                index++;
            }
            while (!end() && Character.isDigit(text.charAt(index))) {
                index++;
            }
        }
        if (start == index) {
            throw new FdxException("Expected JSON number at " + index);
        }
        return Double.valueOf(text.substring(start, index));
    }

    private void expect(String value) {
        if (!text.startsWith(value, index)) {
            throw new FdxException("Expected '" + value + "' at " + index);
        }
        index += value.length();
    }

    private void expect(char value) {
        if (end() || text.charAt(index) != value) {
            throw new FdxException("Expected '" + value + "' at " + index);
        }
        index++;
    }

    private boolean peek(char value) {
        return !end() && text.charAt(index) == value;
    }

    private void skipWhitespace() {
        while (!end()) {
            char c = text.charAt(index);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return;
            }
            index++;
        }
    }

    private boolean end() {
        return index >= text.length();
    }
}
