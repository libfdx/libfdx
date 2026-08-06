package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.reflection.ShaderParameter;
import io.github.libfdx.graphics.shader.reflection.ShaderWorkgroupSizeKind;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterDomain;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceUse;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVisibility;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureSampleType;
import io.github.libfdx.graphics.shader.reflection.ShaderUpdateFrequency;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.internal.BuiltInPbrShaderManifest;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileResult;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphOutput;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphSymbols;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphTypeKind;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialDefinition;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialInstance;

/**
 * Composes typed graph evaluation into the renderer-owned PBR template.
 */
final class PbrGraphCustomization {
    private static final int MATERIAL_GROUP = 1;
    private static final int MATERIAL_BINDING = 0;

    private final ShaderGraphMaterialDefinition definition;
    private final ShaderProfile profile;
    private final ShaderGraphMaterialInstance defaultMaterial;
    private final GraphMaterial defaultGraphMaterial;
    private final ShaderGraph vertexGraph;
    private final ShaderGraphCompileResult vertexCompilation;
    private final ShaderGraph lightingGraph;
    private final ShaderGraphCompileResult lightingCompilation;
    private final String staticSource;
    private final String skinnedSource;
    private final ShaderReflection staticReflection;
    private final ShaderReflection skinnedReflection;

    PbrGraphCustomization(ShaderGraphMaterialDefinition definition) {
        this(definition, ShaderProfile.PORTABLE_WEBGPU,
                null, null, null, null);
    }

    PbrGraphCustomization(ShaderGraphMaterialDefinition definition,
            ShaderGraph vertexGraph,
            ShaderGraphCompileResult vertexCompilation,
            ShaderGraph lightingGraph,
            ShaderGraphCompileResult lightingCompilation) {
        this(definition, ShaderProfile.PORTABLE_WEBGPU,
                vertexGraph, vertexCompilation, lightingGraph,
                lightingCompilation);
    }

    PbrGraphCustomization(ShaderGraphMaterialDefinition definition,
            ShaderProfile profile,
            ShaderGraph vertexGraph,
            ShaderGraphCompileResult vertexCompilation,
            ShaderGraph lightingGraph,
            ShaderGraphCompileResult lightingCompilation) {
        if (definition == null) {
            throw new FdxException("PBR graph customization requires a material definition");
        }
        if (profile == null) {
            throw new FdxException(
                    "PBR graph customization requires a shader profile");
        }
        requireOptionalCompilation(vertexGraph, vertexCompilation,
                "vertex");
        requireOptionalCompilation(lightingGraph,
                lightingCompilation, "lighting");
        this.definition = definition;
        this.profile = profile;
        this.vertexGraph = vertexGraph;
        this.vertexCompilation = vertexCompilation;
        this.lightingGraph = lightingGraph;
        this.lightingCompilation = lightingCompilation;
        defaultMaterial = new ShaderGraphMaterialInstance(definition);
        defaultGraphMaterial = new GraphMaterial(
                "libfdx.standard.pbr.default", defaultMaterial);
        String materialFields = materialFields();
        String declarations = declarations();
        String surfaceEvaluation = surfaceEvaluation();
        String vertexEvaluation = vertexEvaluation();
        String lightingEvaluation = lightingEvaluation();
        staticSource = compose(PbrShaderProvider.pbrRendererTemplate(
                        false, materialFields),
                declarations, surfaceEvaluation,
                vertexEvaluation, lightingEvaluation);
        skinnedSource = compose(PbrShaderProvider.pbrRendererTemplate(
                        true, materialFields),
                declarations, surfaceEvaluation,
                vertexEvaluation, lightingEvaluation);
        staticReflection = reflection(
                BuiltInPbrShaderManifest.staticReflection());
        skinnedReflection = reflection(
                BuiltInPbrShaderManifest.skinnedReflection());
    }

    ShaderGraphMaterialDefinition definition() {
        return definition;
    }

    ShaderGraphMaterialInstance newMaterialInstance() {
        return new ShaderGraphMaterialInstance(definition);
    }

