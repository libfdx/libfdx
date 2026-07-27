package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;

/**
 * Verifies WGPU/WebGPU WGSL by parsing and validating the complete module
 * through the runtime Tint bridge.
 *
 * @author xpenatan
 */
public final class RuntimeWgslTargetVerifier implements ShaderTargetVerifier {
    public static final ShaderVerifierId ID = ShaderVerifierId.of("libfdx.tint-wgsl");
    public static final String VERSION = "runtime-abi-1";

    private final String version;

    public RuntimeWgslTargetVerifier(RuntimeShaderCompiler compiler) {
        this(compiler, VERSION);
    }

    public RuntimeWgslTargetVerifier(RuntimeShaderCompiler compiler, String version) {
        if (compiler == null || version == null || version.trim().length() == 0) {
            throw new FdxException("Runtime WGSL verifier compiler/version cannot be empty");
        }
        this.version = version.trim();
    }

    @Override
    public ShaderVerifierId id() {
        return ID;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public ShaderTargetEnvironment[] environments() {
        return new ShaderTargetEnvironment[] {
                ShaderTargetEnvironments.WEBGPU_WGSL_1,
                ShaderTargetEnvironments.WGPU_WGSL_1
        };
    }

    @Override
    public boolean supports(ShaderTargetVerifyRequest request) {
        if (request == null || !ShaderArtifactFormats.WGSL_TEXT.equals(request.artifact().format())) {
            return false;
        }
        return ShaderTargets.WEBGPU_WGSL.equals(request.artifact().target())
                || ShaderTargets.WGPU_WGSL.equals(request.artifact().target());
    }

    @Override
    public ShaderTargetVerifyResult verify(ShaderTargetVerifyRequest request) {
        if (!supports(request)) {
            return ShaderTargetVerifyResult.failure(id(), version(),
                    ShaderTargetDiagnostic.error("shader.wgsl.verifier-unsupported",
                            "Runtime WGSL verifier does not support this target"));
        }
        ShaderStageArtifact module = request.artifact().find(ShaderArtifactStage.MODULE, "");
        if (module == null || !module.text().equals(request.compileRequest().wgsl())) {
            return ShaderTargetVerifyResult.failure(id(), version(),
                    ShaderTargetDiagnostic.error("shader.wgsl.module-mismatch",
                            "Tint-validated WGSL module does not match canonical WGSL"));
        }
        ShaderReflection reflected = request.artifact().translatedInterface().target();
        ShaderReflection canonical = request.artifact().translatedInterface().canonical();
        if (!reflected.complete() || !canonical.physicallyEquivalent(reflected)) {
            return ShaderTargetVerifyResult.failure(id(), version(),
                    ShaderTargetDiagnostic.error("shader.wgsl.reflection-mismatch",
                            "Tint-validated WGSL artifact does not contain a complete matching interface"));
        }
        return ShaderTargetVerifyResult.success(id(), version(),
                request.artifact().translatedInterface().entryPoints());
    }
}
