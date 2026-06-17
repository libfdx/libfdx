package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;

/**
 * Represents a shader bundle.
 *
 * @author xpenatan
 */
public final class ShaderBundle {
    private final String label;
    private final ShaderProfile profile;
    private final String wgslSource;
    private final String glslVertexSource;
    private final String glslFragmentSource;
    private final String glslEsVertexSource;
    private final String glslEsFragmentSource;
    private final int[] spirvVertexWords;
    private final int[] spirvFragmentWords;
    private final String mslSource;
    private final String hlslSource;
    private final ShaderReflection reflection;

    private ShaderBundle(Builder builder) {
        label = builder.label;
        profile = builder.profile;
        wgslSource = builder.wgslSource;
        glslVertexSource = builder.glslVertexSource;
        glslFragmentSource = builder.glslFragmentSource;
        glslEsVertexSource = builder.glslEsVertexSource;
        glslEsFragmentSource = builder.glslEsFragmentSource;
        spirvVertexWords = cloneOrNull(builder.spirvVertexWords);
        spirvFragmentWords = cloneOrNull(builder.spirvFragmentWords);
        mslSource = builder.mslSource;
        hlslSource = builder.hlslSource;
        reflection = builder.reflection != null ? builder.reflection : ShaderReflection.empty();
    }

    /**
     * Runs the builder step.
     *
     * @param label the debug label
     * @return the created value
     */
    public static Builder builder(String label) {
        return new Builder(label);
    }

    /**
     * Returns the label.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Returns the profile.
     *
     * @return the profile
     */
    public ShaderProfile profile() {
        return profile;
    }

    /**
     * Returns the wgsl source.
     *
     * @return the wgsl source
     */
    public String wgslSource() {
        return wgslSource;
    }

    /**
     * Returns the glsl vertex source.
     *
     * @return the glsl vertex source
     */
    public String glslVertexSource() {
        return glslVertexSource;
    }

    /**
     * Returns the glsl fragment source.
     *
     * @return the glsl fragment source
     */
    public String glslFragmentSource() {
        return glslFragmentSource;
    }

    /**
     * Returns the glsl es vertex source.
     *
     * @return the glsl es vertex source
     */
    public String glslEsVertexSource() {
        return glslEsVertexSource;
    }

    /**
     * Returns the glsl es fragment source.
     *
     * @return the glsl es fragment source
     */
    public String glslEsFragmentSource() {
        return glslEsFragmentSource;
    }

    /**
     * Returns the SPIR-V vertex words.
     *
     * @return the SPIR-V vertex words
     */
    public int[] spirvVertexWords() {
        return cloneOrNull(spirvVertexWords);
    }

    /**
     * Returns the SPIR-V fragment words.
     *
     * @return the SPIR-V fragment words
     */
    public int[] spirvFragmentWords() {
        return cloneOrNull(spirvFragmentWords);
    }

    /**
     * Returns the msl source.
     *
     * @return the msl source
     */
    public String mslSource() {
        return mslSource;
    }

    /**
     * Returns the hlsl source.
     *
     * @return the hlsl source
     */
    public String hlslSource() {
        return hlslSource;
    }

    /**
     * Returns the reflection.
     *
     * @return the reflection
     */
    public ShaderReflection reflection() {
        return reflection;
    }

    /**
     * Runs the descriptor for provider step.
     *
     * @param providerId the provider ID
     * @return the descriptor for provider
     */
    public ShaderModuleDescriptor descriptorForProvider(ProviderId providerId) {
        return descriptorForTarget(ShaderTarget.forProvider(providerId));
    }

    /**
     * Runs the descriptor for provider step.
     *
     * @param providerId the provider ID
     * @return the descriptor for provider
     */
    public ShaderModuleDescriptor descriptorForProvider(String providerId) {
        return descriptorForTarget(ShaderTarget.forProvider(providerId));
    }

