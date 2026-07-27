package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;

/**
 * Immutable verification metadata attached to one target artifact.
 *
 * @author xpenatan
 */
public final class ShaderTargetVerification {
    public static final ShaderVerifierId PROVIDER_PIPELINE_VERIFIER =
            ShaderVerifierId.of("libfdx.provider-pipeline");

    private final ShaderTargetVerificationStatus status;
    private final ShaderVerifierId verifier;
    private final String verifierVersion;
    private final ShaderTargetEnvironment environment;
    private final ShaderEntryPointRemap[] verifiedEntryPoints;
    private final ShaderTargetDiagnostic[] diagnostics;
    private final String cacheKey;

    private ShaderTargetVerification(ShaderTargetVerificationStatus status, ShaderVerifierId verifier,
            String verifierVersion, ShaderTargetEnvironment environment,
            ShaderEntryPointRemap[] verifiedEntryPoints, ShaderTargetDiagnostic[] diagnostics,
            String compileCacheKey) {
        if (status == null || verifier == null || environment == null) {
            throw new FdxException("Shader target verification metadata cannot contain null identity values");
        }
        this.status = status;
        this.verifier = verifier;
        this.verifierVersion = verifierVersion != null ? verifierVersion : "";
        this.environment = environment;
        this.verifiedEntryPoints = verifiedEntryPoints != null
                ? verifiedEntryPoints.clone() : new ShaderEntryPointRemap[0];
        this.diagnostics = diagnostics != null ? diagnostics.clone() : new ShaderTargetDiagnostic[0];
        cacheKey = new PortableSha256().updateSizedUtf8("fdx-shader-target-verification-v1")
                .updateSizedUtf8(compileCacheKey != null ? compileCacheKey : "")
                .updateSizedUtf8(status.name())
                .updateSizedUtf8(verifier.value())
                .updateSizedUtf8(this.verifierVersion)
                .updateSizedUtf8(environment.cacheKey())
                .digestHex();
    }

    /**
     * Creates verified metadata.
     *
     * @param result the successful verifier result
     * @param environment the exact environment
     * @param compileCacheKey the compile cache key
     * @return the metadata
     */
    public static ShaderTargetVerification verified(ShaderTargetVerifyResult result,
            ShaderTargetEnvironment environment, String compileCacheKey) {
        if (result == null || !result.success()) {
            throw new FdxException("Successful shader target verifier result is required");
        }
        return new ShaderTargetVerification(ShaderTargetVerificationStatus.VERIFIED,
                result.verifier(), result.verifierVersion(), environment,
                result.verifiedEntryPoints(), result.diagnostics(), compileCacheKey);
    }

    /**
     * Creates metadata requiring verification during provider pipeline creation.
     *
     * @param environment the exact environment
     * @param entryPoints the entry points that the provider must verify
     * @param compileCacheKey the compile cache key
     * @return the metadata
     */
    public static ShaderTargetVerification providerPipeline(ShaderTargetEnvironment environment,
            ShaderEntryPointRemap[] entryPoints, String compileCacheKey) {
        return new ShaderTargetVerification(ShaderTargetVerificationStatus.PROVIDER_PIPELINE_REQUIRED,
                PROVIDER_PIPELINE_VERIFIER, "1", environment, entryPoints, null, compileCacheKey);
    }

    public ShaderTargetVerificationStatus status() {
        return status;
    }

    public boolean verified() {
        return status == ShaderTargetVerificationStatus.VERIFIED;
    }

    public ShaderVerifierId verifier() {
        return verifier;
    }

    public String verifierVersion() {
        return verifierVersion;
    }

    public ShaderTargetEnvironment environment() {
        return environment;
    }

    public ShaderEntryPointRemap[] verifiedEntryPoints() {
        return verifiedEntryPoints.clone();
    }

    public ShaderTargetDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }

    public String cacheKey() {
        return cacheKey;
    }
}
