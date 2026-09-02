package io.github.libfdx.graphics;

import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.ClipDepthRange;

import java.util.Arrays;

/**
 * Immutable profiles, features, limits, formats, and sample counts exposed by
 * one graphics device.
 */
public final class GraphicsCapabilities {
    private static final GraphicsCapabilities CONSERVATIVE = builder()
            .profile(ShaderProfile.PORTABLE_WEBGL2)
            .feature(GraphicsFeature.DEPTH_STENCIL_ATTACHMENTS)
            .colorFormats(TextureFormat.RGBA8_UNORM, TextureFormat.RGBA8_UNORM_SRGB,
                    TextureFormat.BGRA8_UNORM, TextureFormat.BGRA8_UNORM_SRGB)
            .depthStencilFormats(TextureFormat.DEPTH24_STENCIL8, TextureFormat.DEPTH32_FLOAT)
            // The conservative profile is WebGL2, so it clips the OpenGL way.
            .clipDepthRange(ClipDepthRange.NEGATIVE_ONE_TO_ONE)
            .sampleCounts(1)
            .limits(GraphicsLimits.conservativeRender())
            .build();

    private final boolean[] profiles;
    private final boolean[] features;
    private final TextureFormat[] colorFormats;
    private final TextureFormat[] depthStencilFormats;
    private final TextureFormat[] resolveFormats;
    private final int[] sampleCounts;
    private final int[][] formatSampleCounts;
    private final GraphicsLimits limits;
    private final ClipDepthRange clipDepthRange;

