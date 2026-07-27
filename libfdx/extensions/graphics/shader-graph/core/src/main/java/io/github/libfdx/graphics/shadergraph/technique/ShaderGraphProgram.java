package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;

/**
 * Immutable linkage description for one complete vertex/fragment program.
 *
 * <p>The stage graphs remain independently serializable semantic assets. This
 * container owns only entry-point linkage and the optional material-uniform
 * binding used by their material parameters.</p>
 */
public final class ShaderGraphProgram {
    private final ShaderGraphId id;
    private final ShaderGraph vertex;
    private final ShaderGraph fragment;
    private final String vertexEntryPoint;
    private final String fragmentEntryPoint;
    private final int materialGroup;
    private final int materialBinding;

    private ShaderGraphProgram(Builder builder) {
        if (builder.id == null || builder.vertex == null
                || builder.fragment == null
                || builder.vertex.kind() != ShaderGraphKind.VERTEX
                || builder.fragment.kind() != ShaderGraphKind.FRAGMENT
                || !identifier(builder.vertexEntryPoint)
                || !identifier(builder.fragmentEntryPoint)
                || builder.materialGroup < 0 || builder.materialBinding < 0) {
            throw new FdxException("Shader graph program is incomplete");
        }
        id = builder.id;
        vertex = builder.vertex;
        fragment = builder.fragment;
        vertexEntryPoint = builder.vertexEntryPoint;
        fragmentEntryPoint = builder.fragmentEntryPoint;
        materialGroup = builder.materialGroup;
        materialBinding = builder.materialBinding;
    }

    public static Builder builder(String id, ShaderGraph vertex,
            ShaderGraph fragment) {
        return new Builder(id, vertex, fragment);
    }

    public ShaderGraphId id() {
        return id;
    }

    public ShaderGraph vertex() {
        return vertex;
    }

    public ShaderGraph fragment() {
        return fragment;
    }

    public String vertexEntryPoint() {
        return vertexEntryPoint;
    }

    public String fragmentEntryPoint() {
        return fragmentEntryPoint;
    }

    public int materialGroup() {
        return materialGroup;
    }

    public int materialBinding() {
        return materialBinding;
    }

    public String semanticHash() {
        return PortableSha256.hashUtf8(id.value() + '\n'
                + vertex.semanticHash() + '\n' + fragment.semanticHash()
                + '\n' + vertexEntryPoint + '\n' + fragmentEntryPoint
                + '\n' + materialGroup + ':' + materialBinding);
    }

    private static boolean identifier(String value) {
        if (value == null || value.isEmpty()
                || !Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mutable program linkage scope.
     */
    public static final class Builder {
        private final ShaderGraphId id;
        private final ShaderGraph vertex;
        private final ShaderGraph fragment;
        private String vertexEntryPoint = "vertexMain";
        private String fragmentEntryPoint = "fragmentMain";
        private int materialGroup;
        private int materialBinding;

        private Builder(String id, ShaderGraph vertex, ShaderGraph fragment) {
            this.id = ShaderGraphId.of(id);
            this.vertex = vertex;
            this.fragment = fragment;
        }

        public Builder entryPoints(String vertex, String fragment) {
            vertexEntryPoint = vertex;
            fragmentEntryPoint = fragment;
            return this;
        }

        public Builder materialBinding(int group, int binding) {
            materialGroup = group;
            materialBinding = binding;
            return this;
        }

        public ShaderGraphProgram build() {
            return new ShaderGraphProgram(this);
        }
    }
}
