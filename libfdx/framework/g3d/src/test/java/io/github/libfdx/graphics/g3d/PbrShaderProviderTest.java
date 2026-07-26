package io.github.libfdx.graphics.g3d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(PbrShaderProvider.PBR_SHADER_SOURCE.contains(
                "base = vec4f(base.rgb * srgbToLinear(texel.rgb), base.a * texel.a);"));
        assertFalse(PbrShaderProvider.PBR_SHADER_SOURCE.contains(
                "let albedo = srgbToLinear(base.rgb);"));
    }

    @Test
    void gpuPbrShaderIncludesReferenceQualityDisplayAndLightingFeatures() {
        assertTrue(PbrShaderProvider.PBR_SHADER_SOURCE.contains("fn neutralToneMapping"));
        assertTrue(PbrShaderProvider.PBR_SHADER_SOURCE.contains("fillLightColorIntensity"));
        assertTrue(PbrShaderProvider.PBR_SHADER_SOURCE.contains("irradiance * albedo / PI"));
        assertTrue(PbrShaderProvider.PBR_SHADER_SOURCE.contains("fn unpackShadowDepth"));
        assertTrue(PbrShaderProvider.PBR_SHADER_SOURCE.contains("return visibility / 256.0;"));
        assertFalse(PbrShaderProvider.PBR_SHADER_SOURCE.contains("return visibility / 9.0;"));
    }
}
