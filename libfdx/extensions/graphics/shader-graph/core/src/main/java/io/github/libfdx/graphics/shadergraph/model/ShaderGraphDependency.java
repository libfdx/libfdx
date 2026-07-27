package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Content-addressed reference to a function or subgraph asset.
 */
public final class ShaderGraphDependency implements Comparable<ShaderGraphDependency> {
    private final ShaderGraphId graphId;
    private final String semanticHash;

    private ShaderGraphDependency(ShaderGraphId graphId, String semanticHash) {
        if (graphId == null || semanticHash == null || semanticHash.trim().isEmpty()) {
            throw new FdxException("Shader graph dependency requires an ID and semantic hash");
        }
        this.graphId = graphId;
        this.semanticHash = semanticHash.trim().toLowerCase();
    }

    public static ShaderGraphDependency of(String graphId, String semanticHash) {
        return new ShaderGraphDependency(ShaderGraphId.of(graphId), semanticHash);
    }

    public ShaderGraphId graphId() {
        return graphId;
    }

    public String semanticHash() {
        return semanticHash;
    }

    @Override
    public int compareTo(ShaderGraphDependency other) {
        return graphId.compareTo(other.graphId);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphDependency other
                && graphId.equals(other.graphId) && semanticHash.equals(other.semanticHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graphId, semanticHash);
    }
}