    GraphMaterial material(String id) {
        return new GraphMaterial(id, newMaterialInstance());
    }

    GraphMaterial defaultMaterial() {
        return defaultGraphMaterial;
    }

    ShaderModuleDescriptor shader(boolean skinned) {
        return ShaderModuleDescriptor.wgsl(
                skinned ? "model batch graph skinned pbr"
                        : "model batch graph pbr",
                skinned ? skinnedSource : staticSource)
                .reflection(skinned ? skinnedReflection
                        : staticReflection);
    }

    String staticSource() {
        return staticSource;
    }

    private String declarations() {
        StringBuilder result = new StringBuilder(
                definition.compilation().libraryWgsl());
        if (vertexCompilation != null) {
            result.append(vertexCompilation.libraryWgsl());
        }
        if (lightingCompilation != null) {
            result.append(lightingCompilation.libraryWgsl());
        }
        return result.toString();
    }

    private String materialFields() {
        StringBuilder result = new StringBuilder();
        for (ShaderGraphParameter parameter :
                definition.parameters()) {
            result.append(ShaderGraphSymbols.parameter(
                            parameter.id()))
                    .append(": ")
                    .append(type(parameter.type()))
                    .append(",\n");
        }
        return result.toString();
    }

    private ShaderReflection reflection(ShaderReflection renderer) {
        ShaderBinding rendererUniform =
                renderer.requireBinding(MATERIAL_GROUP,
                        MATERIAL_BINDING);
        ShaderBinding extendedUniform =
                definition.parameterCount() > 0
                        ? extendUniform(rendererUniform) : null;
        ShaderBinding[] graphBindings = graphResourceBindings();
        ShaderBinding[] bindings = new ShaderBinding[
                renderer.bindingCount() + graphBindings.length];
        ShaderBinding[] rendererBindings = renderer.bindings();
        for (int i = 0; i < rendererBindings.length; i++) {
            ShaderBinding binding = rendererBindings[i];
            bindings[i] = extendedUniform != null
                    && binding.group() == MATERIAL_GROUP
                    && binding.binding() == MATERIAL_BINDING
                            ? extendedUniform : binding;
        }
        System.arraycopy(graphBindings, 0, bindings,
                renderer.bindingCount(), graphBindings.length);

        ShaderResourceUse[] graphUses =
                new ShaderResourceUse[graphBindings.length];
        for (int i = 0; i < graphBindings.length; i++) {
            ShaderBinding binding = graphBindings[i];
            graphUses[i] = ShaderResourceUse.of(binding.group(),
                    binding.binding(),
                    binding.minimumBindingSize());
        }
        ShaderEntryPoint[] sourceEntries = renderer.entryPoints();
        ShaderEntryPoint[] entries =
                new ShaderEntryPoint[sourceEntries.length];
        for (int i = 0; i < entries.length; i++) {
            ShaderEntryPoint source = sourceEntries[i];
            ShaderResourceUse[] uses = source.resources();
            if (extendedUniform != null) {
                uses = withMinimumBindingSize(uses,
                        MATERIAL_GROUP, MATERIAL_BINDING,
                        extendedUniform.minimumBindingSize());
            }
            if (source.stage() == ShaderStage.FRAGMENT
                    && graphUses.length != 0) {
                ShaderResourceUse[] merged =
                        new ShaderResourceUse[
                                uses.length + graphUses.length];
                System.arraycopy(uses, 0, merged, 0,
                        uses.length);
                System.arraycopy(graphUses, 0, merged,
                        uses.length, graphUses.length);
                uses = merged;
            }
            entries[i] = copy(source, uses);
        }
        return ShaderReflection.builder(profile)
                .entryPoints(entries)
                .bindings(bindings)
                .attributes(renderer.attributes())
                .requiredCapabilities(
                        renderer.requiredCapabilities())
                .complete(true)
                .build();
    }

