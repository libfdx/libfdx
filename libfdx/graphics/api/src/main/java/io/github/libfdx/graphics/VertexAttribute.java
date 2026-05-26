package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

public final class VertexAttribute {
    private final int location;
    private final VertexFormat format;
    private final int offset;

    private VertexAttribute(int location, VertexFormat format, int offset) {
        if (location < 0) {
            throw new FdxException("Vertex attribute location cannot be negative");
        }
        if (format == null) {
            throw new FdxException("Vertex attribute format cannot be null");
        }
        if (offset < 0) {
            throw new FdxException("Vertex attribute offset cannot be negative");
        }
        this.location = location;
        this.format = format;
        this.offset = offset;
    }

    public static VertexAttribute of(int location, VertexFormat format, int offset) {
        return new VertexAttribute(location, format, offset);
    }

    public int location() {
        return location;
    }

    public VertexFormat format() {
        return format;
    }

    public int offset() {
        return offset;
    }
}
