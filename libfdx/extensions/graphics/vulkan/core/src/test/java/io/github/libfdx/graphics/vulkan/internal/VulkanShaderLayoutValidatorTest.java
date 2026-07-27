package io.github.libfdx.graphics.vulkan.internal;

import io.github.libfdx.graphics.shader.target.ShaderEntryPointRemap;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.target.ShaderArtifactFormats;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.reflection.ShaderAttribute;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.target.ShaderBindingRemap;
import io.github.libfdx.graphics.shader.target.ShaderBindingRemapKind;
import io.github.libfdx.graphics.shader.reflection.ShaderBindingType;
import io.github.libfdx.graphics.shader.target.ShaderCompilerId;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.target.ShaderStageArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetEnvironments;
import io.github.libfdx.graphics.shader.target.ShaderTargets;
import io.github.libfdx.graphics.shader.target.ShaderTranslatedInterface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class VulkanShaderLayoutValidatorTest {
    private static final ShaderReflection REFLECTION = ShaderReflection.of(
            new ShaderBinding[] {
                    ShaderBinding.of(0, 0, "firstTexture",
                            ShaderBindingType.TEXTURE),
                    ShaderBinding.of(0, 1, "firstSampler",
                            ShaderBindingType.SAMPLER),
                    ShaderBinding.of(0, 4, "secondTexture",
                            ShaderBindingType.TEXTURE),
                    ShaderBinding.of(0, 7, "secondSampler",
                            ShaderBindingType.SAMPLER),
                    ShaderBinding.of(1, 3, "uniforms",
                            ShaderBindingType.UNIFORM_BUFFER)
            }, new ShaderAttribute[0]);

    @Test
    void acceptsCompactCombinedSamplerBindings() {
        assertDoesNotThrow(() -> VulkanShaderLayoutValidator.requireArtifact(
                artifact(1, 0)));
    }

    @Test
    void rejectsSparseCombinedSamplerBindingsBeforePipelineCreation() {
        assertThrows(FdxException.class,
                () -> VulkanShaderLayoutValidator.requireArtifact(
                        artifact(4, 0)));
    }

    @Test
    void rejectsUniformBindingThatDoesNotMatchProviderLayout() {
        assertThrows(FdxException.class,
                () -> VulkanShaderLayoutValidator.requireArtifact(
                        artifact(1, 3)));
    }

    private static ShaderTargetArtifact artifact(int secondTextureTarget,
            int uniformTarget) {
        ShaderBindingRemap[] remaps = {
                remap(0, 0, "texture", 0,
                        ShaderBindingRemapKind.COMBINED_TEXTURE),
                remap(0, 1, "sampler", 0,
                        ShaderBindingRemapKind.COMBINED_SAMPLER),
                remap(0, 4, "texture", secondTextureTarget,
                        ShaderBindingRemapKind.COMBINED_TEXTURE),
                remap(0, 7, "sampler", secondTextureTarget,
                        ShaderBindingRemapKind.COMBINED_SAMPLER),
                remap(1, 3, "buffer", uniformTarget,
                        ShaderBindingRemapKind.DIRECT)
        };
        ShaderTranslatedInterface translated = ShaderTranslatedInterface.of(
                REFLECTION, REFLECTION,
                new io.github.libfdx.graphics.shader.target.ShaderEntryPointRemap[0],
                remaps);
        return ShaderTargetArtifact.compiled(ShaderTargets.VULKAN_SPIRV,
                ShaderArtifactFormats.SPIRV_BINARY,
                ShaderTargetEnvironments.VULKAN_1_0_SPIRV_1_0,
                new ShaderStageArtifact[] {
                        ShaderStageArtifact.binary(ShaderArtifactStage.VERTEX,
                                "vertexMain",
                                ShaderArtifactFormats.SPIRV_BINARY,
                                new byte[] { 3, 2, 35, 7 })
                }, translated, ShaderCompilerId.of("test.vulkan"),
                "1", "");
    }

    private static ShaderBindingRemap remap(int group, int binding,
            String namespace, int targetBinding,
            ShaderBindingRemapKind kind) {
        return ShaderBindingRemap.of(group, binding, namespace,
                group, targetBinding, "", kind);
    }
}
