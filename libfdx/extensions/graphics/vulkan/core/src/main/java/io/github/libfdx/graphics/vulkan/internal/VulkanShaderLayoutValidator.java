package io.github.libfdx.graphics.vulkan.internal;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.target.ShaderBindingRemap;
import io.github.libfdx.graphics.shader.target.ShaderBindingRemapKind;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.target.ShaderTargetArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetBinding;
import io.github.libfdx.graphics.shader.target.ShaderTargets;
import io.github.libfdx.graphics.internal.ShaderRenderBindings;

/**
 * Validates the compact descriptor ABI implemented by the Vulkan providers.
 *
 * <p>The canonical interface remains the public binding API. This validator
 * checks that a translated SPIR-V artifact maps that interface to the compact
 * descriptor slots the native providers bind, so an incompatible custom or
 * stale compiler artifact fails before pipeline creation instead of rendering
 * with the wrong resources.</p>
 */
public final class VulkanShaderLayoutValidator {
    private VulkanShaderLayoutValidator() {
    }

    /**
     * Requires translated SPIR-V resource bindings compatible with the current
     * Vulkan provider descriptor ABI.
     *
     * @param artifact the translated target artifact, or {@code null}
     */
    public static void requireArtifact(ShaderTargetArtifact artifact) {
        if (artifact == null) {
            return;
        }
        if (!ShaderTargets.VULKAN_SPIRV.equals(artifact.target())) {
            throw new FdxException("Vulkan provider received a non-Vulkan shader artifact");
        }
        ShaderReflection canonical = artifact.translatedInterface().canonical();
        for (ShaderBindingRemap remap : artifact.translatedInterface().bindings()) {
            ShaderBinding binding = canonical.findBinding(
                    remap.sourceGroup(), remap.sourceBinding());
            if (binding == null || !supported(binding.resourceKind())) {
                continue;
            }
            requireRemap(canonical, binding, remap);
        }
    }

    /**
     * Requires the canonical resource groups supported by the current Vulkan
     * pipeline-layout implementation.
     *
     * @param bindings analyzed canonical render bindings
     */
    public static void requireRenderLayout(ShaderRenderBindings bindings) {
        if (bindings == null) {
            throw new FdxException("Vulkan render bindings cannot be null");
        }
        boolean textures = bindings.sampledTextureCount() > 0;
        if (textures && bindings.textureSetIndex() != 0) {
            throw new FdxException(
                    "Vulkan sampled resources must use canonical bind group 0");
        }
        if (bindings.uniformBufferCount() > 1) {
            throw new FdxException(
                    "Current Vulkan render pipelines support one uniform buffer");
        }
        if (bindings.hasUniformBuffer()) {
            int expectedSet = textures ? 1 : 0;
            if (bindings.uniformSetIndex() != expectedSet) {
                throw new FdxException("Vulkan uniform resources must use canonical bind group "
                        + expectedSet);
            }
        }
    }

    private static void requireRemap(ShaderReflection canonical,
            ShaderBinding binding, ShaderBindingRemap remap) {
        Expected expected = expected(canonical, binding);
        if (remap.targetCount() != 1) {
            throw incompatible(binding, remap,
                    "must map to exactly one Vulkan descriptor");
        }
        ShaderTargetBinding target = remap.target(0);
        if (!expected.namespace.equals(target.namespace())
                || expected.group != target.group()
                || expected.binding != target.binding()
                || !"resource".equals(target.role())
                || expected.kind != remap.kind()) {
            throw incompatible(binding, remap, "expected "
                    + expected.namespace + ' ' + expected.group + ':'
                    + expected.binding + " as " + expected.kind);
        }
        if ((sampled(binding.resourceKind())
                || binding.resourceKind() == ShaderResourceKind.SAMPLER)
                && remap.stage() != ShaderArtifactStage.FRAGMENT
                && remap.stage() != ShaderArtifactStage.MODULE) {
            throw incompatible(binding, remap,
                    "sampled resources are supported only by fragment shaders");
        }
    }

    private static Expected expected(ShaderReflection canonical,
            ShaderBinding binding) {
        ShaderResourceKind kind = binding.resourceKind();
        if (sampled(kind)) {
            return new Expected("texture", binding.group(),
                    ordinal(canonical, binding, true),
                    ShaderBindingRemapKind.COMBINED_TEXTURE);
        }
        if (kind == ShaderResourceKind.SAMPLER) {
            return new Expected("sampler", binding.group(),
                    ordinal(canonical, binding, false),
                    ShaderBindingRemapKind.COMBINED_SAMPLER);
        }
        return new Expected("buffer", binding.group(), 0,
                ShaderBindingRemapKind.DIRECT);
    }

    private static int ordinal(ShaderReflection canonical,
            ShaderBinding selected, boolean texture) {
        int ordinal = 0;
        for (ShaderBinding binding : canonical.bindings()) {
            if (binding == selected) {
                return ordinal;
            }
            if (texture ? sampled(binding.resourceKind())
                    : binding.resourceKind() == ShaderResourceKind.SAMPLER) {
                ordinal++;
            }
        }
        throw new FdxException("Vulkan shader binding is absent from its canonical interface");
    }

    private static boolean supported(ShaderResourceKind kind) {
        return kind == ShaderResourceKind.UNIFORM_BUFFER
                || kind == ShaderResourceKind.SAMPLER || sampled(kind);
    }

    private static boolean sampled(ShaderResourceKind kind) {
        return kind == ShaderResourceKind.SAMPLED_TEXTURE
                || kind == ShaderResourceKind.MULTISAMPLED_TEXTURE
                || kind == ShaderResourceKind.DEPTH_TEXTURE
                || kind == ShaderResourceKind.DEPTH_MULTISAMPLED_TEXTURE
                || kind == ShaderResourceKind.EXTERNAL_TEXTURE;
    }

    private static FdxException incompatible(ShaderBinding binding,
            ShaderBindingRemap remap, String detail) {
        return new FdxException("Vulkan target binding for canonical "
                + binding.group() + ':' + binding.binding() + " ("
                + binding.name() + ") is incompatible: " + detail
                + "; translated scope is " + remap.stage() + ' '
                + remap.sourceEntryPoint());
    }

    private static final class Expected {
        final String namespace;
        final int group;
        final int binding;
        final ShaderBindingRemapKind kind;

        Expected(String namespace, int group, int binding,
                ShaderBindingRemapKind kind) {
            this.namespace = namespace;
            this.group = group;
            this.binding = binding;
            this.kind = kind;
        }
    }
}
