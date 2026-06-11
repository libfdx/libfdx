package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

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

    public static ShaderAttribute of(int location, String name, VertexFormat format) {
        return new ShaderAttribute(location, name, format);
    }

    public int location() {
        return location;
    }

    public String name() {
        return name;
    }

    public VertexFormat format() {
        return format;
    }
}
