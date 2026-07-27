package io.github.libfdx.graphics.shader;

import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable reflected shader override declaration.
 */
public final class ShaderOverride {
    private final String name;
    private final int id;
    private final ShaderScalarType type;
    private final boolean initialized;
    private final boolean explicitId;

    private ShaderOverride(String name, int id, ShaderScalarType type, boolean initialized, boolean explicitId) {
        if (name == null || name.trim().isEmpty()) {
            throw new FdxException("Shader override name cannot be empty");
        }
        if (id < 0) {
            throw new FdxException("Shader override ID cannot be negative");
        }
        if (type == null || type == ShaderScalarType.UNKNOWN) {
            throw new FdxException("Shader override type cannot be unknown");
        }
        this.name = name;
        this.id = id;
        this.type = type;
        this.initialized = initialized;
        this.explicitId = explicitId;
    }

    public static ShaderOverride of(String name, int id, ShaderScalarType type, boolean initialized,
            boolean explicitId) {
        return new ShaderOverride(name, id, type, initialized, explicitId);
    }

    public String name() {
        return name;
    }

    public int id() {
        return id;
    }

    public ShaderScalarType type() {
        return type;
    }

    public boolean initialized() {
        return initialized;
    }

    public boolean explicitId() {
        return explicitId;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderOverride other && id == other.id && initialized == other.initialized
                && explicitId == other.explicitId && name.equals(other.name) && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, id, type, initialized, explicitId);
    }
}
