package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;

import java.util.Arrays;

/**
 * Immutable provider-ready artifact for one exact target environment.
 *
 * @author xpenatan
 */
public final class ShaderTargetArtifact implements Comparable<ShaderTargetArtifact> {
    private final ShaderTargetId target;
    private final ShaderArtifactFormat format;
    private final ShaderTargetEnvironment environment;
    private final ShaderStageArtifact[] stages;
    private final ShaderTranslatedInterface translatedInterface;
    private final ShaderCompilerId compiler;
    private final String compilerVersion;
    private final String compileCacheKey;
    private final ShaderTargetVerification verification;

    private ShaderTargetArtifact(ShaderTargetId target, ShaderArtifactFormat format,
            ShaderTargetEnvironment environment, ShaderStageArtifact[] stages,
            ShaderTranslatedInterface translatedInterface, ShaderCompilerId compiler,
            String compilerVersion, String compileCacheKey, ShaderTargetVerification verification) {
        if (target == null || format == null || environment == null || translatedInterface == null
                || compiler == null) {
            throw new FdxException("Shader target artifact identity and interface cannot be null");
        }
        if (!environment.target().equals(target) || !environment.format().equals(format)) {
            throw new FdxException("Shader target artifact environment does not match target/format");
        }
        if (stages == null || stages.length == 0) {
            throw new FdxException("Shader target artifact must contain at least one stage/module payload");
        }
        this.target = target;
        this.format = format;
        this.environment = environment;
        this.stages = stages.clone();
        for (ShaderStageArtifact stage : this.stages) {
            if (stage == null) {
                throw new FdxException("Shader target stage artifact cannot be null");
            }
        }
        Arrays.sort(this.stages);
        for (int i = 0; i < this.stages.length; i++) {
            ShaderStageArtifact stage = this.stages[i];
            if (!format.equals(stage.format())) {
                throw new FdxException("Shader target stage artifact has a mismatched format");
            }
            if (i > 0 && this.stages[i - 1].compareTo(stage) == 0) {
                throw new FdxException("Duplicate shader target stage artifact: "
                        + stage.stage() + " " + stage.entryPoint());
            }
        }
        this.translatedInterface = translatedInterface;
        this.compiler = compiler;
        this.compilerVersion = compilerVersion != null ? compilerVersion : "";
        this.compileCacheKey = compileCacheKey != null && compileCacheKey.length() > 0
                ? compileCacheKey : computePayloadHash();
        this.verification = verification;
        if (verification != null && !verification.environment().equals(environment)) {
            throw new FdxException("Shader artifact verification environment does not match the artifact");
        }
    }

    /**
     * Creates an unverified compiler output. A registry attaches verification
     * metadata before returning it to a consumer.
     *
     * @param target the target
     * @param format the format
     * @param environment the environment
     * @param stages the stage artifacts
     * @param translatedInterface the translated interface
     * @param compiler the compiler ID
     * @param compilerVersion the compiler version
     * @param compileCacheKey the compile cache key
     * @return the artifact
     */
    public static ShaderTargetArtifact compiled(ShaderTargetId target, ShaderArtifactFormat format,
            ShaderTargetEnvironment environment, ShaderStageArtifact[] stages,
            ShaderTranslatedInterface translatedInterface, ShaderCompilerId compiler,
            String compilerVersion, String compileCacheKey) {
        return new ShaderTargetArtifact(target, format, environment, stages, translatedInterface,
                compiler, compilerVersion, compileCacheKey, null);
    }

    /**
     * Returns a copy with verification metadata.
     *
     * @param verification the verification
     * @return the verified or provider-gated artifact
     */
    public ShaderTargetArtifact withVerification(ShaderTargetVerification verification) {
        return new ShaderTargetArtifact(target, format, environment, stages, translatedInterface,
                compiler, compilerVersion, compileCacheKey, verification);
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

    public ShaderStageArtifact[] stages() {
        return stages.clone();
    }

    public ShaderStageArtifact find(ShaderArtifactStage stage, String entryPoint) {
        String name = entryPoint != null ? entryPoint : "";
        for (ShaderStageArtifact artifact : stages) {
            if (artifact.stage() == stage && artifact.entryPoint().equals(name)) {
                return artifact;
            }
        }
        return null;
    }

    public ShaderTranslatedInterface translatedInterface() {
        return translatedInterface;
    }

    public ShaderCompilerId compiler() {
        return compiler;
    }

    public String compilerVersion() {
        return compilerVersion;
    }

    public String compileCacheKey() {
        return compileCacheKey;
    }

    public ShaderTargetVerification verification() {
        return verification;
    }

    public boolean verified() {
        return verification != null && verification.verified();
    }

    /**
     * Returns the complete artifact cache key, including verification metadata.
     *
     * @return the key
     */
    public String cacheKey() {
        return verification != null ? verification.cacheKey() : compileCacheKey;
    }

    @Override
    public int compareTo(ShaderTargetArtifact other) {
        if (other == null) {
            return 1;
        }
        int comparison = target.compareTo(other.target);
        if (comparison != 0) {
            return comparison;
        }
        comparison = format.compareTo(other.format);
        return comparison != 0 ? comparison : environment.compareTo(other.environment);
    }

    private String computePayloadHash() {
        PortableSha256 digest = new PortableSha256().updateSizedUtf8("fdx-shader-target-artifact-v2")
                .updateSizedUtf8(target.value())
                .updateSizedUtf8(format.id())
                .updateSizedUtf8(environment.cacheKey())
                .updateSizedUtf8(compiler.value())
                .updateSizedUtf8(compilerVersion)
                .updateSizedUtf8(translatedInterface.canonical().fullHash())
                .updateSizedUtf8(translatedInterface.target().fullHash())
                .updateInt(stages.length);
        for (ShaderStageArtifact stage : stages) {
            digest.updateSizedUtf8(stage.stage().name())
                    .updateSizedUtf8(stage.entryPoint())
                    .update(stage.payload());
        }
        ShaderEntryPointRemap[] entryPoints = translatedInterface.entryPoints();
        digest.updateInt(entryPoints.length);
        for (ShaderEntryPointRemap entryPoint : entryPoints) {
            digest.updateSizedUtf8(entryPoint.stage().name())
                    .updateSizedUtf8(entryPoint.sourceName())
                    .updateSizedUtf8(entryPoint.targetName());
        }
        ShaderBindingRemap[] bindings = translatedInterface.bindings();
        digest.updateInt(bindings.length);
        for (ShaderBindingRemap binding : bindings) {
            digest.updateSizedUtf8(binding.stage().name())
                    .updateSizedUtf8(binding.sourceEntryPoint())
                    .updateInt(binding.sourceGroup())
                    .updateInt(binding.sourceBinding())
                    .updateSizedUtf8(binding.kind().name())
                    .updateInt(binding.targetCount());
            for (ShaderTargetBinding targetBinding : binding.targets()) {
                digest.updateSizedUtf8(targetBinding.namespace())
                        .updateInt(targetBinding.group())
                        .updateInt(targetBinding.binding())
                        .updateSizedUtf8(targetBinding.role())
                        .updateSizedUtf8(targetBinding.name());
            }
        }
        return digest.digestHex();
    }
}
