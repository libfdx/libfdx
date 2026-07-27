package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.core.FdxException;

/**
 * Line-oriented association from emitted WGSL back to a graph node/port.
 */
public final class ShaderSourceSpan {
    private final int firstLine;
    private final int lastLine;
    private final ShaderGraphId graphId;
    private final ShaderGraphId nodeId;
    private final ShaderGraphId portId;

    public ShaderSourceSpan(int firstLine, int lastLine, ShaderGraphId graphId,
            ShaderGraphId nodeId, ShaderGraphId portId) {
        if (firstLine <= 0 || lastLine < firstLine || graphId == null
                || nodeId == null || portId == null) {
            throw new FdxException("Shader source span is invalid");
        }
        this.firstLine = firstLine;
        this.lastLine = lastLine;
        this.graphId = graphId;
        this.nodeId = nodeId;
        this.portId = portId;
    }

    public int firstLine() {
        return firstLine;
    }

    public int lastLine() {
        return lastLine;
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