    private ShaderBinding extendUniform(ShaderBinding renderer) {
        if (renderer.resourceKind()
                != ShaderResourceKind.UNIFORM_BUFFER
                || renderer.bufferLayout() == null) {
            throw new FdxException(
                    "PBR renderer material parameters require the reflected uniform block at 1:0");
        }
        ShaderParameterLayout layout =
                materialLayout(renderer.bufferLayout());
        long sizeWithoutPadding = renderer.sizeWithoutPadding();
        for (int i = renderer.bufferLayout().parameterCount();
                i < layout.parameterCount(); i++) {
            io.github.libfdx.graphics.shader.reflection.ShaderParameter parameter =
                    layout.parameter(i);
            sizeWithoutPadding = Math.max(sizeWithoutPadding,
                    parameter.byteOffset()
                            + parameter.occupiedSize());
        }
        return ShaderBinding.builder(renderer.group(),
                        renderer.binding(), renderer.name(),
                        renderer.resourceKind())
                .stableId(renderer.stableId())
                .visibility(renderer.visibility())
                .access(renderer.access())
                .bindingArrayCount(
                        renderer.bindingArrayCount())
                .buffer(layout.minimumBindingSize(),
                        sizeWithoutPadding, layout.alignment(),
                        layout)
                .semantics(renderer.domain(),
                        renderer.updateFrequency())
                .build();
    }

    private ShaderBinding[] graphResourceBindings() {
        ShaderBinding[] bindings =
                new ShaderBinding[definition.resourceCount()];
        int index = 0;
        for (int i = 0; i < definition.resourceCount(); i++) {
            ShaderGraphResource resource = definition.resource(i);
            String name = "fdx_resource_" + resource.group()
                    + '_' + resource.binding();
            ShaderBinding.Builder builder;
            if (resource.type().kind()
                    == ShaderGraphTypeKind.TEXTURE) {
                ShaderResourceKind kind = textureKind(
                        resource.type());
                builder = ShaderBinding.builder(resource.group(),
                                resource.binding(), name, kind)
                        .visibility(
                                ShaderStageVisibility.FRAGMENT)
                        .access(ShaderResourceAccess.READ)
                        .texture(resource.type().textureDimension(),
                                kind == ShaderResourceKind.DEPTH_TEXTURE
                                        || kind == ShaderResourceKind
                                                .DEPTH_MULTISAMPLED_TEXTURE
                                                ? ShaderTextureSampleType.NONE
                                                : resource.type()
                                                        .textureSampleType());
            } else if (resource.type().kind()
                    == ShaderGraphTypeKind.SAMPLER) {
                builder = ShaderBinding.builder(resource.group(),
                                resource.binding(), name,
                                ShaderResourceKind.SAMPLER)
                        .visibility(
                                ShaderStageVisibility.FRAGMENT)
                        .access(ShaderResourceAccess.NONE)
                        .samplerKind(
                                resource.type().samplerKind());
            } else {
                throw new FdxException(
                        "PBR surface material resources support sampled textures and samplers");
            }
            bindings[index++] = builder
                    .stableId("material.graph."
                            + resource.id().value())
                    .semantics(ShaderParameterDomain.MATERIAL,
                            ShaderUpdateFrequency.ON_CHANGE)
                    .build();
        }
        return bindings;
    }

