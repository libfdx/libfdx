package io.github.libfdx.graphics.shader;

import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.target.RuntimeShaderTargetCompiler;
import io.github.libfdx.graphics.shader.target.RuntimeWgslTargetVerifier;
import io.github.libfdx.graphics.shader.target.ShaderArtifactFormat;
import io.github.libfdx.graphics.shader.target.ShaderArtifactFormats;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.target.ShaderCompilerRegistry;
import io.github.libfdx.graphics.shader.target.ShaderEntryPointRemap;
import io.github.libfdx.graphics.shader.target.ShaderEntryPointSelection;
import io.github.libfdx.graphics.shader.target.ShaderStageArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetCompileRequest;
import io.github.libfdx.graphics.shader.target.ShaderTargetCompileResult;
import io.github.libfdx.graphics.shader.target.ShaderTargetEnvironment;
import io.github.libfdx.graphics.shader.target.ShaderTargetId;
import io.github.libfdx.graphics.shader.target.ShaderVerificationRequirement;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.runtime.core.RuntimeCore;
import io.github.libfdx.runtime.core.RuntimeCoreException;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates provider-ready shader module descriptors through target artifacts.
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
     * Returns a descriptor containing an artifact for the requested built-in target.
     *
     * <p>This compatibility overload adapts the current backend-owned runtime
     * compiler into a short-lived immutable registry. New build/runtime
     * composition should supply an explicit {@link ShaderCompilerRegistry}.</p>
     *
     * @param descriptor the canonical descriptor
     * @param target the built-in target
     * @param providerName the provider name used in diagnostics
     * @return the provider-ready descriptor
     */
    public static ShaderModuleDescriptor requireTarget(ShaderModuleDescriptor descriptor, ShaderTarget target,
            String providerName) {
        if (target == null) {
            throw new FdxException("Shader target cannot be null");
        }
        requireDescriptor(descriptor);
        requireWgsl(descriptor, target.id(), providerName, target.format());

        ShaderTargetArtifact packaged = descriptor.targetArtifact();
        if (packaged != null && packaged.target().equals(target.id())
                && packaged.format().equals(target.format())
                && packaged.environment().equals(target.environment())) {
            return descriptor;
        }
        if (packaged == null && hasGeneratedSource(descriptor, target.format())) {
            return descriptor;
        }

        RuntimeShaderCompiler runtime;
        try {
            runtime = RuntimeCore.shaderCompiler();
        } catch (RuntimeCoreException unavailable) {
            if (target == ShaderTarget.WEBGPU_WGSL || target == ShaderTarget.WGPU_WGSL) {
                return descriptor;
            }
            throw missingCompiler(descriptor, target.id(), providerName, target.format(), unavailable);
        }
        if (runtime == null) {
            if (target == ShaderTarget.WEBGPU_WGSL || target == ShaderTarget.WGPU_WGSL) {
                return descriptor;
            }
            throw missingCompiler(descriptor, target.id(), providerName, target.format(), null);
        }

        ShaderCompilerRegistry.Builder registryBuilder = ShaderCompilerRegistry.builder()
                .compiler(new RuntimeShaderTargetCompiler(runtime));
        ShaderVerificationRequirement verification = ShaderVerificationRequirement.PROVIDER_PIPELINE;
        if (target == ShaderTarget.WEBGPU_WGSL || target == ShaderTarget.WGPU_WGSL) {
            registryBuilder.verifier(new RuntimeWgslTargetVerifier(runtime));
            verification = ShaderVerificationRequirement.REQUIRED;
        }
        try {
            return requireTarget(descriptor, target.id(), target.format(), target.environment(),
                    registryBuilder.build(), verification, providerName);
        } catch (FdxException error) {
            String action = target == ShaderTarget.WEBGPU_WGSL || target == ShaderTarget.WGPU_WGSL
                    ? "validate" : "compile";
            throw new FdxException("Could not " + action + " WGSL shader " + descriptor.label()
                    + (action.equals("compile") ? " for " + target : "") + ": " + error.getMessage(), error);
        }
    }

    /**
     * Compiles through an explicit immutable registry.
     *
     * @param descriptor the canonical descriptor
     * @param target the stable target ID
     * @param format the requested artifact format
     * @param environment the exact consumer environment
     * @param registry the compiler/verifier registry
     * @param verification the verification requirement
     * @param providerName the consumer/provider diagnostic name
     * @return the provider-ready descriptor
     */
    public static ShaderModuleDescriptor requireTarget(ShaderModuleDescriptor descriptor,
            ShaderTargetId target, ShaderArtifactFormat format, ShaderTargetEnvironment environment,
            ShaderCompilerRegistry registry, ShaderVerificationRequirement verification, String providerName) {
        requireDescriptor(descriptor);
        if (target == null || format == null || environment == null || registry == null) {
            throw new FdxException("Shader target, format, environment, and registry cannot be null");
        }
        requireWgsl(descriptor, target, providerName, format);

        ShaderTargetCompileRequest request = ShaderTargetCompileRequest.builder(
                        descriptor.label(), descriptor.wgslSource(), target, format, environment)
                .shaderInterface(descriptor.reflection())
                .profile(descriptor.reflection().profile())
                .entryPoints(entryPoints(descriptor, format))
                .verification(verification)
                .build();
        ShaderTargetCompileResult result = registry.compile(request);
        result.throwIfFailed(descriptor.label());
        return descriptor(result.artifact(), descriptor.label(), descriptor.wgslSource());
    }

    private static ShaderEntryPointSelection[] entryPoints(
            ShaderModuleDescriptor descriptor, ShaderArtifactFormat format) {
        if (!ShaderArtifactFormats.WGSL_TEXT.equals(format)) {
            return new ShaderEntryPointSelection[] {
                    ShaderEntryPointSelection.of(ShaderArtifactStage.VERTEX,
                            descriptor.vertexEntryPoint()),
                    ShaderEntryPointSelection.of(ShaderArtifactStage.FRAGMENT,
                            descriptor.fragmentEntryPoint())
            };
        }
        ShaderReflection reflection = descriptor.reflection();
        if (!reflection.complete()) {
            return new ShaderEntryPointSelection[0];
        }
        ShaderEntryPointSelection[] selections =
                new ShaderEntryPointSelection[reflection.entryPointCount()];
        for (int i = 0; i < selections.length; i++) {
            ShaderEntryPoint entryPoint = reflection.entryPoint(i);
            selections[i] = ShaderEntryPointSelection.of(
                    switch (entryPoint.stage()) {
                        case VERTEX -> ShaderArtifactStage.VERTEX;
                        case FRAGMENT -> ShaderArtifactStage.FRAGMENT;
                        case COMPUTE -> ShaderArtifactStage.COMPUTE;
                    }, entryPoint.name());
        }
        return selections;
    }

    /**
     * Converts a target artifact to the current provider module descriptor.
     *
     * <p>Unknown/custom formats remain available through
     * {@link ShaderModuleDescriptor#targetArtifact()} while canonical WGSL is
     * retained as the compatibility source.</p>
     *
     * @param artifact the target artifact
     * @param label the label
     * @param canonicalWgsl the canonical WGSL source
     * @return the descriptor
     */
    public static ShaderModuleDescriptor descriptor(ShaderTargetArtifact artifact,
            String label, String canonicalWgsl) {
        if (artifact == null) {
            throw new FdxException("Shader target artifact cannot be null");
        }
        ShaderModuleDescriptor descriptor;
        ShaderEntryPointRemap vertexRemap = findRemap(artifact, ShaderArtifactStage.VERTEX);
        ShaderEntryPointRemap fragmentRemap = findRemap(artifact, ShaderArtifactStage.FRAGMENT);
        String vertexEntry = vertexRemap != null
                ? vertexRemap.targetName() : ShaderModuleDescriptor.DEFAULT_VERTEX_ENTRY_POINT;
        String fragmentEntry = fragmentRemap != null
                ? fragmentRemap.targetName() : ShaderModuleDescriptor.DEFAULT_FRAGMENT_ENTRY_POINT;

        if (ShaderArtifactFormats.WGSL_TEXT.equals(artifact.format())) {
            ShaderStageArtifact module = requireStage(artifact, ShaderArtifactStage.MODULE, "");
            descriptor = ShaderModuleDescriptor.wgsl(label, module.text());
        } else if (ShaderArtifactFormats.GLSL_TEXT.equals(artifact.format())
                || ShaderArtifactFormats.GLSL_ES_TEXT.equals(artifact.format())) {
            descriptor = ShaderModuleDescriptor.generatedGlsl(label,
                    requireStage(artifact, ShaderArtifactStage.VERTEX, vertexEntry).text(),
                    requireStage(artifact, ShaderArtifactStage.FRAGMENT, fragmentEntry).text())
                    .wgsl(canonicalWgsl);
        } else if (ShaderArtifactFormats.SPIRV_BINARY.equals(artifact.format())) {
            descriptor = ShaderModuleDescriptor.generatedSpirv(label,
                    words(requireStage(artifact, ShaderArtifactStage.VERTEX, vertexEntry).payload()),
                    words(requireStage(artifact, ShaderArtifactStage.FRAGMENT, fragmentEntry).payload()))
                    .wgsl(canonicalWgsl);
        } else if (ShaderArtifactFormats.MSL_TEXT.equals(artifact.format())) {
            ShaderStageArtifact module = artifact.find(ShaderArtifactStage.MODULE, "");
            String source = module != null ? module.text() : combineMsl(
                    requireStage(artifact, ShaderArtifactStage.VERTEX, vertexEntry).text(),
                    requireStage(artifact, ShaderArtifactStage.FRAGMENT, fragmentEntry).text());
            descriptor = ShaderModuleDescriptor.generatedMsl(label, source).wgsl(canonicalWgsl);
        } else if (ShaderArtifactFormats.HLSL_TEXT.equals(artifact.format())) {
            descriptor = ShaderModuleDescriptor.generatedHlsl(label,
                    requireStage(artifact, ShaderArtifactStage.VERTEX, vertexEntry).text(),
                    requireStage(artifact, ShaderArtifactStage.FRAGMENT, fragmentEntry).text())
                    .wgsl(canonicalWgsl);
        } else {
            descriptor = ShaderModuleDescriptor.wgsl(label, canonicalWgsl);
        }
        return descriptor.entryPoints(vertexEntry, fragmentEntry)
                .reflection(artifact.translatedInterface().canonical())
                .targetArtifact(artifact);
    }

    private static ShaderStageArtifact requireStage(ShaderTargetArtifact artifact,
            ShaderArtifactStage stage, String entryPoint) {
        ShaderStageArtifact result = artifact.find(stage, entryPoint);
        if (result == null) {
            throw new FdxException("Shader artifact " + artifact.target() + " is missing "
                    + stage + " entry point " + entryPoint);
        }
        return result;
    }

    private static ShaderEntryPointRemap findRemap(ShaderTargetArtifact artifact, ShaderArtifactStage stage) {
        for (ShaderEntryPointRemap remap : artifact.translatedInterface().entryPoints()) {
            if (remap.stage() == stage) {
                return remap;
            }
        }
        return null;
    }

    private static boolean hasGeneratedSource(ShaderModuleDescriptor descriptor, ShaderArtifactFormat format) {
        if (ShaderArtifactFormats.GLSL_TEXT.equals(format)
                || ShaderArtifactFormats.GLSL_ES_TEXT.equals(format)) {
            return descriptor.hasSource(ShaderLanguage.GLSL);
        }
        if (ShaderArtifactFormats.SPIRV_BINARY.equals(format)) {
            return descriptor.hasSource(ShaderLanguage.SPIRV);
        }
        if (ShaderArtifactFormats.MSL_TEXT.equals(format)) {
            return descriptor.hasSource(ShaderLanguage.MSL);
        }
        if (ShaderArtifactFormats.HLSL_TEXT.equals(format)) {
            return descriptor.hasSource(ShaderLanguage.HLSL);
        }
        return false;
    }

    private static void requireDescriptor(ShaderModuleDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("ShaderModuleDescriptor cannot be null");
        }
    }

    private static void requireWgsl(ShaderModuleDescriptor descriptor, ShaderTargetId target,
            String providerName, ShaderArtifactFormat format) {
        if (!descriptor.hasSource(ShaderLanguage.WGSL)) {
            throw new FdxException(provider(providerName) + " requires " + format
                    + " shader modules for target " + target + ". Shader module " + descriptor.label()
                    + " must provide WGSL as its canonical source.");
        }
    }

    private static FdxException missingCompiler(ShaderModuleDescriptor descriptor, ShaderTargetId target,
            String providerName, ShaderArtifactFormat format, Throwable cause) {
        String message = provider(providerName) + " requires " + format + " shader modules for target "
                + target + ". Shader module " + descriptor.label()
                + " only provides WGSL and the runtime shader compiler is not available.";
        return cause != null ? new FdxException(message, cause) : new FdxException(message);
    }

    private static String provider(String providerName) {
        return providerName != null && providerName.length() > 0 ? providerName : "Provider";
    }

    private static int[] words(byte[] bytes) {
        if (bytes.length % Integer.BYTES != 0) {
            throw new FdxException("SPIR-V output size is not a multiple of 4 bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] words = new int[bytes.length / Integer.BYTES];
        for (int i = 0; i < words.length; i++) {
            words[i] = buffer.getInt();
        }
        return words;
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
