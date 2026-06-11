package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;

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

    public static Builder builder(String label) {
        return new Builder(label);
    }

    public String label() {
        return label;
    }

    public ShaderProfile profile() {
        return profile;
    }

    public String wgslSource() {
        return wgslSource;
    }

    public String glslVertexSource() {
        return glslVertexSource;
    }

    public String glslFragmentSource() {
        return glslFragmentSource;
    }

    public String glslEsVertexSource() {
        return glslEsVertexSource;
    }

    public String glslEsFragmentSource() {
        return glslEsFragmentSource;
    }

    public int[] spirvVertexWords() {
        return cloneOrNull(spirvVertexWords);
    }

    public int[] spirvFragmentWords() {
        return cloneOrNull(spirvFragmentWords);
    }

    public String mslSource() {
        return mslSource;
    }

    public String hlslSource() {
        return hlslSource;
    }

    public ShaderReflection reflection() {
        return reflection;
    }

    public ShaderModuleDescriptor descriptorForProvider(ProviderId providerId) {
        return descriptorForTarget(ShaderTarget.forProvider(providerId));
    }

    public ShaderModuleDescriptor descriptorForProvider(String providerId) {
        return descriptorForTarget(ShaderTarget.forProvider(providerId));
    }

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
            case DIRECTX_HLSL:
                throw new FdxException("Shader target " + target + " is generated metadata only until a provider "
                        + "accepts that language");
            default:
                throw new FdxException("Unsupported shader target: " + target);
        }
    }

    public ShaderValidationResult validateProfile() {
        if (wgslSource == null || wgslSource.length() == 0) {
            return ShaderValidationResult.of(new ShaderValidationDiagnostic[] {
                    ShaderValidationDiagnostic.error("shader.bundle.wgsl-missing",
                            "Shader bundle " + label + " does not contain its WGSL source of truth")
            });
        }
        return ShaderProfileValidator.validateWgsl(profile, wgslSource);
    }

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

        public Builder profile(ShaderProfile profile) {
            this.profile = profile != null ? profile : ShaderProfile.PORTABLE_WEBGPU;
            return this;
        }

        public Builder wgsl(String source) {
            wgslSource = requireSource(source, "WGSL shader source");
            return this;
        }

        public Builder glsl(String vertexSource, String fragmentSource) {
            glslVertexSource = requireSource(vertexSource, "GLSL vertex shader source");
            glslFragmentSource = requireSource(fragmentSource, "GLSL fragment shader source");
            return this;
        }

        public Builder glslEs(String vertexSource, String fragmentSource) {
            glslEsVertexSource = requireSource(vertexSource, "GLSL ES vertex shader source");
            glslEsFragmentSource = requireSource(fragmentSource, "GLSL ES fragment shader source");
            return this;
        }

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

        public Builder msl(String source) {
            mslSource = requireSource(source, "MSL shader source");
            return this;
        }

        public Builder hlsl(String source) {
            hlslSource = requireSource(source, "HLSL shader source");
            return this;
        }

        public Builder reflection(ShaderReflection reflection) {
            this.reflection = reflection != null ? reflection : ShaderReflection.empty();
            return this;
        }

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
