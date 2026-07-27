package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeCompileResult;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnosticSeverity;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderWgslEmitter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphTypeKind;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Links a typed compute graph into one complete canonical WGSL entry point.
 */
public final class ShaderGraphComputeProgramCompiler {
    private final ShaderGraphCompiler graphCompiler;

    public ShaderGraphComputeProgramCompiler() {
        this(new ShaderGraphCompiler());
    }

    public ShaderGraphComputeProgramCompiler(
            ShaderGraphCompiler graphCompiler) {
        if (graphCompiler == null) {
            throw new FdxException(
                    "Compute program compiler requires a graph compiler");
        }
        this.graphCompiler = graphCompiler;
    }

    public ShaderGraphComputeCompileResult compile(
            ShaderGraphComputeProgram program,
            ShaderGraphCompileOptions options) {
        if (program == null) {
            throw new FdxException(
                    "Shader graph compute program cannot be null");
        }
        ShaderGraphCompileOptions actual = options != null ? options
                : ShaderGraphCompileOptions.builder().build();
        ShaderGraphCompileResult graph = graphCompiler.compile(
                program.graph(), actual);
        List<ShaderGraphDiagnostic> diagnostics = new ArrayList<>();
        for (ShaderGraphDiagnostic diagnostic : graph.diagnostics()) {
            diagnostics.add(diagnostic);
        }
        validateProgram(program, actual, diagnostics);
        diagnostics.sort(null);
        String source = graph.module() != null
                ? link(program, graph, diagnostics) : "";
        return new ShaderGraphComputeCompileResult(program, graph.module(),
                hasErrors(diagnostics) ? "" : source,
                diagnostics.toArray(ShaderGraphDiagnostic[]::new),
                graph.sourceMap());
    }

    private static void validateProgram(ShaderGraphComputeProgram program,
            ShaderGraphCompileOptions options,
            List<ShaderGraphDiagnostic> diagnostics) {
        ShaderGraph graph = program.graph();
        if (options.profile() == ShaderProfile.PORTABLE_WEBGL2) {
            error(diagnostics, graph, "FDXG_COMPUTE_PROFILE",
                    "The WebGL2 profile does not support compute programs");
        }
        GraphicsCapabilities capabilities = options.capabilities();
        if (capabilities != null) {
            if (!capabilities.supports(GraphicsFeature.COMPUTE)) {
                error(diagnostics, graph, "FDXG_COMPUTE_CAPABILITY",
                        "The graphics provider does not support compute");
            } else {
                validateWorkgroup(program, capabilities.limits(),
                        diagnostics);
            }
        }

        List<String> builtins = new ArrayList<>();
        for (ShaderGraphParameter parameter : graph.parameters()) {
            if (parameter.kind()
                    == ShaderGraphParameterKind.STATIC_SWITCH) {
                if (parameter.defaultValue() == null) {
                    error(diagnostics, graph,
                            "FDXG_COMPUTE_STATIC_VALUE",
                            "Compute static switch requires a specialized/default value: "
                                    + parameter.id());
                }
                continue;
            }
            String builtin = builtin(parameter.semantic());
            if (parameter.kind()
                    != ShaderGraphParameterKind.STAGE_INPUT
                    || builtin == null) {
                error(diagnostics, graph, "FDXG_COMPUTE_PARAMETER",
                        "Compute root parameters must be stage built-ins or static switches: "
                                + parameter.id());
                continue;
            }
            if (!builtinType(parameter.type(), builtin)) {
                error(diagnostics, graph, "FDXG_COMPUTE_BUILTIN_TYPE",
                        "Compute built-in " + builtin
                                + " has an invalid graph type");
            }
            if (builtins.contains(builtin)) {
                error(diagnostics, graph,
                        "FDXG_COMPUTE_BUILTIN_DUPLICATE",
                        "Compute built-in is declared more than once: "
                                + builtin);
            } else {
                builtins.add(builtin);
            }
        }
    }

