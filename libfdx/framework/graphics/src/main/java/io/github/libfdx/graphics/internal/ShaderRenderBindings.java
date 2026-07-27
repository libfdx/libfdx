package io.github.libfdx.graphics.internal;

import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;

/**
 * Provider-facing analysis of one reflected render resource layout.
 *
 * <p>This class deliberately contains no renderer or material names. It
 * converts the canonical manifest into the compact texture/sampler/uniform
 * model implemented by the current render providers and rejects layouts that
 * model cannot represent before native pipeline creation.</p>
 */
public final class ShaderRenderBindings {
    private static final ShaderBinding[] NO_BINDINGS = new ShaderBinding[0];

    private final ShaderResourceLayout layout;
    private final ShaderBinding[] uniformBuffers;
    private final ShaderBinding[] textures;
    private final ShaderBinding[] samplers;
    private final int textureGroup;
    private final int textureSetIndex;
    private final int bindGroupCount;

    private ShaderRenderBindings(RenderPipelineDescriptor descriptor) {
        layout = descriptor.resourceLayout();
        if (layout == null) {
            textures = new ShaderBinding[descriptor.sampledTextureCount()];
            samplers = new ShaderBinding[textures.length];
            uniformBuffers = NO_BINDINGS;
            textureGroup = textures.length > 0 ? 0 : -1;
            textureSetIndex = textures.length > 0 ? 0 : -1;
            bindGroupCount = textures.length > 0 ? 1 : 0;
            return;
        }

        uniformBuffers = bindings(layout, ShaderResourceKind.UNIFORM_BUFFER);
        textures = sampledTextures(layout);
        samplers = bindings(layout, ShaderResourceKind.SAMPLER);
        if (textures.length != descriptor.sampledTextureCount()) {
            throw new FdxException("Render pipeline sampled-texture count does not match its resource layout");
        }
        if (samplers.length != 0 && samplers.length != textures.length) {
            throw new FdxException("Current render providers require one sampler per sampled texture");
        }
        textureGroup = commonGroup(textures, samplers, "sampled textures and samplers");
        for (ShaderBinding uniform : uniformBuffers) {
            if (uniform.group() == textureGroup) {
                throw new FdxException("Current render providers require uniform buffers and sampled resources "
                        + "in separate bind groups");
            }
        }
        requireOneUniformPerGroup(uniformBuffers);
        bindGroupCount = requireContiguousGroups(textureGroup, uniformBuffers);
        textureSetIndex = textureGroup;
        rejectUnsupportedRenderResources(layout);
    }

    public static ShaderRenderBindings from(RenderPipelineDescriptor descriptor) {
        if (descriptor == null) {
            throw new FdxException("Render pipeline descriptor cannot be null");
        }
        return new ShaderRenderBindings(descriptor);
    }

    public ShaderResourceLayout layout() {
        return layout;
    }

    public boolean reflected() {
        return layout != null;
    }

    public boolean hasUniformBuffer() {
        return uniformBuffers.length > 0;
    }

    /**
     * Returns the first uniform buffer for the named-uniform compatibility
     * path. New provider code should use the indexed methods.
     */
    public ShaderBinding uniformBuffer() {
        return uniformBuffers.length > 0 ? uniformBuffers[0] : null;
    }

    public int uniformByteCount() {
        return uniformByteCount(0);
    }

    public int uniformGroup() {
        return uniformBuffers.length > 0 ? uniformBuffers[0].group() : -1;
    }

    public int uniformBinding() {
        return uniformBuffers.length > 0 ? uniformBuffers[0].binding() : -1;
    }

    /**
     * Returns the native set index of the first uniform buffer.
     */
    public int uniformSetIndex() {
        return uniformSetIndex(0);
    }

    public int uniformBufferCount() {
        return uniformBuffers.length;
    }

    public ShaderBinding uniformBuffer(int index) {
        if (index < 0 || index >= uniformBuffers.length) {
            throw new FdxException("Uniform buffer index is out of range: " + index);
        }
        return uniformBuffers[index];
    }

    public int uniformByteCount(int index) {
        return uniformBuffers.length == 0 ? 0
                : Math.toIntExact(uniformBuffer(index).minimumBindingSize());
    }

    public int uniformSetIndex(int index) {
        return uniformBuffers.length == 0 ? -1 : uniformBuffer(index).group();
    }

    public int uniformBufferIndex(int group, int binding) {
        for (int i = 0; i < uniformBuffers.length; i++) {
            ShaderBinding candidate = uniformBuffers[i];
            if (candidate.group() == group && candidate.binding() == binding) {
                return i;
            }
        }
        return -1;
    }

    public int uniformBufferIndex(int group) {
        for (int i = 0; i < uniformBuffers.length; i++) {
            if (uniformBuffers[i].group() == group) {
                return i;
            }
        }
        return -1;
    }

    public int bindGroupCount() {
        return bindGroupCount;
    }

    public int textureGroup() {
        return textureGroup;
    }

    /**
     * Returns the compact native set index for sampled resources.
     */
    public int textureSetIndex() {
        return textureSetIndex;
    }

    public int sampledTextureCount() {
        return textures.length;
    }

    public ShaderBinding texture(int slot) {
        requireSlot(slot);
        return textures[slot];
    }

    public ShaderBinding sampler(int slot) {
        requireSlot(slot);
        return samplers.length == 0 ? null : samplers[slot];
    }