    private ShaderParameterLayout materialLayout(
            ShaderParameterLayout renderer) {
        io.github.libfdx.graphics.shader.reflection.ShaderParameter[]
                rendererParameters = renderer.parameters();
        io.github.libfdx.graphics.shader.reflection.ShaderParameter[] parameters =
                new io.github.libfdx.graphics.shader.reflection.ShaderParameter[
                        rendererParameters.length
                                + definition.parameterCount()];
        System.arraycopy(rendererParameters, 0, parameters, 0,
                rendererParameters.length);
        long offset = renderer.minimumBindingSize();
        long maximumAlignment = Math.max(16,
                renderer.alignment());
        for (int i = 0; i < definition.parameterCount(); i++) {
            ShaderGraphParameter graphParameter =
                    definition.parameter(i);
            ShaderValueType valueType =
                    graphParameter.type().valueType()
                            .named(type(graphParameter.type()));
            long alignment = alignment(valueType);
            long occupiedSize = occupiedSize(valueType);
            long matrixStride = matrixStride(valueType);
            offset = roundUp(alignment, offset);
            String name = ShaderGraphSymbols.parameter(
                    graphParameter.id());
            parameters[rendererParameters.length + i] =
                    ShaderParameter.builder(graphParameter.id().value(), name,
                            valueType, offset, occupiedSize,
                            alignment)
                    .matrixStride(matrixStride)
                    .semantics(ShaderParameterDomain.MATERIAL,
                            ShaderUpdateFrequency.ON_CHANGE)
                    .build();
            offset += occupiedSize;
            maximumAlignment = Math.max(maximumAlignment,
                    alignment);
        }
        long blockAlignment = roundUp(16, maximumAlignment);
        return ShaderParameterLayout.of(
                roundUp(blockAlignment, offset),
                blockAlignment, parameters);
    }

    private static ShaderResourceUse[] withMinimumBindingSize(
            ShaderResourceUse[] uses, int group, int binding,
            long minimumBindingSize) {
        ShaderResourceUse[] result = uses.clone();
        for (int i = 0; i < result.length; i++) {
            ShaderResourceUse use = result[i];
            if (use.group() == group
                    && use.binding() == binding) {
                result[i] = ShaderResourceUse.of(group, binding,
                        minimumBindingSize);
                return result;
            }
        }
        ShaderResourceUse[] appended =
                new ShaderResourceUse[result.length + 1];
        System.arraycopy(result, 0, appended, 0,
                result.length);
        appended[result.length] = ShaderResourceUse.of(group,
                binding, minimumBindingSize);
        return appended;
    }

    private static long alignment(ShaderValueType type) {
        requireHostShareable(type);
        return switch (type.kind()) {
            case SCALAR -> 4;
            case VECTOR -> type.rows() == 2 ? 8 : 16;
            case MATRIX -> type.rows() == 2 ? 8 : 16;
            default -> throw new FdxException(
                    "PBR graph material parameter type is unsupported: "
                            + type);
        };
    }

    private static long occupiedSize(ShaderValueType type) {
        return switch (type.kind()) {
            case SCALAR -> 4;
            case VECTOR -> type.rows() * 4L;
            case MATRIX -> matrixStride(type)
                    * type.columns();
            default -> throw new FdxException(
                    "PBR graph material parameter type is unsupported: "
                            + type);
        };
    }

    private static long matrixStride(ShaderValueType type) {
        if (type.kind() != ShaderValueKind.MATRIX) {
            return 0;
        }
        long vectorSize = type.rows() * 4L;
        return roundUp(type.rows() == 2 ? 8 : 16,
                vectorSize);
    }

    private static void requireHostShareable(
            ShaderValueType type) {
        if (type == null
                || type.scalarType() == ShaderScalarType.BOOL
                || type.scalarType() != ShaderScalarType.F32
                        && (type.kind() != ShaderValueKind.SCALAR
                                || type.scalarType()
                                        != ShaderScalarType.I32
                                && type.scalarType()
                                        != ShaderScalarType.U32)) {
            throw new FdxException(
                    "PBR graph material parameters support f32 values and i32/u32 scalars");
        }
    }

    private static ShaderResourceKind textureKind(
            ShaderGraphType type) {
        if (type.textureSampleType()
                == ShaderTextureSampleType.DEPTH) {
            return type.multisampled()
                    ? ShaderResourceKind
                            .DEPTH_MULTISAMPLED_TEXTURE
                    : ShaderResourceKind.DEPTH_TEXTURE;
        }
        return type.multisampled()
                ? ShaderResourceKind.MULTISAMPLED_TEXTURE
                : ShaderResourceKind.SAMPLED_TEXTURE;
    }

