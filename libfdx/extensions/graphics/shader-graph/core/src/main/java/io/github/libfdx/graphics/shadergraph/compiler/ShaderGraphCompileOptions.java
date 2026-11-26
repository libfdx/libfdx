package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLibrary;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;

/**
 * Immutable profile/capability context for semantic graph compilation.
 */
public final class ShaderGraphCompileOptions {
    private static final ShaderGraphCompileOptions PORTABLE =
            new ShaderGraphCompileOptions(null, null, null, null);
    private final ShaderProfile profile;
    private final GraphicsCapabilities capabilities;
    private final ShaderStage stage;
    private final ShaderGraphLibrary library;

    private ShaderGraphCompileOptions(ShaderProfile profile, GraphicsCapabilities capabilities,
            ShaderStage stage, ShaderGraphLibrary library) {
        this.profile = profile;
        this.capabilities = capabilities;
        this.stage = stage;
        this.library = library != null ? library : ShaderGraphLibrary.empty();
    }

    /** Default options for compiling a graph without a device, target profile, or external library. */
    public static ShaderGraphCompileOptions portable() {
        return PORTABLE;
    }

    /** Compiles canonical WGSL using the supplied graph library, or an empty library if null. */
    public static ShaderGraphCompileOptions portable(ShaderGraphLibrary library) {
        return new ShaderGraphCompileOptions(null, null, null, library);
    }

    ShaderGraphCompileOptions withStage(ShaderStage stage) {
        return new ShaderGraphCompileOptions(profile, capabilities, stage, library);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Returns the requested target profile, or null for portable compilation. */
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
            return new ShaderGraphCompileOptions(profile != null ? profile : ShaderProfile.PORTABLE_WEBGPU,
                    capabilities, stage, library);
        }
    }
}