    /**
     * Runs the descriptor for target step.
     *
     * @param target the target value
     * @return the descriptor for target
     */
    public ShaderModuleDescriptor descriptorForTarget(ShaderTarget target) {
        if (target == null) {
            throw new FdxException("Shader target cannot be null");
        }
        switch (target) {
            case WEBGPU_WGSL:
            case WGPU_WGSL:
                if (wgslSource == null || wgslSource.length() == 0) {
                    throw missing(target, "WGSL");
                }
                return ShaderModuleDescriptor.wgsl(label, wgslSource);
            case WEBGL_GLSL_ES:
            case GLES_GLSL_ES:
                if (glslEsVertexSource != null && glslEsFragmentSource != null) {
                    return ShaderModuleDescriptor.glsl(label, glslEsVertexSource, glslEsFragmentSource);
                }
                if (glslVertexSource != null && glslFragmentSource != null) {
                    return ShaderModuleDescriptor.glsl(label, glslVertexSource, glslFragmentSource);
                }
                throw missing(target, "GLSL ES");
            case OPENGL_GLSL:
                if (glslVertexSource != null && glslFragmentSource != null) {
                    return ShaderModuleDescriptor.glsl(label, glslVertexSource, glslFragmentSource);
                }
                if (glslEsVertexSource != null && glslEsFragmentSource != null) {
                    return ShaderModuleDescriptor.glsl(label, glslEsVertexSource, glslEsFragmentSource);
                }
                throw missing(target, "GLSL");
            case VULKAN_SPIRV:
                if (spirvVertexWords == null || spirvFragmentWords == null) {
                    throw missing(target, "SPIR-V");
                }
                return ShaderModuleDescriptor.spirv(label, spirvVertexWords, spirvFragmentWords);
            case METAL_MSL:
                if (mslSource == null || mslSource.length() == 0) {
                    throw missing(target, "MSL");
                }
                return ShaderModuleDescriptor.msl(label, mslSource);
            case DIRECTX_HLSL:
                throw new FdxException("Shader target " + target + " is generated metadata only until a descriptor "
                        + "accepts that language");
            default:
                throw new FdxException("Unsupported shader target: " + target);
        }
    }

    /**
     * Returns the validate profile.
     *
     * @return the validate profile
     */
    public ShaderValidationResult validateProfile() {
        if (wgslSource == null || wgslSource.length() == 0) {
            return ShaderValidationResult.of(new ShaderValidationDiagnostic[] {
                    ShaderValidationDiagnostic.error("shader.bundle.wgsl-missing",
                            "Shader bundle " + label + " does not contain its WGSL source of truth")
            });
        }
        return ShaderProfileValidator.validateWgsl(profile, wgslSource);
    }

    /**
     * Returns whether this instance has target.
     *
     * @param target the target value
     * @return true if this instance has target; false otherwise
     */
    public boolean hasTarget(ShaderTarget target) {
        if (target == null) {
            return false;
        }
        switch (target) {
            case WEBGPU_WGSL:
            case WGPU_WGSL:
                return wgslSource != null && wgslSource.length() > 0;
            case WEBGL_GLSL_ES:
            case GLES_GLSL_ES:
                return (glslEsVertexSource != null && glslEsFragmentSource != null)
                        || (glslVertexSource != null && glslFragmentSource != null);
            case OPENGL_GLSL:
                return (glslVertexSource != null && glslFragmentSource != null)
                        || (glslEsVertexSource != null && glslEsFragmentSource != null);
            case VULKAN_SPIRV:
                return spirvVertexWords != null && spirvFragmentWords != null;
            case METAL_MSL:
                return mslSource != null && mslSource.length() > 0;
            case DIRECTX_HLSL:
                return hlslSource != null && hlslSource.length() > 0;
            default:
                return false;
        }
    }

    private FdxException missing(ShaderTarget target, String language) {
        return new FdxException("Shader bundle " + label + " does not contain " + language
                + " output for target " + target);
    }

    private static int[] cloneOrNull(int[] values) {
        return values != null ? values.clone() : null;
    }

