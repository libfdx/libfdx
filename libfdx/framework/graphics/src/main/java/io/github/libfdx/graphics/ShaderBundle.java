package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;

/**
 * Represents a WGSL shader source bundle.
 *
 * @author xpenatan
 */
public final class ShaderBundle {
    private final String label;
    private final ShaderProfile profile;
    private final String wgslSource;
    private final ShaderReflection reflection;
    private final GeneratedTarget[] generatedTargets;

    private ShaderBundle(Builder builder) {
        label = builder.label;
        profile = builder.profile;
        wgslSource = builder.wgslSource;
        reflection = builder.reflection != null ? builder.reflection : ShaderReflection.empty();
        generatedTargets = builder.generatedTargets.clone();
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
        GeneratedTarget generatedTarget = generatedTargets[target.ordinal()];
        if (generatedTarget != null) {
            return generatedTarget.descriptor(label, wgslSource);
        }
        return ShaderModuleDescriptor.wgsl(label, wgslSource);
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
     * Returns whether this instance can provide its WGSL source for the target.
     *
     * @param target the target value
     * @return true if the bundle has WGSL and the target is non-null; false otherwise
     */
    public boolean hasTarget(ShaderTarget target) {
        return target != null && wgslSource != null && wgslSource.length() > 0;
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
        private ShaderReflection reflection = ShaderReflection.empty();
        private final GeneratedTarget[] generatedTargets = new GeneratedTarget[ShaderTarget.values().length];

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
         * Adds generated GLSL output for a GL-family target.
         *
         * @param target the OpenGL, GLES, or WebGL target
         * @param vertexSource the generated vertex shader source
         * @param fragmentSource the generated fragment shader source
         * @return this builder for chaining
         */
        public Builder generatedGlsl(ShaderTarget target, String vertexSource, String fragmentSource) {
            if (target != ShaderTarget.OPENGL_GLSL
                    && target != ShaderTarget.GLES_GLSL_ES
                    && target != ShaderTarget.WEBGL_GLSL_ES) {
                throw new FdxException("Generated GLSL requires an OpenGL, GLES, or WebGL shader target");
            }
            generatedTargets[target.ordinal()] = GeneratedTarget.glsl(
                    requireSource(vertexSource, "GLSL vertex shader source"),
                    requireSource(fragmentSource, "GLSL fragment shader source"));
            return this;
        }

        /**
         * Adds generated SPIR-V output for Vulkan.
         *
         * @param vertexWords the generated vertex shader words
         * @param fragmentWords the generated fragment shader words
         * @return this builder for chaining
         */
        public Builder generatedSpirv(int[] vertexWords, int[] fragmentWords) {
            generatedTargets[ShaderTarget.VULKAN_SPIRV.ordinal()] = GeneratedTarget.spirv(
                    requireWords(vertexWords, "SPIR-V vertex shader words"),
                    requireWords(fragmentWords, "SPIR-V fragment shader words"));
            return this;
        }

        /**
         * Adds generated MSL output for Metal.
         *
         * @param source the generated MSL source
         * @return this builder for chaining
         */
        public Builder generatedMsl(String source) {
            generatedTargets[ShaderTarget.METAL_MSL.ordinal()] = GeneratedTarget.msl(
                    requireSource(source, "MSL shader source"));
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

        private static int[] requireWords(int[] words, String name) {
            if (words == null || words.length == 0) {
                throw new FdxException(name + " cannot be empty");
            }
            return words;
        }
    }

    private static final class GeneratedTarget {
        private final ShaderLanguage language;
        private final String vertexSource;
        private final String fragmentSource;
        private final int[] vertexWords;
        private final int[] fragmentWords;

        private GeneratedTarget(ShaderLanguage language, String vertexSource, String fragmentSource,
                int[] vertexWords, int[] fragmentWords) {
            this.language = language;
            this.vertexSource = vertexSource;
            this.fragmentSource = fragmentSource;
            this.vertexWords = vertexWords != null ? vertexWords.clone() : null;
            this.fragmentWords = fragmentWords != null ? fragmentWords.clone() : null;
        }

        private static GeneratedTarget glsl(String vertexSource, String fragmentSource) {
            return new GeneratedTarget(ShaderLanguage.GLSL, vertexSource, fragmentSource, null, null);
        }

        private static GeneratedTarget spirv(int[] vertexWords, int[] fragmentWords) {
            return new GeneratedTarget(ShaderLanguage.SPIRV, null, null, vertexWords, fragmentWords);
        }

        private static GeneratedTarget msl(String source) {
            return new GeneratedTarget(ShaderLanguage.MSL, source, null, null, null);
        }

        private ShaderModuleDescriptor descriptor(String label, String wgslSource) {
            ShaderModuleDescriptor descriptor;
            if (language == ShaderLanguage.GLSL) {
                descriptor = ShaderModuleDescriptor.generatedGlsl(label, vertexSource, fragmentSource);
            } else if (language == ShaderLanguage.SPIRV) {
                descriptor = ShaderModuleDescriptor.generatedSpirv(label, vertexWords, fragmentWords);
            } else if (language == ShaderLanguage.MSL) {
                descriptor = ShaderModuleDescriptor.generatedMsl(label, vertexSource);
            } else {
                throw new FdxException("Unsupported generated shader language: " + language);
            }
            return descriptor.wgsl(wgslSource);
        }
    }
}
