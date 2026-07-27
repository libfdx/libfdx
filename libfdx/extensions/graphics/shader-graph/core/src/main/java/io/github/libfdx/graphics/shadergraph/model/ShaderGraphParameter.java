package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable typed public input of a graph.
 */
public final class ShaderGraphParameter implements Comparable<ShaderGraphParameter> {
    private final ShaderGraphId id;
    private final ShaderGraphType type;
    private final ShaderGraphParameterKind kind;
    private final ShaderGraphLiteral defaultValue;
    private final String semantic;

    private ShaderGraphParameter(ShaderGraphId id, ShaderGraphType type,
            ShaderGraphParameterKind kind, ShaderGraphLiteral defaultValue,
            String semantic) {
        if (id == null || type == null || kind == null) {
            throw new FdxException("Shader graph parameter requires an ID, type, and kind");
        }
        if (defaultValue != null && !type.equals(defaultValue.type())) {
            throw new FdxException("Shader graph parameter default does not match its type: " + id);
        }
        if (kind == ShaderGraphParameterKind.STATIC_SWITCH && !type.isBoolean()) {
            throw new FdxException("Static shader graph switches must be boolean: " + id);
        }
        this.id = id;
        this.type = type;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.semantic = semantic != null ? semantic.trim() : "";
    }

    public static ShaderGraphParameter of(String id, ShaderGraphType type,
            ShaderGraphParameterKind kind, ShaderGraphLiteral defaultValue) {
        return new ShaderGraphParameter(ShaderGraphId.of(id), type, kind,
                defaultValue, "");
    }

    public static ShaderGraphParameter semantic(String id, ShaderGraphType type,
            ShaderGraphParameterKind kind, ShaderGraphLiteral defaultValue,
            String semantic) {
        return new ShaderGraphParameter(ShaderGraphId.of(id), type, kind,
                defaultValue, semantic);
    }

    public ShaderGraphId id() {
        return id;
    }

    public ShaderGraphType type() {
        return type;
    }

    public ShaderGraphParameterKind kind() {
        return kind;
    }

    public ShaderGraphLiteral defaultValue() {
        return defaultValue;
    }

    public String semantic() {
        return semantic;
    }

    @Override
    public int compareTo(ShaderGraphParameter other) {
        return id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphParameter other
                && id.equals(other.id) && type.equals(other.type)
                && kind == other.kind && Objects.equals(defaultValue, other.defaultValue)
                && semantic.equals(other.semantic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, kind, defaultValue, semantic);
    }
}
