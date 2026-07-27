package io.github.libfdx.graphics.shadergraph.ir;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.core.FdxException;

/**
 * Typed lowered representation of one graph asset.
 */
public final class ShaderIrFunction {
    private final ShaderGraphId graphId;
    private final ShaderGraphKind kind;
    private final ShaderGraphParameter[] parameters;
    private final ShaderGraphResource[] resources;
    private final ShaderIrInstruction[] instructions;
    private final ShaderIrOutput[] outputs;

    public ShaderIrFunction(ShaderGraphId graphId, ShaderGraphKind kind,
            ShaderGraphParameter[] parameters, ShaderGraphResource[] resources,
            ShaderIrInstruction[] instructions, ShaderIrOutput[] outputs) {
        if (graphId == null || kind == null || parameters == null
                || resources == null || instructions == null || outputs == null) {
            throw new FdxException("Shader IR function is incomplete");
        }
        this.graphId = graphId;
        this.kind = kind;
        this.parameters = parameters.clone();
        this.resources = resources.clone();
        this.instructions = instructions.clone();
        this.outputs = outputs.clone();
    }

    public ShaderGraphId graphId() {
        return graphId;
    }

    public ShaderGraphKind kind() {
        return kind;
    }

    public ShaderGraphParameter[] parameters() {
        return parameters.clone();
    }

    public ShaderGraphResource[] resources() {
        return resources.clone();
    }

    public ShaderIrInstruction[] instructions() {
        return instructions.clone();
    }

    public ShaderIrOutput[] outputs() {
        return outputs.clone();
    }
}
