package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Immutable provider limits used before native resource or pipeline creation.
 */
public final class GraphicsLimits {
    private final int maxBindGroups;
    private final int maxBindingsPerGroup;
    private final int maxUniformBuffersPerStage;
    private final int maxStorageBuffersPerStage;
    private final int maxSampledTexturesPerStage;
    private final int maxSamplersPerStage;
    private final int maxStorageTexturesPerStage;
    private final int maxColorAttachments;
    private final int maxVertexBuffers;
    private final int maxVertexAttributes;
    private final int maxComputeWorkgroupsPerDimension;
    private final int maxComputeWorkgroupSizeX;
    private final int maxComputeWorkgroupSizeY;
    private final int maxComputeWorkgroupSizeZ;
    private final int maxComputeInvocationsPerWorkgroup;
    private final int maxComputeWorkgroupStorageSize;
    private final long maxUniformBufferBindingSize;
    private final long maxStorageBufferBindingSize;

    private GraphicsLimits(Builder builder) {
        maxBindGroups = positive(builder.maxBindGroups, "bind groups");
        maxBindingsPerGroup = positive(builder.maxBindingsPerGroup, "bindings per group");
        maxUniformBuffersPerStage = nonNegative(builder.maxUniformBuffersPerStage,
                "uniform buffers per stage");
        maxStorageBuffersPerStage = nonNegative(builder.maxStorageBuffersPerStage,
                "storage buffers per stage");
        maxSampledTexturesPerStage = nonNegative(builder.maxSampledTexturesPerStage,
                "sampled textures per stage");
        maxSamplersPerStage = nonNegative(builder.maxSamplersPerStage, "samplers per stage");
        maxStorageTexturesPerStage = nonNegative(builder.maxStorageTexturesPerStage,
                "storage textures per stage");
        maxColorAttachments = positive(builder.maxColorAttachments, "color attachments");
        maxVertexBuffers = positive(builder.maxVertexBuffers, "vertex buffers");
        maxVertexAttributes = positive(builder.maxVertexAttributes, "vertex attributes");
        maxComputeWorkgroupsPerDimension = nonNegative(builder.maxComputeWorkgroupsPerDimension,
                "compute workgroups per dimension");
        maxComputeWorkgroupSizeX = nonNegative(builder.maxComputeWorkgroupSizeX,
                "compute workgroup size X");
        maxComputeWorkgroupSizeY = nonNegative(builder.maxComputeWorkgroupSizeY,
                "compute workgroup size Y");
        maxComputeWorkgroupSizeZ = nonNegative(builder.maxComputeWorkgroupSizeZ,
                "compute workgroup size Z");
        maxComputeInvocationsPerWorkgroup = nonNegative(
                builder.maxComputeInvocationsPerWorkgroup,
                "compute invocations per workgroup");
        maxComputeWorkgroupStorageSize = nonNegative(
                builder.maxComputeWorkgroupStorageSize,
                "compute workgroup storage size");
        maxUniformBufferBindingSize = positive(builder.maxUniformBufferBindingSize,
                "uniform buffer binding size");
        maxStorageBufferBindingSize = nonNegative(builder.maxStorageBufferBindingSize,
                "storage buffer binding size");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns conservative limits supported by every current render provider.
     *
     * @return conservative limits
     */
    public static GraphicsLimits conservativeRender() {
        return builder().build();
    }

    public int maxBindGroups() {
        return maxBindGroups;
    }

    public int maxBindingsPerGroup() {
        return maxBindingsPerGroup;
    }

    public int maxUniformBuffersPerStage() {
        return maxUniformBuffersPerStage;
    }

    public int maxStorageBuffersPerStage() {
        return maxStorageBuffersPerStage;
    }

    public int maxSampledTexturesPerStage() {
        return maxSampledTexturesPerStage;
    }

    public int maxSamplersPerStage() {
        return maxSamplersPerStage;
    }

    public int maxStorageTexturesPerStage() {
        return maxStorageTexturesPerStage;
    }

    public int maxColorAttachments() {
        return maxColorAttachments;
    }

    public int maxVertexBuffers() {
        return maxVertexBuffers;
    }

    public int maxVertexAttributes() {
        return maxVertexAttributes;
    }

    public int maxComputeWorkgroupsPerDimension() {
        return maxComputeWorkgroupsPerDimension;
    }

    public int maxComputeWorkgroupSizeX() {
        return maxComputeWorkgroupSizeX;
    }

    public int maxComputeWorkgroupSizeY() {
        return maxComputeWorkgroupSizeY;
    }

    public int maxComputeWorkgroupSizeZ() {
        return maxComputeWorkgroupSizeZ;
    }

    public int maxComputeInvocationsPerWorkgroup() {
        return maxComputeInvocationsPerWorkgroup;
    }

    public int maxComputeWorkgroupStorageSize() {
        return maxComputeWorkgroupStorageSize;
    }

    public long maxUniformBufferBindingSize() {
        return maxUniformBufferBindingSize;
    }

    public long maxStorageBufferBindingSize() {
        return maxStorageBufferBindingSize;
    }

    private static int positive(int value, String label) {
        if (value <= 0) {
            throw new FdxException("Graphics limit must be positive for " + label);
        }
        return value;
    }

    private static long positive(long value, String label) {
        if (value <= 0) {
            throw new FdxException("Graphics limit must be positive for " + label);
        }
        return value;
    }

    private static int nonNegative(int value, String label) {
        if (value < 0) {
            throw new FdxException("Graphics limit cannot be negative for " + label);
        }
        return value;
    }

    private static long nonNegative(long value, String label) {
        if (value < 0) {
            throw new FdxException("Graphics limit cannot be negative for " + label);
        }
        return value;
    }

    /**
     * Builds immutable graphics limits.
     */
    public static final class Builder {
        private int maxBindGroups = 2;
        private int maxBindingsPerGroup = 32;
        private int maxUniformBuffersPerStage = 1;
        private int maxStorageBuffersPerStage;
        private int maxSampledTexturesPerStage = 16;
        private int maxSamplersPerStage = 16;
        private int maxStorageTexturesPerStage;
        private int maxColorAttachments = 1;
        private int maxVertexBuffers = 4;
        private int maxVertexAttributes = 16;
        private int maxComputeWorkgroupsPerDimension;
        private int maxComputeWorkgroupSizeX;
        private int maxComputeWorkgroupSizeY;
        private int maxComputeWorkgroupSizeZ;
        private int maxComputeInvocationsPerWorkgroup;
        private int maxComputeWorkgroupStorageSize;
        private long maxUniformBufferBindingSize = 64L * 1024L;
        private long maxStorageBufferBindingSize;

        public Builder maxBindGroups(int value) {
            maxBindGroups = value;
            return this;
        }

        public Builder maxBindingsPerGroup(int value) {
            maxBindingsPerGroup = value;
            return this;
        }

        public Builder maxUniformBuffersPerStage(int value) {
            maxUniformBuffersPerStage = value;
            return this;
        }

        public Builder maxStorageBuffersPerStage(int value) {
            maxStorageBuffersPerStage = value;
            return this;
        }

        public Builder maxSampledTexturesPerStage(int value) {
            maxSampledTexturesPerStage = value;
            return this;
        }

        public Builder maxSamplersPerStage(int value) {
            maxSamplersPerStage = value;
            return this;
        }

        public Builder maxStorageTexturesPerStage(int value) {
            maxStorageTexturesPerStage = value;
            return this;
        }

        public Builder maxColorAttachments(int value) {
            maxColorAttachments = value;
            return this;
        }

        public Builder maxVertexBuffers(int value) {
            maxVertexBuffers = value;
            return this;
        }

        public Builder maxVertexAttributes(int value) {
            maxVertexAttributes = value;
            return this;
        }

        public Builder maxComputeWorkgroupsPerDimension(int value) {
            maxComputeWorkgroupsPerDimension = value;
            return this;
        }

        public Builder maxComputeWorkgroupSize(int x, int y, int z) {
            maxComputeWorkgroupSizeX = x;
            maxComputeWorkgroupSizeY = y;
            maxComputeWorkgroupSizeZ = z;
            return this;
        }

        public Builder maxComputeInvocationsPerWorkgroup(int value) {
            maxComputeInvocationsPerWorkgroup = value;
            return this;
        }

        public Builder maxComputeWorkgroupStorageSize(int value) {
            maxComputeWorkgroupStorageSize = value;
            return this;
        }

        public Builder maxUniformBufferBindingSize(long value) {
            maxUniformBufferBindingSize = value;
            return this;
        }

        public Builder maxStorageBufferBindingSize(long value) {
            maxStorageBufferBindingSize = value;
            return this;
        }

        public GraphicsLimits build() {
            return new GraphicsLimits(this);
        }
    }
}
