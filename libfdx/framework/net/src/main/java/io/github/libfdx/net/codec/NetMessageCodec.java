package io.github.libfdx.net.codec;

import io.github.libfdx.net.buffer.NetReader;
import io.github.libfdx.net.buffer.NetWriter;

/**
 * Serializes messages without reflection.
 *
 * @param <T> the message type
 *
 * @author xpenatan
 */
public interface NetMessageCodec<T> {
    /**
     * Writes a message.
     *
     * @param message the message
     * @param out the writer
     */
    void write(T message, NetWriter out);

    /**
     * Reads into a reusable target.
     *
     * @param in the reader
     * @param target the target
     */
    void read(NetReader in, T target);
}
