package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.graphics.internal.ShaderStableId;

import java.util.Arrays;

/**
 * Immutable request for translating canonical WGSL to one exact target.
 *
 * @author xpenatan
 */
public final class ShaderTargetCompileRequest {
    private final String label;
    private final String wgsl;
    private final ShaderReflection shaderInterface;
    private final ShaderTargetId target;
    private final ShaderArtifactFormat format;
    private final ShaderTargetEnvironment environment;
    private final ShaderProfile profile;
    private final ShaderEntryPointSelection[] entryPoints;
    private final ShaderTargetOptions options;
    private final String[] requiredCapabilities;
    private final String dependencyHash;
    private final String sourceMapHash;
    private final ShaderOptimizationLevel optimization;
    private final boolean debug;
    private final ShaderCompilerId compiler;
    private final ShaderVerifierId verifier;
    private final ShaderVerificationRequirement verificationRequirement;
    private final String wgslHash;
    private final String cacheKey;

    private ShaderTargetCompileRequest(Builder builder) {
        label = builder.label != null ? builder.label : "";
        if (builder.wgsl == null || builder.wgsl.length() == 0) {
            throw new FdxException("Shader target compile request WGSL cannot be empty");
        }
        if (builder.target == null || builder.format == null || builder.environment == null) {
            throw new FdxException("Shader target compile request target, format, and environment cannot be null");
        }
        if (!builder.environment.target().equals(builder.target)
                || !builder.environment.format().equals(builder.format)) {
            throw new FdxException("Shader target compile request environment does not match target/format");
        }
        wgsl = builder.wgsl;
        shaderInterface = builder.shaderInterface != null ? builder.shaderInterface : ShaderReflection.empty();
        target = builder.target;
        format = builder.format;
        environment = builder.environment;
        profile = builder.profile != null ? builder.profile : shaderInterface.profile();
        entryPoints = builder.entryPoints != null
                ? builder.entryPoints.clone() : new ShaderEntryPointSelection[0];
        for (int i = 0; i < entryPoints.length; i++) {
            if (entryPoints[i] == null) {
                throw new FdxException("Shader target entry-point selection cannot be null");
            }
        }
        Arrays.sort(entryPoints);
        for (int i = 0; i < entryPoints.length; i++) {
            if (i > 0 && entryPoints[i - 1].compareTo(entryPoints[i]) == 0) {
                throw new FdxException("Duplicate shader target entry-point selection: "
                        + entryPoints[i].entryPoint());
            }
        }
        options = builder.options != null ? builder.options : ShaderTargetOptions.empty();
        requiredCapabilities = normalizeCapabilities(builder.requiredCapabilities);
        dependencyHash = valueOrEmpty(builder.dependencyHash);
        sourceMapHash = valueOrEmpty(builder.sourceMapHash);
        optimization = builder.optimization != null ? builder.optimization : ShaderOptimizationLevel.PERFORMANCE;
        debug = builder.debug;
        compiler = builder.compiler;
        verifier = builder.verifier;
        verificationRequirement = builder.verificationRequirement != null
                ? builder.verificationRequirement : ShaderVerificationRequirement.PROVIDER_PIPELINE;
        wgslHash = PortableSha256.hashUtf8(wgsl);
        cacheKey = computeCacheKey();
    }

    /**
     * Creates a builder.
     *
     * @param label the label
     * @param wgsl the canonical WGSL
     * @param target the target
     * @param format the artifact format
     * @param environment the exact consumer environment
     * @return the builder
     */
    public static Builder builder(String label, String wgsl, ShaderTargetId target,
            ShaderArtifactFormat format, ShaderTargetEnvironment environment) {
        return new Builder(label, wgsl, target, format, environment);
    }

    public String label() {
        return label;
    }

    public String wgsl() {
        return wgsl;
    }

