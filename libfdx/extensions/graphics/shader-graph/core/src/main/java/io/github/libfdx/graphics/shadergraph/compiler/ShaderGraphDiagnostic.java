package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Structured deterministic graph diagnostic.
 */
public final class ShaderGraphDiagnostic implements Comparable<ShaderGraphDiagnostic> {
    private final ShaderGraphDiagnosticSeverity severity;
    private final String code;
    private final String message;
    private final ShaderGraphId graphId;
    private final ShaderGraphId nodeId;
    private final ShaderGraphId portId;

    public ShaderGraphDiagnostic(ShaderGraphDiagnosticSeverity severity,
            String code, String message, ShaderGraphId graphId,
            ShaderGraphId nodeId, ShaderGraphId portId) {
        if (severity == null || code == null || code.trim().isEmpty()
                || message == null || message.trim().isEmpty() || graphId == null) {
            throw new FdxException("Shader graph diagnostic is incomplete");
        }
        this.severity = severity;
        this.code = code.trim();
        this.message = message.trim();
        this.graphId = graphId;
        this.nodeId = nodeId;
        this.portId = portId;
    }

    public ShaderGraphDiagnosticSeverity severity() {
        return severity;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
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

    @Override
    public int compareTo(ShaderGraphDiagnostic other) {
        int graph = graphId.compareTo(other.graphId);
        if (graph != 0) {
            return graph;
        }
        int node = nullable(nodeId, other.nodeId);
        if (node != 0) {
            return node;
        }
        int port = nullable(portId, other.portId);
        if (port != 0) {
            return port;
        }
        int codeOrder = code.compareTo(other.code);
        return codeOrder != 0 ? codeOrder : message.compareTo(other.message);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphDiagnostic other
                && severity == other.severity && code.equals(other.code)
                && message.equals(other.message) && graphId.equals(other.graphId)
                && Objects.equals(nodeId, other.nodeId)
                && Objects.equals(portId, other.portId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, code, message, graphId, nodeId, portId);
    }

    private static int nullable(ShaderGraphId left, ShaderGraphId right) {
        if (left == null) {
            return right == null ? 0 : -1;
        }
        return right == null ? 1 : left.compareTo(right);
    }
}
