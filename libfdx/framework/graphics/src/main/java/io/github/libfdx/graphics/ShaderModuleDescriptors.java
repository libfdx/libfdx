package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.runtime.core.RuntimeCore;
import io.github.libfdx.runtime.core.RuntimeCoreException;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileDiagnostic;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileOutputKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates provider-ready shader module descriptors.
 *
 * @author xpenatan
 */
public final class ShaderModuleDescriptors {
    private static final String LINE_BREAK_REGEX = "(?:\\r\\n|\\n|\\r)";
    private static final Pattern MSL_STRUCT_PATTERN = Pattern.compile("struct\\s+(\\w+)\\s*\\{.*?\\};"
            + LINE_BREAK_REGEX + LINE_BREAK_REGEX, Pattern.DOTALL);
    private ShaderModuleDescriptors() {
    }

    /**
     * Returns a descriptor that contains source for the requested target.
     *
     * @param descriptor the descriptor
     * @param target the target
     * @param providerName the provider name used in diagnostics
     * @return the provider-ready descriptor
     */
    public static ShaderModuleDescriptor requireTarget(ShaderModuleDescriptor descriptor, ShaderTarget target,
            String providerName) {
        if (descriptor == null) {
            throw new FdxException("ShaderModuleDescriptor cannot be null");
        }
        if (target == null) {
            throw new FdxException("Shader target cannot be null");
        }
        switch (target) {
            case WEBGPU_WGSL:
            case WGPU_WGSL:
                if (descriptor.hasSource(ShaderLanguage.WGSL)) {
                    return descriptor;
                }
                throw missingWgsl(descriptor, target, providerName, "WGSL");
            case WEBGL_GLSL_ES:
            case GLES_GLSL_ES:
                return compileGlsl(descriptor, target, providerName, true);
            case OPENGL_GLSL:
                return compileGlsl(descriptor, target, providerName, false);
            case VULKAN_SPIRV:
                return compileSpirv(descriptor, target, providerName);
            case METAL_MSL:
                return compileMsl(descriptor, target, providerName);
            case DIRECTX_HLSL:
                throw new FdxException("Shader target " + target + " is not supported by ShaderModuleDescriptor yet");
            default:
                throw new FdxException("Unsupported shader target: " + target);
        }
    }

    private static ShaderModuleDescriptor compileGlsl(ShaderModuleDescriptor descriptor, ShaderTarget target,
            String providerName, boolean glslEs) {
        String source = requireWgsl(descriptor, target, providerName, glslEs ? "GLSL ES" : "GLSL");
        RuntimeShaderCompiler compiler = requireCompiler(descriptor, target, providerName, glslEs ? "GLSL ES" :
                "GLSL");
        String vertex = compileText(compiler, descriptor, source, target, RuntimeShaderCompileStage.VERTEX,
                descriptor.vertexEntryPoint());
        String fragment = compileText(compiler, descriptor, source, target, RuntimeShaderCompileStage.FRAGMENT,
                descriptor.fragmentEntryPoint());
        if (glslEs) {
            vertex = toGlslEsVertex(vertex);
            fragment = toGlslEsFragment(fragment);
        } else {
            vertex = toDesktopGlsl(vertex);
            fragment = toDesktopGlsl(fragment);
        }
        return ShaderModuleDescriptor.generatedGlsl(descriptor.label(), vertex, fragment)
                .entryPoints(descriptor.vertexEntryPoint(), descriptor.fragmentEntryPoint());
    }

    private static ShaderModuleDescriptor compileSpirv(ShaderModuleDescriptor descriptor, ShaderTarget target,
            String providerName) {
        String source = requireWgsl(descriptor, target, providerName, "SPIR-V");
        RuntimeShaderCompiler compiler = requireCompiler(descriptor, target, providerName, "SPIR-V");
        int[] vertex = compileSpirvWords(compiler, descriptor, source, target, RuntimeShaderCompileStage.VERTEX,
                descriptor.vertexEntryPoint());
        int[] fragment = compileSpirvWords(compiler, descriptor, source, target, RuntimeShaderCompileStage.FRAGMENT,
                descriptor.fragmentEntryPoint());
        return ShaderModuleDescriptor.generatedSpirv(descriptor.label(), vertex, fragment)
                .entryPoints(descriptor.vertexEntryPoint(), descriptor.fragmentEntryPoint());
    }

