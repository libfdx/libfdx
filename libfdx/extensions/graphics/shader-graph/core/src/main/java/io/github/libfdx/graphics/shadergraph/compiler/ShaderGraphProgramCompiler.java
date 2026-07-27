package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphProgramCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnosticSeverity;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderSourceSpan;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderWgslEmitter;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrFunction;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrModule;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphOutput;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphTypeKind;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Deterministically links complete vertex and fragment graphs and emits real
 * WGSL entry points without a renderer template.
 */
public final class ShaderGraphProgramCompiler {
    private final ShaderGraphCompiler graphCompiler;

    public ShaderGraphProgramCompiler() {
        this(new ShaderGraphCompiler());
    }

    public ShaderGraphProgramCompiler(ShaderGraphCompiler graphCompiler) {
        if (graphCompiler == null) {
            throw new IllegalArgumentException(
                    "Shader graph program compiler requires a graph compiler");
        }
        this.graphCompiler = graphCompiler;
    }

    public ShaderGraphProgramCompileResult compile(ShaderGraphProgram program,
            ShaderGraphCompileOptions options) {
        if (program == null) {
            throw new IllegalArgumentException("Shader graph program cannot be null");
        }
        ShaderGraphCompileOptions actual = options != null ? options
                : ShaderGraphCompileOptions.builder().build();
        ShaderGraphCompileResult vertex = graphCompiler.compile(
                program.vertex(), stageOptions(actual, ShaderStage.VERTEX));
        ShaderGraphCompileResult fragment = graphCompiler.compile(
                program.fragment(), stageOptions(actual, ShaderStage.FRAGMENT));
        List<ShaderGraphDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(Arrays.asList(vertex.diagnostics()));
        diagnostics.addAll(Arrays.asList(fragment.diagnostics()));
        if (!vertex.success() || !fragment.success()) {
            diagnostics.sort(null);
            return failure(program, diagnostics);
        }

        Link link = new Link(program, diagnostics);
        link.validate();
        if (hasErrors(diagnostics)) {
            diagnostics.sort(null);
            return failure(program, diagnostics);
        }

        ShaderIrModule module = merge(vertex.module(), fragment.module(),
                diagnostics, program);
        if (module == null) {
            diagnostics.sort(null);
            return failure(program, diagnostics);
        }
        ShaderWgslEmitter.Result library =
                new ShaderWgslEmitter().emitLibrary(module);
        String wgsl = library.source() + link.emit();
        diagnostics.sort(null);
        return new ShaderGraphProgramCompileResult(module, wgsl,
                program.semanticHash(), program.vertexEntryPoint(),
                program.fragmentEntryPoint(),
                diagnostics.toArray(ShaderGraphDiagnostic[]::new),
                library.sourceMap());
    }

    private static ShaderGraphCompileOptions stageOptions(
            ShaderGraphCompileOptions source, ShaderStage stage) {
        return ShaderGraphCompileOptions.builder()
                .profile(source.profile())
                .capabilities(source.capabilities())
                .library(source.library())
                .stage(stage)
                .build();
    }

    private static ShaderIrModule merge(ShaderIrModule vertex,
            ShaderIrModule fragment, List<ShaderGraphDiagnostic> diagnostics,
            ShaderGraphProgram program) {
        List<ShaderIrFunction> functions = new ArrayList<>();
        append(functions, vertex.functions());
        for (ShaderIrFunction function : fragment.functions()) {
            ShaderIrFunction existing = find(functions, function.graphId());
            if (existing == null) {
                functions.add(function);
            } else if (function == fragment.root()
                    || existing == vertex.root()) {
                diagnostics.add(error(program.fragment(),
                        "FDXG_PROGRAM_GRAPH_ID",
                        "Vertex and fragment graph/dependency IDs must identify "
                                + "the same content or be unique", null, null));
                return null;
            }
        }
        return new ShaderIrModule(functions.toArray(ShaderIrFunction[]::new));
    }

    private static void append(List<ShaderIrFunction> target,
            ShaderIrFunction[] functions) {
        for (ShaderIrFunction function : functions) {
            if (find(target, function.graphId()) == null) {
                target.add(function);
            }
        }
    }

    private static ShaderIrFunction find(List<ShaderIrFunction> functions,
            ShaderGraphId id) {
        for (ShaderIrFunction function : functions) {
            if (function.graphId().equals(id)) {
                return function;
            }
        }
        return null;
    }