    public int samplerCount() {
        return samplers.length;
    }

    public int textureSlot(int group, int binding) {
        for (int i = 0; i < textures.length; i++) {
            ShaderBinding candidate = textures[i];
            if (candidate != null && candidate.group() == group && candidate.binding() == binding) {
                return i;
            }
        }
        return -1;
    }

    public int samplerSlot(int group, int binding) {
        for (int i = 0; i < samplers.length; i++) {
            ShaderBinding candidate = samplers[i];
            if (candidate != null && candidate.group() == group && candidate.binding() == binding) {
                return i;
            }
        }
        return -1;
    }

    public void requireParameterBlock(int group, int binding,
            io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock block) {
        int index = uniformBufferIndex(group, binding);
        if (index < 0) {
            throw new FdxException("Uniform binding is not declared by the active render pipeline: "
                    + group + ':' + binding);
        }
        ShaderBinding uniformBuffer = uniformBuffers[index];
        if (block == null
                || !uniformBuffer.bufferLayout().physicallyEquivalent(block.layout())
                || block.byteSize() < uniformBuffer.minimumBindingSize()) {
            throw new FdxException("Shader parameter block does not match uniform binding "
                    + group + ':' + binding);
        }
    }

    private void requireSlot(int slot) {
        if (slot < 0 || slot >= textures.length) {
            throw new FdxException("Sampled texture slot is out of range: " + slot);
        }
    }

    private static ShaderBinding[] sampledTextures(ShaderResourceLayout layout) {
        int count = 0;
        for (int i = 0; i < layout.bindingCount(); i++) {
            if (sampled(layout.binding(i).resourceKind())) {
                count++;
            }
        }
        if (count == 0) {
            return NO_BINDINGS;
        }
        ShaderBinding[] result = new ShaderBinding[count];
        int cursor = 0;
        for (int i = 0; i < layout.bindingCount(); i++) {
            ShaderBinding binding = layout.binding(i);
            if (sampled(binding.resourceKind())) {
                result[cursor++] = binding;
            }
        }
        return result;
    }

    private static ShaderBinding[] bindings(ShaderResourceLayout layout, ShaderResourceKind kind) {
        int count = layout.bindingCount(kind);
        if (count == 0) {
            return NO_BINDINGS;
        }
        ShaderBinding[] result = new ShaderBinding[count];
        for (int i = 0; i < count; i++) {
            result[i] = layout.binding(kind, i);
        }
        return result;
    }

    private static int commonGroup(ShaderBinding[] first, ShaderBinding[] second, String label) {
        int group = -1;
        for (ShaderBinding binding : first) {
            if (binding != null) {
                group = mergeGroup(group, binding.group(), label);
            }
        }
        for (ShaderBinding binding : second) {
            if (binding != null) {
                group = mergeGroup(group, binding.group(), label);
            }
        }
        return group;
    }

    private static int mergeGroup(int current, int value, String label) {
        if (current >= 0 && current != value) {
            throw new FdxException("Current render providers require " + label
                    + " in one bind group");
        }
        return value;
    }

    private static void rejectUnsupportedRenderResources(ShaderResourceLayout layout) {
        for (int i = 0; i < layout.bindingCount(); i++) {
            ShaderResourceKind kind = layout.binding(i).resourceKind();
            if (kind != ShaderResourceKind.UNIFORM_BUFFER
                    && kind != ShaderResourceKind.SAMPLER && !sampled(kind)) {
                throw new FdxException("Current render providers do not support "
                        + kind + " in render pipelines");
            }
        }
    }

    private static void requireOneUniformPerGroup(ShaderBinding[] uniforms) {
        for (int i = 0; i < uniforms.length; i++) {
            for (int j = i + 1; j < uniforms.length; j++) {
                if (uniforms[i].group() == uniforms[j].group()) {
                    throw new FdxException("Current render providers support one uniform buffer "
                            + "per bind group");
                }
            }
        }
    }

    private static int requireContiguousGroups(int textureGroup,
            ShaderBinding[] uniforms) {
        int maximum = textureGroup;
        int used = textureGroup >= 0 ? 1 : 0;
        for (ShaderBinding uniform : uniforms) {
            maximum = Math.max(maximum, uniform.group());
            used++;
        }
        if (maximum < 0) {
            return 0;
        }
        boolean[] groups = new boolean[maximum + 1];
        if (textureGroup >= 0) {
            groups[textureGroup] = true;
        }
        for (ShaderBinding uniform : uniforms) {
            groups[uniform.group()] = true;
        }
        for (boolean group : groups) {
            if (!group) {
                throw new FdxException("Current render providers require used bind groups "
                        + "to be contiguous from group 0");
            }
        }
        if (used != groups.length) {
            throw new FdxException("Current render providers require used bind groups "
                    + "to contain one supported resource class each");
        }
        return groups.length;
    }

    private static boolean sampled(ShaderResourceKind kind) {
        return kind == ShaderResourceKind.SAMPLED_TEXTURE
                || kind == ShaderResourceKind.MULTISAMPLED_TEXTURE
                || kind == ShaderResourceKind.DEPTH_TEXTURE
                || kind == ShaderResourceKind.DEPTH_MULTISAMPLED_TEXTURE
                || kind == ShaderResourceKind.EXTERNAL_TEXTURE;
    }
}
