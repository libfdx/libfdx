package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Stable reference to one node port.
 */
public final class ShaderEndpoint implements Comparable<ShaderEndpoint> {
    private final ShaderGraphId nodeId;
    private final ShaderGraphId portId;

    private ShaderEndpoint(ShaderGraphId nodeId, ShaderGraphId portId) {
        if (nodeId == null || portId == null) {
            throw new FdxException("Shader endpoint requires node and port IDs");
        }
        this.nodeId = nodeId;
        this.portId = portId;
    }

    public static ShaderEndpoint of(String nodeId, String portId) {
        return new ShaderEndpoint(ShaderGraphId.of(nodeId), ShaderGraphId.of(portId));
    }

    public static ShaderEndpoint of(ShaderGraphId nodeId, ShaderGraphId portId) {
        return new ShaderEndpoint(nodeId, portId);
    }

    public ShaderGraphId nodeId() {
        return nodeId;
    }

    public ShaderGraphId portId() {
        return portId;
    }

    @Override
    public int compareTo(ShaderEndpoint other) {
        int node = nodeId.compareTo(other.nodeId);
        return node != 0 ? node : portId.compareTo(other.portId);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderEndpoint other
                && nodeId.equals(other.nodeId) && portId.equals(other.portId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, portId);
    }

    @Override
    public String toString() {
        return nodeId + "." + portId;
    }
}
