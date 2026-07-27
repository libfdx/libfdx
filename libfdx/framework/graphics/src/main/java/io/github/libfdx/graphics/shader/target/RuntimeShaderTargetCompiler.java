package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.ShaderValidationSeverity;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderBindingType;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileDiagnostic;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileOutputKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;
import io.github.libfdx.runtime.core.shader.RuntimeShaderBindingRemap;
import io.github.libfdx.runtime.core.shader.RuntimeShaderBindingRemapKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderEntryPointRemap;
import io.github.libfdx.runtime.core.shader.RuntimeShaderTargetBinding;
import io.github.libfdx.runtime.core.shader.RuntimeShaderTargetInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts the runtime Tint bridge to the extensible target compiler contract.
 *
 * @author xpenatan
 */
public final class RuntimeShaderTargetCompiler implements ShaderTargetCompiler {
    public static final ShaderCompilerId ID = ShaderCompilerId.of("libfdx.tint");
    public static final String VERSION = "runtime-abi-2";

    private final RuntimeShaderCompiler compiler;
    private final String version;

    /**
     * Creates a Tint-backed target compiler.
     *
     * @param compiler the runtime compiler bridge
     */
    public RuntimeShaderTargetCompiler(RuntimeShaderCompiler compiler) {
        this(compiler, VERSION);
    }

    /**
     * Creates a Tint-backed target compiler with an explicit cache version.
     *
     * @param compiler the runtime compiler bridge
     * @param version the compiler/cache version
     */
    public RuntimeShaderTargetCompiler(RuntimeShaderCompiler compiler, String version) {
        if (compiler == null) {
            throw new FdxException("Runtime shader compiler cannot be null");
        }
        if (version == null || version.trim().length() == 0) {
            throw new FdxException("Runtime shader target compiler version cannot be empty");
        }
        this.compiler = compiler;
        this.version = version.trim();
    }

