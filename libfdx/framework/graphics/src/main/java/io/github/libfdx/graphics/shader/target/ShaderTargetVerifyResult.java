package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.ShaderValidationSeverity;
import io.github.libfdx.core.FdxException;

/**
 * Result returned by an exact target verifier.
 *
 * @author xpenatan
 */
public final class ShaderTargetVerifyResult {
    private final boolean success;
    private final ShaderVerifierId verifier;
    private final String verifierVersion;
    private final ShaderEntryPointRemap[] verifiedEntryPoints;
    private final ShaderTargetDiagnostic[] diagnostics;

    private ShaderTargetVerifyResult(boolean success, ShaderVerifierId verifier, String verifierVersion,
            ShaderEntryPointRemap[] verifiedEntryPoints, ShaderTargetDiagnostic[] diagnostics) {
        if (verifier == null || verifierVersion == null || verifierVersion.trim().length() == 0) {
            throw new FdxException("Shader verifier result ID and version cannot be empty");
        }
        this.success = success;
        this.verifier = verifier;
        this.verifierVersion = verifierVersion.trim();
        this.verifiedEntryPoints = verifiedEntryPoints != null
                ? verifiedEntryPoints.clone() : new ShaderEntryPointRemap[0];
        this.diagnostics = diagnostics != null ? diagnostics.clone() : new ShaderTargetDiagnostic[0];
    }

    public static ShaderTargetVerifyResult success(ShaderVerifierId verifier, String version,
            ShaderEntryPointRemap[] verifiedEntryPoints) {
        return new ShaderTargetVerifyResult(true, verifier, version, verifiedEntryPoints, null);
    }

    public static ShaderTargetVerifyResult failure(ShaderVerifierId verifier, String version,
            ShaderTargetDiagnostic... diagnostics) {
        if (diagnostics == null || diagnostics.length == 0) {
            throw new FdxException("Failed shader target verification requires a diagnostic");
        }
        return new ShaderTargetVerifyResult(false, verifier, version, null, diagnostics);
    }

    public boolean success() {
        if (!success) {
            return false;
        }
        for (ShaderTargetDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null && diagnostic.severity() == ShaderValidationSeverity.ERROR) {
                return false;
            }
        }
        return true;
    }

    public ShaderVerifierId verifier() {
        return verifier;
    }

    public String verifierVersion() {
        return verifierVersion;
    }

    public ShaderEntryPointRemap[] verifiedEntryPoints() {
        return verifiedEntryPoints.clone();
    }

    public ShaderTargetDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }
}
