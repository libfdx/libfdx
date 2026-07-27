package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable public graph output.
 */
public final class ShaderGraphOutput implements Comparable<ShaderGraphOutput> {
    private final ShaderGraphId id;
    private final ShaderGraphType type;
    private final ShaderEndpoint source;
    private final String semantic;

    private ShaderGraphOutput(ShaderGraphId id, ShaderGraphType type,
            ShaderEndpoint source, String semantic) {
        if (id == null || type == null || source == null) {
            throw new FdxException("Shader graph output requires an ID, type, and source");
        }
        this.id = id;
        this.type = type;
        this.source = source;
        this.semantic = semantic != null ? semantic.trim() : "";
    }

    public static ShaderGraphOutput of(String id, ShaderGraphType type,
            ShaderEndpoint source) {
        return new ShaderGraphOutput(ShaderGraphId.of(id), type, source, "");
    }

    public static ShaderGraphOutput semantic(String id, ShaderGraphType type,
            ShaderEndpoint source, String semantic) {
        return new ShaderGraphOutput(ShaderGraphId.of(id), type, source, semantic);
    }

    public ShaderGraphId id() {
        return id;
    }

    public ShaderGraphType type() {
        return type;
    }

    public ShaderEndpoint source() {
        return source;
    }

    public String semantic() {
        return semantic;
    }

    @Override
    public int compareTo(ShaderGraphOutput other) {
        return id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphOutput other
                && id.equals(other.id) && type.equals(other.type)
                && source.equals(other.source) && semantic.equals(other.semantic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, source, semantic);
    }
}
