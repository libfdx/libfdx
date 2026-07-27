package io.github.libfdx.graphics.shadergraph.cache;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * One embedded target artifact encoded as text or base64 binary data.
 */
public final class ShaderGraphCompiledArtifact {
    public enum Encoding {
        TEXT("text"),
        BASE64("base64");

        private final String id;

        Encoding(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Encoding fromId(String id) {
            for (Encoding value : values()) {
                if (value.id.equals(id)) {
                    return value;
                }
            }
            throw new FdxException(
                    "Unknown shader graph artifact encoding: " + id);
        }
    }

    private final String format;
    private final Encoding encoding;
    private final String data;
    private final String contentHash;

    private ShaderGraphCompiledArtifact(String format,
            Encoding encoding, String data) {
        this.format = require(format, "format");
        if (encoding == null) {
            throw new FdxException(
                    "Shader graph artifact encoding cannot be null");
        }
        this.encoding = encoding;
        if (data == null || data.isEmpty()) {
            throw new FdxException(
                    "Shader graph artifact data cannot be empty");
        }
        this.data = data;
        byte[] bytes;
        try {
            bytes = encoding == Encoding.TEXT
                    ? data.getBytes(StandardCharsets.UTF_8)
                    : Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException error) {
            throw new FdxException(
                    "Shader graph artifact base64 data is invalid", error);
        }
        if (bytes.length == 0) {
            throw new FdxException(
                    "Shader graph artifact data cannot be empty");
        }
        contentHash = PortableSha256.hash(bytes);
    }

    public static ShaderGraphCompiledArtifact text(
            String format, String source) {
        return new ShaderGraphCompiledArtifact(
                format, Encoding.TEXT, source);
    }

    public static ShaderGraphCompiledArtifact binary(
            String format, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new FdxException(
                    "Shader graph artifact bytes cannot be empty");
        }
        return new ShaderGraphCompiledArtifact(format, Encoding.BASE64,
                Base64.getEncoder().encodeToString(bytes));
    }

    static ShaderGraphCompiledArtifact encoded(String format,
            Encoding encoding, String data) {
        return new ShaderGraphCompiledArtifact(format, encoding, data);
    }

    public String format() {
        return format;
    }

    public Encoding encoding() {
        return encoding;
    }

    public String encodedData() {
        return data;
    }

    public String contentHash() {
        return contentHash;
    }

    public String text() {
        if (encoding != Encoding.TEXT) {
            throw new FdxException(
                    "Shader graph artifact is not text");
        }
        return data;
    }

    public byte[] bytes() {
        return encoding == Encoding.TEXT
                ? data.getBytes(StandardCharsets.UTF_8)
                : Base64.getDecoder().decode(data);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphCompiledArtifact other
                && format.equals(other.format)
                && encoding == other.encoding
                && data.equals(other.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, encoding, data);
    }

    private static String require(String value, String label) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isEmpty()) {
            throw new FdxException(
                    "Shader graph artifact " + label + " cannot be empty");
        }
        return normalized;
    }
}