    private static ShaderModuleDescriptor compileMsl(ShaderModuleDescriptor descriptor, ShaderTarget target,
            String providerName) {
        String source = requireWgsl(descriptor, target, providerName, "MSL");
        RuntimeShaderCompiler compiler = requireCompiler(descriptor, target, providerName, "MSL");
        String vertex = compileText(compiler, descriptor, source, target, RuntimeShaderCompileStage.VERTEX,
                descriptor.vertexEntryPoint());
        String fragment = compileText(compiler, descriptor, source, target, RuntimeShaderCompileStage.FRAGMENT,
                descriptor.fragmentEntryPoint());
        return ShaderModuleDescriptor.generatedMsl(descriptor.label(), combineMsl(vertex, fragment))
                .entryPoints(descriptor.vertexEntryPoint(), descriptor.fragmentEntryPoint());
    }

    private static String requireWgsl(ShaderModuleDescriptor descriptor, ShaderTarget target, String providerName,
            String nativeLanguage) {
        if (!descriptor.hasSource(ShaderLanguage.WGSL)) {
            throw missingWgsl(descriptor, target, providerName, nativeLanguage);
        }
        return descriptor.wgslSource();
    }

    private static RuntimeShaderCompiler requireCompiler(ShaderModuleDescriptor descriptor, ShaderTarget target,
            String providerName, String nativeLanguage) {
        try {
            return RuntimeCore.shaderCompiler();
        } catch (RuntimeCoreException exception) {
            throw new FdxException(provider(providerName) + " requires " + nativeLanguage
                    + " shader modules for target " + target + ". Shader module " + descriptor.label()
                    + " only provides WGSL and the runtime shader compiler is not available.", exception);
        }
    }

    private static String compileText(RuntimeShaderCompiler compiler, ShaderModuleDescriptor descriptor,
            String source, ShaderTarget target, RuntimeShaderCompileStage stage, String entryPoint) {
        RuntimeShaderCompileResult result = compile(compiler, descriptor, source, target, stage, entryPoint);
        if (result.outputKind() != RuntimeShaderCompileOutputKind.TEXT) {
            throw new FdxException("Runtime shader compiler returned " + result.outputKind()
                    + " for text target " + target + " stage " + stage);
        }
        return result.outputText();
    }

