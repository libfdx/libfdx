package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable shader resource binding.
 *
 * <p>{@link #of(int, int, String, ShaderBindingType)} preserves the original coarse handwritten
 * declaration. Complete reflected resources are created with {@link #builder(int, int, String,
 * ShaderResourceKind)} and included in a complete {@link ShaderReflection}.</p>
 */
public final class ShaderBinding {
    public static final int ABSENT = -1;

    private final int group;
    private final int binding;
    private final String stableId;
    private final String name;
    private final ShaderBindingType type;
    private final ShaderResourceKind resourceKind;
    private final boolean complete;
    private final ShaderStageVisibility visibility;
    private final ShaderResourceAccess access;
    private final long bindingArrayCount;
    private final long minimumBindingSize;
    private final long sizeWithoutPadding;
    private final long alignment;
    private final ShaderParameterLayout bufferLayout;
    private final ShaderTextureDimension textureDimension;
    private final ShaderTextureSampleType textureSampleType;
    private final ShaderSamplerKind samplerKind;
    private final ShaderStorageTextureFormat storageFormat;
    private final long inputAttachmentIndex;
    private final ShaderParameterDomain domain;
    private final ShaderUpdateFrequency updateFrequency;

    private ShaderBinding(Builder builder) {
        if (builder.group < 0) {
            throw new FdxException("Shader binding group cannot be negative");
        }
        if (builder.binding < 0) {
            throw new FdxException("Shader binding index cannot be negative");
        }
        if (builder.name == null || builder.name.trim().isEmpty()) {
            throw new FdxException("Shader binding name cannot be empty");
        }
        if (builder.stableId == null || builder.stableId.trim().isEmpty()) {
            throw new FdxException("Shader binding stable ID cannot be empty");
        }
        group = builder.group;
        binding = builder.binding;
        stableId = builder.stableId;
        name = builder.name;
        resourceKind = builder.resourceKind != null ? builder.resourceKind : ShaderResourceKind.UNKNOWN;
        type = builder.type != null ? builder.type : compatibilityType(resourceKind);
        complete = builder.complete;
        visibility = builder.visibility != null ? builder.visibility : ShaderStageVisibility.NONE;
        access = builder.access != null ? builder.access : ShaderResourceAccess.UNKNOWN;
        bindingArrayCount = requireOptional(builder.bindingArrayCount, "binding array count");
        minimumBindingSize = requireNonNegative(builder.minimumBindingSize, "minimum binding size");
        sizeWithoutPadding = requireNonNegative(builder.sizeWithoutPadding, "size without padding");
        alignment = requireNonNegative(builder.alignment, "alignment");
        bufferLayout = builder.bufferLayout;
        textureDimension = builder.textureDimension != null
                ? builder.textureDimension : ShaderTextureDimension.UNKNOWN;
        textureSampleType = builder.textureSampleType != null
                ? builder.textureSampleType : ShaderTextureSampleType.UNKNOWN;
        samplerKind = builder.samplerKind != null ? builder.samplerKind : ShaderSamplerKind.UNKNOWN;
        storageFormat = builder.storageFormat != null ? builder.storageFormat : ShaderStorageTextureFormat.NONE;
        inputAttachmentIndex = requireOptional(builder.inputAttachmentIndex, "input attachment index");
        domain = builder.domain != null ? builder.domain : ShaderParameterDomain.UNSPECIFIED;
        updateFrequency = builder.updateFrequency != null
                ? builder.updateFrequency : ShaderUpdateFrequency.UNSPECIFIED;
        if (complete) {
            validateComplete();
        }
    }

    /**
     * Creates an explicitly incomplete, coarse handwritten binding.
     *
     * @param group the group
     * @param binding the binding
     * @param name the name
     * @param type the compatibility binding type
     * @return a new binding
     */
    public static ShaderBinding of(int group, int binding, String name, ShaderBindingType type) {
        ShaderBindingType checked = type != null ? type : ShaderBindingType.UNKNOWN;
        return new Builder(group, binding, name, resourceKind(checked))
                .type(checked)
                .complete(false)
                .build();
    }

    /**
     * Creates a complete resource builder.
     *
     * @param group the bind group
     * @param binding the binding index
     * @param name the source name
     * @param resourceKind the complete resource kind
     * @return the builder
     */
    public static Builder builder(int group, int binding, String name, ShaderResourceKind resourceKind) {
        return new Builder(group, binding, name, resourceKind);
    }

