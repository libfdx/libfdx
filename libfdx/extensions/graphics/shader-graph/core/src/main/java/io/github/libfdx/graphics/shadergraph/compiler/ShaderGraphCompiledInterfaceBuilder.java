package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledInterface;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;

import java.util.Locale;
import java.util.TreeMap;

/**
 * Builds the graph-declared provider-neutral interface cached with WGSL.
 */
final class ShaderGraphCompiledInterfaceBuilder {
    private ShaderGraphCompiledInterfaceBuilder() {
    }

    static ShaderGraphCompiledInterface graph(ShaderGraph graph,
            String abiVersion, boolean validationEntryPoints) {
        return create(abiVersion,
                graphEntryPoints(graph, validationEntryPoints),
                new ShaderGraph[] { graph });
    }

    static ShaderGraphCompiledInterface.EntryPoint[] graphEntryPoints(
            ShaderGraph graph, boolean validationEntryPoints) {
        if (!validationEntryPoints) {
            return new ShaderGraphCompiledInterface.EntryPoint[0];
        }
        if (graph.kind()
                == io.github.libfdx.graphics.shadergraph.model
                        .ShaderGraphKind.COMPUTE) {
            return new ShaderGraphCompiledInterface.EntryPoint[] {
                    ShaderGraphCompiledInterface.EntryPoint.of(
                            ShaderStage.COMPUTE,
                            "fdx_graph_compute")
            };
        }
        return new ShaderGraphCompiledInterface.EntryPoint[] {
                ShaderGraphCompiledInterface.EntryPoint.of(
                        ShaderStage.VERTEX,
                        "fdx_graph_vertex"),
                ShaderGraphCompiledInterface.EntryPoint.of(
                        ShaderStage.FRAGMENT,
                        "fdx_graph_fragment")
        };
    }

    static ShaderGraphCompiledInterface program(
            ShaderGraphProgram program, String abiVersion) {
        return create(abiVersion, programEntryPoints(program),
                new ShaderGraph[] {
                        program.vertex(), program.fragment()
                });
    }

    static ShaderGraphCompiledInterface.EntryPoint[] programEntryPoints(
            ShaderGraphProgram program) {
        return new ShaderGraphCompiledInterface.EntryPoint[] {
                ShaderGraphCompiledInterface.EntryPoint.of(
                        ShaderStage.VERTEX,
                        program.vertexEntryPoint()),
                ShaderGraphCompiledInterface.EntryPoint.of(
                        ShaderStage.FRAGMENT,
                        program.fragmentEntryPoint())
        };
    }

    static ShaderGraphCompiledInterface computeProgram(
            ShaderGraphComputeProgram program, String abiVersion) {
        return create(abiVersion, computeProgramEntryPoints(program),
                new ShaderGraph[] { program.graph() });
    }

    static ShaderGraphCompiledInterface.EntryPoint[]
            computeProgramEntryPoints(
                    ShaderGraphComputeProgram program) {
        return new ShaderGraphCompiledInterface.EntryPoint[] {
                ShaderGraphCompiledInterface.EntryPoint.of(
                        ShaderStage.COMPUTE,
                        program.entryPoint())
        };
    }

    private static ShaderGraphCompiledInterface create(
            String abiVersion,
            ShaderGraphCompiledInterface.EntryPoint[] entryPoints,
            ShaderGraph[] graphs) {
        TreeMap<String, ShaderGraphCompiledInterface.Binding>
                bindings = new TreeMap<String,
                        ShaderGraphCompiledInterface.Binding>();
        TreeMap<String, ShaderGraphCompiledInterface.Parameter>
                parameters = new TreeMap<String,
                        ShaderGraphCompiledInterface.Parameter>();
        for (ShaderGraph graph : graphs) {
            for (ShaderGraphResource resource : graph.resources()) {
                if (!resource.bound()) {
                    continue;
                }
                String slot = String.format(Locale.ROOT,
                        "%08d:%08d", resource.group(),
                        resource.binding());
                ShaderGraphCompiledInterface.Binding value =
                        ShaderGraphCompiledInterface.Binding.of(
                                resource.group(), resource.binding(),
                                resource.id().value(),
                                resource.type().kind().name()
                                        .toLowerCase(Locale.ROOT),
                                resource.type().toString(),
                                resource.type().resourceAccess() != null
                                        ? resource.type()
                                                .resourceAccess()
                                                .name()
                                                .toLowerCase(Locale.ROOT)
                                        : "");
                ShaderGraphCompiledInterface.Binding previous =
                        bindings.putIfAbsent(slot, value);
                if (previous != null && !previous.equals(value)) {
                    throw new FdxException(
                            "Shader graph programs cannot declare "
                                    + "different resources at binding "
                                    + resource.group() + ":"
                                    + resource.binding());
                }
            }
            for (ShaderGraphParameter parameter : graph.parameters()) {
                String id = parameter.id().value();
                ShaderGraphCompiledInterface.Parameter value =
                        ShaderGraphCompiledInterface.Parameter.of(
                                id,
                                parameter.kind().name()
                                        .toLowerCase(Locale.ROOT),
                                parameter.type().toString(),
                                parameter.semantic(), -1, -1);
                ShaderGraphCompiledInterface.Parameter previous =
                        parameters.putIfAbsent(id, value);
                if (previous != null && !previous.equals(value)) {
                    String qualifiedId =
                            graph.id().value() + "." + id;
                    parameters.put(qualifiedId,
                            ShaderGraphCompiledInterface.Parameter.of(
                                    qualifiedId,
                                    parameter.kind().name()
                                            .toLowerCase(Locale.ROOT),
                                    parameter.type().toString(),
                                    parameter.semantic(), -1, -1));
                }
            }
        }
        return ShaderGraphCompiledInterface.of(abiVersion,
                entryPoints,
                bindings.values().toArray(
                        ShaderGraphCompiledInterface.Binding[]::new),
                parameters.values().toArray(
                        ShaderGraphCompiledInterface.Parameter[]::new));
    }
}
