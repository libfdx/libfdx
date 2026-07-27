package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.reflection.ShaderParameter;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVariable;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialDefinition;
import io.github.libfdx.runtime.core.shader.RuntimeShaderReflection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates the exact graph-composed static and skinned PBR sources with the
 * host Tint reflection bridge.
 */
public final class PbrGraphTintTool {
    private PbrGraphTintTool() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: PbrGraphTintTool <fdx_shaderc_reflect>");
        }
        ShaderGraphMaterialDefinition definition =
                ShaderGraphMaterialDefinition.compile(
                        StandardPbrSurfaceGraph.create(),
                        new ShaderGraphCompiler(),
                        ShaderGraphCompileOptions.builder().build());
        ShaderGraphCompiler compiler = new ShaderGraphCompiler();
        ShaderGraphCompileOptions options =
                ShaderGraphCompileOptions.builder().build();
        var vertexGraph = StandardPbrVertexGraph.create();
        var lightingGraph = StandardPbrLightingGraph.create();
        PbrGraphCustomization customization =
                new PbrGraphCustomization(definition,
                        vertexGraph,
                        compiler.compile(vertexGraph, options),
                        lightingGraph,
                        compiler.compile(lightingGraph, options));
        Path directory = Files.createTempDirectory("libfdx-graph-pbr-tint-");
        try {
            validate(arguments[0], directory, "static",
                    customization.shader(false).source(),
                    customization.shader(false).reflection());
            validate(arguments[0], directory, "skinned",
                    customization.shader(true).source(),
                    customization.shader(true).reflection());
        } finally {
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
            Files.deleteIfExists(directory);
        }
    }

    private static void validate(String executable, Path directory,
            String name, String source, ShaderReflection expected)
            throws Exception {
        if (source.contains("__PBR_SURFACE_GRAPH_")
                || source.contains("__PBR_VERTEX_GRAPH_")
                || source.contains("__PBR_LIGHTING_GRAPH_")) {
            throw new IllegalStateException(
                    "Graph PBR source still contains composition markers");
        }
        Path wgsl = directory.resolve(name + ".wgsl");
        Path reflection = directory.resolve(name + ".fdxi");
        Files.writeString(wgsl, source, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(executable,
                wgsl.toString(), reflection.toString())
                .inheritIO().start();
        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.isRegularFile(reflection)
                || Files.size(reflection) == 0) {
            throw new IllegalStateException(
                    "Tint rejected graph PBR " + name
                            + " WGSL with exit code " + exitCode);
        }
        ShaderReflection actual = ShaderReflection.fromRuntime(
                RuntimeShaderReflection.fromBytes(
                        Files.readAllBytes(reflection)));
        if (!expected.physicallyEquivalent(actual)) {
            throw new IllegalStateException(
                    "Graph PBR " + name
                            + " interface does not match Tint reflection"
                            + "\ndifference: "
                            + firstDifference(expected, actual)
                            + "\nexpected: " + describe(expected)
                            + "\nactual:   " + describe(actual));
        }
    }

    private static String firstDifference(ShaderReflection expected,
            ShaderReflection actual) {
        if (!java.util.Arrays.equals(expected.requiredCapabilities(),
                actual.requiredCapabilities())) {
            return "required capabilities";
        }
        var expectedEntries = expected.entryPoints();
        var actualEntries = actual.entryPoints();
        if (expectedEntries.length != actualEntries.length) {
            return "entry-point count";
        }
        for (int i = 0; i < expectedEntries.length; i++) {
            var first = expectedEntries[i];
            var second = actualEntries[i];
            if (!first.name().equals(second.name())
                    || first.stage() != second.stage()
                    || first.workgroupSizeKind()
                            != second.workgroupSizeKind()
                    || first.workgroupX() != second.workgroupX()
                    || first.workgroupY() != second.workgroupY()
                    || first.workgroupZ() != second.workgroupZ()
                    || first.builtinMask() != second.builtinMask()
                    || first.clipDistanceSize()
                            != second.clipDistanceSize()) {
                return "entry-point scalar fields at " + i;
            }
            String input = variableDifference(first.inputs(),
                    second.inputs());
            if (input != null) {
                return "entry-point " + i + " inputs: " + input;
            }
            String output = variableDifference(first.outputs(),
                    second.outputs());
            if (output != null) {
                return "entry-point " + i + " outputs: " + output;
            }
            if (!java.util.Arrays.equals(first.overrides(),
                    second.overrides())) {
                return "entry-point " + i + " overrides";
            }
            if (!java.util.Arrays.equals(first.resources(),
                    second.resources())) {
                return "entry-point " + i + " resources";
            }
        }
        var expectedBindings = expected.bindings();
        var actualBindings = actual.bindings();
        if (expectedBindings.length != actualBindings.length) {
            return "binding count";
        }
        for (int i = 0; i < expectedBindings.length; i++) {
            var first = expectedBindings[i];
            var second = actualBindings[i];
            if (first.group() != second.group()
                    || first.binding() != second.binding()
                    || first.resourceKind() != second.resourceKind()
                    || !first.visibility().equals(second.visibility())
                    || first.access() != second.access()
                    || first.bindingArrayCount()
                            != second.bindingArrayCount()
                    || first.minimumBindingSize()
                            != second.minimumBindingSize()
                    || first.sizeWithoutPadding()
                            != second.sizeWithoutPadding()
                    || first.alignment() != second.alignment()
                    || first.textureDimension()
                            != second.textureDimension()
                    || first.textureSampleType()
                            != second.textureSampleType()
                    || first.samplerKind() != second.samplerKind()
                    || first.storageFormat()
                            != second.storageFormat()
                    || first.inputAttachmentIndex()
                            != second.inputAttachmentIndex()) {
                return "binding scalar fields at " + first.group()
                        + ':' + first.binding();
            }
            if (first.bufferLayout() == null
                    ? second.bufferLayout() != null
                    : !first.bufferLayout().physicallyEquivalent(
                            second.bufferLayout())) {
                return "buffer layout at " + first.group() + ':'
                        + first.binding() + " ("
                        + parameterDifference(first.bufferLayout(),
                                second.bufferLayout())
                        + ')';
            }
        }
        return "unknown physical field";
    }

    private static String variableDifference(
            io.github.libfdx.graphics.shader.reflection.ShaderStageVariable[] expected,
            io.github.libfdx.graphics.shader.reflection.ShaderStageVariable[] actual) {
        if (expected.length != actual.length) {
            return "count";
        }
        for (int i = 0; i < expected.length; i++) {
            var first = expected[i];
            var second = actual[i];
            if (first.location() != second.location()
                    || first.color() != second.color()
                    || first.blendSource() != second.blendSource()
                    || !first.valueType().equals(second.valueType())
                    || first.interpolation()
                            != second.interpolation()
                    || first.sampling() != second.sampling()) {
                return "variable " + i;
            }
        }
        return null;
    }

    private static String parameterDifference(
            io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout expected,
            io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout actual) {
        if (expected == null || actual == null) {
            return "one layout is null";
        }
        if (expected.minimumBindingSize()
                != actual.minimumBindingSize()) {
            return "minimum binding size "
                    + expected.minimumBindingSize() + " != "
                    + actual.minimumBindingSize();
        }
        if (expected.alignment() != actual.alignment()) {
            return "alignment " + expected.alignment() + " != "
                    + actual.alignment();
        }
        return parameterDifference(expected.parameters(),
                actual.parameters(), "");
    }

    private static String parameterDifference(
            io.github.libfdx.graphics.shader.reflection.ShaderParameter[] expected,
            io.github.libfdx.graphics.shader.reflection.ShaderParameter[] actual,
            String parent) {
        if (expected.length != actual.length) {
            return parent + " member count " + expected.length
                    + " != " + actual.length;
        }
        for (int i = 0; i < expected.length; i++) {
            var first = expected[i];
            var second = actual[i];
            String path = parent.isEmpty() ? first.name()
                    : parent + '.' + first.name();
            if (!first.valueType().equals(second.valueType())) {
                return path + " type " + first.valueType()
                        + '[' + first.valueType().typeName()
                        + "] != " + second.valueType() + '['
                        + second.valueType().typeName() + ']';
            }
            if (first.byteOffset() != second.byteOffset()
                    || first.occupiedSize()
                            != second.occupiedSize()
                    || first.minimumRequiredSize()
                            != second.minimumRequiredSize()
                    || first.alignment() != second.alignment()) {
                return path + " layout "
                        + first.byteOffset() + '/'
                        + first.occupiedSize() + '/'
                        + first.minimumRequiredSize() + '/'
                        + first.alignment() + " != "
                        + second.byteOffset() + '/'
                        + second.occupiedSize() + '/'
                        + second.minimumRequiredSize() + '/'
                        + second.alignment();
            }
            String nested = parameterDifference(first.members(),
                    second.members(), path);
            if (!nested.isEmpty()) {
                return nested;
            }
        }
        return "";
    }

    private static String describe(ShaderReflection reflection) {
        StringBuilder result = new StringBuilder()
                .append(reflection.physicalHash())
                .append(" capabilities=")
                .append(java.util.Arrays.toString(
                        reflection.requiredCapabilities()))
                .append(" entries=");
        for (var entry : reflection.entryPoints()) {
            result.append(entry.stage()).append(':')
                    .append(entry.name()).append('/')
                    .append(entry.workgroupSizeKind()).append('/')
                    .append(entry.workgroupX()).append(',')
                    .append(entry.workgroupY()).append(',')
                    .append(entry.workgroupZ()).append('/')
                    .append(entry.builtinMask()).append('/')
                    .append(entry.clipDistanceSize())
                    .append(" inputs=");
            for (var input : entry.inputs()) {
                result.append(input.location()).append('/')
                        .append(input.color()).append('/')
                        .append(input.blendSource()).append('/')
                        .append(input.valueType()).append('/')
                        .append(input.interpolation()).append('/')
                        .append(input.sampling()).append(',');
            }
            result.append(" outputs=");
            for (var output : entry.outputs()) {
                result.append(output.location()).append('/')
                        .append(output.color()).append('/')
                        .append(output.blendSource()).append('/')
                        .append(output.valueType()).append('/')
                        .append(output.interpolation()).append('/')
                        .append(output.sampling()).append(',');
            }
            result.append(" resources=[");
            for (var resource : entry.resources()) {
                result.append(resource.group()).append(':')
                        .append(resource.binding()).append('/')
                        .append(resource.minimumBindingSize())
                        .append(',');
            }
            result.append("] ");
        }
        result.append("bindings=");
        for (var binding : reflection.bindings()) {
            result.append(binding.group()).append(':')
                    .append(binding.binding()).append('/')
                    .append(binding.resourceKind()).append('/')
                    .append(binding.visibility()).append('/')
                    .append(binding.minimumBindingSize()).append('/')
                    .append(binding.sizeWithoutPadding()).append('/')
                    .append(binding.alignment()).append('/')
                    .append(binding.bindingArrayCount()).append('/')
                    .append(binding.access()).append('/')
                    .append(binding.textureDimension()).append('/')
                    .append(binding.textureSampleType()).append('/')
                    .append(binding.samplerKind()).append('/')
                    .append(binding.storageFormat()).append('/')
                    .append(binding.inputAttachmentIndex()).append('/');
            if (binding.bufferLayout() != null) {
                result.append('{');
                for (var parameter :
                        binding.bufferLayout().parameters()) {
                    result.append(parameter.name()).append('@')
                            .append(parameter.byteOffset()).append('+')
                            .append(parameter.occupiedSize()).append('/')
                            .append(parameter.minimumRequiredSize())
                            .append('/').append(parameter.alignment())
                            .append('/').append(parameter.arrayStride())
                            .append('/').append(parameter.matrixStride())
                            .append('/').append(parameter.valueType())
                            .append(',');
                }
                result.append('}');
            }
            result.append(' ');
        }
        return result.toString();
    }
}
