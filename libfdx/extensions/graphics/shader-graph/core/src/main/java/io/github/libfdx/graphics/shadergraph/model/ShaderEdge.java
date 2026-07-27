package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Directed value edge from an output port to an input port.
 */
public final class ShaderEdge implements Comparable<ShaderEdge> {
    private final ShaderEndpoint source;
    private final ShaderEndpoint target;

    private ShaderEdge(ShaderEndpoint source, ShaderEndpoint target) {
        if (source == null || target == null) {
            throw new FdxException("Shader edge requires source and target endpoints");
        }
        this.source = source;
        this.target = target;
    }

    public static ShaderEdge of(ShaderEndpoint source, ShaderEndpoint target) {
        return new ShaderEdge(source, target);
    }

    public ShaderEndpoint source() {
        return source;
    }

    public ShaderEndpoint target() {
        return target;
    }

    @Override
    public int compareTo(ShaderEdge other) {
        int targetOrder = target.compareTo(other.target);
        return targetOrder != 0 ? targetOrder : source.compareTo(other.source);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderEdge other
                && source.equals(other.source) && target.equals(other.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target);
    }
}