    private static void validateWorkgroup(
            ShaderGraphComputeProgram program, GraphicsLimits limits,
            List<ShaderGraphDiagnostic> diagnostics) {
        int x = program.workgroupX();
        int y = program.workgroupY();
        int z = program.workgroupZ();
        if (x > limits.maxComputeWorkgroupSizeX()
                || y > limits.maxComputeWorkgroupSizeY()
                || z > limits.maxComputeWorkgroupSizeZ()) {
            error(diagnostics, program.graph(),
                    "FDXG_COMPUTE_WORKGROUP_DIMENSION",
                    "Compute workgroup dimensions exceed provider limits");
        }
        if ((long) x * y * z
                > limits.maxComputeInvocationsPerWorkgroup()) {
            error(diagnostics, program.graph(),
                    "FDXG_COMPUTE_WORKGROUP_INVOCATIONS",
                    "Compute workgroup invocation count exceeds the provider limit");
        }
    }

    private static String link(ShaderGraphComputeProgram program,
            ShaderGraphCompileResult graph,
            List<ShaderGraphDiagnostic> diagnostics) {
        StringBuilder source = new StringBuilder(graph.libraryWgsl());
        source.append("@compute @workgroup_size(")
                .append(program.workgroupX()).append(", ")
                .append(program.workgroupY()).append(", ")
                .append(program.workgroupZ()).append(")\nfn ")
                .append(program.entryPoint()).append('(');
        boolean first = true;
        List<String> emitted = new ArrayList<>();
        for (ShaderGraphParameter parameter
                : program.graph().parameters()) {
            String builtin = builtin(parameter.semantic());
            if (builtin == null || emitted.contains(builtin)) {
                continue;
            }
            if (!first) {
                source.append(", ");
            }
            first = false;
            emitted.add(builtin);
            source.append("@builtin(").append(builtin).append(") ")
                    .append(builtin).append(": ")
                    .append(ShaderWgslEmitter.type(parameter.type()));
        }
        source.append(") {\n  let fdx_compute_result = ")
                .append(ShaderWgslEmitter.functionName(
                        program.graph().id()))
                .append('(');
        ShaderGraphParameter[] parameters =
                program.graph().parameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                source.append(", ");
            }
            String builtin = builtin(parameters[i].semantic());
            if (builtin != null) {
                source.append(builtin);
            } else if (parameters[i].defaultValue() != null) {
                source.append(ShaderWgslEmitter.literal(
                        parameters[i].defaultValue()));
            } else {
                source.append(ShaderWgslEmitter.zero(
                        parameters[i].type()));
            }
        }
        return source.append(");\n}\n").toString();
    }

    private static String builtin(String semantic) {
        if (!ShaderGraphStageSemantic.builtin(semantic)) {
            return null;
        }
        return switch (ShaderGraphStageSemantic.builtinName(semantic)) {
            case "global_invocation_id" -> "global_invocation_id";
            case "local_invocation_id" -> "local_invocation_id";
            case "local_invocation_index" -> "local_invocation_index";
            case "workgroup_id" -> "workgroup_id";
            case "num_workgroups" -> "num_workgroups";
            default -> null;
        };
    }

    private static boolean builtinType(ShaderGraphType type,
            String builtin) {
        if (type.kind() != ShaderGraphTypeKind.VALUE
                || type.valueType().scalarType()
                        != ShaderScalarType.U32) {
            return false;
        }
        if ("local_invocation_index".equals(builtin)) {
            return type.valueType().kind() == ShaderValueKind.SCALAR;
        }
        return type.valueType().kind() == ShaderValueKind.VECTOR
                && type.componentCount() == 3;
    }

    private static void error(List<ShaderGraphDiagnostic> diagnostics,
            ShaderGraph graph, String code, String message) {
        diagnostics.add(new ShaderGraphDiagnostic(
                ShaderGraphDiagnosticSeverity.ERROR, code, message,
                graph.id(), null, null));
    }

    private static boolean hasErrors(
            List<ShaderGraphDiagnostic> diagnostics) {
        for (ShaderGraphDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity()
                    == ShaderGraphDiagnosticSeverity.ERROR) {
                return true;
            }
        }
        return false;
    }
}
