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
}
