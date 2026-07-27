package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * One ordered field in a graph structure type.
 */
public final class ShaderStructField {
    private final ShaderGraphId id;
    private final ShaderGraphType type;

    private ShaderStructField(ShaderGraphId id, ShaderGraphType type) {
        if (id == null || type == null) {
            throw new FdxException("Shader structure fields require an ID and type");
        }
        this.id = id;
        this.type = type;
    }

    public static ShaderStructField of(String id, ShaderGraphType type) {
        return new ShaderStructField(ShaderGraphId.of(id), type);
    }

    public ShaderGraphId id() {
        return id;
    }

    public ShaderGraphType type() {
        return type;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderStructField other
                && id.equals(other.id) && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }
}
