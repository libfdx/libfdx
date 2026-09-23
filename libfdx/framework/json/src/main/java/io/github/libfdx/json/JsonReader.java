package io.github.libfdx.json;

import io.github.libfdx.core.FdxException;

import java.nio.charset.StandardCharsets;

/**
 * Represents a json reader.
 *
 * @author xpenatan
 */
public final class JsonReader {
    private String text;
    private int index;
    private int line;
    private int column;
    private final java.util.ArrayDeque<Frame> stack = new java.util.ArrayDeque<>();
    private JsonValue parsed;
    private StringBuilder string;
    private boolean memberName, complete;

    /**
     * Runs the parse step.
     *
     * @param bytes the bytes
     * @return the parse
     */
    public JsonValue parse(byte[] bytes) {
        return parse(new String(bytes != null ? bytes : new byte[0], StandardCharsets.UTF_8));
    }

    /**
     * Runs the parse step.
     *
     * @param text the text
     * @return the parse
     */
    public JsonValue parse(String text) {
        begin(text);
        while (!step(8192)) { }
        return result();
    }

    /** Starts a resumable parse. One caller owns this reader until completion. */
    public JsonReader begin(String text) {
        this.text = text != null ? text : "";
        index = 0;
        line = 1;
        column = 1;
        stack.clear(); parsed = null; string = null; complete = false;
        return this;
    }

    /** Advances by a character budget, yielding even inside long strings. Scalar numbers and
     * escapes are indivisible; allocation and number conversion can exceed this work budget. */
    public boolean step(int maxCharacters) {
        if (maxCharacters < 1) throw error("JSON character budget must be positive");
        if (text == null) throw error("JSON parse has not started");
        int start = index;
        while (!complete && index - start < maxCharacters) {
            if (string != null) {
                if (end()) throw error("Unterminated JSON string");
                char c = next();
                if (c == '"') {
                    String value = string.toString(); string = null;
                    if (memberName) { stack.peek().name = value; stack.peek().state = 1; }
                    else accept(JsonValue.value(value));
                } else if (c < 0x20) throw error("Unescaped control character in JSON string");
                else if (c == '\\') string.append(readEscape());
                else string.append(c);
                continue;
            }
            if (!end() && (peek() == ' ' || peek() == '\n' || peek() == '\r' || peek() == '\t')) { next(); continue; }
            Frame frame = stack.peek();
            if (frame == null && parsed != null) {
                if (!end()) throw error("Unexpected content after JSON value");
                complete = true; continue;
            }
            if (end()) throw error("Unexpected end of JSON");
            if (frame != null) {
                char close = frame.object ? '}' : ']';
                if (frame.state == 3) {
                    if (consumeIf(close)) { stack.pop(); continue; }
                    expect(','); frame.state = frame.object ? 4 : 2; continue;
                }
                if (frame.object && (frame.state == 0 || frame.state == 4)) {
                    if (frame.state == 0 && consumeIf('}')) { stack.pop(); continue; }
                    expect('"'); memberName = true; string = new StringBuilder(); continue;
                }
                if (frame.object && frame.state == 1) { expect(':'); frame.state = 2; continue; }
                if (!frame.object && frame.state == 0 && consumeIf(']')) { stack.pop(); continue; }
            }
            char c = peek();
            if (c == '{' || c == '[') {
                next(); JsonValue value = c == '{' ? JsonValue.object() : JsonValue.array();
                accept(value); stack.push(new Frame(value, c == '{'));
            } else if (c == '"') {
                next(); memberName = false; string = new StringBuilder();
            } else accept(readValue());
        }
        return complete;
    }

    /** Returns the completed tree; incomplete parses must not be published. */
    public JsonValue result() {
        if (!complete) throw error("JSON parse is still pending");
        return parsed;
    }

    private void accept(JsonValue value) {
        Frame frame = stack.peek();
        if (frame == null) parsed = value;
        else {
            if (frame.object) frame.value.put(frame.name, value); else frame.value.add(value);
            frame.state = 3;
        }
    }

    private static final class Frame {
        final JsonValue value;
        final boolean object;
        int state;
        String name;
        Frame(JsonValue value, boolean object) { this.value = value; this.object = object; }
    }

    private JsonValue readValue() {
        skipWhitespace();
        if (end()) {
            throw error("Unexpected end of JSON");
        }
        char c = peek();
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