    private static ShaderEntryPoint copy(
            ShaderEntryPoint source,
            ShaderResourceUse[] resources) {
        ShaderEntryPoint.Builder builder = ShaderEntryPoint
                .builder(source.name(), source.stage())
                .builtins(source.builtinMask(),
                        source.clipDistanceSize())
                .inputs(source.inputs())
                .outputs(source.outputs())
                .overrides(source.overrides())
                .resources(resources);
        if (source.workgroupSizeKind() == ShaderWorkgroupSizeKind.FIXED) {
            builder.fixedWorkgroupSize(source.workgroupX(),
                    source.workgroupY(), source.workgroupZ());
        } else if (source.workgroupSizeKind()
                == ShaderWorkgroupSizeKind.OVERRIDE_DEPENDENT) {
            builder.overrideDependentWorkgroupSize();
        }
        return builder.build();
    }

    private static long roundUp(long alignment, long value) {
        return (value + alignment - 1) & -alignment;
    }

    private String surfaceEvaluation() {
        StringBuilder call = new StringBuilder("let fdx_surface = ")
                .append(ShaderGraphSymbols.function(definition.graph().id()))
                .append('(');
        ShaderGraphParameter[] parameters = definition.graph().parameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                call.append(", ");
            }
            call.append(argument(parameters[i]));
        }
        call.append(");\n");
        ShaderGraphOutput[] outputs = definition.graph().outputs();
        if (outputs.length == 1) {
            throw new FdxException("PBR surface graph must expose named surface fields");
        }
        appendAssignment(call, outputs, "baseColor", "base_color",
                "base = vec4f(VALUE, base.a);");
        appendAssignment(call, outputs, "alpha", "alpha",
                "base.a = VALUE;");
        appendAssignment(call, outputs, "normal", "normal",
                "n = normalize(VALUE);");
        appendAssignment(call, outputs, "metallic", "metallic",
                "metallic = clamp(VALUE, 0.0, 1.0);");
        appendAssignment(call, outputs, "roughness", "roughness",
                "roughness = clamp(VALUE, 0.04, 1.0);");
        appendAssignment(call, outputs, "occlusion", "occlusion",
                "ao = clamp(VALUE, 0.0, 1.0);");
        appendAssignment(call, outputs, "emissive", "emissive",
                "emissive = VALUE;");
        return call.toString();
    }

    private String vertexEvaluation() {
        if (vertexGraph == null) {
            return "";
        }
        ShaderGraphOutput position = requireOutput(vertexGraph,
                "position", "position", ShaderValueKind.VECTOR, 3);
        ShaderGraphOutput normal = requireOutput(vertexGraph,
                "normal", "normal", ShaderValueKind.VECTOR, 3);
        StringBuilder source = call(vertexGraph,
                new ArgumentResolver() {
                    @Override
                    public String resolve(ShaderGraphParameter parameter) {
                        return switch (semantic(parameter)) {
                            case "localposition", "position" ->
                                    "localPosition.xyz";
                            case "localnormal", "normal" ->
                                    "localNormal.xyz";
                            case "uv0", "uv" -> "input.uv";
                            default -> defaultArgument(parameter);
                        };
                    }
                }, "fdx_vertex");
        source.append("localPosition = vec4f(")
                .append(outputValue(vertexGraph, position,
                        "fdx_vertex"))
                .append(", 1.0);\n");
        source.append("localNormal = vec4f(normalize(")
                .append(outputValue(vertexGraph, normal,
                        "fdx_vertex"))
                .append("), 0.0);\n");
        return source.toString();
    }

    private String lightingEvaluation() {
        if (lightingGraph == null) {
            return "";
        }
        ShaderGraphOutput output = requireOutput(lightingGraph,
                "color", "color", ShaderValueKind.VECTOR, 3);
        StringBuilder source = call(lightingGraph,
                new ArgumentResolver() {
                    @Override
                    public String resolve(ShaderGraphParameter parameter) {
                        return switch (semantic(parameter)) {
                            case "litcolor", "color" -> "color";
                            case "basecolor", "base_color" -> "albedo";
                            case "normal" -> "n";
                            case "viewdirection", "view_direction" -> "v";
                            case "worldposition", "world_position" ->
                                    "input.worldPosition";
                            case "emissive" -> "emissive";
                            case "metallic" -> "metallic";
                            case "roughness" -> "roughness";
                            case "occlusion" -> "ao";
                            case "uv0", "uv" -> "uv";
                            default -> defaultArgument(parameter);
                        };
                    }
                }, "fdx_lighting");
        source.append("color = ")
                .append(outputValue(lightingGraph, output,
                        "fdx_lighting"))
                .append(";\n");
        return source.toString();
    }

    private String argument(ShaderGraphParameter parameter) {
        if (parameter.kind() == ShaderGraphParameterKind.MATERIAL) {
            return "uniforms."
                    + ShaderGraphSymbols.parameter(parameter.id());
        }
        if (parameter.kind() == ShaderGraphParameterKind.STATIC_SWITCH) {
            return literal(parameter.defaultValue());
        }
        return switch (parameter.semantic().toLowerCase()) {
            case "basecolor", "base_color" -> "base.rgb";
            case "alpha" -> "base.a";
            case "normal" -> "n";
            case "metallic" -> "metallic";
            case "roughness" -> "roughness";
            case "occlusion" -> "ao";
            case "emissive" -> "emissive";
            case "uv0", "uv" -> "uv";
            default -> parameter.defaultValue() != null
                    ? literal(parameter.defaultValue())
                    : type(parameter.type()) + "()";
        };
    }

    private static void appendAssignment(StringBuilder source,
            ShaderGraphOutput[] outputs, String semantic, String id,
            String statement) {
        for (ShaderGraphOutput output : outputs) {
            if (semantic.equalsIgnoreCase(output.semantic())
                    || id.equalsIgnoreCase(output.id().value())) {
                String value = "fdx_surface."
                        + ShaderGraphSymbols.output(output.id());
                source.append(statement.replace("VALUE", value)).append('\n');
                return;
            }
        }
    }

    private static String compose(String template,
            String declarations,
            String surfaceEvaluation, String vertexEvaluation,
            String lightingEvaluation) {
        if (!template.contains("//__PBR_SURFACE_GRAPH_DECLARATIONS__")
                || !template.contains("//__PBR_SURFACE_GRAPH_EVALUATION__")
                || !template.contains(
                        "//__PBR_VERTEX_GRAPH_EVALUATION__")
                || !template.contains(
                        "//__PBR_LIGHTING_GRAPH_EVALUATION__")) {
            throw new FdxException("Built-in PBR template is missing graph composition markers");
        }
        return template
                .replace("//__PBR_SURFACE_GRAPH_DECLARATIONS__", declarations)
                .replace("//__PBR_SURFACE_GRAPH_EVALUATION__",
                        surfaceEvaluation)
                .replace("//__PBR_VERTEX_GRAPH_EVALUATION__",
                        vertexEvaluation)
                .replace("//__PBR_LIGHTING_GRAPH_EVALUATION__",
                        lightingEvaluation);
    }

    private static void requireOptionalCompilation(ShaderGraph graph,
            ShaderGraphCompileResult compilation, String label) {
        if ((graph == null) != (compilation == null)
                || compilation != null && !compilation.success()) {
            throw new FdxException("PBR " + label
                    + " graph requires a successful matching compilation");
        }
        if (graph == null) {
            return;
        }
        if (!graph.semanticHash().equals(
                compilation.semanticHash())) {
            throw new FdxException("PBR " + label
                    + " graph compilation belongs to a different graph");
        }
        for (ShaderGraphParameter parameter : graph.parameters()) {
            if (parameter.kind()
                    == ShaderGraphParameterKind.MATERIAL) {
                throw new FdxException("PBR " + label
                        + " extension parameters must be function inputs; "
                        + "material parameters belong to the surface graph");
            }
        }
        if (graph.resources().length != 0) {
            throw new FdxException("PBR " + label
                    + " extension resources are not material bindings; "
                    + "declare material textures and samplers in the surface graph");
        }
    }

    private static StringBuilder call(ShaderGraph graph,
            ArgumentResolver resolver, String resultName) {
        StringBuilder source = new StringBuilder("let ")
                .append(resultName).append(" = ")
                .append(ShaderGraphSymbols.function(graph.id()))
                .append('(');
        ShaderGraphParameter[] parameters = graph.parameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                source.append(", ");
            }
            source.append(resolver.resolve(parameters[i]));
        }
        return source.append(");\n");
    }

    private static ShaderGraphOutput requireOutput(ShaderGraph graph,
            String semantic, String id, ShaderValueKind valueKind,
            int rows) {
        for (ShaderGraphOutput output : graph.outputs()) {
            if (semantic.equalsIgnoreCase(output.semantic())
                    || id.equalsIgnoreCase(output.id().value())) {
                if (output.type().kind()
                        != ShaderGraphTypeKind.VALUE
                        || output.type().valueType().kind()
                                != valueKind
                        || valueKind == ShaderValueKind.VECTOR
                                && output.type().valueType().rows()
                                        != rows
                        || output.type().valueType().scalarType()
                                != ShaderScalarType.F32) {
                    throw new FdxException("PBR graph output "
                            + output.id() + " has an invalid type");
                }
                return output;
            }
        }
        throw new FdxException("PBR graph " + graph.id()
                + " is missing required output " + semantic);
    }

    private static String outputValue(ShaderGraph graph,
            ShaderGraphOutput output, String resultName) {
        return graph.outputs().length == 1 ? resultName
                : resultName + '.'
                        + ShaderGraphSymbols.output(output.id());
    }

    private static String semantic(ShaderGraphParameter parameter) {
        String value = parameter.semantic();
        return (value != null && !value.isBlank()
                ? value : parameter.id().value())
                .replace("_", "").replace(".", "")
                .toLowerCase();
    }

    private static String defaultArgument(
            ShaderGraphParameter parameter) {
        return parameter.defaultValue() != null
                ? literal(parameter.defaultValue())
                : type(parameter.type()) + "()";
    }

    private interface ArgumentResolver {
        String resolve(ShaderGraphParameter parameter);
    }

    private static String type(ShaderGraphType type) {
        if (type.kind() != ShaderGraphTypeKind.VALUE) {
            throw new FdxException(
                    "PBR graph material blocks support value parameters");
        }
        return switch (type.valueType().kind()) {
            case SCALAR -> scalar(type.valueType().scalarType());
            case VECTOR -> "vec" + type.valueType().rows() + '<'
                    + scalar(type.valueType().scalarType()) + '>';
            case MATRIX -> "mat" + type.valueType().columns() + 'x'
                    + type.valueType().rows() + '<'
                    + scalar(type.valueType().scalarType()) + '>';
            default -> throw new FdxException(
                    "PBR graph material block type is unsupported: " + type);
        };
    }

    private static String scalar(ShaderScalarType type) {
        return switch (type) {
            case BOOL -> "bool";
            case I32 -> "i32";
            case U32 -> "u32";
            case F32 -> "f32";
            default -> throw new FdxException(
                    "PBR graph material scalar is unsupported: " + type);
        };
    }

    private static String literal(ShaderGraphLiteral literal) {
        if (literal == null) {
            throw new FdxException("Static graph values require defaults");
        }
        ShaderGraphType type = literal.type();
        if (type.kind() == ShaderGraphTypeKind.VALUE
                && type.valueType().kind() == ShaderValueKind.SCALAR) {
            return switch (type.valueType().scalarType()) {
                case BOOL -> literal.bits() == 0 ? "false" : "true";
                case I32 -> Integer.toString((int) literal.bits());
                case U32 -> Long.toUnsignedString(literal.bits() & 0xffffffffL) + "u";
                case F32 -> Float.toString(
                        Float.intBitsToFloat((int) literal.bits()));
                default -> throw new FdxException("Unsupported graph literal");
            };
        }
        StringBuilder result = new StringBuilder(type(type)).append('(');
        for (int i = 0; i < literal.elementCount(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(literal(literal.element(i)));
        }
        return result.append(')').toString();
    }

}
