package io.github.libfdx.runtime.core.shader;

import io.github.libfdx.core.FdxException;

/**
 * Describes one runtime shader compiler request.
 *
 * @author xpenatan
 */
public final class RuntimeShaderCompileRequest {
    private final String source;
    private final RuntimeShaderCompileTarget target;
    private final RuntimeShaderCompileStage stage;
    private final String entryPoint;
    private final String glslProfile;
    private final String glslEsProfile;

    private RuntimeShaderCompileRequest(Builder builder) {
        if (builder.source == null || builder.source.length() == 0) {
            throw new FdxException("Runtime shader compiler source cannot be empty");
        }
        source = builder.source;
        target = builder.target != null ? builder.target : RuntimeShaderCompileTarget.WEBGPU_WGSL;
        stage = builder.stage != null ? builder.stage : RuntimeShaderCompileStage.MODULE;
        entryPoint = builder.entryPoint != null ? builder.entryPoint : "";
        glslProfile = builder.glslProfile != null ? builder.glslProfile : "330";
        glslEsProfile = builder.glslEsProfile != null ? builder.glslEsProfile : "300";
    }

    /**
     * Creates a builder.
     *
     * @param source the WGSL source
     * @param target the target
     * @return a new builder
     */
    public static Builder builder(String source, RuntimeShaderCompileTarget target) {
        return new Builder().source(source).target(target);
    }

    /**
     * Returns the source.
     *
     * @return the source
     */
    public String source() {
        return source;
    }

    /**
     * Returns the target.
     *
     * @return the target
     */
    public RuntimeShaderCompileTarget target() {
        return target;
    }

    /**
     * Returns the stage.
     *
     * @return the stage
     */
    public RuntimeShaderCompileStage stage() {
        return stage;
    }

    /**
     * Returns the entry point.
     *
     * @return the entry point
     */
    public String entryPoint() {
        return entryPoint;
    }

    /**
     * Returns the GLSL profile.
     *
     * @return the GLSL profile
     */
    public String glslProfile() {
        return glslProfile;
    }

    /**
     * Returns the GLSL ES profile.
     *
     * @return the GLSL ES profile
     */
    public String glslEsProfile() {
        return glslEsProfile;
    }

    /**
     * Builds request values.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private String source;
        private RuntimeShaderCompileTarget target;
        private RuntimeShaderCompileStage stage;
        private String entryPoint;
        private String glslProfile;
        private String glslEsProfile;

        private Builder() {
        }

        /**
         * Sets the source.
         *
         * @param source the source
         * @return this builder
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * Sets the target.
         *
         * @param target the target
         * @return this builder
         */
        public Builder target(RuntimeShaderCompileTarget target) {
            this.target = target;
            return this;
        }

        /**
         * Sets the stage.
         *
         * @param stage the stage
         * @return this builder
         */
        public Builder stage(RuntimeShaderCompileStage stage) {
            this.stage = stage;
            return this;
        }

        /**
         * Sets the entry point.
         *
         * @param entryPoint the entry point
         * @return this builder
         */
        public Builder entryPoint(String entryPoint) {
            this.entryPoint = entryPoint;
            return this;
        }

        /**
         * Sets the GLSL profile.
         *
         * @param glslProfile the GLSL profile
         * @return this builder
         */
        public Builder glslProfile(String glslProfile) {
            this.glslProfile = glslProfile;
            return this;
        }

        /**
         * Sets the GLSL ES profile.
         *
         * @param glslEsProfile the GLSL ES profile
         * @return this builder
         */
        public Builder glslEsProfile(String glslEsProfile) {
            this.glslEsProfile = glslEsProfile;
            return this;
        }

        /**
         * Builds a request.
         *
         * @return a new request
         */
        public RuntimeShaderCompileRequest build() {
            return new RuntimeShaderCompileRequest(this);
        }
    }
}
