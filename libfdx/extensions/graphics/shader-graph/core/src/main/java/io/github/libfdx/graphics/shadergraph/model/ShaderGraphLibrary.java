package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Arrays;

/**
 * Immutable graph dependency library used during headless compilation.
 */
public final class ShaderGraphLibrary {
    private static final ShaderGraphLibrary EMPTY = new ShaderGraphLibrary(
            new ShaderGraph[0]);
    private final ShaderGraph[] graphs;

    private ShaderGraphLibrary(ShaderGraph[] graphs) {
        this.graphs = graphs.clone();
        Arrays.sort(this.graphs, (left, right) -> left.id().compareTo(right.id()));
        for (int i = 0; i < this.graphs.length; i++) {
            if (this.graphs[i] == null || i > 0
                    && this.graphs[i - 1].id().equals(this.graphs[i].id())) {
                throw new FdxException("Shader graph library IDs must be unique");
            }
        }
    }

    public static ShaderGraphLibrary empty() {
        return EMPTY;
    }

    public static ShaderGraphLibrary of(ShaderGraph... graphs) {
        return new ShaderGraphLibrary(graphs != null ? graphs : new ShaderGraph[0]);
    }

    public ShaderGraph resolve(ShaderGraphId id) {
        for (ShaderGraph graph : graphs) {
            if (graph.id().equals(id)) {
                return graph;
            }
        }
        return null;
    }

    public ShaderGraph[] graphs() {
        return graphs.clone();
    }
}
