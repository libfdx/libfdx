package io.github.libfdx.graphics.shadergraph.ir;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import io.github.libfdx.core.FdxException;

import java.util.Arrays;

/**
 * One typed graph instruction. Language emitters consume opcodes and typed
 * operands; they never ask semantic nodes to splice source text.
 */
public final class ShaderIrInstruction {
    private final ShaderIrOpcode opcode;
    private final ShaderIrValue result;
    private final ShaderIrValue[] operands;
    private final ShaderNodeProperty[] properties;
    private final ShaderGraphId graphId;
    private final ShaderGraphId nodeId;
    private final ShaderGraphId portId;

    public ShaderIrInstruction(ShaderIrOpcode opcode, ShaderIrValue result,
            ShaderIrValue[] operands, ShaderNodeProperty[] properties,
            ShaderGraphId graphId, ShaderGraphId nodeId, ShaderGraphId portId) {
        if (opcode == null || result == null || operands == null
                || properties == null || graphId == null || nodeId == null
                || portId == null) {
            throw new FdxException("Shader IR instruction is incomplete");
        }
        this.opcode = opcode;
        this.result = result;
        this.operands = operands.clone();
        this.properties = properties.clone();
        Arrays.sort(this.properties);
        this.graphId = graphId;
        this.nodeId = nodeId;
        this.portId = portId;
    }

    public ShaderIrOpcode opcode() {
        return opcode;
    }

    public ShaderIrValue result() {
        return result;
    }

    public ShaderIrValue[] operands() {
        return operands.clone();
    }

    public ShaderNodeProperty[] properties() {
        return properties.clone();
    }

    public ShaderNodeProperty property(String id) {
        ShaderGraphId key = ShaderGraphId.of(id);
        for (ShaderNodeProperty property : properties) {
            if (property.id().equals(key)) {
                return property;
            }
        }
        return null;
    }

    public ShaderGraphId graphId() {
        return graphId;
    }

    public ShaderGraphId nodeId() {
        return nodeId;
    }

    public ShaderGraphId portId() {
        return portId;
    }
}
