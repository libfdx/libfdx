package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledInterface;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable compiled surface graph and material-owned schema.
 */
public final class ShaderGraphMaterialDefinition {
    private static final String[] RESERVED_SEMANTIC_PREFIXES = {
            "frame.", "view.", "environment.", "pass.", "object.",
            "draw.", "shadow.", "display."
    };

    private final ShaderGraph graph;
    private final ShaderGraphRuntimeGraph compilation;
    private final ShaderGraphParameter[] parameters;
    private final ShaderGraphResource[] resources;

    private ShaderGraphMaterialDefinition(ShaderGraph graph,
            ShaderGraphRuntimeGraph compilation) {
        if (graph.kind() != ShaderGraphKind.SURFACE) {
            throw new FdxException("Graph material definitions require a surface graph");
        }
        if (compilation == null
                || !graph.semanticHash().equals(
                        compilation.graph().semanticHash())) {
            throw new FdxException("Graph material definition requires a successful compilation");
        }
        this.graph = graph;
        this.compilation = compilation;
        List<ShaderGraphParameter> material = new ArrayList<>();
        for (ShaderGraphParameter parameter : graph.parameters()) {
            if (parameter.kind() == ShaderGraphParameterKind.MATERIAL) {
                requireMaterialSemantic(parameter);
                material.add(parameter);
            }
        }
        parameters = material.toArray(ShaderGraphParameter[]::new);
        resources = graph.resources();
    }

    public static ShaderGraphMaterialDefinition compile(ShaderGraph graph,
            ShaderGraphCompiler compiler, ShaderGraphCompileOptions options) {
        if (graph == null) {
            throw new FdxException("Shader graph material cannot be null");
        }
        ShaderGraphCompiler actual = compiler != null
                ? compiler : new ShaderGraphCompiler();
        ShaderGraphCompileResult result = actual.compile(graph, options);
        if (!result.success()) {
            StringBuilder message =
                    new StringBuilder("Could not compile shader graph material");
            for (var diagnostic : result.diagnostics()) {
                message.append('\n').append(diagnostic.code()).append(": ")
                        .append(diagnostic.message());
            }
            throw new FdxException(message.toString());
        }
        ShaderGraphCompiledInterface shaderInterface =
                ShaderGraphCompiledInterface.empty(
                        "fdx-graph-interface-v1");
        return new ShaderGraphMaterialDefinition(graph,
                new ShaderGraphRuntimeGraph(graph,
                        result.wgsl(), result.libraryWgsl(),
                        shaderInterface, shaderInterface));
    }

    /**
     * Creates a material definition from a cache-hit or cache-miss runtime
     * graph without compiling it again.
     */
    public static ShaderGraphMaterialDefinition compiled(
            ShaderGraphRuntimeGraph compilation) {
        if (compilation == null) {
            throw new FdxException(
                    "Compiled shader graph material cannot be null");
        }
        return new ShaderGraphMaterialDefinition(
                compilation.graph(), compilation);
    }

    public ShaderGraph graph() {
        return graph;
    }

    public ShaderGraphRuntimeGraph compilation() {
        return compilation;
    }

    public ShaderGraphParameter[] parameters() {
        return parameters.clone();
    }

    public int parameterCount() {
        return parameters.length;
    }

    public ShaderGraphParameter parameter(int index) {
        return parameters[index];
    }

    public int parameterIndex(String id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].id().value().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    public ShaderGraphResource[] resources() {
        return resources.clone();
    }

    public int resourceCount() {
        return resources.length;
    }

    public ShaderGraphResource resource(int index) {
        return resources[index];
    }

    public int resourceIndex(String id) {
        if (id == null) {
            return -1;
        }
        for (int i = 0; i < resources.length; i++) {
            if (resources[i].id().value().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static void requireMaterialSemantic(ShaderGraphParameter parameter) {
        String semantic = parameter.semantic().toLowerCase();
        for (String prefix : RESERVED_SEMANTIC_PREFIXES) {
            if (semantic.startsWith(prefix)) {
                throw new FdxException("Material parameter " + parameter.id()
                        + " cannot claim renderer-owned semantic "
                        + parameter.semantic());
            }
        }
    }
}
