package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.core.FdxException;

import java.util.Arrays;

/**
 * Immutable binding table for one shader resource group.
 *
 * <p>The set borrows buffers, textures, samplers, and parameter blocks. It
 * never disposes them. Mutable parameter blocks retain their own identity and
 * revision, which providers must snapshot or validate when recording delayed
 * commands.</p>
 */
public final class ShaderResourceSet {
    private final ShaderResourceLayout layout;
    private final int group;
    private final ShaderResourceValue[] values;

    private ShaderResourceSet(Builder builder) {
        layout = builder.layout;
        group = builder.group;
        int expected = layout.groupBindingCount(group);
        if (expected == 0) {
            throw new FdxException("Shader resource layout has no group " + group);
        }
        values = new ShaderResourceValue[expected];
        int cursor = 0;
        for (int i = 0; i < layout.bindingCount(); i++) {
            ShaderBinding binding = layout.binding(i);
            if (binding.group() != group) {
                continue;
            }
            ShaderResourceValue value = builder.find(binding.binding());
            if (value == null) {
                throw new FdxException("Shader resource set is missing binding "
                        + group + ':' + binding.binding() + " (" + binding.name() + ')');
            }
            validate(binding, value);
            values[cursor++] = value;
        }
    }

    public static Builder builder(ShaderResourceLayout layout, int group) {
        return new Builder(layout, group);
    }

    public ShaderResourceLayout layout() {
        return layout;
    }

    public int group() {
        return group;
    }

    public ShaderResourceValue[] values() {
        return values.clone();
    }

    public int valueCount() {
        return values.length;
    }

    public ShaderResourceValue value(int index) {
        return values[index];
    }

    public ShaderResourceValue find(int binding) {
        for (ShaderResourceValue value : values) {
            if (value.binding() == binding) {
                return value;
            }
        }
        return null;
    }

    private static void validate(ShaderBinding binding, ShaderResourceValue value) {
        switch (binding.resourceKind()) {
            case UNIFORM_BUFFER -> {
                if (value.kind() == ShaderResourceValueKind.PARAMETER_BLOCK) {
                    if (!binding.bufferLayout().physicallyEquivalent(value.parameterBlock().layout())
                            || value.parameterBlock().byteSize() < binding.minimumBindingSize()) {
                        throw mismatch(binding, value);
                    }
                } else if (value.kind() != ShaderResourceValueKind.BUFFER
                        || value.buffer().usage() != BufferUsage.UNIFORM
                        || value.size() < binding.minimumBindingSize()) {
                    throw mismatch(binding, value);
                }
            }
            case STORAGE_BUFFER -> {
                if (value.kind() != ShaderResourceValueKind.BUFFER
                        || value.buffer().usage() != BufferUsage.STORAGE
                        || value.size() < binding.minimumBindingSize()) {
                    throw mismatch(binding, value);
                }
            }
            case SAMPLER -> {
                if (value.kind() != ShaderResourceValueKind.SAMPLER
                        && value.kind() != ShaderResourceValueKind.TEXTURE_SAMPLER) {
                    throw mismatch(binding, value);
                }
            }
            case SAMPLED_TEXTURE, MULTISAMPLED_TEXTURE, DEPTH_TEXTURE,
                    DEPTH_MULTISAMPLED_TEXTURE, STORAGE_TEXTURE, EXTERNAL_TEXTURE -> {
                if (value.kind() != ShaderResourceValueKind.TEXTURE) {
                    throw mismatch(binding, value);
                }
            }
            case TEXEL_BUFFER, INPUT_ATTACHMENT, UNKNOWN -> throw new FdxException(
                    "Shader resource sets do not support binding kind "
                            + binding.resourceKind() + ": " + binding.name());
        }
    }

    private static FdxException mismatch(ShaderBinding binding, ShaderResourceValue value) {
        return new FdxException("Shader resource value " + value.kind()
                + " is incompatible with " + binding.resourceKind() + " binding "
                + binding.group() + ':' + binding.binding());
    }

    /**
     * Builds a resource set. Builder instances are setup-time objects and are
     * not retained by the resulting set.
     */
    public static final class Builder {
        private final ShaderResourceLayout layout;
        private final int group;
        private ShaderResourceValue[] values = new ShaderResourceValue[8];
        private int count;

        private Builder(ShaderResourceLayout layout, int group) {
            if (layout == null) {
                throw new FdxException("Shader resource set layout cannot be null");
            }
            if (group < 0) {
                throw new FdxException("Shader resource set group cannot be negative");
            }
            this.layout = layout;
            this.group = group;
        }

        public Builder parameterBlock(int binding, ShaderParameterBlock block) {
            if (block == null) {
                throw new FdxException("Shader parameter block cannot be null");
            }
            return put(new ShaderResourceValue(binding, ShaderResourceValueKind.PARAMETER_BLOCK,
                    block, null, null, null, 0, block.byteSize()));
        }

        public Builder buffer(int binding, Buffer buffer) {
            if (buffer == null) {
                throw new FdxException("Shader buffer cannot be null");
            }
            return buffer(binding, buffer, 0, buffer.size());
        }

        public Builder buffer(int binding, Buffer buffer, int offset, int size) {
            if (buffer == null || offset < 0 || size <= 0 || offset > buffer.size() - size) {
                throw new FdxException("Shader buffer binding range is invalid");
            }
            return put(new ShaderResourceValue(binding, ShaderResourceValueKind.BUFFER,
                    null, buffer, null, null, offset, size));
        }

        public Builder texture(int binding, Texture texture) {
            if (texture == null) {
                throw new FdxException("Shader texture cannot be null");
            }
            return put(new ShaderResourceValue(binding, ShaderResourceValueKind.TEXTURE,
                    null, null, texture, null, 0, 0));
        }

        public Builder sampler(int binding, Sampler sampler) {
            if (sampler == null) {
                throw new FdxException("Shader sampler cannot be null");
            }
            return put(new ShaderResourceValue(binding, ShaderResourceValueKind.SAMPLER,
                    null, null, null, sampler, 0, 0));
        }

        /**
         * Binds the sampler currently owned by a texture. This preserves the
         * existing texture convenience path while separate samplers remain the
         * explicit portable contract.
         *
         * @param binding sampler binding
         * @param texture texture whose configured sampler is borrowed
         * @return this builder
         */
        public Builder textureSampler(int binding, Texture texture) {
            if (texture == null) {
                throw new FdxException("Shader texture sampler source cannot be null");
            }
            return put(new ShaderResourceValue(binding, ShaderResourceValueKind.TEXTURE_SAMPLER,
                    null, null, texture, null, 0, 0));
        }

        public ShaderResourceSet build() {
            return new ShaderResourceSet(this);
        }

        private Builder put(ShaderResourceValue value) {
            if (value.binding() < 0) {
                throw new FdxException("Shader resource binding cannot be negative");
            }
            if (find(value.binding()) != null) {
                throw new FdxException("Duplicate shader resource set binding: "
                        + group + ':' + value.binding());
            }
            if (count == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[count++] = value;
            return this;
        }

        private ShaderResourceValue find(int binding) {
            for (int i = 0; i < count; i++) {
                if (values[i].binding() == binding) {
                    return values[i];
                }
            }
            return null;
        }
    }
}