    public int group() {
        return group;
    }

    public int binding() {
        return binding;
    }

    public String stableId() {
        return stableId;
    }

    public String name() {
        return name;
    }

    /**
     * Returns the compatibility resource category.
     *
     * @return the compatibility type
     */
    public ShaderBindingType type() {
        return type;
    }

    public ShaderResourceKind resourceKind() {
        return resourceKind;
    }

    public boolean complete() {
        return complete;
    }

    public ShaderStageVisibility visibility() {
        return visibility;
    }

    public ShaderResourceAccess access() {
        return access;
    }

    /**
     * Returns the binding-array count, or {@code -1} when this is not a binding array.
     *
     * @return the binding-array count
     */
    public long bindingArrayCount() {
        return bindingArrayCount;
    }

    public long minimumBindingSize() {
        return minimumBindingSize;
    }

    public long sizeWithoutPadding() {
        return sizeWithoutPadding;
    }

    public long alignment() {
        return alignment;
    }

    public ShaderParameterLayout bufferLayout() {
        return bufferLayout;
    }

    public ShaderTextureDimension textureDimension() {
        return textureDimension;
    }

    public ShaderTextureSampleType textureSampleType() {
        return textureSampleType;
    }

    public ShaderSamplerKind samplerKind() {
        return samplerKind;
    }

    /**
     * Returns the stable FDXI storage-format tag, or {@code -1} when absent.
     *
     * @return the storage-format tag
     */
    public int storageFormatTag() {
        return storageFormat.fdxiTag();
    }

    public ShaderStorageTextureFormat storageFormat() {
        return storageFormat;
    }

    /**
     * Returns the image format used by storage textures or texel buffers.
     *
     * @return the reflected image format
     */
    public ShaderStorageTextureFormat imageFormat() {
        return storageFormat;
    }

    public long inputAttachmentIndex() {
        return inputAttachmentIndex;
    }

    public ShaderParameterDomain domain() {
        return domain;
    }

    public ShaderUpdateFrequency updateFrequency() {
        return updateFrequency;
    }

    ShaderBinding withMetadata(String newStableId, ShaderParameterDomain newDomain,
            ShaderUpdateFrequency newFrequency, ShaderParameterLayout newLayout) {
        return copyBuilder()
                .stableId(newStableId)
                .semantics(newDomain, newFrequency)
                .bufferLayout(newLayout)
                .build();
    }

    Builder copyBuilder() {
        return new Builder(group, binding, name, resourceKind)
                .stableId(stableId)
                .type(type)
                .complete(complete)
                .visibility(visibility)
                .access(access)
                .bindingArrayCount(bindingArrayCount)
                .buffer(minimumBindingSize, sizeWithoutPadding, alignment, bufferLayout)
                .texture(textureDimension, textureSampleType)
                .samplerKind(samplerKind)
                .storageFormat(storageFormat)
                .inputAttachmentIndex(inputAttachmentIndex)
                .semantics(domain, updateFrequency);
    }