    /**
     * Builds value instances and related output.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private final String label;
        private ShaderProfile profile = ShaderProfile.PORTABLE_WEBGPU;
        private String wgslSource;
        private String glslVertexSource;
        private String glslFragmentSource;
        private String glslEsVertexSource;
        private String glslEsFragmentSource;
        private int[] spirvVertexWords;
        private int[] spirvFragmentWords;
        private String mslSource;
        private String hlslSource;
        private ShaderReflection reflection = ShaderReflection.empty();

        private Builder(String label) {
            if (label == null || label.trim().isEmpty()) {
                throw new FdxException("Shader bundle label cannot be empty");
            }
            this.label = label;
        }

        /**
         * Sets the profile and returns this builder.
         *
         * @param profile the profile
         * @return this builder for chaining
         */
        public Builder profile(ShaderProfile profile) {
            this.profile = profile != null ? profile : ShaderProfile.PORTABLE_WEBGPU;
            return this;
        }

        /**
         * Sets the wgsl and returns this builder.
         *
         * @param source the source value
         * @return this builder for chaining
         */
        public Builder wgsl(String source) {
            wgslSource = requireSource(source, "WGSL shader source");
            return this;
        }

        /**
         * Sets the glsl and returns this builder.
         *
         * @param vertexSource the vertex source
         * @param fragmentSource the fragment source
         * @return this builder for chaining
         */
        public Builder glsl(String vertexSource, String fragmentSource) {
            glslVertexSource = requireSource(vertexSource, "GLSL vertex shader source");
            glslFragmentSource = requireSource(fragmentSource, "GLSL fragment shader source");
            return this;
        }

        /**
         * Sets the glsl es and returns this builder.
         *
         * @param vertexSource the vertex source
         * @param fragmentSource the fragment source
         * @return this builder for chaining
         */
        public Builder glslEs(String vertexSource, String fragmentSource) {
            glslEsVertexSource = requireSource(vertexSource, "GLSL ES vertex shader source");
            glslEsFragmentSource = requireSource(fragmentSource, "GLSL ES fragment shader source");
            return this;
        }

        /**
         * Sets the SPIR-V and returns this builder.
         *
         * @param vertexWords the vertex words
         * @param fragmentWords the fragment words
         * @return this builder for chaining
         */
        public Builder spirv(int[] vertexWords, int[] fragmentWords) {
            if (vertexWords == null || vertexWords.length == 0) {
                throw new FdxException("SPIR-V vertex shader words cannot be empty");
            }
            if (fragmentWords == null || fragmentWords.length == 0) {
                throw new FdxException("SPIR-V fragment shader words cannot be empty");
            }
            spirvVertexWords = vertexWords.clone();
            spirvFragmentWords = fragmentWords.clone();
            return this;
        }

        /**
         * Sets the msl and returns this builder.
         *
         * @param source the source value
         * @return this builder for chaining
         */
        public Builder msl(String source) {
            mslSource = requireSource(source, "MSL shader source");
            return this;
        }

        /**
         * Sets the hlsl and returns this builder.
         *
         * @param source the source value
         * @return this builder for chaining
         */
        public Builder hlsl(String source) {
            hlslSource = requireSource(source, "HLSL shader source");
            return this;
        }

        /**
         * Sets the reflection and returns this builder.
         *
         * @param reflection the reflection
         * @return this builder for chaining
         */
        public Builder reflection(ShaderReflection reflection) {
            this.reflection = reflection != null ? reflection : ShaderReflection.empty();
            return this;
        }

        /**
         * Returns the build.
         *
         * @return the created value
         */
        public ShaderBundle build() {
            ShaderBundle bundle = new ShaderBundle(this);
            bundle.validateProfile().throwIfFailed(label);
            return bundle;
        }

        private static String requireSource(String source, String name) {
            if (source == null || source.length() == 0) {
                throw new FdxException(name + " cannot be empty");
            }
            return source;
        }
    }
}
