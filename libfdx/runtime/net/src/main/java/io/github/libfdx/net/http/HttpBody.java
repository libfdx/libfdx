package io.github.libfdx.net.http;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Stores an HTTP body.
 *
 * @author xpenatan
 */
public final class HttpBody {
    private static final byte[] EMPTY = new byte[0];
    private final byte[] bytes;

    private HttpBody(byte[] bytes, boolean copy) {
        if (bytes == null || bytes.length == 0) {
            this.bytes = EMPTY;
        } else if (copy) {
            this.bytes = new byte[bytes.length];
            System.arraycopy(bytes, 0, this.bytes, 0, bytes.length);
        } else {
            this.bytes = bytes;
        }
    }

    /**
     * Creates a body from bytes.
     *
     * @param bytes the bytes
     * @return the body
     */
    public static HttpBody bytes(byte[] bytes) {
        return new HttpBody(bytes, true);
    }

    /**
     * Creates a body from text.
     *
     * @param text the text
     * @param charset the charset
     * @return the body
     */
    public static HttpBody text(String text, Charset charset) {
        Charset actualCharset = charset != null ? charset : StandardCharsets.UTF_8;
        return new HttpBody((text != null ? text : "").getBytes(actualCharset), false);
    }

    /**
     * Returns a copy of the bytes.
     *
     * @return the bytes
     */
    public byte[] bytes() {
        byte[] copy = new byte[bytes.length];
        System.arraycopy(bytes, 0, copy, 0, bytes.length);
        return copy;
    }

    /**
     * Returns the body as text.
     *
     * @param charset the charset
     * @return the text
     */
    public String text(Charset charset) {
        return new String(bytes, charset != null ? charset : StandardCharsets.UTF_8);
    }

    /**
     * Returns the body length.
     *
     * @return the length
     */
    public int length() {
        return bytes.length;
    }
}