    private static boolean hasErrors(List<ShaderGraphDiagnostic> diagnostics) {
        for (ShaderGraphDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == ShaderGraphDiagnosticSeverity.ERROR) {
                return true;
            }
        }
        return false;
    }

    private static ShaderGraphProgramCompileResult failure(
            ShaderGraphProgram program,
            List<ShaderGraphDiagnostic> diagnostics) {
        return new ShaderGraphProgramCompileResult(null, "",
                program.semanticHash(), program.vertexEntryPoint(),
                program.fragmentEntryPoint(),
                diagnostics.toArray(ShaderGraphDiagnostic[]::new),
                new ShaderSourceSpan[0]);
    }

    private static ShaderGraphDiagnostic error(ShaderGraph graph, String code,
            String message, ShaderGraphId node, ShaderGraphId port) {
        return new ShaderGraphDiagnostic(ShaderGraphDiagnosticSeverity.ERROR,
                code, message, graph.id(), node, port);
    }

    private static final class Link {
        private final ShaderGraphProgram program;
        private final ShaderGraph vertex;
        private final ShaderGraph fragment;
        private final List<ShaderGraphDiagnostic> diagnostics;
        private final List<Field> vertexInputs = new ArrayList<>();
        private final List<Field> vertexOutputs = new ArrayList<>();
        private final List<Field> fragmentInputs = new ArrayList<>();
        private final List<Field> fragmentOutputs = new ArrayList<>();
        private final List<ShaderGraphParameter> materialParameters =
                new ArrayList<>();

        Link(ShaderGraphProgram program,
                List<ShaderGraphDiagnostic> diagnostics) {
            this.program = program;
            vertex = program.vertex();
            fragment = program.fragment();
            this.diagnostics = diagnostics;
        }

        void validate() {
            validateResources();
            collectMaterials(vertex);
            collectMaterials(fragment);
            collectVertexInputs();
            collectVertexOutputs();
            collectFragmentInputs();
            collectFragmentOutputs();
            validateMaterialBinding();
        }

        private void validateResources() {
            ShaderGraphResource[] left = vertex.resources();
            ShaderGraphResource[] right = fragment.resources();
            for (ShaderGraphResource first : left) {
                for (ShaderGraphResource second : right) {
                    if (first.group() == second.group()
                            && first.binding() == second.binding()
                            && !first.type().equals(second.type())) {
                        diagnostics.add(error(fragment,
                                "FDXG_PROGRAM_RESOURCE_LINK",
                                "Stage resources at @group(" + first.group()
                                        + ") @binding(" + first.binding()
                                        + ") have different types",
                                null, null));
                    }
                }
            }
        }

        private void collectMaterials(ShaderGraph graph) {
            for (ShaderGraphParameter parameter : graph.parameters()) {
                if (parameter.kind() == ShaderGraphParameterKind.FUNCTION_INPUT) {
                    diagnostics.add(error(graph, "FDXG_STAGE_PARAMETER_KIND",
                            "Complete stage graphs cannot expose function inputs",
                            null, null));
                } else if (parameter.kind()
                        == ShaderGraphParameterKind.STATIC_SWITCH
                        && parameter.defaultValue() == null) {
                    diagnostics.add(error(graph, "FDXG_STATIC_DEFAULT",
                            "Stage static switches require a default value",
                            null, null));
                } else if (parameter.kind()
                        == ShaderGraphParameterKind.MATERIAL) {
                    if (!parameter.type().isNumeric()) {
                        diagnostics.add(error(graph,
                                "FDXG_MATERIAL_UNIFORM_TYPE",
                                "Complete-stage material parameters must be "
                                        + "host-shareable numeric values",
                                null, null));
                    }
                    ShaderGraphParameter existing =
                            material(parameter.id());
                    if (existing == null) {
                        materialParameters.add(parameter);
                    } else if (!existing.type().equals(parameter.type())
                            || !java.util.Objects.equals(
                                    existing.defaultValue(),
                                    parameter.defaultValue())) {
                        diagnostics.add(error(graph,
                                "FDXG_MATERIAL_PARAMETER_LINK",
                                "Material parameter " + parameter.id()
                                        + " differs between stages",
                                null, null));
                    }
                }
            }
            materialParameters.sort(null);
        }

        private void collectVertexInputs() {
            int next = 0;
            for (ShaderGraphParameter parameter : vertex.parameters()) {
                if (parameter.kind() != ShaderGraphParameterKind.STAGE_INPUT) {
                    continue;
                }
                String semantic = semantic(parameter.id(), parameter.semantic());
                if (ShaderGraphStageSemantic.builtin(semantic)) {
                    String builtin = ShaderGraphStageSemantic.builtinName(semantic);
                    if (!"vertex_index".equals(builtin)
                            && !"instance_index".equals(builtin)
                            || !u32(parameter.type())) {
                        diagnostics.add(error(vertex,
                                "FDXG_VERTEX_BUILTIN_INPUT",
                                "Vertex built-in input " + semantic
                                        + " has an invalid type or stage",
                                null, null));
                    }
                    vertexInputs.add(Field.parameter(parameter, -1, builtin));
                } else {
                    int requested = ShaderGraphStageSemantic.location(semantic);
                    int location = requested >= 0 ? requested
                            : nextAvailable(vertexInputs, next);
                    next = Math.max(next, location + 1);
                    addUnique(vertex, vertexInputs,
                            Field.parameter(parameter, location, null),
                            "FDXG_VERTEX_INPUT_LOCATION", diagnostics);
                }
            }
        }

        private void collectVertexOutputs() {
            List<ShaderGraphOutput> automatic = new ArrayList<>();
            for (ShaderGraphOutput output : vertex.outputs()) {
                String semantic = semantic(output.id(), output.semantic());
                if (ShaderGraphStageSemantic.builtin(semantic)) {
                    String builtin = ShaderGraphStageSemantic.builtinName(semantic);
                    if (!"position".equals(builtin) || !vec4f(output.type())) {
                        diagnostics.add(outputError(vertex, output,
                                "FDXG_VERTEX_BUILTIN_OUTPUT",
                                "Vertex output built-in must be vec4<f32> position"));
                    }
                    addUnique(vertex, vertexOutputs,
                            Field.output(output, -1, builtin),
                            "FDXG_VERTEX_OUTPUT_BUILTIN", diagnostics);
                } else if (ShaderGraphStageSemantic.location(semantic) >= 0) {
                    addUnique(vertex, vertexOutputs,
                            Field.output(output,
                                    ShaderGraphStageSemantic.location(semantic),
                                    null),
                            "FDXG_VERTEX_OUTPUT_LOCATION", diagnostics);
                } else {
                    automatic.add(output);
                }
            }
            automatic.sort((left, right) ->
                    semantic(left.id(), left.semantic()).compareTo(
                            semantic(right.id(), right.semantic())));
            int next = 0;
            for (ShaderGraphOutput output : automatic) {
                int location = nextAvailable(vertexOutputs, next);
                next = location + 1;
                vertexOutputs.add(Field.output(output, location, null));
            }
            if (builtin(vertexOutputs, "position") == null) {
                diagnostics.add(error(vertex, "FDXG_VERTEX_POSITION",
                        "Vertex graph requires one builtin.position output",
                        null, null));
            }
        }

        private void collectFragmentInputs() {
            for (ShaderGraphParameter parameter : fragment.parameters()) {
                if (parameter.kind() != ShaderGraphParameterKind.STAGE_INPUT) {
                    continue;
                }
                String semantic = semantic(parameter.id(), parameter.semantic());
                if (ShaderGraphStageSemantic.builtin(semantic)) {
                    String builtin = ShaderGraphStageSemantic.builtinName(semantic);
                    if (!validFragmentInputBuiltin(builtin, parameter.type())) {
                        diagnostics.add(error(fragment,
                                "FDXG_FRAGMENT_BUILTIN_INPUT",
                                "Fragment built-in input " + semantic
                                        + " has an invalid type",
                                null, null));
                    }
                    fragmentInputs.add(Field.parameter(parameter, -1, builtin));
                    continue;
                }
                Field source = ShaderGraphStageSemantic.location(semantic) >= 0
                        ? location(vertexOutputs,
                                ShaderGraphStageSemantic.location(semantic))
                        : semantic(vertexOutputs, semantic);
                if (source == null) {
                    diagnostics.add(error(fragment, "FDXG_STAGE_LINK_MISSING",
                            "Fragment input " + parameter.id()
                                    + " has no matching vertex output",
                            null, null));
                    continue;
                }
                if (!source.type.equals(parameter.type())) {
                    diagnostics.add(error(fragment, "FDXG_STAGE_LINK_TYPE",
                            "Fragment input " + parameter.id()
                                    + " type does not match vertex output "
                                    + source.id, null, null));
                }
                addUnique(fragment, fragmentInputs,
                        Field.parameter(parameter, source.location, null),
                        "FDXG_FRAGMENT_INPUT_LOCATION", diagnostics);
            }
        }

        private void collectFragmentOutputs() {
            List<ShaderGraphOutput> automatic = new ArrayList<>();
            for (ShaderGraphOutput output : fragment.outputs()) {
                String semantic = semantic(output.id(), output.semantic());
                if (ShaderGraphStageSemantic.builtin(semantic)) {
                    String builtin = ShaderGraphStageSemantic.builtinName(semantic);
                    if (!validFragmentOutputBuiltin(builtin, output.type())) {
                        diagnostics.add(outputError(fragment, output,
                                "FDXG_FRAGMENT_BUILTIN_OUTPUT",
                                "Fragment built-in output " + semantic
                                        + " has an invalid type"));
                    }
                    addUnique(fragment, fragmentOutputs,
                            Field.output(output, -1, builtin),
                            "FDXG_FRAGMENT_OUTPUT_BUILTIN", diagnostics);
                    continue;
                }
                int requested = ShaderGraphStageSemantic.location(semantic);
                if (requested < 0) {
                    requested = colorLocation(semantic);
                }
                if (requested >= 0) {
                    addUnique(fragment, fragmentOutputs,
                            Field.output(output, requested, null),
                            "FDXG_FRAGMENT_OUTPUT_LOCATION", diagnostics);
                } else {
                    automatic.add(output);
                }
            }
            automatic.sort((left, right) ->
                    semantic(left.id(), left.semantic()).compareTo(
                            semantic(right.id(), right.semantic())));
            int next = 0;
            for (ShaderGraphOutput output : automatic) {
                int location = nextAvailable(fragmentOutputs, next);
                next = location + 1;
                fragmentOutputs.add(Field.output(output, location, null));
            }
        }

        private void validateMaterialBinding() {
            if (materialParameters.isEmpty()) {
                return;
            }
            for (ShaderGraphResource resource : vertex.resources()) {
                collision(resource);
            }
            for (ShaderGraphResource resource : fragment.resources()) {
                collision(resource);
            }
        }

        private void collision(ShaderGraphResource resource) {
            if (resource.group() == program.materialGroup()
                    && resource.binding() == program.materialBinding()) {
                diagnostics.add(error(fragment,
                        "FDXG_MATERIAL_RESOURCE_BINDING",
                        "Material uniform binding collides with graph resource "
                                + resource.id(), null, null));
            }
        }

        String emit() {
            String prefix = "FdxProgram_" + sanitize(program.id().value());
            StringBuilder source = new StringBuilder();
            emitMaterial(source, prefix);
            emitInputStruct(source, prefix + "_VertexInput", vertexInputs);
            emitOutputStruct(source, prefix + "_VertexOutput", vertexOutputs);
            emitInputStruct(source, prefix + "_FragmentInput", fragmentInputs);
            emitOutputStruct(source, prefix + "_FragmentOutput",
                    fragmentOutputs);
            emitVertexEntry(source, prefix);
            emitFragmentEntry(source, prefix);
            return source.toString();
        }

        private void emitMaterial(StringBuilder source, String prefix) {
            if (materialParameters.isEmpty()) {
                return;
            }
            source.append("struct ").append(prefix).append("_Material {\n");
            for (ShaderGraphParameter parameter : materialParameters) {
                source.append("  ").append(materialField(parameter.id()))
                        .append(": ")
                        .append(ShaderWgslEmitter.type(parameter.type()))
                        .append(",\n");
            }
            source.append("}\n@group(").append(program.materialGroup())
                    .append(") @binding(").append(program.materialBinding())
                    .append(") var<uniform> fdx_program_material: ")
                    .append(prefix).append("_Material;\n\n");
        }

        private static void emitInputStruct(StringBuilder source, String name,
                List<Field> fields) {
            if (fields.isEmpty()) {
                return;
            }
            emitStruct(source, name, fields);
        }

        private static void emitOutputStruct(StringBuilder source, String name,
                List<Field> fields) {
            emitStruct(source, name, fields);
        }

        private static void emitStruct(StringBuilder source, String name,
                List<Field> fields) {
            source.append("struct ").append(name).append(" {\n");
            for (Field field : fields) {
                source.append("  ").append(field.attribute());
                if (field.location >= 0 && flat(field.type)) {
                    source.append(" @interpolate(flat)");
                }
                source.append(' ').append(field.fieldName()).append(": ")
                        .append(ShaderWgslEmitter.type(field.type))
                        .append(",\n");
            }
            source.append("}\n\n");
        }

        private void emitVertexEntry(StringBuilder source, String prefix) {
            source.append("@vertex\nfn ")
                    .append(program.vertexEntryPoint()).append('(');
            if (!vertexInputs.isEmpty()) {
                source.append("input: ").append(prefix).append("_VertexInput");
            }
            source.append(") -> ").append(prefix)
                    .append("_VertexOutput {\n  let result = ")
                    .append(ShaderWgslEmitter.functionName(vertex.id()))
                    .append('(').append(arguments(vertex, vertexInputs))
                    .append(");\n  var output: ").append(prefix)
                    .append("_VertexOutput;\n");
            ShaderGraphOutput[] outputs = vertex.outputs();
            for (Field field : vertexOutputs) {
                source.append("  output.").append(field.fieldName())
                        .append(" = ")
                        .append(result(outputs, field.output)).append(";\n");
            }
            source.append("  return output;\n}\n\n");
        }

        private void emitFragmentEntry(StringBuilder source, String prefix) {
            source.append("@fragment\nfn ")
                    .append(program.fragmentEntryPoint()).append('(');
            if (!fragmentInputs.isEmpty()) {
                source.append("input: ").append(prefix)
                        .append("_FragmentInput");
            }
            source.append(") -> ").append(prefix)
                    .append("_FragmentOutput {\n  let result = ")
                    .append(ShaderWgslEmitter.functionName(fragment.id()))
                    .append('(').append(arguments(fragment, fragmentInputs))
                    .append(");\n  var output: ").append(prefix)
                    .append("_FragmentOutput;\n");
            ShaderGraphOutput[] outputs = fragment.outputs();
            for (Field field : fragmentOutputs) {
                source.append("  output.").append(field.fieldName())
                        .append(" = ")
                        .append(result(outputs, field.output)).append(";\n");
            }
            source.append("  return output;\n}\n");
        }

        private String arguments(ShaderGraph graph, List<Field> inputs) {
            StringBuilder result = new StringBuilder();
            ShaderGraphParameter[] parameters = graph.parameters();
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) {
                    result.append(", ");
                }
                ShaderGraphParameter parameter = parameters[i];
                switch (parameter.kind()) {
                    case STAGE_INPUT -> {
                        Field field = parameter(inputs, parameter.id());
                        result.append("input.").append(field.fieldName());
                    }
                    case MATERIAL -> result.append("fdx_program_material.")
                            .append(materialField(parameter.id()));
                    case STATIC_SWITCH, FUNCTION_INPUT ->
                            result.append(parameter.defaultValue() != null
                                    ? ShaderWgslEmitter.literal(
                                            parameter.defaultValue())
                                    : ShaderWgslEmitter.zero(parameter.type()));
                }
            }
            return result.toString();
        }

        private static String result(ShaderGraphOutput[] outputs,
                ShaderGraphOutput output) {
            if (outputs.length == 1) {
                return "result";
            }
            return "result." + ShaderWgslEmitter.symbol(output.id());
        }

        private ShaderGraphParameter material(ShaderGraphId id) {
            for (ShaderGraphParameter parameter : materialParameters) {
                if (parameter.id().equals(id)) {
                    return parameter;
                }
            }
            return null;
        }
    }

    private static ShaderGraphDiagnostic outputError(ShaderGraph graph,
            ShaderGraphOutput output, String code, String message) {
        return error(graph, code, message, output.source().nodeId(),
                output.source().portId());
    }

    private static String semantic(ShaderGraphId id, String value) {
        return value != null && !value.trim().isEmpty()
                ? value.trim().toLowerCase()
                : id.value().trim().toLowerCase();
    }

    private static int nextAvailable(List<Field> fields, int start) {
        int result = Math.max(0, start);
        while (location(fields, result) != null) {
            result++;
        }
        return result;
    }

    private static void addUnique(ShaderGraph graph, List<Field> fields,
            Field value, String code,
            List<ShaderGraphDiagnostic> diagnostics) {
        Field existing = value.builtin != null
                ? builtin(fields, value.builtin)
                : location(fields, value.location);
        if (existing != null) {
            ShaderGraphId node = value.output != null
                    ? value.output.source().nodeId() : null;
            ShaderGraphId port = value.output != null
                    ? value.output.source().portId() : null;
            diagnostics.add(error(graph, code,
                    "Duplicate stage interface slot "
                            + (value.builtin != null
                                    ? "builtin." + value.builtin
                                    : "location." + value.location),
                    node, port));
            return;
        }
        fields.add(value);
    }

    private static Field location(List<Field> fields, int value) {
        for (Field field : fields) {
            if (field.location == value) {
                return field;
            }
        }
        return null;
    }

    private static Field builtin(List<Field> fields, String value) {
        for (Field field : fields) {
            if (value.equals(field.builtin)) {
                return field;
            }
        }
        return null;
    }

    private static Field semantic(List<Field> fields, String value) {
        for (Field field : fields) {
            if (value.equals(field.semantic)) {
                return field;
            }
        }
        return null;
    }

    private static Field parameter(List<Field> fields, ShaderGraphId id) {
        for (Field field : fields) {
            if (field.parameter != null && field.parameter.id().equals(id)) {
                return field;
            }
        }
        throw new IllegalStateException("Missing linked stage parameter " + id);
    }

    private static boolean validFragmentInputBuiltin(String builtin,
            ShaderGraphType type) {
        return switch (builtin) {
            case "position" -> vec4f(type);
            case "front_facing" -> type.isBoolean();
            case "sample_index", "sample_mask", "primitive_index" -> u32(type);
            default -> false;
        };
    }

    private static boolean validFragmentOutputBuiltin(String builtin,
            ShaderGraphType type) {
        return "frag_depth".equals(builtin) && f32(type)
                || "sample_mask".equals(builtin) && u32(type);
    }

    private static boolean vec4f(ShaderGraphType type) {
        return type.kind() == ShaderGraphTypeKind.VALUE
                && type.valueType().kind() == ShaderValueKind.VECTOR
                && type.valueType().rows() == 4
                && type.valueType().scalarType() == ShaderScalarType.F32;
    }

    private static boolean f32(ShaderGraphType type) {
        return type.kind() == ShaderGraphTypeKind.VALUE
                && type.valueType().kind() == ShaderValueKind.SCALAR
                && type.valueType().scalarType() == ShaderScalarType.F32;
    }

    private static boolean u32(ShaderGraphType type) {
        return type.kind() == ShaderGraphTypeKind.VALUE
                && type.valueType().kind() == ShaderValueKind.SCALAR
                && type.valueType().scalarType() == ShaderScalarType.U32;
    }

    private static boolean flat(ShaderGraphType type) {
        if (type.kind() != ShaderGraphTypeKind.VALUE) {
            return false;
        }
        ShaderScalarType scalar = type.valueType().scalarType();
        return scalar == ShaderScalarType.BOOL
                || scalar == ShaderScalarType.I32
                || scalar == ShaderScalarType.U32;
    }

    private static int colorLocation(String semantic) {
        String value = semantic.replace("_", "").replace(".", "");
        if (!value.startsWith("color") || value.length() == 5) {
            return -1;
        }
        try {
            int result = Integer.parseInt(value.substring(5));
            return result >= 0 ? result : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String materialField(ShaderGraphId id) {
        return "fdx_material_" + sanitize(id.value());
    }

    private static String sanitize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            result.append(Character.isLetterOrDigit(character)
                    || character == '_' ? character : '_');
        }
        return result.toString();
    }

    private static final class Field {
        final ShaderGraphId id;
        final String semantic;
        final ShaderGraphType type;
        final int location;
        final String builtin;
        final ShaderGraphParameter parameter;
        final ShaderGraphOutput output;

        private Field(ShaderGraphId id, String semantic,
                ShaderGraphType type, int location, String builtin,
                ShaderGraphParameter parameter, ShaderGraphOutput output) {
            this.id = id;
            this.semantic = semantic(id, semantic);
            this.type = type;
            this.location = location;
            this.builtin = builtin;
            this.parameter = parameter;
            this.output = output;
        }

        static Field parameter(ShaderGraphParameter value, int location,
                String builtin) {
            return new Field(value.id(), value.semantic(), value.type(),
                    location, builtin, value, null);
        }

        static Field output(ShaderGraphOutput value, int location,
                String builtin) {
            return new Field(value.id(), value.semantic(), value.type(),
                    location, builtin, null, value);
        }

        String fieldName() {
            return "fdx_io_" + sanitize(id.value());
        }

        String attribute() {
            return builtin != null ? "@builtin(" + builtin + ')'
                    : "@location(" + location + ')';
        }
    }

}
