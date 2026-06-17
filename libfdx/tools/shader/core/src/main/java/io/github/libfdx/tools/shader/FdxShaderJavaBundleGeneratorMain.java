package io.github.libfdx.tools.shader;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ShaderAttribute;
import io.github.libfdx.graphics.ShaderBinding;
import io.github.libfdx.graphics.ShaderBindingType;
import io.github.libfdx.graphics.ShaderTarget;
import io.github.libfdx.graphics.ShaderReflection;
import io.github.libfdx.graphics.VertexFormat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emits Java ShaderBundle source from WGSL shader inputs.
 *
 * @author xpenatan
 */
public final class FdxShaderJavaBundleGeneratorMain {
    private static final String VERTEX_ENTRY = "vertexMain";
    private static final String FRAGMENT_ENTRY = "fragmentMain";
    private static final Pattern MSL_STRUCT_PATTERN = Pattern.compile("struct\\s+(\\w+)\\s*\\{.*?\\};\\R\\R",
            Pattern.DOTALL);

    private FdxShaderJavaBundleGeneratorMain() {
    }

    public static void main(String[] args) throws IOException {
        Arguments arguments = Arguments.parse(args);
        Path compiler = findCompiler(arguments.compilerDirectory);
        FdxTintNativeShaderCompiler compilerBridge =
                new FdxTintNativeShaderCompiler(new FdxTintProcessShaderCompiler(compiler));
        Files.createDirectories(arguments.outputDirectory);
        String source = generate(arguments, compilerBridge);
        Path packageDirectory = arguments.outputDirectory.resolve(arguments.packageName.replace('.', '/'));
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve(arguments.className + ".java"), source, StandardCharsets.UTF_8);
    }

    private static String generate(Arguments arguments, FdxShaderCompiler compiler) throws IOException {
        StringBuilder source = new StringBuilder(128 * 1024);
        source.append("package ").append(arguments.packageName).append(";\n\n");
        source.append("import io.github.libfdx.graphics.ShaderAttribute;\n");
        source.append("import io.github.libfdx.graphics.ShaderBinding;\n");
        source.append("import io.github.libfdx.graphics.ShaderBundle;\n");
        source.append("import io.github.libfdx.graphics.ShaderBindingType;\n");
        source.append("import io.github.libfdx.graphics.ShaderReflection;\n");
        source.append("import io.github.libfdx.graphics.VertexFormat;\n\n");
        source.append("/**\n");
        source.append(" * Generated built-in shader bundles.\n");
        source.append(" *\n");
        source.append(" * @author libfdx shader generator\n");
        source.append(" */\n");
        source.append("public final class ").append(arguments.className).append(" {\n");
        source.append("    private ").append(arguments.className).append("() {\n");
        source.append("    }\n\n");
        for (ShaderSpec shader : arguments.shaders) {
            GeneratedShader generated = generateShader(shader, compiler);
            appendShaderMethod(source, shader, generated);
        }
        source.append("}\n");
        return source.toString();
    }

    private static GeneratedShader generateShader(ShaderSpec shader, FdxShaderCompiler compiler) throws IOException {
        String wgsl = Files.readString(shader.wgslPath, StandardCharsets.UTF_8);
        ShaderReflection reflection = FdxWgslReflectionParser.parse(wgsl);
        String glslVertex;
        String glslFragment;
        String glslEsVertex;
        String glslEsFragment;
        if (shader.glslVertexPath != null && shader.glslFragmentPath != null) {
            glslVertex = Files.readString(shader.glslVertexPath, StandardCharsets.UTF_8);
            glslFragment = Files.readString(shader.glslFragmentPath, StandardCharsets.UTF_8);
            glslEsVertex = shader.glslEsVertexPath != null
                    ? Files.readString(shader.glslEsVertexPath, StandardCharsets.UTF_8)
                    : toGlslEs(glslVertex);
            glslEsFragment = shader.glslEsFragmentPath != null
                    ? Files.readString(shader.glslEsFragmentPath, StandardCharsets.UTF_8)
                    : toGlslEs(glslFragment);
        } else {
            glslVertex = toDesktopGlsl(compileText(compiler, wgsl, ShaderTarget.OPENGL_GLSL,
                    FdxTintShaderStage.VERTEX, VERTEX_ENTRY));
            glslFragment = toDesktopGlsl(compileText(compiler, wgsl, ShaderTarget.OPENGL_GLSL,
                    FdxTintShaderStage.FRAGMENT, FRAGMENT_ENTRY));
            glslEsVertex = toGeneratedGlslEsVertex(compileText(compiler, wgsl, ShaderTarget.WEBGL_GLSL_ES,
                    FdxTintShaderStage.VERTEX, VERTEX_ENTRY));
            glslEsFragment = toGeneratedGlslEsFragment(compileText(compiler, wgsl, ShaderTarget.WEBGL_GLSL_ES,
                    FdxTintShaderStage.FRAGMENT, FRAGMENT_ENTRY));
        }
        int[] spirvVertex = compileSpirv(compiler, wgsl, FdxTintShaderStage.VERTEX, VERTEX_ENTRY);
        int[] spirvFragment = compileSpirv(compiler, wgsl, FdxTintShaderStage.FRAGMENT, FRAGMENT_ENTRY);
        String mslVertex = compileText(compiler, wgsl, ShaderTarget.METAL_MSL, FdxTintShaderStage.VERTEX,
                VERTEX_ENTRY);
        String mslFragment = compileText(compiler, wgsl, ShaderTarget.METAL_MSL, FdxTintShaderStage.FRAGMENT,
                FRAGMENT_ENTRY);
        return new GeneratedShader(wgsl, glslVertex, glslFragment, glslEsVertex, glslEsFragment, spirvVertex,
                spirvFragment, combineMsl(mslVertex, mslFragment), reflection);
    }

    private static String compileText(FdxShaderCompiler compiler, String wgsl, ShaderTarget target,
            FdxTintShaderStage stage, String entryPoint) {
        FdxShaderCompilerResult result = compile(compiler, wgsl, target, stage, entryPoint);
        if (result.outputKind() != FdxTintCompilerOutput.TEXT) {
            throw new FdxException("Shader compiler returned non-text output for " + target + " " + stage);
        }
        return result.outputText();
    }

    private static int[] compileSpirv(FdxShaderCompiler compiler, String wgsl, FdxTintShaderStage stage,
            String entryPoint) {
        FdxShaderCompilerResult result = compile(compiler, wgsl, ShaderTarget.VULKAN_SPIRV, stage, entryPoint);
        if (result.outputKind() != FdxTintCompilerOutput.SPIRV) {
            throw new FdxException("Shader compiler returned non-SPIR-V output for " + stage);
        }
        byte[] bytes = result.output();
        if (bytes.length % 4 != 0) {
            throw new FdxException("SPIR-V output size is not a multiple of 4 bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] words = new int[bytes.length / 4];
        for (int i = 0; i < words.length; i++) {
            words[i] = buffer.getInt();
        }
        return words;
    }

    private static FdxShaderCompilerResult compile(FdxShaderCompiler compiler, String wgsl, ShaderTarget target,
            FdxTintShaderStage stage, String entryPoint) {
        FdxShaderCompilerResult result = compiler.compile(FdxShaderCompilerRequest.builder(wgsl, target)
                .stage(stage)
                .entryPoint(entryPoint)
                .build());
        if (!result.success()) {
            throw new FdxException("Could not compile shader target " + target + " stage " + stage + ": "
                    + diagnostics(result));
        }
        return result;
    }

    private static String diagnostics(FdxShaderCompilerResult result) {
        StringBuilder builder = new StringBuilder();
        FdxShaderCompilerDiagnostic[] diagnostics = result.diagnostics();
        for (int i = 0; i < diagnostics.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(diagnostics[i].message());
        }
        return builder.toString();
    }

    private static String toGlslEs(String source) {
        if (source.startsWith("#version 330 core")) {
            return "#version 300 es\nprecision highp float;\nprecision highp int;"
                    + source.substring("#version 330 core".length());
        }
        if (source.startsWith("#version 330")) {
            return "#version 300 es\nprecision highp float;\nprecision highp int;"
                    + source.substring("#version 330".length());
        }
        return source;
    }

    private static String toDesktopGlsl(String source) {
        if (source.startsWith("#version 330\n")) {
            return "#version 330 core\n#extension GL_ARB_separate_shader_objects : enable\n"
                    + source.substring("#version 330\n".length());
        }
        if (source.startsWith("#version 330 core\n")
                && !source.contains("GL_ARB_separate_shader_objects")) {
            return "#version 330 core\n#extension GL_ARB_separate_shader_objects : enable\n"
                    + source.substring("#version 330 core\n".length());
        }
        return source;
    }

    private static String toGeneratedGlslEsVertex(String source) {
        return removeGlslEsLocationQualifier(source, "out");
    }

    private static String toGeneratedGlslEsFragment(String source) {
        return removeGlslEsLocationQualifier(removeGlslEsLocationQualifier(source, "in"), "out");
    }

    private static String removeGlslEsLocationQualifier(String source, String qualifier) {
        return source.replaceAll("(?m)^layout\\(location = \\d+\\)\\s+" + qualifier + "\\s+", qualifier + " ");
    }

    private static String combineMsl(String vertex, String fragment) {
        String cleanedFragment = fragment
                .replaceFirst("^#include <metal_stdlib>\\Rusing namespace metal;\\R\\R", "");
        Set<String> vertexStructs = new HashSet<>();
        Matcher vertexMatcher = MSL_STRUCT_PATTERN.matcher(vertex);
        while (vertexMatcher.find()) {
            vertexStructs.add(vertexMatcher.group(1));
        }
        Matcher fragmentMatcher = MSL_STRUCT_PATTERN.matcher(cleanedFragment);
        StringBuffer buffer = new StringBuffer();
        while (fragmentMatcher.find()) {
            if (vertexStructs.contains(fragmentMatcher.group(1))) {
                fragmentMatcher.appendReplacement(buffer, "");
            }
        }
        fragmentMatcher.appendTail(buffer);
        return vertex + "\n" + buffer;
    }

    private static void appendShaderMethod(StringBuilder source, ShaderSpec shader, GeneratedShader generated) {
        source.append("    /**\n");
        source.append("     * Returns the ").append(shader.label).append(" shader bundle.\n");
        source.append("     *\n");
        source.append("     * @return the shader bundle\n");
        source.append("     */\n");
        source.append("    public static ShaderBundle ").append(shader.methodName).append("() {\n");
        source.append("        return ShaderBundle.builder(\"").append(escapeJava(shader.label)).append("\")\n");
        appendTextBlockCall(source, "wgsl", generated.wgsl);
        appendTextBlockCall(source, "glsl", generated.glslVertex, generated.glslFragment);
        appendTextBlockCall(source, "glslEs", generated.glslEsVertex, generated.glslEsFragment);
        appendIntArrayCall(source, generated.spirvVertex, generated.spirvFragment);
        appendTextBlockCall(source, "msl", generated.msl);
        appendReflectionCall(source, generated.reflection);
        source.append("                .build();\n");
        source.append("    }\n\n");
    }

    private static void appendTextBlockCall(StringBuilder source, String method, String value) {
        source.append("                .").append(method).append("(\"\"\"\n");
        source.append(escapeTextBlock(value));
        source.append("\"\"\")\n");
    }

    private static void appendTextBlockCall(StringBuilder source, String method, String first, String second) {
        source.append("                .").append(method).append("(\"\"\"\n");
        source.append(escapeTextBlock(first));
        source.append("\"\"\", \"\"\"\n");
        source.append(escapeTextBlock(second));
        source.append("\"\"\")\n");
    }

    private static void appendIntArrayCall(StringBuilder source, int[] vertex, int[] fragment) {
        source.append("                .spirv(new int[] {\n");
        appendIntArray(source, vertex);
        source.append("                }, new int[] {\n");
        appendIntArray(source, fragment);
        source.append("                })\n");
    }

    private static void appendReflectionCall(StringBuilder source, ShaderReflection reflection) {
        source.append("                .reflection(ShaderReflection.of(new ShaderBinding[] {\n");
        ShaderBinding[] bindings = reflection.bindings();
        for (int i = 0; i < bindings.length; i++) {
            ShaderBinding binding = bindings[i];
            source.append("                        ShaderBinding.of(")
                    .append(binding.group())
                    .append(", ")
                    .append(binding.binding())
                    .append(", \"")
                    .append(escapeJava(binding.name()))
                    .append("\", ShaderBindingType.")
                    .append(binding.type().name())
                    .append(")");
            if (i + 1 < bindings.length) {
                source.append(',');
            }
            source.append('\n');
        }
        source.append("                }, new ShaderAttribute[] {\n");
        ShaderAttribute[] attributes = reflection.attributes();
        for (int i = 0; i < attributes.length; i++) {
            ShaderAttribute attribute = attributes[i];
            source.append("                        ShaderAttribute.of(")
                    .append(attribute.location())
                    .append(", \"")
                    .append(escapeJava(attribute.name()))
                    .append("\", VertexFormat.")
                    .append(attribute.format().name())
                    .append(")");
            if (i + 1 < attributes.length) {
                source.append(',');
            }
            source.append('\n');
        }
        source.append("                }))\n");
    }

    private static void appendIntArray(StringBuilder source, int[] words) {
        for (int i = 0; i < words.length; i++) {
            if (i % 8 == 0) {
                source.append("                        ");
            }
            source.append(String.format("0x%08x", words[i]));
            if (i + 1 < words.length) {
                source.append(", ");
            }
            if (i % 8 == 7 || i + 1 == words.length) {
                source.append('\n');
            }
        }
    }

    private static String escapeTextBlock(String value) {
        return value.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"");
    }

    private static String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Path findCompiler(Path directory) throws IOException {
        if (directory == null) {
            throw new FdxException("Shader compiler directory is required");
        }
        String executableName = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "libfdx_shaderc_cli.exe"
                : "libfdx_shaderc_cli";
        if (!Files.isDirectory(directory)) {
            throw new FdxException("Shader compiler directory does not exist: " + directory);
        }
        try (var stream = Files.walk(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(executableName))
                    .findFirst()
                    .orElseThrow(() -> new FdxException("Could not find " + executableName + " under "
                            + directory));
        }
    }

    private static final class Arguments {
        private final Path compilerDirectory;
        private final Path outputDirectory;
        private final String packageName;
        private final String className;
        private final List<ShaderSpec> shaders;

        private Arguments(Path compilerDirectory, Path outputDirectory, String packageName, String className,
                List<ShaderSpec> shaders) {
            this.compilerDirectory = compilerDirectory;
            this.outputDirectory = outputDirectory;
            this.packageName = packageName;
            this.className = className;
            this.shaders = shaders;
        }

        static Arguments parse(String[] args) {
            Path compilerDirectory = null;
            Path outputDirectory = null;
            String packageName = null;
            String className = null;
            List<ShaderSpec> shaders = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--compiler-dir".equals(arg)) {
                    compilerDirectory = Path.of(requireValue(args, ++i, arg));
                } else if ("--output".equals(arg)) {
                    outputDirectory = Path.of(requireValue(args, ++i, arg));
                } else if ("--package".equals(arg)) {
                    packageName = requireValue(args, ++i, arg);
                } else if ("--class".equals(arg)) {
                    className = requireValue(args, ++i, arg);
                } else if ("--shader".equals(arg)) {
                    shaders.add(ShaderSpec.parse(requireValue(args, ++i, arg)));
                } else {
                    throw new FdxException("Unknown argument: " + arg);
                }
            }
            if (compilerDirectory == null || outputDirectory == null || packageName == null || className == null) {
                throw new FdxException("Usage: --compiler-dir <dir> --output <dir> --package <name> "
                        + "--class <name> --shader <method|label|wgsl[|glslVertex|glslFragment"
                        + "[|glslEsVertex|glslEsFragment]]>");
            }
            if (shaders.isEmpty()) {
                throw new FdxException("At least one --shader value is required");
            }
            return new Arguments(compilerDirectory, outputDirectory, packageName, className, shaders);
        }

        private static String requireValue(String[] args, int index, String argument) {
            if (index >= args.length) {
                throw new FdxException("Missing value for " + argument);
            }
            return args[index];
        }
    }

    private static final class ShaderSpec {
        private final String methodName;
        private final String label;
        private final Path wgslPath;
        private final Path glslVertexPath;
        private final Path glslFragmentPath;
        private final Path glslEsVertexPath;
        private final Path glslEsFragmentPath;

        private ShaderSpec(String methodName, String label, Path wgslPath, Path glslVertexPath, Path glslFragmentPath,
                Path glslEsVertexPath, Path glslEsFragmentPath) {
            this.methodName = methodName;
            this.label = label;
            this.wgslPath = wgslPath;
            this.glslVertexPath = glslVertexPath;
            this.glslFragmentPath = glslFragmentPath;
            this.glslEsVertexPath = glslEsVertexPath;
            this.glslEsFragmentPath = glslEsFragmentPath;
        }

        static ShaderSpec parse(String value) {
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3 && parts.length != 5 && parts.length != 7) {
                throw new FdxException("Invalid --shader value: " + value);
            }
            Path glslVertex = parts.length >= 5 ? Path.of(parts[3]) : null;
            Path glslFragment = parts.length >= 5 ? Path.of(parts[4]) : null;
            Path glslEsVertex = parts.length == 7 && parts[5].length() > 0 ? Path.of(parts[5]) : null;
            Path glslEsFragment = parts.length == 7 && parts[6].length() > 0 ? Path.of(parts[6]) : null;
            return new ShaderSpec(parts[0], parts[1], Path.of(parts[2]), glslVertex, glslFragment, glslEsVertex,
                    glslEsFragment);
        }
    }

    private record GeneratedShader(String wgsl, String glslVertex, String glslFragment, String glslEsVertex,
            String glslEsFragment, int[] spirvVertex, int[] spirvFragment, String msl, ShaderReflection reflection) {
    }
}