    @Override
    public ShaderCompilerId id() {
        return ID;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public ShaderTargetId[] targets() {
        return ShaderTargets.standard();
    }

    @Override
    public boolean supports(ShaderTargetCompileRequest request) {
        if (request == null || !isStandardTarget(request.target())) {
            return false;
        }
        try {
            ShaderTargetEnvironment environment = request.environment();
            return environment.target().equals(request.target())
                    && environment.format().equals(request.format())
                    && defaultFormat(request.target()).equals(request.format());
        } catch (FdxException ignored) {
            return false;
        }
    }

    @Override
    public ShaderTargetCompileResult compile(ShaderTargetCompileRequest request) {
        if (!supports(request)) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.tint.unsupported", "Tint runtime compiler does not support the requested target"));
        }
        try {
            return compileSupported(request);
        } catch (FdxException error) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.tint.invalid-result", error.getMessage()));
        } catch (RuntimeException error) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.tint.exception", message(error)));
        }
    }

    private ShaderTargetCompileResult compileSupported(ShaderTargetCompileRequest request) {
        ShaderEntryPointSelection[] selections = request.entryPoints();
        if (isWgsl(request.target())) {
            RuntimeShaderCompileResult result = compiler.compile(runtimeRequest(request,
                    RuntimeShaderCompileStage.MODULE, ""));
            ShaderTargetCompileResult failure = failure(result, ShaderArtifactStage.MODULE, "");
            if (failure != null) {
                return failure;
            }
            if (result.outputKind() != RuntimeShaderCompileOutputKind.TEXT) {
                return outputKindFailure(request, ShaderArtifactStage.MODULE, result.outputKind());
            }
            ShaderReflection reflection = resolveReflection(request.shaderInterface(),
                    new RuntimeShaderCompileResult[] { result });
            ShaderTranslatedInterface translated = ShaderTranslatedInterface.identity(reflection, selections);
            ShaderStageArtifact module = ShaderStageArtifact.text(ShaderArtifactStage.MODULE, "",
                    request.format(), result.outputText());
            return success(request, new ShaderStageArtifact[] { module }, translated);
        }

        if (selections.length == 0) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.tint.entry-points-missing",
                    "Translated target " + request.target() + " requires at least one entry point"));
        }
        ShaderStageArtifact[] stages = new ShaderStageArtifact[selections.length];
        RuntimeShaderCompileResult[] results = new RuntimeShaderCompileResult[selections.length];
        for (int i = 0; i < selections.length; i++) {
            ShaderEntryPointSelection selection = selections[i];
            RuntimeShaderCompileResult result = compiler.compile(runtimeRequest(request,
                    runtimeStage(selection.stage()), selection.entryPoint()));
            results[i] = result;
            ShaderTargetCompileResult failure = failure(result, selection.stage(), selection.entryPoint());
            if (failure != null) {
                return failure;
            }
            String targetEntryPoint = targetEntryPoint(result, selection);
            if (request.format().encoding() == ShaderArtifactEncoding.TEXT) {
                if (result.outputKind() != RuntimeShaderCompileOutputKind.TEXT) {
                    return outputKindFailure(request, selection.stage(), result.outputKind());
                }
                String source = normalizeTargetText(request.target(), selection.stage(), result.outputText());
                stages[i] = ShaderStageArtifact.text(selection.stage(), targetEntryPoint,
                        request.format(), source);
            } else {
                if (result.outputKind() != RuntimeShaderCompileOutputKind.SPIRV) {
                    return outputKindFailure(request, selection.stage(), result.outputKind());
                }
                stages[i] = ShaderStageArtifact.binary(selection.stage(), targetEntryPoint,
                        request.format(), result.output());
            }
        }
        ShaderReflection reflection = resolveReflection(request.shaderInterface(), results);
        ShaderTranslatedInterface translated = translatedInterface(reflection, selections, results);
        return success(request, stages, translated);
    }

    private static String targetEntryPoint(RuntimeShaderCompileResult result,
            ShaderEntryPointSelection selection) {
        if (!result.hasTargetInterface()) {
            throw new FdxException("Tint returned no translated target interface for "
                    + selection.stage() + ' ' + selection.entryPoint());
        }
        RuntimeShaderEntryPointRemap[] entries = result.targetInterface().entryPoints();
        if (entries.length != 1 || artifactStage(entries[0].stage()) != selection.stage()
                || !entries[0].sourceName().equals(selection.entryPoint())) {
            throw new FdxException("Tint target interface entry point does not match its compile request");
        }
        return entries[0].targetName();
    }

    private static ShaderTranslatedInterface translatedInterface(ShaderReflection reflection,
            ShaderEntryPointSelection[] selections, RuntimeShaderCompileResult[] results) {
        List<ShaderEntryPointRemap> entries = new ArrayList<>();
        List<ShaderBindingRemap> bindings = new ArrayList<>();
        for (int i = 0; i < selections.length; i++) {
            ShaderEntryPointSelection selection = selections[i];
            RuntimeShaderCompileResult result = results[i];
            if (!result.hasTargetInterface()) {
                throw new FdxException("Tint returned no translated target interface for "
                        + selection.stage() + ' ' + selection.entryPoint());
            }
            RuntimeShaderTargetInterface targetInterface = result.targetInterface();
            RuntimeShaderEntryPointRemap[] runtimeEntries = targetInterface.entryPoints();
            if (runtimeEntries.length != 1) {
                throw new FdxException("Tint target interface must describe exactly one compiled entry point");
            }
            RuntimeShaderEntryPointRemap runtimeEntry = runtimeEntries[0];
            ShaderArtifactStage runtimeStage = artifactStage(runtimeEntry.stage());
            if (runtimeStage != selection.stage()
                    || !runtimeEntry.sourceName().equals(selection.entryPoint())) {
                throw new FdxException("Tint target interface entry point does not match its compile request");
            }
            entries.add(ShaderEntryPointRemap.of(runtimeStage,
                    runtimeEntry.sourceName(), runtimeEntry.targetName()));
            for (RuntimeShaderBindingRemap runtimeBinding : targetInterface.bindings()) {
                ShaderBinding canonical = reflection.findBinding(
                        runtimeBinding.sourceGroup(), runtimeBinding.sourceBinding());
                if (canonical == null) {
                    throw new FdxException("Tint target interface maps an unknown canonical binding "
                            + runtimeBinding.sourceGroup() + ':' + runtimeBinding.sourceBinding());
                }
                RuntimeShaderTargetBinding[] runtimeTargets = runtimeBinding.targets();
                ShaderTargetBinding[] targets = new ShaderTargetBinding[runtimeTargets.length];
                for (int targetIndex = 0; targetIndex < runtimeTargets.length; targetIndex++) {
                    RuntimeShaderTargetBinding runtimeTarget = runtimeTargets[targetIndex];
                    String targetName = runtimeTarget.name().length() > 0
                            ? runtimeTarget.name() : derivedTargetName(canonical, runtimeTarget);
                    targets[targetIndex] = ShaderTargetBinding.of(runtimeTarget.namespace(),
                            runtimeTarget.group(), runtimeTarget.binding(), runtimeTarget.role(), targetName);
                }
                bindings.add(ShaderBindingRemap.ofEntryPoint(runtimeStage, selection.entryPoint(),
                        runtimeBinding.sourceGroup(), runtimeBinding.sourceBinding(), targets,
                        remapKind(runtimeBinding.kind())));
            }
        }
        return ShaderTranslatedInterface.of(reflection, reflection,
                entries.toArray(new ShaderEntryPointRemap[0]),
                bindings.toArray(new ShaderBindingRemap[0]));
    }

    private static String derivedTargetName(ShaderBinding binding, RuntimeShaderTargetBinding target) {
        return "resource".equals(target.role())
                ? binding.name() : binding.name() + "__" + target.role();
    }

    private static ShaderBindingRemapKind remapKind(RuntimeShaderBindingRemapKind kind) {
        return switch (kind) {
            case DIRECT -> ShaderBindingRemapKind.DIRECT;
            case COMBINED_TEXTURE -> ShaderBindingRemapKind.COMBINED_TEXTURE;
            case COMBINED_SAMPLER -> ShaderBindingRemapKind.COMBINED_SAMPLER;
        };
    }

    private static ShaderArtifactStage artifactStage(RuntimeShaderCompileStage stage) {
        return switch (stage) {
            case VERTEX -> ShaderArtifactStage.VERTEX;
            case FRAGMENT -> ShaderArtifactStage.FRAGMENT;
            case COMPUTE -> ShaderArtifactStage.COMPUTE;
            case MODULE -> ShaderArtifactStage.MODULE;
        };
    }

    private ShaderTargetCompileResult success(ShaderTargetCompileRequest request,
            ShaderStageArtifact[] stages, ShaderTranslatedInterface translated) {
        String cacheKey = ShaderTargetCacheKeys.compilation(request, id(), version());
        return ShaderTargetCompileResult.success(ShaderTargetArtifact.compiled(
                request.target(), request.format(), request.environment(), stages, translated,
                id(), version(), cacheKey));
    }

    private RuntimeShaderCompileRequest runtimeRequest(ShaderTargetCompileRequest request,
            RuntimeShaderCompileStage stage, String entryPoint) {
        String glsl = request.options().value("glsl.version",
                request.environment().options().value("glsl.version", "330"));
        String glslEs = request.options().value("glsl-es.version",
                request.environment().options().value("glsl-es.version", "300"));
        return RuntimeShaderCompileRequest.builder(request.wgsl(), runtimeTarget(request.target()))
                .stage(stage)
                .entryPoint(entryPoint)
                .glslProfile(glsl)
                .glslEsProfile(glslEs)
                .build();
    }

    private static ShaderTargetCompileResult failure(RuntimeShaderCompileResult result,
            ShaderArtifactStage stage, String entryPoint) {
        if (result != null && result.success()) {
            return null;
        }
        RuntimeShaderCompileDiagnostic[] runtime = result != null
                ? result.diagnostics() : new RuntimeShaderCompileDiagnostic[0];
        if (runtime.length == 0) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.at(
                    ShaderValidationSeverity.ERROR, "shader.tint.failure",
                    "Tint runtime compiler returned no result", stage, entryPoint, -1, -1));
        }
        ShaderTargetDiagnostic[] diagnostics = new ShaderTargetDiagnostic[runtime.length];
        for (int i = 0; i < runtime.length; i++) {
            diagnostics[i] = ShaderTargetDiagnostic.at(ShaderValidationSeverity.ERROR,
                    "shader.tint.failure", runtime[i].message(), stage, entryPoint, -1, -1);
        }
        return ShaderTargetCompileResult.failure(diagnostics);
    }

    private static ShaderTargetCompileResult outputKindFailure(ShaderTargetCompileRequest request,
            ShaderArtifactStage stage, RuntimeShaderCompileOutputKind kind) {
        String message = ShaderArtifactFormats.SPIRV_BINARY.equals(request.format())
                ? "Runtime shader compiler returned " + kind + " for SPIR-V target " + request.target()
                : "Runtime shader compiler returned " + kind + " for text target " + request.target();
        return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.at(
                ShaderValidationSeverity.ERROR, "shader.tint.output-kind",
                message,
                stage, "", -1, -1));
    }

    static ShaderReflection resolveReflection(ShaderReflection supplied,
            RuntimeShaderCompileResult[] results) {
        ShaderReflection declared = supplied != null ? supplied : ShaderReflection.empty();
        ShaderReflection compiled = null;
        for (RuntimeShaderCompileResult result : results) {
            if (result == null || !result.hasReflection()) {
                continue;
            }
            ShaderReflection current = ShaderReflection.fromRuntime(result.reflection(), declared.profile());
            if (compiled != null && !compiled.physicallyEquivalent(current)) {
                throw new FdxException("Tint returned different physical interfaces for target entry points");
            }
            compiled = current;
        }
        if (compiled == null) {
            return declared;
        }
        if (declared.complete()) {
            if (!declared.physicallyEquivalent(compiled)) {
                throw new FdxException("Bundled shader reflection does not match fresh Tint reflection");
            }
            return declared;
        }
        validateCompatibilityReflection(declared, compiled);
        return ShaderReflection.builder(compiled.profile())
                .entryPoints(compiled.entryPoints())
                .bindings(compiled.bindings())
                .attributes(declared.attributes())
                .requiredCapabilities(compiled.requiredCapabilities())
                .complete(compiled.complete())
                .build();
    }

    private static void validateCompatibilityReflection(ShaderReflection coarse, ShaderReflection complete) {
        for (ShaderBinding binding : coarse.bindings()) {
            ShaderBinding reflected = complete.findBinding(binding.group(), binding.binding());
            if (reflected == null || binding.type() != ShaderBindingType.UNKNOWN
                    && binding.type() != reflected.type()) {
                throw new FdxException("Handwritten shader reflection does not match Tint binding "
                        + binding.group() + ':' + binding.binding());
            }
        }
    }

    static RuntimeShaderCompileTarget runtimeTarget(ShaderTargetId target) {
        if (ShaderTargets.WEBGPU_WGSL.equals(target)) {
            return RuntimeShaderCompileTarget.WEBGPU_WGSL;
        }
        if (ShaderTargets.WGPU_WGSL.equals(target)) {
            return RuntimeShaderCompileTarget.WGPU_WGSL;
        }
        if (ShaderTargets.WEBGL_GLSL_ES.equals(target)) {
            return RuntimeShaderCompileTarget.WEBGL_GLSL_ES;
        }
        if (ShaderTargets.GLES_GLSL_ES.equals(target)) {
            return RuntimeShaderCompileTarget.GLES_GLSL_ES;
        }
        if (ShaderTargets.OPENGL_GLSL.equals(target)) {
            return RuntimeShaderCompileTarget.OPENGL_GLSL;
        }
        if (ShaderTargets.VULKAN_SPIRV.equals(target)) {
            return RuntimeShaderCompileTarget.VULKAN_SPIRV;
        }
        if (ShaderTargets.METAL_MSL.equals(target)) {
            return RuntimeShaderCompileTarget.METAL_MSL;
        }
        if (ShaderTargets.DIRECTX_HLSL.equals(target)) {
            return RuntimeShaderCompileTarget.DIRECTX_HLSL;
        }
        throw new FdxException("Tint runtime compiler does not know target " + target);
    }

    static ShaderArtifactFormat defaultFormat(ShaderTargetId target) {
        if (ShaderTargets.WEBGPU_WGSL.equals(target) || ShaderTargets.WGPU_WGSL.equals(target)) {
            return ShaderArtifactFormats.WGSL_TEXT;
        }
        if (ShaderTargets.WEBGL_GLSL_ES.equals(target) || ShaderTargets.GLES_GLSL_ES.equals(target)) {
            return ShaderArtifactFormats.GLSL_ES_TEXT;
        }
        if (ShaderTargets.OPENGL_GLSL.equals(target)) {
            return ShaderArtifactFormats.GLSL_TEXT;
        }
        if (ShaderTargets.VULKAN_SPIRV.equals(target)) {
            return ShaderArtifactFormats.SPIRV_BINARY;
        }
        if (ShaderTargets.METAL_MSL.equals(target)) {
            return ShaderArtifactFormats.MSL_TEXT;
        }
        if (ShaderTargets.DIRECTX_HLSL.equals(target)) {
            return ShaderArtifactFormats.HLSL_TEXT;
        }
        throw new FdxException("No built-in artifact format exists for target " + target);
    }

    private static RuntimeShaderCompileStage runtimeStage(ShaderArtifactStage stage) {
        if (stage == ShaderArtifactStage.VERTEX) {
            return RuntimeShaderCompileStage.VERTEX;
        }
        if (stage == ShaderArtifactStage.FRAGMENT) {
            return RuntimeShaderCompileStage.FRAGMENT;
        }
        if (stage == ShaderArtifactStage.COMPUTE) {
            return RuntimeShaderCompileStage.COMPUTE;
        }
        return RuntimeShaderCompileStage.MODULE;
    }

    private static boolean isStandardTarget(ShaderTargetId target) {
        try {
            runtimeTarget(target);
            return true;
        } catch (FdxException ignored) {
            return false;
        }
    }

    private static boolean isWgsl(ShaderTargetId target) {
        return ShaderTargets.WEBGPU_WGSL.equals(target) || ShaderTargets.WGPU_WGSL.equals(target);
    }

    private static String normalizeTargetText(ShaderTargetId target, ShaderArtifactStage stage, String source) {
        if (ShaderTargets.OPENGL_GLSL.equals(target)) {
            return toDesktopGlsl(source);
        }
        if (ShaderTargets.WEBGL_GLSL_ES.equals(target) || ShaderTargets.GLES_GLSL_ES.equals(target)) {
            String withoutLocations = stage == ShaderArtifactStage.VERTEX
                    ? removeGlslEsLocationQualifier(source, "out")
                    : removeGlslEsLocationQualifier(removeGlslEsLocationQualifier(source, "in"), "out");
            return normalizeGlslEsFloats(withoutLocations);
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
            char character = line.charAt(i);
            if ((character < '0' || character > '9') && character != ' ') {
                return line;
            }
        }
        int qualifierStart = closeIndex + 2;
        return line.startsWith(qualifier + " ", qualifierStart)
                ? line.substring(qualifierStart) : line;
    }

    private static String normalizeGlslEsFloats(String source) {
        StringBuilder builder = null;
        for (int i = 0; i < source.length(); i++) {
            char character = source.charAt(i);
            if (character == 'f' && isGlslFloatSuffix(source, i)) {
                if (builder == null) {
                    builder = new StringBuilder(source.length());
                    builder.append(source, 0, i);
                }
                continue;
            }
            if (builder != null) {
                builder.append(character);
            }
        }
        return builder != null ? builder.toString() : source;
    }

    private static boolean isGlslFloatSuffix(String source, int suffixIndex) {
        if (suffixIndex == 0 || !isDigit(source.charAt(suffixIndex - 1))) {
            return false;
        }
        if (suffixIndex + 1 < source.length() && isIdentifierPart(source.charAt(suffixIndex + 1))) {
            return false;
        }
        int start = suffixIndex - 1;
        while (start >= 0 && isNumericLiteralPart(source.charAt(start))) {
            start--;
        }
        return start < 0 || !isIdentifierPart(source.charAt(start));
    }

    private static boolean isNumericLiteralPart(char character) {
        return isDigit(character) || character == '.' || character == 'e' || character == 'E'
                || character == '+' || character == '-';
    }

    private static boolean isIdentifierPart(char character) {
        return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9' || character == '_';
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static String message(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }
}
