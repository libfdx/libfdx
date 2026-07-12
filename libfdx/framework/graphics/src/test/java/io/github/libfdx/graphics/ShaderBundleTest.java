package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderBundleTest {
    private static final String WGSL = "@vertex fn vertexMain() -> @builtin(position) vec4f { return vec4f(); }"
            + " @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(); }";

    @Test
    void selectsGeneratedGlslForRequestedTarget() {
        ShaderBundle bundle = ShaderBundle.builder("test")
                .wgsl(WGSL)
                .generatedGlsl(ShaderTarget.WEBGL_GLSL_ES, "webgl vertex", "webgl fragment")
                .generatedGlsl(ShaderTarget.OPENGL_GLSL, "desktop vertex", "desktop fragment")
                .build();

        ShaderModuleDescriptor webgl = bundle.descriptorForTarget(ShaderTarget.WEBGL_GLSL_ES);
        ShaderModuleDescriptor desktop = bundle.descriptorForTarget(ShaderTarget.OPENGL_GLSL);

        assertTrue(webgl.hasSource(ShaderLanguage.WGSL));
        assertEquals("webgl vertex", webgl.glslVertexSource());
        assertEquals("webgl fragment", webgl.glslFragmentSource());
        assertEquals("desktop vertex", desktop.glslVertexSource());
        assertEquals("desktop fragment", desktop.glslFragmentSource());
    }

    @Test
    void selectsGeneratedSpirvForVulkan() {
        ShaderBundle bundle = ShaderBundle.builder("test")
                .wgsl(WGSL)
                .generatedSpirv(new int[] { 1, 2 }, new int[] { 3, 4 })
                .build();

        ShaderModuleDescriptor descriptor = bundle.descriptorForTarget(ShaderTarget.VULKAN_SPIRV);

        assertTrue(descriptor.hasSource(ShaderLanguage.WGSL));
        assertArrayEquals(new int[] { 1, 2 }, descriptor.spirvVertexWords());
        assertArrayEquals(new int[] { 3, 4 }, descriptor.spirvFragmentWords());
    }

    @Test
    void fallsBackToWgslWhenTargetOutputIsNotBundled() {
        ShaderModuleDescriptor descriptor = ShaderBundle.builder("test")
                .wgsl(WGSL)
                .build()
                .descriptorForTarget(ShaderTarget.GLES_GLSL_ES);

        assertTrue(descriptor.hasSource(ShaderLanguage.WGSL));
        assertNull(descriptor.glslVertexSource());
    }

    @Test
    void rejectsGlslForNonGlTarget() {
        assertThrows(FdxException.class, () -> ShaderBundle.builder("test")
                .wgsl(WGSL)
                .generatedGlsl(ShaderTarget.VULKAN_SPIRV, "vertex", "fragment"));
    }
}
