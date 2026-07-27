package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable, UI-independent shader graph asset.
 */
public final class ShaderGraph {
    private final int formatVersion;
    private final ShaderGraphId id;
    private final ShaderGraphKind kind;
    private final ShaderGraphParameter[] parameters;
    private final ShaderGraphResource[] resources;
    private final ShaderNode[] nodes;
    private final ShaderEdge[] edges;
    private final ShaderGraphOutput[] outputs;
    private final ShaderGraphDependency[] dependencies;
    private String semanticHash;

    private ShaderGraph(Builder builder) {
        if (builder.formatVersion <= 0 || builder.id == null || builder.kind == null) {
            throw new FdxException("Shader graph requires a format version, ID, and kind");
        }
        formatVersion = builder.formatVersion;
        id = builder.id;
        kind = builder.kind;
        parameters = sortedUnique(builder.parameters, "parameter");
        resources = sortedUnique(builder.resources, "resource");
        nodes = sortedUnique(builder.nodes, "node");
        edges = builder.edges.clone();
        Arrays.sort(edges);
        outputs = sortedUnique(builder.outputs, "output");
        dependencies = sortedUnique(builder.dependencies, "dependency");
    }

    public static Builder builder(String id, ShaderGraphKind kind) {
        return new Builder(ShaderGraphId.of(id), kind);
    }

    public int formatVersion() {
        return formatVersion;
    }

    public ShaderGraphId id() {
        return id;
    }

    public ShaderGraphKind kind() {
        return kind;
    }

    public ShaderGraphParameter[] parameters() {
        return parameters.clone();
    }

    public ShaderGraphParameter parameter(ShaderGraphId parameterId) {
        for (ShaderGraphParameter parameter : parameters) {
            if (parameter.id().equals(parameterId)) {
                return parameter;
            }
        }
        return null;
    }

    public ShaderGraphResource[] resources() {
        return resources.clone();
    }

    public ShaderGraphResource resource(ShaderGraphId resourceId) {
        for (ShaderGraphResource resource : resources) {
            if (resource.id().equals(resourceId)) {
                return resource;
            }
        }
        return null;
    }

    public ShaderNode[] nodes() {
        return nodes.clone();
    }

    public ShaderNode node(ShaderGraphId nodeId) {
        for (ShaderNode node : nodes) {
            if (node.id().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }

    public ShaderEdge[] edges() {
        return edges.clone();
    }

    public ShaderGraphOutput[] outputs() {
        return outputs.clone();
    }

    public ShaderGraphDependency[] dependencies() {
        return dependencies.clone();
    }

    /**
     * Returns a SHA-256 hash of deterministic semantic JSON. Editor data is
     * necessarily excluded because it is stored by a different model/codec.
     *
     * @return semantic hash
     */
    public String semanticHash() {
        if (semanticHash == null) {
            semanticHash = PortableSha256.hashUtf8(ShaderGraphCodec.write(this));
        }
        return semanticHash;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraph other
                && formatVersion == other.formatVersion && id.equals(other.id)
                && kind == other.kind
                && Arrays.equals(parameters, other.parameters)
                && Arrays.equals(resources, other.resources)
                && Arrays.equals(nodes, other.nodes)
                && Arrays.equals(edges, other.edges)
                && Arrays.equals(outputs, other.outputs)
                && Arrays.equals(dependencies, other.dependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(formatVersion, id, kind,
                Arrays.hashCode(parameters), Arrays.hashCode(resources),
                Arrays.hashCode(nodes), Arrays.hashCode(edges),
                Arrays.hashCode(outputs), Arrays.hashCode(dependencies));
    }

    /**
     * Mutable construction scope. The built graph owns immutable copies.
     */
    public static final class Builder {
        private int formatVersion = ShaderGraphFormat.CURRENT_VERSION;
        private final ShaderGraphId id;
        private final ShaderGraphKind kind;
        private ShaderGraphParameter[] parameters = new ShaderGraphParameter[0];
        private ShaderGraphResource[] resources = new ShaderGraphResource[0];
        private ShaderNode[] nodes = new ShaderNode[0];
        private ShaderEdge[] edges = new ShaderEdge[0];
        private ShaderGraphOutput[] outputs = new ShaderGraphOutput[0];
        private ShaderGraphDependency[] dependencies = new ShaderGraphDependency[0];

        private Builder(ShaderGraphId id, ShaderGraphKind kind) {
            this.id = id;
            this.kind = kind;
        }

        public Builder formatVersion(int value) {
            formatVersion = value;
            return this;
        }

        public Builder parameters(ShaderGraphParameter... values) {
            parameters = values != null ? values : new ShaderGraphParameter[0];
            return this;
        }

        public Builder resources(ShaderGraphResource... values) {
            resources = values != null ? values : new ShaderGraphResource[0];
            return this;
        }

        public Builder nodes(ShaderNode... values) {
            nodes = values != null ? values : new ShaderNode[0];
            return this;
        }

        public Builder edges(ShaderEdge... values) {
            edges = values != null ? values : new ShaderEdge[0];
            return this;
        }

        public Builder outputs(ShaderGraphOutput... values) {
            outputs = values != null ? values : new ShaderGraphOutput[0];
            return this;
        }

        public Builder dependencies(ShaderGraphDependency... values) {
            dependencies = values != null ? values : new ShaderGraphDependency[0];
            return this;
        }

        public ShaderGraph build() {
            return new ShaderGraph(this);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> T[] sortedUnique(T[] source,
            String kind) {
        if (source == null) {
            throw new FdxException("Shader graph " + kind + " array cannot be null");
        }
        T[] result = source.clone();
        Arrays.sort(result);
        for (int i = 0; i < result.length; i++) {
            if (result[i] == null) {
                throw new FdxException("Shader graph " + kind + " cannot be null");
            }
            if (i > 0 && result[i - 1].compareTo(result[i]) == 0) {
                throw new FdxException("Duplicate shader graph " + kind + ": " + result[i]);
            }
        }
        return result;
    }
}
