package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Represents a shader attribute.
 *
 * @author xpenatan
 */
public final class ShaderAttribute {
    private final int location;
    private final String name;
    private final VertexFormat format;

    private ShaderAttribute(int location, String name, VertexFormat format) {
        if (location < 0) {
            throw new FdxException("Shader attribute location cannot be negative");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new FdxException("Shader attribute name cannot be empty");
        }
        if (format == null) {
            throw new FdxException("Shader attribute format cannot be null");
        }
        this.location = location;
        this.name = name;
        this.format = format;
    }

    /**
     * Creates a shader attribute from the supplied values.
     *
     * @param location the location
     * @param name the name
     * @param format the format
     * @return a new shader attribute
     */
    public static ShaderAttribute of(int location, String name, VertexFormat format) {
        return new ShaderAttribute(location, name, format);
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
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the format.
     *
     * @return the format
     */
    public VertexFormat format() {
        return format;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderAttribute other && location == other.location && name.equals(other.name)
                && format == other.format;
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, name, format);
    }
}