    private void validateComplete() {
        if (resourceKind == ShaderResourceKind.UNKNOWN) {
            throw new FdxException("Complete shader binding resource kind cannot be unknown");
        }
        if (visibility.equals(ShaderStageVisibility.NONE)) {
            throw new FdxException("Complete shader binding visibility cannot be empty: " + name);
        }
        if (bindingArrayCount == 0) {
            throw new FdxException("Shader binding array count must be absent or positive: " + name);
        }
        if ((resourceKind == ShaderResourceKind.UNIFORM_BUFFER
                || resourceKind == ShaderResourceKind.STORAGE_BUFFER)) {
            if (bufferLayout == null) {
                throw new FdxException("Complete buffer binding must contain a parameter layout: " + name);
            }
            if (minimumBindingSize != bufferLayout.minimumBindingSize()) {
                throw new FdxException("Shader buffer minimum binding size does not match its parameter layout: "
                        + name);
            }
            if (alignment == 0 || sizeWithoutPadding > minimumBindingSize) {
                throw new FdxException("Shader buffer size/alignment metadata is contradictory: " + name);
            }
            if (resourceKind == ShaderResourceKind.UNIFORM_BUFFER && access != ShaderResourceAccess.READ) {
                throw new FdxException("Shader uniform buffer access must be READ: " + name);
            }
            if (resourceKind == ShaderResourceKind.STORAGE_BUFFER
                    && access != ShaderResourceAccess.READ && access != ShaderResourceAccess.WRITE
                    && access != ShaderResourceAccess.READ_WRITE) {
                throw new FdxException("Shader storage buffer access must be explicit: " + name);
            }
        } else if (bufferLayout != null) {
            throw new FdxException("Only shader buffer resources can contain a parameter layout: " + name);
        } else if (minimumBindingSize != 0 || sizeWithoutPadding != 0 || alignment != 0) {
            throw new FdxException("Non-buffer shader resources cannot contain buffer layout metadata: " + name);
        }
        if (resourceKind == ShaderResourceKind.SAMPLER) {
            if (samplerKind == ShaderSamplerKind.UNKNOWN || samplerKind == ShaderSamplerKind.NONE
                    || access != ShaderResourceAccess.NONE || textureDimension != ShaderTextureDimension.NONE
                    || textureSampleType != ShaderTextureSampleType.NONE
                    || storageFormat != ShaderStorageTextureFormat.NONE) {
                throw new FdxException("Complete sampler binding metadata is contradictory: " + name);
            }
        } else if (samplerKind != ShaderSamplerKind.NONE) {
            throw new FdxException("Only shader sampler resources can declare a sampler kind: " + name);
        }
        if (isTexture(resourceKind) && (textureDimension == ShaderTextureDimension.UNKNOWN
                || textureDimension == ShaderTextureDimension.NONE)) {
            throw new FdxException("Complete texture binding must declare its dimension: " + name);
        }
        if (resourceKind == ShaderResourceKind.SAMPLED_TEXTURE
                || resourceKind == ShaderResourceKind.MULTISAMPLED_TEXTURE) {
            if (access != ShaderResourceAccess.READ || textureSampleType == ShaderTextureSampleType.NONE
                    || textureSampleType == ShaderTextureSampleType.UNKNOWN
                    || storageFormat != ShaderStorageTextureFormat.NONE) {
                throw new FdxException("Complete sampled texture metadata is contradictory: " + name);
            }
        }
        if (resourceKind == ShaderResourceKind.DEPTH_TEXTURE
                || resourceKind == ShaderResourceKind.DEPTH_MULTISAMPLED_TEXTURE) {
            if (access != ShaderResourceAccess.READ || textureSampleType != ShaderTextureSampleType.NONE
                    || storageFormat != ShaderStorageTextureFormat.NONE) {
                throw new FdxException("Complete depth texture metadata is contradictory: " + name);
            }
        }
        if (resourceKind == ShaderResourceKind.STORAGE_TEXTURE) {
            if (access == ShaderResourceAccess.NONE || access == ShaderResourceAccess.UNKNOWN
                    || storageFormat == ShaderStorageTextureFormat.NONE
                    || textureSampleType != ShaderTextureSampleType.NONE) {
                throw new FdxException("Complete storage texture metadata is contradictory: " + name);
            }
        } else if (resourceKind == ShaderResourceKind.TEXEL_BUFFER) {
            if (access == ShaderResourceAccess.NONE || access == ShaderResourceAccess.UNKNOWN
                    || storageFormat == ShaderStorageTextureFormat.NONE
                    || textureDimension == ShaderTextureDimension.NONE
                    || textureDimension == ShaderTextureDimension.UNKNOWN
                    || textureSampleType == ShaderTextureSampleType.NONE
                    || textureSampleType == ShaderTextureSampleType.UNKNOWN) {
                throw new FdxException("Complete texel-buffer metadata is contradictory: " + name);
            }
        } else if (storageFormat != ShaderStorageTextureFormat.NONE) {
            throw new FdxException("Only storage textures or texel buffers can declare an image format: " + name);
        }
        if (resourceKind == ShaderResourceKind.EXTERNAL_TEXTURE
                && (access != ShaderResourceAccess.READ
                || textureDimension != ShaderTextureDimension.D2
                || textureSampleType != ShaderTextureSampleType.NONE)) {
            throw new FdxException("Complete external texture metadata is contradictory: " + name);
        }
        if (resourceKind == ShaderResourceKind.INPUT_ATTACHMENT) {
            if (inputAttachmentIndex < 0 || access != ShaderResourceAccess.READ
                    || textureDimension != ShaderTextureDimension.D2
                    || (textureSampleType != ShaderTextureSampleType.FLOAT
                    && textureSampleType != ShaderTextureSampleType.UINT
                    && textureSampleType != ShaderTextureSampleType.SINT)) {
                throw new FdxException("Complete input-attachment metadata is contradictory: " + name);
            }
        } else if (inputAttachmentIndex >= 0) {
            throw new FdxException("Only input-attachment resources can declare an attachment index: " + name);
        }
        if (!isTexture(resourceKind) && resourceKind != ShaderResourceKind.INPUT_ATTACHMENT
                && resourceKind != ShaderResourceKind.TEXEL_BUFFER
                && textureDimension != ShaderTextureDimension.NONE) {
            throw new FdxException("Non-texture shader resource declares a texture dimension: " + name);
        }
        if (!isTexture(resourceKind) && resourceKind != ShaderResourceKind.INPUT_ATTACHMENT
                && resourceKind != ShaderResourceKind.TEXEL_BUFFER
                && textureSampleType != ShaderTextureSampleType.NONE) {
            throw new FdxException("Non-texture shader resource declares a texture sample type: " + name);
        }
    }

