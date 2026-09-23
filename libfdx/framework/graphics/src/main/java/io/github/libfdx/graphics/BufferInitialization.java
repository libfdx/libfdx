package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import java.nio.ByteBuffer;

/** Shared validation for initial vertex/index uploads; resource ownership remains provider-owned. */
public final class BufferInitialization {
    private BufferInitialization() { }

    public static void validate(Buffer buffer, int offset, ByteBuffer data) {
        if (buffer == null || data == null) throw new FdxException("Buffer and initialization data cannot be null");
        if (buffer.usage() != BufferUsage.VERTEX && buffer.usage() != BufferUsage.INDEX)
            throw new FdxException("Range initialization requires a vertex or index buffer");
        int size = data.remaining();
        if (offset < 0 || size <= 0 || (offset & 3) != 0 || (size & 3) != 0 || offset > buffer.size() - size)
            throw new FdxException("Buffer initialization range must fit and be aligned to four bytes");
    }
}
