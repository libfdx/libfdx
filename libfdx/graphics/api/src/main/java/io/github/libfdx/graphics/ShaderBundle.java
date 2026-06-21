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

    private ShaderBundle(Builder builder) {
        label = builder.label;
        profile = builder.profile;
        wgslSource = builder.wgslSource;
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