    private static boolean isTexture(ShaderResourceKind kind) {
        return kind == ShaderResourceKind.SAMPLED_TEXTURE || kind == ShaderResourceKind.MULTISAMPLED_TEXTURE
                || kind == ShaderResourceKind.STORAGE_TEXTURE || kind == ShaderResourceKind.DEPTH_TEXTURE
                || kind == ShaderResourceKind.DEPTH_MULTISAMPLED_TEXTURE
                || kind == ShaderResourceKind.EXTERNAL_TEXTURE;
    }

    private static ShaderBindingType compatibilityType(ShaderResourceKind kind) {
        return switch (kind) {
            case UNIFORM_BUFFER -> ShaderBindingType.UNIFORM_BUFFER;
            case STORAGE_BUFFER -> ShaderBindingType.STORAGE_BUFFER;
            case SAMPLER -> ShaderBindingType.SAMPLER;
            case STORAGE_TEXTURE -> ShaderBindingType.STORAGE_TEXTURE;
            case SAMPLED_TEXTURE, MULTISAMPLED_TEXTURE, DEPTH_TEXTURE, DEPTH_MULTISAMPLED_TEXTURE,
                    EXTERNAL_TEXTURE, TEXEL_BUFFER, INPUT_ATTACHMENT -> ShaderBindingType.TEXTURE;
            case UNKNOWN -> ShaderBindingType.UNKNOWN;
        };
    }

    private static ShaderResourceKind resourceKind(ShaderBindingType type) {
        return switch (type) {
            case UNIFORM_BUFFER -> ShaderResourceKind.UNIFORM_BUFFER;
            case STORAGE_BUFFER -> ShaderResourceKind.STORAGE_BUFFER;
            case TEXTURE -> ShaderResourceKind.SAMPLED_TEXTURE;
            case STORAGE_TEXTURE -> ShaderResourceKind.STORAGE_TEXTURE;
            case SAMPLER -> ShaderResourceKind.SAMPLER;
            case UNKNOWN -> ShaderResourceKind.UNKNOWN;
        };
    }

    private static long requireOptional(long value, String label) {
        if (value < ABSENT) {
            throw new FdxException("Shader binding " + label + " cannot be less than -1");
        }
        return value;
    }

