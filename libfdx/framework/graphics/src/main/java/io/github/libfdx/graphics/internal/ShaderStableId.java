package io.github.libfdx.graphics.internal;

import io.github.libfdx.core.FdxException;

import java.util.Locale;

/**
 * Shared validation for portable shader value identifiers.
 */
public final class ShaderStableId {
    private static final int MAX_LENGTH = 128;

    private ShaderStableId() {
    }

    /**
     * Normalizes and validates an identifier.
     *
     * @param value the identifier
     * @param kind the diagnostic kind
     * @return the normalized identifier
     */
    public static String normalize(String value, String kind) {
        String name = kind != null && kind.length() > 0 ? kind : "Shader";
        if (value == null) {
            throw new FdxException(name + " ID cannot be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 0) {
            throw new FdxException(name + " ID cannot be empty");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new FdxException(name + " ID cannot exceed " + MAX_LENGTH + " characters");
        }
        if (!isAlphaNumeric(normalized.charAt(0))) {
            throw new FdxException(name + " ID must start with an ASCII letter or digit: " + value);
        }
        for (int i = 1; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (!isAlphaNumeric(character) && character != '.' && character != '-' && character != '_'
                    && character != ':' && character != '/') {
                throw new FdxException(name + " ID contains an unsupported character: " + value);
            }
        }
        return normalized;
    }

    /**
     * Validates a non-empty version or option value without case normalization.
     *
     * @param value the value
     * @param kind the diagnostic kind
     * @return the trimmed value
     */
    public static String requireValue(String value, String kind) {
        String name = kind != null && kind.length() > 0 ? kind : "Shader value";
        if (value == null || value.trim().length() == 0) {
            throw new FdxException(name + " cannot be empty");
        }
        return value.trim();
    }

    private static boolean isAlphaNumeric(char character) {
        return character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9';
    }
}