    private static int[] compileSpirvWords(RuntimeShaderCompiler compiler, ShaderModuleDescriptor descriptor,
            String source, ShaderTarget target, RuntimeShaderCompileStage stage, String entryPoint) {
        RuntimeShaderCompileResult result = compile(compiler, descriptor, source, target, stage, entryPoint);
        if (result.outputKind() != RuntimeShaderCompileOutputKind.SPIRV) {
            throw new FdxException("Runtime shader compiler returned " + result.outputKind()
                    + " for SPIR-V target " + target + " stage " + stage);
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

    private static RuntimeShaderCompileResult compile(RuntimeShaderCompiler compiler, ShaderModuleDescriptor descriptor,
            String source, ShaderTarget target, RuntimeShaderCompileStage stage, String entryPoint) {
        RuntimeShaderCompileResult result = compiler.compile(RuntimeShaderCompileRequest.builder(source,
                toRuntimeTarget(target))
                .stage(stage)
                .entryPoint(entryPoint)
                .build());
        if (!result.success()) {
            throw new FdxException("Could not compile WGSL shader " + descriptor.label() + " for " + target
                    + " stage " + stage + ": " + diagnostics(result));
        }
        return result;
    }

    private static RuntimeShaderCompileTarget toRuntimeTarget(ShaderTarget target) {
        switch (target) {
            case WEBGPU_WGSL:
                return RuntimeShaderCompileTarget.WEBGPU_WGSL;
            case WGPU_WGSL:
                return RuntimeShaderCompileTarget.WGPU_WGSL;
            case WEBGL_GLSL_ES:
                return RuntimeShaderCompileTarget.WEBGL_GLSL_ES;
            case GLES_GLSL_ES:
                return RuntimeShaderCompileTarget.GLES_GLSL_ES;
            case OPENGL_GLSL:
                return RuntimeShaderCompileTarget.OPENGL_GLSL;
            case VULKAN_SPIRV:
                return RuntimeShaderCompileTarget.VULKAN_SPIRV;
            case METAL_MSL:
                return RuntimeShaderCompileTarget.METAL_MSL;
            case DIRECTX_HLSL:
                return RuntimeShaderCompileTarget.DIRECTX_HLSL;
            default:
                return RuntimeShaderCompileTarget.WEBGPU_WGSL;
        }
    }

    private static String diagnostics(RuntimeShaderCompileResult result) {
        StringBuilder builder = new StringBuilder();
        RuntimeShaderCompileDiagnostic[] diagnostics = result.diagnostics();
        for (int i = 0; i < diagnostics.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(diagnostics[i].message());
        }
        return builder.toString();
    }

    private static FdxException missingWgsl(ShaderModuleDescriptor descriptor, ShaderTarget target,
            String providerName, String nativeLanguage) {
        return new FdxException(provider(providerName) + " requires " + nativeLanguage + " shader modules for target "
                + target + ". Shader module " + descriptor.label()
                + " must provide WGSL for Tint runtime compilation.");
    }

    private static String provider(String providerName) {
        return providerName != null && providerName.length() > 0 ? providerName : "Provider";
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

    private static String toGlslEsVertex(String source) {
        return normalizeGlslEsFloats(removeGlslEsLocationQualifier(source, "out"));
    }

    private static String toGlslEsFragment(String source) {
        return normalizeGlslEsFloats(removeGlslEsLocationQualifier(removeGlslEsLocationQualifier(source, "in"),
                "out"));
    }

    private static String removeGlslEsLocationQualifier(String source, String qualifier) {
        StringBuilder builder = null;
        int lineStart = 0;
        while (lineStart < source.length()) {
            int lineEnd = source.indexOf('\n', lineStart);
            int nextLineStart = lineEnd >= 0 ? lineEnd + 1 : source.length();
            String line = source.substring(lineStart, nextLineStart);
            String replacement = removeGlslEsLocationQualifierLine(line, qualifier);
            if (builder != null) {
                builder.append(replacement);
            } else if (replacement != line) {
                builder = new StringBuilder(source.length());
                builder.append(source, 0, lineStart);
                builder.append(replacement);
            }
            lineStart = nextLineStart;
        }
        return builder != null ? builder.toString() : source;
    }

    private static String normalizeGlslEsFloats(String source) {
        StringBuilder builder = null;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == 'f' && isGlslFloatSuffix(source, i)) {
                if (builder == null) {
                    builder = new StringBuilder(source.length());
                    builder.append(source, 0, i);
                }
                continue;
            }
            if (builder != null) {
                builder.append(c);
            }
        }
        return builder != null ? builder.toString() : source;
    }

    private static String removeGlslEsLocationQualifierLine(String line, String qualifier) {
        String prefix = "layout(location = ";
        if (!line.startsWith(prefix)) {
            return line;
        }
        int closeIndex = line.indexOf(") ");
        if (closeIndex < 0) {
            return line;
        }
        for (int i = prefix.length(); i < closeIndex; i++) {
            char c = line.charAt(i);
            if ((c < '0' || c > '9') && c != ' ') {
                return line;
            }
        }
        int qualifierStart = closeIndex + 2;
        if (line.startsWith(qualifier + " ", qualifierStart)) {
            return line.substring(qualifierStart);
        }
        return line;
    }

    private static boolean isGlslFloatSuffix(String source, int suffixIndex) {
        if (suffixIndex == 0 || !isDigit(source.charAt(suffixIndex - 1))) {
            return false;
        }
        if (suffixIndex + 1 < source.length() && isIdentifierPart(source.charAt(suffixIndex + 1))) {
            return false;
        }
        int start = suffixIndex - 1;
        while (start >= 0) {
            char c = source.charAt(start);
            if (!isNumericLiteralPart(c)) {
                break;
            }
            start--;
        }
        if (start >= 0 && isIdentifierPart(source.charAt(start))) {
            return false;
        }
        return true;
    }

    private static boolean isNumericLiteralPart(char c) {
        return isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
    }

    private static boolean isIdentifierPart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static String combineMsl(String vertex, String fragment) {
        String cleanedFragment = fragment.replaceFirst("^#include <metal_stdlib>" + LINE_BREAK_REGEX
                + "using namespace metal;" + LINE_BREAK_REGEX + LINE_BREAK_REGEX, "");
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
}
