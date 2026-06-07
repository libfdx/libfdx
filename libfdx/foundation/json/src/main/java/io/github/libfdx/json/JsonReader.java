package io.github.libfdx.json;

import io.github.libfdx.core.FdxException;

import java.nio.charset.StandardCharsets;

public final class JsonReader {
    private String text;
    private int index;
    private int line;
    private int column;

    public JsonValue parse(byte[] bytes) {
        return parse(new String(bytes != null ? bytes : new byte[0], StandardCharsets.UTF_8));
    }

    public JsonValue parse(String text) {
        this.text = text != null ? text : "";
        index = 0;
        line = 1;
        column = 1;
        JsonValue value = readValue();
        skipWhitespace();
        if (!end()) {
            throw error("Unexpected content after JSON value");
        }
        return value;
    }

    private JsonValue readValue() {
        skipWhitespace();
        if (end()) {
            throw error("Unexpected end of JSON");
        }
        char c = peek();
        if (c == '{') {
            return readObject();
        }
        if (c == '[') {
            return readArray();
        }
        if (c == '"') {
            return JsonValue.value(readString());
        }
        if (c == 't') {
            expect("true");
            return JsonValue.value(true);
        }
        if (c == 'f') {
            expect("false");
            return JsonValue.value(false);
        }
        if (c == 'n') {
            expect("null");
            return JsonValue.nullValue();
        }
        if (c == '-' || isDigit(c)) {
            return readNumber();
        }
        throw error("Unexpected JSON value");
    }

    private JsonValue readObject() {
        expect('{');
        JsonValue object = JsonValue.object();
        skipWhitespace();
        if (consumeIf('}')) {
            return object;
        }
        while (true) {
            skipWhitespace();
            if (end() || peek() != '"') {
                throw error("Expected JSON object member name");
            }
            String name = readString();
            skipWhitespace();
            expect(':');
            object.put(name, readValue());
            skipWhitespace();
            if (consumeIf('}')) {
                return object;
            }
            expect(',');
        }
    }

    private JsonValue readArray() {
        expect('[');
        JsonValue array = JsonValue.array();
        skipWhitespace();
        if (consumeIf(']')) {
            return array;
        }
        while (true) {
            array.add(readValue());
            skipWhitespace();
            if (consumeIf(']')) {
                return array;
            }
            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (!end()) {
            char c = next();
            if (c == '"') {
                return builder.toString();
            }
            if (c < 0x20) {
                throw error("Unescaped control character in JSON string");
            }
            if (c == '\\') {
                builder.append(readEscape());
            }
            else {
                builder.append(c);
            }
        }
        throw error("Unterminated JSON string");
    }

    private String readEscape() {
        if (end()) {
            throw error("Unterminated JSON escape");
        }
        char escaped = next();
        if (escaped == '"' || escaped == '\\' || escaped == '/') {
            return Character.toString(escaped);
        }
        if (escaped == 'b') {
            return "\b";
        }
        if (escaped == 'f') {
            return "\f";
        }
        if (escaped == 'n') {
            return "\n";
        }
        if (escaped == 'r') {
            return "\r";
        }
        if (escaped == 't') {
            return "\t";
        }
        if (escaped == 'u') {
            char first = (char)readHexCodePoint();
            if (Character.isHighSurrogate(first)) {
                if (end() || next() != '\\' || end() || next() != 'u') {
                    throw error("JSON high surrogate must be followed by a low surrogate escape");
                }
                char second = (char)readHexCodePoint();
                if (!Character.isLowSurrogate(second)) {
                    throw error("JSON high surrogate must be followed by a low surrogate escape");
                }
                return new String(Character.toChars(Character.toCodePoint(first, second)));
            }
            if (Character.isLowSurrogate(first)) {
                throw error("JSON low surrogate has no preceding high surrogate");
            }
            return Character.toString(first);
        }
        throw error("Unsupported JSON escape: " + escaped);
    }

    private int readHexCodePoint() {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            if (end()) {
                throw error("Unterminated JSON unicode escape");
            }
            char c = next();
            int digit = Character.digit(c, 16);
            if (digit < 0) {
                throw error("Invalid JSON unicode escape");
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    private JsonValue readNumber() {
        int start = index;
        if (consumeIf('-') && end()) {
            throw error("Expected JSON number digit");
        }
        if (consumeIf('0')) {
            if (!end() && isDigit(peek())) {
                throw error("JSON number must not contain a leading zero");
            }
        }
        else {
            if (end() || !isDigitOneToNine(peek())) {
                throw error("Expected JSON number digit");
            }
            while (!end() && isDigit(peek())) {
                next();
            }
        }
        if (!end() && peek() == '.') {
            next();
            if (end() || !isDigit(peek())) {
                throw error("Expected JSON fraction digit");
            }
            while (!end() && isDigit(peek())) {
                next();
            }
        }
        if (!end() && (peek() == 'e' || peek() == 'E')) {
            next();
            if (!end() && (peek() == '+' || peek() == '-')) {
                next();
            }
            if (end() || !isDigit(peek())) {
                throw error("Expected JSON exponent digit");
            }
            while (!end() && isDigit(peek())) {
                next();
            }
        }
        return JsonValue.numberLiteral(text.substring(start, index));
    }

    private void skipWhitespace() {
        while (!end()) {
            char c = peek();
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return;
            }
            next();
        }
    }

    private void expect(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (end() || next() != value.charAt(i)) {
                throw error("Expected '" + value + "'");
            }
        }
    }

    private void expect(char value) {
        if (end() || next() != value) {
            throw error("Expected '" + value + "'");
        }
    }

    private boolean consumeIf(char value) {
        if (!end() && peek() == value) {
            next();
            return true;
        }
        return false;
    }

    private char peek() {
        return text.charAt(index);
    }

    private char next() {
        char c = text.charAt(index++);
        if (c == '\n') {
            line++;
            column = 1;
        }
        else {
            column++;
        }
        return c;
    }

    private boolean end() {
        return index >= text.length();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isDigitOneToNine(char c) {
        return c >= '1' && c <= '9';
    }

    private FdxException error(String message) {
        return new FdxException(message + " at line " + line + ", column " + column);
    }
}
