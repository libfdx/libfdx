package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Stable, serialization-safe identifier used by graph assets and their
 * semantic members.
 */
public final class ShaderGraphId implements Comparable<ShaderGraphId> {
    private final String value;

    private ShaderGraphId(String value) {
        this.value = validate(value);
    }

    public static ShaderGraphId of(String value) {
        return new ShaderGraphId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public int compareTo(ShaderGraphId other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphId other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }

    private static String validate(String source) {
        if (source == null) {
            throw new FdxException("Shader graph ID cannot be null");
        }
        String result = source.trim();
        if (result.isEmpty()) {
            throw new FdxException("Shader graph ID cannot be empty");
        }
        char first = result.charAt(0);
        if (!asciiLetter(first) && first != '_') {
            throw new FdxException("Shader graph ID must start with an ASCII letter or underscore: "
                    + source);
        }
        for (int i = 1; i < result.length(); i++) {
            char value = result.charAt(i);
            if (!asciiLetter(value) && !asciiDigit(value)
                    && value != '_' && value != '-' && value != '.'
                    && value != ':' && value != '/') {
                throw new FdxException("Shader graph ID contains an unsupported character: "
                        + source);
            }
        }
        return result;
    }

    private static boolean asciiLetter(char value) {
        return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z';
    }

    private static boolean asciiDigit(char value) {
        return value >= '0' && value <= '9';
    }
}
