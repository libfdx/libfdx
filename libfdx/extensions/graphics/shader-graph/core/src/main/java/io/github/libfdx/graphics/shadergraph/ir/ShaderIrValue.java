package io.github.libfdx.graphics.shadergraph.ir;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * One typed SSA-like IR value.
 */
public final class ShaderIrValue {
    private final ShaderGraphId id;
    private final ShaderGraphType type;

    public ShaderIrValue(ShaderGraphId id, ShaderGraphType type) {
        if (id == null || type == null) {
            throw new FdxException("Shader IR value requires an ID and type");
        }
        this.id = id;
        this.type = type;
    }

    public ShaderGraphId id() {
        return id;
    }

    public ShaderGraphType type() {
        return type;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderIrValue other
                && id.equals(other.id) && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }
}