    public ShaderReflection shaderInterface() {
        return shaderInterface;
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

    public ShaderProfile profile() {
        return profile;
    }

    public ShaderEntryPointSelection[] entryPoints() {
        return entryPoints.clone();
    }

    public ShaderTargetOptions options() {
        return options;
    }

    public String[] requiredCapabilities() {
        return requiredCapabilities.clone();
    }

    public String dependencyHash() {
        return dependencyHash;
    }

    public String sourceMapHash() {
        return sourceMapHash;
    }

    public ShaderOptimizationLevel optimization() {
        return optimization;
    }

    public boolean debug() {
        return debug;
    }

    public ShaderCompilerId compiler() {
        return compiler;
    }

    public ShaderVerifierId verifier() {
        return verifier;
    }

    public ShaderVerificationRequirement verificationRequirement() {
        return verificationRequirement;
    }

    public String wgslHash() {
        return wgslHash;
    }

    /**
     * Returns the compiler-independent request cache key.
     *
     * @return the key
     */
    public String cacheKey() {
        return cacheKey;
    }

    private String computeCacheKey() {
        PortableSha256 digest = new PortableSha256().updateSizedUtf8("fdx-shader-target-request-v1")
                .updateSizedUtf8(wgslHash)
                .updateSizedUtf8(shaderInterface.fullHash())
                .updateSizedUtf8(target.value())
                .updateSizedUtf8(format.id())
                .updateSizedUtf8(environment.cacheKey())
                .updateSizedUtf8(profile.id())
                .updateSizedUtf8(options.hash())
                .updateSizedUtf8(dependencyHash)
                .updateSizedUtf8(sourceMapHash)
                .updateSizedUtf8(optimization.name())
                .updateByte(debug ? 1 : 0)
                .updateInt(entryPoints.length);
        for (ShaderEntryPointSelection entryPoint : entryPoints) {
            digest.updateSizedUtf8(entryPoint.stage().name()).updateSizedUtf8(entryPoint.entryPoint());
        }
        digest.updateInt(requiredCapabilities.length);
        for (String capability : requiredCapabilities) {
            digest.updateSizedUtf8(capability);
        }
        return digest.digestHex();
    }

    private static String[] normalizeCapabilities(String[] capabilities) {
        if (capabilities == null || capabilities.length == 0) {
            return new String[0];
        }
        String[] normalized = new String[capabilities.length];
        for (int i = 0; i < capabilities.length; i++) {
            normalized[i] = ShaderStableId.normalize(capabilities[i], "Shader capability");
        }
        Arrays.sort(normalized);
        for (int i = 1; i < normalized.length; i++) {
            if (normalized[i - 1].equals(normalized[i])) {
                throw new FdxException("Duplicate shader capability: " + normalized[i]);
            }
        }
        return normalized;
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value.trim() : "";
    }

    /**
     * Builds target compile requests.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private final String label;
        private final String wgsl;
        private final ShaderTargetId target;
        private final ShaderArtifactFormat format;
        private final ShaderTargetEnvironment environment;
        private ShaderReflection shaderInterface;
        private ShaderProfile profile;
        private ShaderEntryPointSelection[] entryPoints;
        private ShaderTargetOptions options;
        private String[] requiredCapabilities;
        private String dependencyHash;
        private String sourceMapHash;
        private ShaderOptimizationLevel optimization;
        private boolean debug;
        private ShaderCompilerId compiler;
        private ShaderVerifierId verifier;
        private ShaderVerificationRequirement verificationRequirement;

        private Builder(String label, String wgsl, ShaderTargetId target,
                ShaderArtifactFormat format, ShaderTargetEnvironment environment) {
            this.label = label;
            this.wgsl = wgsl;
            this.target = target;
            this.format = format;
            this.environment = environment;
        }

        public Builder shaderInterface(ShaderReflection shaderInterface) {
            this.shaderInterface = shaderInterface;
            return this;
        }

        public Builder profile(ShaderProfile profile) {
            this.profile = profile;
            return this;
        }

        public Builder entryPoints(ShaderEntryPointSelection... entryPoints) {
            this.entryPoints = entryPoints;
            return this;
        }

        public Builder options(ShaderTargetOptions options) {
            this.options = options;
            return this;
        }

        public Builder requiredCapabilities(String... capabilities) {
            requiredCapabilities = capabilities;
            return this;
        }

        public Builder dependencyHash(String dependencyHash) {
            this.dependencyHash = dependencyHash;
            return this;
        }

        public Builder sourceMapHash(String sourceMapHash) {
            this.sourceMapHash = sourceMapHash;
            return this;
        }

        public Builder optimization(ShaderOptimizationLevel optimization) {
            this.optimization = optimization;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder compiler(ShaderCompilerId compiler) {
            this.compiler = compiler;
            return this;
        }

        public Builder verifier(ShaderVerifierId verifier) {
            this.verifier = verifier;
            return this;
        }

        public Builder verification(ShaderVerificationRequirement requirement) {
            verificationRequirement = requirement;
            return this;
        }

        public ShaderTargetCompileRequest build() {
            return new ShaderTargetCompileRequest(this);
        }
    }
}