    private GraphicsCapabilities(Builder builder) {
        profiles = builder.profiles.clone();
        features = builder.features.clone();
        colorFormats = normalizeFormats(builder.colorFormats, false);
        depthStencilFormats = normalizeFormats(builder.depthStencilFormats, true);
        resolveFormats = normalizeResolveFormats(builder.resolveFormats, colorFormats);
        sampleCounts = normalizeSampleCounts(builder.sampleCounts);
        formatSampleCounts = normalizeFormatSampleCounts(builder.formatSampleCounts,
                colorFormats, depthStencilFormats, sampleCounts);
        limits = builder.limits != null ? builder.limits : GraphicsLimits.conservativeRender();
        // Deliberately has no default. A device that silently guessed here
        // would build projections for the wrong clip volume and discard
        // geometry with no error reported, which is exactly the failure this
        // property exists to prevent.
        if (builder.clipDepthRange == null) {
            throw new FdxException(
                    "Graphics capabilities must declare a clip depth range");
        }
        clipDepthRange = builder.clipDepthRange;
        if (!supports(ShaderProfile.PORTABLE_WEBGL2)
                && !supports(ShaderProfile.PORTABLE_WEBGPU)
                && !supports(ShaderProfile.NATIVE)) {
            throw new FdxException("Graphics capabilities must expose at least one shader profile");
        }
        if (supports(GraphicsFeature.COMPUTE)
                && (limits.maxComputeWorkgroupsPerDimension() == 0
                        || limits.maxComputeWorkgroupSizeX() == 0
                        || limits.maxComputeWorkgroupSizeY() == 0
                        || limits.maxComputeWorkgroupSizeZ() == 0
                        || limits.maxComputeInvocationsPerWorkgroup() == 0
                        || limits.maxComputeWorkgroupStorageSize() == 0)) {
            throw new FdxException(
                    "Compute capability requires complete non-zero compute limits");
        }
        if (supports(GraphicsFeature.RESOLVE_ATTACHMENTS) && resolveFormats.length == 0) {
            throw new FdxException("Resolve capability requires at least one resolvable color format");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static GraphicsCapabilities conservativeRender() {
        return CONSERVATIVE;
    }

    public boolean supports(ShaderProfile profile) {
        return profile != null && profiles[profile.ordinal()];
    }

    public boolean supports(GraphicsFeature feature) {
        return feature != null && features[feature.ordinal()];
    }

    public boolean supportsColorFormat(TextureFormat format) {
        return contains(colorFormats, format);
    }

    public boolean supportsDepthStencilFormat(TextureFormat format) {
        return contains(depthStencilFormats, format);
    }

    public boolean supportsSampleCount(int sampleCount) {
        return Arrays.binarySearch(sampleCounts, sampleCount) >= 0;
    }

    /**
     * Returns whether a concrete attachment format supports a sample count.
     *
     * @param format attachment format
     * @param sampleCount requested count
     * @return whether supported
     */
    public boolean supportsSampleCount(TextureFormat format, int sampleCount) {
        if (format == null || format == TextureFormat.UNKNOWN
                || !supportsSampleCount(sampleCount)) {
            return false;
        }
        int[] counts = formatSampleCounts[format.ordinal()];
        return counts != null && Arrays.binarySearch(counts, sampleCount) >= 0;
    }

    /**
     * Returns whether a color format can be the source and destination format
     * of a multisample resolve operation.
     *
     * @param format color format
     * @return whether resolvable
     */
    public boolean supportsResolveFormat(TextureFormat format) {
        return contains(resolveFormats, format);
    }

    public TextureFormat[] colorFormats() {
        return colorFormats.clone();
    }

    public TextureFormat[] depthStencilFormats() {
        return depthStencilFormats.clone();
    }

    public TextureFormat[] resolveFormats() {
        return resolveFormats.clone();
    }

    public int[] sampleCounts() {
        return sampleCounts.clone();
    }

    public GraphicsLimits limits() {
        return limits;
    }

    public void require(ShaderProfile profile) {
        if (!supports(profile)) {
            throw new FdxException("Graphics device does not support shader profile " + profile);
        }
    }

    public void require(GraphicsFeature feature) {
        if (!supports(feature)) {
            throw new FdxException("Graphics device does not support feature " + feature);
        }
    }

    private static TextureFormat[] normalizeFormats(TextureFormat[] values, boolean depth) {
        TextureFormat[] result = values != null ? values.clone() : new TextureFormat[0];
        for (TextureFormat value : result) {
            if (value == null || value == TextureFormat.UNKNOWN
                    || depth != value.isDepthStencil()) {
                throw new FdxException("Graphics capabilities contain an invalid "
                        + (depth ? "depth/stencil" : "color") + " format");
            }
        }
        Arrays.sort(result);
        for (int i = 1; i < result.length; i++) {
            if (result[i - 1] == result[i]) {
                throw new FdxException("Duplicate graphics capability format: " + result[i]);
            }
        }
        return result;
    }

    private static int[] normalizeSampleCounts(int[] values) {
        int[] result = values != null && values.length > 0 ? values.clone() : new int[] { 1 };
        for (int value : result) {
            if (value <= 0 || (value & value - 1) != 0) {
                throw new FdxException("Graphics sample counts must be positive powers of two");
            }
        }
        Arrays.sort(result);
        for (int i = 1; i < result.length; i++) {
            if (result[i - 1] == result[i]) {
                throw new FdxException("Duplicate graphics sample count: " + result[i]);
            }
        }
        return result;
    }

    private static TextureFormat[] normalizeResolveFormats(TextureFormat[] values,
            TextureFormat[] colorFormats) {
        TextureFormat[] result = normalizeFormats(values, false);
        for (TextureFormat format : result) {
            if (!contains(colorFormats, format)) {
                throw new FdxException("Resolvable format is not a supported color format: "
                        + format);
            }
        }
        return result;
    }

    private static int[][] normalizeFormatSampleCounts(int[][] values,
            TextureFormat[] colorFormats, TextureFormat[] depthStencilFormats,
            int[] globalCounts) {
        int[][] result = new int[TextureFormat.values().length][];
        for (TextureFormat format : colorFormats) {
            result[format.ordinal()] = globalCounts.clone();
        }
        for (TextureFormat format : depthStencilFormats) {
            result[format.ordinal()] = globalCounts.clone();
        }
        if (values == null) {
            return result;
        }
        for (TextureFormat format : TextureFormat.values()) {
            int[] override = values[format.ordinal()];
            if (override == null) {
                continue;
            }
            if (!contains(colorFormats, format) && !contains(depthStencilFormats, format)) {
                throw new FdxException("Sample-count override uses an unsupported format: "
                        + format);
            }
            int[] normalized = normalizeSampleCounts(override);
            for (int count : normalized) {
                if (Arrays.binarySearch(globalCounts, count) < 0) {
                    throw new FdxException("Format sample count " + count
                            + " is not in the device sample-count set");
                }
            }
            result[format.ordinal()] = normalized;
        }
        return result;
    }

    private static boolean contains(TextureFormat[] values, TextureFormat format) {
        return format != null && Arrays.binarySearch(values, format) >= 0;
    }

    /**
     * Builds immutable device capabilities.
     */
    /**
     * Returns the depth range this device clips against in clip space.
     *
     * <p>{@link ClipDepthRange#ZERO_TO_ONE} for Vulkan, Direct3D 12, Metal and
     * WebGPU; {@link ClipDepthRange#NEGATIVE_ONE_TO_ONE} for the OpenGL
     * family. Projection matrices, frustum plane extraction and unprojection
     * all have to agree with it.</p>
     *
     * @return the clip depth range
     */
    public ClipDepthRange clipDepthRange() {
        return clipDepthRange;
    }

    public static final class Builder {
        private final boolean[] profiles = new boolean[ShaderProfile.values().length];
        private final boolean[] features = new boolean[GraphicsFeature.values().length];
        private TextureFormat[] colorFormats = new TextureFormat[0];
        private TextureFormat[] depthStencilFormats = new TextureFormat[0];
        private TextureFormat[] resolveFormats = new TextureFormat[0];
        private int[] sampleCounts = { 1 };
        private final int[][] formatSampleCounts =
                new int[TextureFormat.values().length][];
        private GraphicsLimits limits;
        private ClipDepthRange clipDepthRange;

        /**
         * Declares the depth range this device clips against. Required.
         *
         * @param value the clip depth range
         * @return this builder for chaining
         */
        public Builder clipDepthRange(ClipDepthRange value) {
            if (value == null) {
                throw new FdxException("Clip depth range cannot be null");
            }
            clipDepthRange = value;
            return this;
        }

        public Builder profile(ShaderProfile profile) {
            if (profile == null) {
                throw new FdxException("Graphics shader profile cannot be null");
            }
            profiles[profile.ordinal()] = true;
            return this;
        }

        public Builder feature(GraphicsFeature feature) {
            if (feature == null) {
                throw new FdxException("Graphics feature cannot be null");
            }
            features[feature.ordinal()] = true;
            return this;
        }

        public Builder colorFormats(TextureFormat... values) {
            colorFormats = values;
            return this;
        }

        public Builder depthStencilFormats(TextureFormat... values) {
            depthStencilFormats = values;
            return this;
        }

        public Builder resolveFormats(TextureFormat... values) {
            resolveFormats = values;
            return this;
        }

        public Builder sampleCounts(int... values) {
            sampleCounts = values;
            return this;
        }

        public Builder formatSampleCounts(TextureFormat format, int... values) {
            if (format == null || format == TextureFormat.UNKNOWN) {
                throw new FdxException("Format sample-count override requires a concrete format");
            }
            formatSampleCounts[format.ordinal()] = values;
            return this;
        }

        public Builder limits(GraphicsLimits value) {
            limits = value;
            return this;
        }

        public GraphicsCapabilities build() {
            return new GraphicsCapabilities(this);
        }
    }
}
