package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLibrary;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;

/**
 * Immutable profile/capability context for semantic graph compilation.
 */
public final class ShaderGraphCompileOptions {
    private final ShaderProfile profile;
    private final GraphicsCapabilities capabilities;
    private final ShaderStage stage;
    private final ShaderGraphLibrary library;

    private ShaderGraphCompileOptions(Builder builder) {
        profile = builder.profile != null
                ? builder.profile : ShaderProfile.PORTABLE_WEBGPU;
        capabilities = builder.capabilities;
        stage = builder.stage;
        library = builder.library != null
                ? builder.library : ShaderGraphLibrary.empty();
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

    public ShaderStage stage() {
        return stage;
    }

    public ShaderGraphLibrary library() {
        return library;
    }

    /**
     * Mutable construction scope.
     */
    public static final class Builder {
        private ShaderProfile profile;
        private GraphicsCapabilities capabilities;
        private ShaderStage stage;
        private ShaderGraphLibrary library;

        public Builder profile(ShaderProfile value) {
            profile = value;
            return this;
        }

        public Builder capabilities(GraphicsCapabilities value) {
            capabilities = value;
            return this;
        }

        public Builder stage(ShaderStage value) {
            stage = value;
            return this;
        }

        public Builder library(ShaderGraphLibrary value) {
            library = value;
            return this;
        }

        public ShaderGraphCompileOptions build() {
            return new ShaderGraphCompileOptions(this);
        }
    }
}
