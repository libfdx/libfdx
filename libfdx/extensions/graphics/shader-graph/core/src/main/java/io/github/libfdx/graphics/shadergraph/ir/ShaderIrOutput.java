package io.github.libfdx.graphics.shadergraph.ir;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.core.FdxException;

/**
 * Typed public IR function output.
 */
public final class ShaderIrOutput {
    private final ShaderGraphId id;
    private final String semantic;
    private final ShaderIrValue value;

    public ShaderIrOutput(ShaderGraphId id, String semantic, ShaderIrValue value) {
        if (id == null || value == null) {
            throw new FdxException("Shader IR output requires an ID and value");
        }
        this.id = id;
        this.semantic = semantic != null ? semantic : "";
        this.value = value;
    }

    public ShaderGraphId id() {
        return id;
    }

    public String semantic() {
        return semantic;
    }

    public ShaderIrValue value() {
        return value;
    }
}
