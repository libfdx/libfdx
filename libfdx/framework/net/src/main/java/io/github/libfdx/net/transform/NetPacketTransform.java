package io.github.libfdx.net.transform;

import io.github.libfdx.net.buffer.NetReader;
import io.github.libfdx.net.buffer.NetWriter;

/**
 * Transforms packet bytes without owning the encryption or compression algorithm.
 *
 * @author xpenatan
 */
public interface NetPacketTransform {
    /**
     * Returns the maximum encoded or decoded output bytes for an input size.
     *
     * @param inputBytes the input byte count
     * @return the maximum output byte count
     */
    int maxOutputBytes(int inputBytes);

    /**
     * Encodes packet bytes before transport send.
     *
     * @param context the context
     * @param input the input reader
     * @param output the output writer
     * @return the transform result
     */
    NetTransformResult encode(NetTransformContext context, NetReader input, NetWriter output);

    /**
     * Decodes packet bytes after transport receive.
     *
     * @param context the context
     * @param input the input reader
     * @param output the output writer
     * @return the transform result
     */
    NetTransformResult decode(NetTransformContext context, NetReader input, NetWriter output);
}
