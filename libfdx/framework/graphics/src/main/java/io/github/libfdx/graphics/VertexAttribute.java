package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Represents a vertex attribute.
 *
 * @author xpenatan
 */
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

    /**
     * Creates a vertex attribute from the supplied values.
     *
     * @param location the location
     * @param format the format
     * @param offset the offset
     * @return a new vertex attribute
     */
    public static VertexAttribute of(int location, VertexFormat format, int offset) {
        return new VertexAttribute(location, format, offset);
    }

    /**
     * Returns the location.
     *
     * @return the location
     */
    public int location() {
        return location;
    }

    /**
     * Returns the format.
     *
     * @return the format
     */
    public VertexFormat format() {
        return format;
    }

    /**
     * Returns the offset.
     *
     * @return the offset
     */
    public int offset() {
        return offset;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof VertexAttribute other
                && location == other.location && format == other.format
                && offset == other.offset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, format, offset);
    }
}
