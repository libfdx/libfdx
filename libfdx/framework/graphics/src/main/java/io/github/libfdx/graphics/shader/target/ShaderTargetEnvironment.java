package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.graphics.internal.ShaderStableId;

/**
 * Exact environment that consumes a translated shader artifact.
 *
 * <p>The environment distinguishes consumer API versions and native compiler
 * families such as FXC and DXC. A language name alone is not a sufficient
 * verification boundary.</p>
 *
 * @author xpenatan
 */
public final class ShaderTargetEnvironment implements Comparable<ShaderTargetEnvironment> {
    private final String id;
    private final ShaderTargetId target;
    private final ShaderArtifactFormat format;
    private final String consumer;
    private final String consumerVersion;
    private final String compilerFamily;
    private final String shaderModel;
    private final ShaderTargetOptions options;
    private final String cacheKey;

    private ShaderTargetEnvironment(Builder builder) {
        id = ShaderStableId.normalize(builder.id, "Shader target environment");
        if (builder.target == null) {
            throw new FdxException("Shader target environment target cannot be null");
        }
        if (builder.format == null) {
            throw new FdxException("Shader target environment format cannot be null");
        }
        target = builder.target;
        format = builder.format;
        consumer = ShaderStableId.requireValue(builder.consumer, "Shader target environment consumer");
        consumerVersion = valueOrEmpty(builder.consumerVersion);
        compilerFamily = valueOrEmpty(builder.compilerFamily);
        shaderModel = valueOrEmpty(builder.shaderModel);
        options = builder.options != null ? builder.options : ShaderTargetOptions.empty();
        cacheKey = new PortableSha256().updateSizedUtf8("fdx-shader-environment-v1")
                .updateSizedUtf8(id)
                .updateSizedUtf8(target.value())
                .updateSizedUtf8(format.id())
                .updateSizedUtf8(format.encoding().name())
                .updateSizedUtf8(consumer)
                .updateSizedUtf8(consumerVersion)
                .updateSizedUtf8(compilerFamily)
                .updateSizedUtf8(shaderModel)
                .updateSizedUtf8(options.hash())
                .digestHex();
    }

    /**
     * Creates a builder.
     *
     * @param id the stable environment ID
     * @param target the target
     * @param format the artifact format
     * @return the builder
     */
    public static Builder builder(String id, ShaderTargetId target, ShaderArtifactFormat format) {
        return new Builder(id, target, format);
    }

    /**
     * Returns the environment ID.
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * Returns the target.
     *
     * @return the target
     */
    public ShaderTargetId target() {
        return target;
    }

    /**
     * Returns the artifact format.
     *
     * @return the format
     */
    public ShaderArtifactFormat format() {
        return format;
    }

    /**
     * Returns the consuming API or runtime family.
     *
     * @return the consumer
     */
    public String consumer() {
        return consumer;
    }

    /**
     * Returns the consuming API/runtime version.
     *
     * @return the version, possibly empty
     */
    public String consumerVersion() {
        return consumerVersion;
    }

    /**
     * Returns the native compiler family.
     *
     * @return the compiler family, possibly empty
     */
    public String compilerFamily() {
        return compilerFamily;
    }

    /**
     * Returns the shader model or dialect profile.
     *
     * @return the shader model, possibly empty
     */
    public String shaderModel() {
        return shaderModel;
    }

    /**
     * Returns target environment options.
     *
     * @return the options
     */
    public ShaderTargetOptions options() {
        return options;
    }

    /**
     * Returns the deterministic environment cache key.
     *
     * @return the key
     */
    public String cacheKey() {
        return cacheKey;
    }

    @Override
    public int compareTo(ShaderTargetEnvironment other) {
        return other != null ? id.compareTo(other.id) : 1;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof ShaderTargetEnvironment)) {
            return false;
        }
        ShaderTargetEnvironment other = (ShaderTargetEnvironment)object;
        return id.equals(other.id) && cacheKey.equals(other.cacheKey);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + cacheKey.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value.trim() : "";
    }

    /**
     * Builds an exact target environment.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private final String id;
        private final ShaderTargetId target;
        private final ShaderArtifactFormat format;
        private String consumer;
        private String consumerVersion;
        private String compilerFamily;
        private String shaderModel;
        private ShaderTargetOptions options;

        private Builder(String id, ShaderTargetId target, ShaderArtifactFormat format) {
            this.id = id;
            this.target = target;
            this.format = format;
        }

        /**
         * Sets the consumer family and version.
         *
         * @param consumer the consumer
         * @param version the version
         * @return this builder
         */
        public Builder consumer(String consumer, String version) {
            this.consumer = consumer;
            consumerVersion = version;
            return this;
        }

        /**
         * Sets the compiler family and shader model.
         *
         * @param family the compiler family
         * @param model the shader model/profile
         * @return this builder
         */
        public Builder compiler(String family, String model) {
            compilerFamily = family;
            shaderModel = model;
            return this;
        }

        /**
         * Sets environment-specific options.
         *
         * @param options the options
         * @return this builder
         */
        public Builder options(ShaderTargetOptions options) {
            this.options = options;
            return this;
        }

        /**
         * Builds the environment.
         *
         * @return the environment
         */
        public ShaderTargetEnvironment build() {
            return new ShaderTargetEnvironment(this);
        }
    }
}
