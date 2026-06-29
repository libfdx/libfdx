package io.github.libfdx.storage;

/**
 * Provides built-in storage codecs.
 *
 * @author xpenatan
 */
public final class StorageCodecs {
    private static final StorageCodec IDENTITY = new StorageCodec() {
        @Override
        public byte[] encode(byte[] bytes) {
            return copy(bytes);
        }

        @Override
        public byte[] decode(byte[] bytes) {
            return copy(bytes);
        }
    };

    private StorageCodecs() {
    }

    /**
     * Returns a codec that stores bytes unchanged.
     *
     * @return the identity codec
     */
    public static StorageCodec identity() {
        return IDENTITY;
    }

    static byte[] copy(byte[] bytes) {
        if (bytes == null) {
            return new byte[0];
        }
        byte[] copy = new byte[bytes.length];
        System.arraycopy(bytes, 0, copy, 0, bytes.length);
        return copy;
    }
}
