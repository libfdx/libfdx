package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.shader.target.ShaderArtifactFormat;
import io.github.libfdx.graphics.shader.target.ShaderArtifactFormats;
import io.github.libfdx.graphics.shader.target.ShaderCompilerId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.target.ShaderTargetEnvironment;
import io.github.libfdx.graphics.shader.target.ShaderTargetEnvironments;
import io.github.libfdx.graphics.shader.target.ShaderTargetId;
import io.github.libfdx.graphics.shader.target.ShaderTargets;
import io.github.libfdx.graphics.shader.target.ShaderVerifierId;
import java.util.Objects;

/**
 * Immutable editor compilation/output selection. This data is editor state and
 * is never written into graph semantics.
 */
public final class ShaderGraphEditorCompileSettings {
    private final ShaderProfile profile;
    private final GraphicsCapabilities capabilities;
    private final ShaderTargetId target;
    private final ShaderArtifactFormat format;
    private final ShaderTargetEnvironment environment;
    private final ShaderCompilerId compiler;
    private final ShaderVerifierId verifier;
    private final ShaderGraphEditorPreviewMode previewMode;

    private ShaderGraphEditorCompileSettings(Builder builder) {
        profile = builder.profile != null ? builder.profile : ShaderProfile.PORTABLE_WEBGPU;
        capabilities = builder.capabilities;
        target = builder.target != null ? builder.target : ShaderTargets.WGPU_WGSL;
        format = builder.format != null ? builder.format : ShaderArtifactFormats.WGSL_TEXT;
        environment = builder.environment != null
                ? builder.environment : ShaderTargetEnvironments.forTarget(target);
        if (!environment.target().equals(target) || !environment.format().equals(format)) {
            throw new FdxException("Shader graph editor target environment does not match target/format");
        }
        compiler = builder.compiler;
        verifier = builder.verifier;
        previewMode = builder.previewMode != null ? builder.previewMode
                : ShaderGraphEditorPreviewMode.NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ShaderProfile profile() {
        return profile;
    }

    public GraphicsCapabilities capabilities() {
        return capabilities;
    }

    public ShaderTargetId target() {
        return target;
    }

    public ShaderArtifactFormat format() {
        return format;
    }

    public ShaderTargetEnvironment environment() {
        return environment;
    }

    public ShaderCompilerId compiler() {
        return compiler;
    }

    public ShaderVerifierId verifier() {
        return verifier;
    }

    public ShaderGraphEditorPreviewMode previewMode() {
        return previewMode;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphEditorCompileSettings other
                && profile == other.profile
                && Objects.equals(capabilities, other.capabilities)
                && target.equals(other.target)
                && format.equals(other.format)
                && environment.equals(other.environment)
                && Objects.equals(compiler, other.compiler)
                && Objects.equals(verifier, other.verifier)
                && previewMode == other.previewMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(profile, capabilities, target, format, environment,
                compiler, verifier, previewMode);
    }

    public static final class Builder {
        private ShaderProfile profile;
        private GraphicsCapabilities capabilities;
        private ShaderTargetId target;
        private ShaderArtifactFormat format;
        private ShaderTargetEnvironment environment;
        private ShaderCompilerId compiler;
        private ShaderVerifierId verifier;
        private ShaderGraphEditorPreviewMode previewMode;

        private Builder() {
        }

        public Builder profile(ShaderProfile value) {
            profile = value;
            return this;
        }

        public Builder capabilities(GraphicsCapabilities value) {
            capabilities = value;
            return this;
        }

        public Builder output(ShaderTargetId target, ShaderArtifactFormat format,
                ShaderTargetEnvironment environment) {
            this.target = target;
            this.format = format;
            this.environment = environment;
            return this;
        }

        public Builder compiler(ShaderCompilerId value) {
            compiler = value;
            return this;
        }

        public Builder verifier(ShaderVerifierId value) {
            verifier = value;
            return this;
        }

        public Builder previewMode(ShaderGraphEditorPreviewMode value) {
            previewMode = value;
            return this;
        }

        public ShaderGraphEditorCompileSettings build() {
            return new ShaderGraphEditorCompileSettings(this);
        }
    }
}
