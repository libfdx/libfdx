package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable typed node port.
 */
public final class ShaderGraphPort implements Comparable<ShaderGraphPort> {
    private final ShaderGraphId id;
    private final ShaderGraphType type;
    private final boolean required;
    private final ShaderGraphLiteral defaultValue;

    private ShaderGraphPort(ShaderGraphId id, ShaderGraphType type,
            boolean required, ShaderGraphLiteral defaultValue) {
        if (id == null || type == null) {
            throw new FdxException("Shader graph port requires an ID and type");
        }
        if (defaultValue != null && !type.equals(defaultValue.type())) {
            throw new FdxException("Shader graph port default does not match its type: " + id);
        }
        this.id = id;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
    }

    public static ShaderGraphPort required(String id, ShaderGraphType type) {
        return new ShaderGraphPort(ShaderGraphId.of(id), type, true, null);
    }

    public static ShaderGraphPort optional(String id, ShaderGraphType type,
            ShaderGraphLiteral defaultValue) {
        return new ShaderGraphPort(ShaderGraphId.of(id), type, false, defaultValue);
    }

    public ShaderGraphId id() {
        return id;
    }

    public ShaderGraphType type() {
        return type;
    }

    public boolean required() {
        return required;
    }

    public ShaderGraphLiteral defaultValue() {
        return defaultValue;
    }

    @Override
    public int compareTo(ShaderGraphPort other) {
        return id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphPort other
                && id.equals(other.id) && type.equals(other.type)
                && required == other.required
                && Objects.equals(defaultValue, other.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, required, defaultValue);
    }
}
