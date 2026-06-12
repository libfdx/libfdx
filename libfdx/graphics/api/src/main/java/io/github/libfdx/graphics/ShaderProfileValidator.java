package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a shader profile validator.
 *
 * @author xpenatan
 */
public final class ShaderProfileValidator {
    private static final Pattern GROUP_BINDING_PATTERN = Pattern.compile(
            "@group\\s*\\(\\s*(\\d+)\\s*\\)\\s*@binding\\s*\\(\\s*(\\d+)\\s*\\)");

    private ShaderProfileValidator() {
    }

    /**
     * Runs the validate wgsl step.
     *
     * @param profile the profile
     * @param source the source value
     * @return the validate wgsl
     */
    public static ShaderValidationResult validateWgsl(ShaderProfile profile, String source) {
        if (source == null || source.trim().isEmpty()) {
            return ShaderValidationResult.of(new ShaderValidationDiagnostic[] {
                    ShaderValidationDiagnostic.error("shader.empty", "WGSL shader source cannot be empty")
            });
        }
        ShaderProfile actualProfile = profile != null ? profile : ShaderProfile.PORTABLE_WEBGPU;
        ArrayList<ShaderValidationDiagnostic> diagnostics = new ArrayList<>();
        validateBindings(source, diagnostics);
        if (actualProfile == ShaderProfile.PORTABLE_WEBGL2) {
            validatePortableWebgl2(source, diagnostics);
        } else if (actualProfile == ShaderProfile.PORTABLE_WEBGPU) {
            validatePortableWebgpu(source, diagnostics);
        }
        if (diagnostics.isEmpty()) {
            return ShaderValidationResult.success();
        }
        return ShaderValidationResult.of(diagnostics.toArray(new ShaderValidationDiagnostic[0]));
    }

    private static void validateBindings(String source, List<ShaderValidationDiagnostic> diagnostics) {
        Matcher matcher = GROUP_BINDING_PATTERN.matcher(source);
        ArrayList<String> seen = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1) + ":" + matcher.group(2);
            if (seen.contains(key)) {
                diagnostics.add(ShaderValidationDiagnostic.error("shader.binding.duplicate",
                        "Duplicate @group/@binding declaration: " + key));
            } else {
                seen.add(key);
            }
        }
    }

    private static void validatePortableWebgl2(String source, List<ShaderValidationDiagnostic> diagnostics) {
        String normalized = normalize(source);
        reject(normalized, diagnostics, "@compute", "shader.webgl2.compute",
                "The fdx-wgsl-webgl2 profile does not support compute shaders");
        reject(normalized, diagnostics, "var<storage", "shader.webgl2.storage-buffer",
                "The fdx-wgsl-webgl2 profile does not support storage buffers");
        reject(normalized, diagnostics, "texture_storage_", "shader.webgl2.storage-texture",
                "The fdx-wgsl-webgl2 profile does not support storage textures");
        reject(normalized, diagnostics, "atomic<", "shader.webgl2.atomic",
                "The fdx-wgsl-webgl2 profile does not support atomic types");
        reject(normalized, diagnostics, "texture_external", "shader.webgl2.external-texture",
                "The fdx-wgsl-webgl2 profile does not support external textures");
        reject(normalized, diagnostics, "texture_multisampled_", "shader.webgl2.multisampled-texture",
                "The fdx-wgsl-webgl2 profile does not support multisampled texture sampling");
        reject(normalized, diagnostics, "texture_depth_multisampled_", "shader.webgl2.depth-multisampled-texture",
                "The fdx-wgsl-webgl2 profile does not support multisampled depth textures");
        reject(normalized, diagnostics, "enable ", "shader.webgl2.extension",
                "The fdx-wgsl-webgl2 profile does not allow WGSL extensions");
        reject(normalized, diagnostics, "requires ", "shader.webgl2.requires",
                "The fdx-wgsl-webgl2 profile does not allow WGSL requires directives");
        reject(normalized, diagnostics, "subgroup", "shader.webgl2.subgroup",
                "The fdx-wgsl-webgl2 profile does not support subgroup operations");
        reject(normalized, diagnostics, "override ", "shader.webgl2.override",
                "The fdx-wgsl-webgl2 profile does not support override constants");
        reject(normalized, diagnostics, "f16", "shader.webgl2.f16",
                "The fdx-wgsl-webgl2 profile does not support 16-bit floating point shader types");
        reject(normalized, diagnostics, "u64", "shader.webgl2.u64",
                "The fdx-wgsl-webgl2 profile does not support 64-bit integer shader types");
        reject(normalized, diagnostics, "i64", "shader.webgl2.i64",
                "The fdx-wgsl-webgl2 profile does not support 64-bit integer shader types");
    }

    private static void validatePortableWebgpu(String source, List<ShaderValidationDiagnostic> diagnostics) {
        String normalized = normalize(source);
        reject(normalized, diagnostics, "enable ", "shader.webgpu.extension",
                "The fdx-wgsl-webgpu profile does not allow backend-specific WGSL extensions by default");
        reject(normalized, diagnostics, "requires ", "shader.webgpu.requires",
                "The fdx-wgsl-webgpu profile does not allow WGSL requires directives by default");
        reject(normalized, diagnostics, "subgroup", "shader.webgpu.subgroup",
                "The fdx-wgsl-webgpu profile does not support subgroup operations by default");
    }

    private static String normalize(String source) {
        if (source == null) {
            throw new FdxException("Shader source cannot be null");
        }
        return source.toLowerCase(Locale.ROOT);
    }

    private static void reject(String source, List<ShaderValidationDiagnostic> diagnostics, String token,
            String code, String message) {
        if (source.contains(token)) {
            diagnostics.add(ShaderValidationDiagnostic.error(code, message));
        }
    }
}