    private static long requireNonNegative(long value, String label) {
        if (value < 0) {
            throw new FdxException("Shader binding " + label + " cannot be negative");
        }
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ShaderBinding other)) {
            return false;
        }
        return group == other.group && binding == other.binding && complete == other.complete
                && bindingArrayCount == other.bindingArrayCount && minimumBindingSize == other.minimumBindingSize
                && sizeWithoutPadding == other.sizeWithoutPadding && alignment == other.alignment
                && storageFormat == other.storageFormat
                && inputAttachmentIndex == other.inputAttachmentIndex && stableId.equals(other.stableId)
                && name.equals(other.name) && type == other.type && resourceKind == other.resourceKind
                && visibility.equals(other.visibility) && access == other.access
                && Objects.equals(bufferLayout, other.bufferLayout) && textureDimension == other.textureDimension
                && textureSampleType == other.textureSampleType && samplerKind == other.samplerKind
                && domain == other.domain && updateFrequency == other.updateFrequency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, binding, stableId, name, type, resourceKind, complete, visibility, access,
                bindingArrayCount, minimumBindingSize, sizeWithoutPadding, alignment, bufferLayout, textureDimension,
                textureSampleType, samplerKind, storageFormat, inputAttachmentIndex, domain, updateFrequency);
    }

    /**
     * Builds complete resource declarations.
     */
    public static final class Builder {
        private final int group;
        private final int binding;
        private final String name;
        private final ShaderResourceKind resourceKind;
        private String stableId;
        private ShaderBindingType type;
        private boolean complete = true;
        private ShaderStageVisibility visibility = ShaderStageVisibility.NONE;
        private ShaderResourceAccess access = ShaderResourceAccess.NONE;
        private long bindingArrayCount = ABSENT;
        private long minimumBindingSize;
        private long sizeWithoutPadding;
        private long alignment;
        private ShaderParameterLayout bufferLayout;
        private ShaderTextureDimension textureDimension = ShaderTextureDimension.NONE;
        private ShaderTextureSampleType textureSampleType = ShaderTextureSampleType.NONE;
        private ShaderSamplerKind samplerKind = ShaderSamplerKind.NONE;
        private ShaderStorageTextureFormat storageFormat = ShaderStorageTextureFormat.NONE;
        private long inputAttachmentIndex = ABSENT;
        private ShaderParameterDomain domain = ShaderParameterDomain.UNSPECIFIED;
        private ShaderUpdateFrequency updateFrequency = ShaderUpdateFrequency.UNSPECIFIED;

        private Builder(int group, int binding, String name, ShaderResourceKind resourceKind) {
            this.group = group;
            this.binding = binding;
            this.name = name;
            this.stableId = name;
            this.resourceKind = resourceKind;
        }

        public Builder stableId(String stableId) {
            this.stableId = stableId;
            return this;
        }

        Builder type(ShaderBindingType type) {
            this.type = type;
            return this;
        }

        Builder complete(boolean complete) {
            this.complete = complete;
            return this;
        }

        public Builder visibility(ShaderStageVisibility visibility) {
            this.visibility = visibility;
            return this;
        }

        public Builder access(ShaderResourceAccess access) {
            this.access = access;
            return this;
        }

        public Builder bindingArrayCount(long bindingArrayCount) {
            this.bindingArrayCount = bindingArrayCount;
            return this;
        }

        public Builder buffer(long minimumBindingSize, long sizeWithoutPadding, long alignment,
                ShaderParameterLayout bufferLayout) {
            this.minimumBindingSize = minimumBindingSize;
            this.sizeWithoutPadding = sizeWithoutPadding;
            this.alignment = alignment;
            this.bufferLayout = bufferLayout;
            return this;
        }

        public Builder bufferLayout(ShaderParameterLayout bufferLayout) {
            this.bufferLayout = bufferLayout;
            if (bufferLayout != null) {
                minimumBindingSize = bufferLayout.minimumBindingSize();
                alignment = bufferLayout.alignment();
                if (sizeWithoutPadding == 0) {
                    sizeWithoutPadding = minimumBindingSize;
                }
            }
            return this;
        }

        public Builder texture(ShaderTextureDimension dimension, ShaderTextureSampleType sampleType) {
            textureDimension = dimension;
            textureSampleType = sampleType;
            return this;
        }

        public Builder samplerKind(ShaderSamplerKind samplerKind) {
            this.samplerKind = samplerKind;
            return this;
        }

        public Builder storageFormatTag(int storageFormatTag) {
            storageFormat = ShaderStorageTextureFormat.fromFdxiTag(storageFormatTag);
            return this;
        }

        public Builder storageFormat(ShaderStorageTextureFormat storageFormat) {
            this.storageFormat = storageFormat;
            return this;
        }

        public Builder inputAttachmentIndex(long inputAttachmentIndex) {
            this.inputAttachmentIndex = inputAttachmentIndex;
            return this;
        }

        public Builder semantics(ShaderParameterDomain domain, ShaderUpdateFrequency updateFrequency) {
            this.domain = domain;
            this.updateFrequency = updateFrequency;
            return this;
        }

        public ShaderBinding build() {
            return new ShaderBinding(this);
        }
    }
}
