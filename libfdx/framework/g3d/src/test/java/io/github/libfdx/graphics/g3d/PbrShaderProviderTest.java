package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.internal.BuiltInPbrShaderManifest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PbrShaderProviderTest {
    @Test
    void direct3d12UsesGpuPbrShader() {
        assertTrue(PbrShaderProvider.usesGpuPbrShader("d3d12"));
    }

    @Test
    void unknownProviderKeepsCompatibilityFallback() {
        assertFalse(PbrShaderProvider.usesGpuPbrShader("custom"));
    }

    @Test
    void baseColorTextureIsLinearizedExactlyOnce() {
        assertTrue(PbrShaderProvider.PBR_RENDERER_TEMPLATE.contains(
                "base = vec4f(base.rgb * srgbToLinear(texel.rgb), base.a * texel.a);"));
        assertFalse(PbrShaderProvider.PBR_RENDERER_TEMPLATE.contains(
                "let albedo = srgbToLinear(base.rgb);"));
    }

    @Test
    void gpuPbrShaderIncludesReferenceQualityDisplayAndLightingFeatures() {
        assertTrue(PbrShaderProvider.PBR_RENDERER_TEMPLATE.contains("fn neutralToneMapping"));
        assertTrue(PbrShaderProvider.PBR_RENDERER_TEMPLATE.contains("fillLightColorIntensity"));
        assertTrue(PbrShaderProvider.PBR_RENDERER_TEMPLATE.contains("irradiance * albedo / PI"));
        assertTrue(PbrShaderProvider.PBR_RENDERER_TEMPLATE.contains("fn unpackShadowDepth"));
        assertTrue(PbrShaderProvider.PBR_RENDERER_TEMPLATE.contains("return visibility / 256.0;"));
        assertFalse(PbrShaderProvider.PBR_RENDERER_TEMPLATE.contains("return visibility / 9.0;"));
    }

    @Test
    void gpuPbrShaderUsesStructuredBranchesInsteadOfWgslSelect() {
        assertFalse(PbrShaderProvider.PBR_RENDERER_TEMPLATE.contains("select("));
        assertTrue(PbrShaderProvider.PBR_RENDERER_TEMPLATE.contains("if (darkest < 0.08)"));
        assertTrue(PbrShaderProvider.PBR_RENDERER_TEMPLATE.contains(
                "if (currentDepth - bias > closestDepth)"));
    }

    @Test
    void staticAndSkinnedWgslLayoutsMatchGeneratedReflection() {
        assertWgslLayout(PbrShaderProvider.PBR_RENDERER_TEMPLATE,
                BuiltInPbrShaderManifest.staticReflection());

        String skinnedSource =
                PbrShaderProvider.skinnedPbrRendererTemplate();
        assertFalse(skinnedSource.contains("//__PBR_SKINNED_"));
        assertFalse(skinnedSource.contains("//__PBR_SKINNING_TRANSFORM__"));
        assertTrue(skinnedSource.contains("@location(6) joints : vec4f"));
        assertTrue(skinnedSource.contains("uniforms.boneMatrices[joint3]"));
        assertWgslLayout(skinnedSource, BuiltInPbrShaderManifest.skinnedReflection());
    }

    private static void assertWgslLayout(String source, ShaderReflection reflection) {
        ShaderParameterLayout layout = reflection.requireBinding(1, 0).bufferLayout();
        String marker = "struct PbrUniforms {";
        int start = source.indexOf(marker);
        int end = source.indexOf("};", start);
        assertTrue(start >= 0 && end > start, "PbrUniforms must be present");

        long byteOffset = 0;
        int members = 0;
        String[] lines = source.substring(start + marker.length(), end).split("\\R");
        for (String line : lines) {
            String member = line.trim();
            if (member.isEmpty() || member.startsWith("//")) {
                continue;
            }
            int separator = member.indexOf(':');
            assertTrue(separator > 0 && member.endsWith(","), "Invalid PbrUniforms member: " + member);
            String name = member.substring(0, separator).trim();
            ShaderParameterHandle handle = layout.findHandle(name);
            assertNotNull(handle, "Missing reflected PBR member " + name);
            assertEquals(byteOffset, handle.byteOffset(), "Unexpected WGSL byte offset for " + name);
            byteOffset += handle.occupiedSize();
            members++;
        }
        assertEquals(layout.parameterCount(), members);
        assertEquals(layout.minimumBindingSize(), byteOffset);
    }
}
