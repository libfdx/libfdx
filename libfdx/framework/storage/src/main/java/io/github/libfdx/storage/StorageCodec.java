package io.github.libfdx.storage;

/**
 * Transforms persisted storage bytes.
 *
 * @author xpenatan
 */
public interface StorageCodec {
    /**
     * Encodes plain bytes before they are persisted.
     *
     * @param bytes the plain bytes
     * @return the encoded bytes
     */
    byte[] encode(byte[] bytes);

    /**
     * Decodes persisted bytes before they are parsed.
     *
     * @param bytes the stored bytes
     * @return the decoded bytes
     */
    byte[] decode(byte[] bytes);
}
