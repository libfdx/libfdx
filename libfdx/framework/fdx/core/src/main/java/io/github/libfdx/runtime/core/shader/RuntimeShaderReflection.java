package io.github.libfdx.runtime.core.shader;

import io.github.libfdx.core.FdxException;
import java.util.Arrays;

/**
 * Holds a provider-neutral shader reflection payload produced by the runtime shader compiler.
 *
 * <p>The payload is deliberately opaque to the runtime-core module. Its versioned {@code FDXI}
 * encoding can be decoded by higher-level shader interface APIs without introducing a graphics
 * dependency into the runtime root.</p>
 *
 * @author xpenatan
 */
public final class RuntimeShaderReflection {
    /** Current reflection payload schema version. */
    public static final int SCHEMA_VERSION = 1;

    private static final int HEADER_SIZE = 8;
    private static final byte MAGIC_F = (byte)'F';
    private static final byte MAGIC_D = (byte)'D';
    private static final byte MAGIC_X = (byte)'X';
    private static final byte MAGIC_I = (byte)'I';

    private final byte[] bytes;

    private RuntimeShaderReflection(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Creates an immutable reflection wrapper from an encoded {@code FDXI} payload.
     *
     * @param bytes the encoded payload
     * @return a reflection wrapper
     * @throws FdxException if the payload header or schema version is unsupported
     */
    public static RuntimeShaderReflection fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < HEADER_SIZE) {
            throw new FdxException("Runtime shader reflection payload is truncated");
        }
        if (bytes[0] != MAGIC_F || bytes[1] != MAGIC_D || bytes[2] != MAGIC_X || bytes[3] != MAGIC_I) {
            throw new FdxException("Runtime shader reflection payload has invalid FDXI magic");
        }
        int version = (bytes[4] & 0xff)
                | ((bytes[5] & 0xff) << 8)
                | ((bytes[6] & 0xff) << 16)
                | ((bytes[7] & 0xff) << 24);
        if (version != SCHEMA_VERSION) {
            throw new FdxException("Unsupported runtime shader reflection schema version: " + version);
        }
        return new RuntimeShaderReflection(bytes.clone());
    }

    /**
     * Returns the encoded reflection payload.
     *
     * @return a defensive copy of the payload
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    /**
     * Returns the payload schema version.
     *
     * @return the schema version
     */
    public int schemaVersion() {
        return SCHEMA_VERSION;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RuntimeShaderReflection
                && Arrays.equals(bytes, ((RuntimeShaderReflection)other).bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
