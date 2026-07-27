package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;

/**
 * Immutable request to verify a translated artifact for its exact consumer.
 *
 * @author xpenatan
 */
public final class ShaderTargetVerifyRequest {
    private final ShaderTargetCompileRequest compileRequest;
    private final ShaderTargetArtifact artifact;

    private ShaderTargetVerifyRequest(ShaderTargetCompileRequest compileRequest, ShaderTargetArtifact artifact) {
        if (compileRequest == null || artifact == null) {
            throw new FdxException("Shader target verify request values cannot be null");
        }
        if (!compileRequest.target().equals(artifact.target())
                || !compileRequest.format().equals(artifact.format())
                || !compileRequest.environment().equals(artifact.environment())) {
            throw new FdxException("Shader target verify request does not match its artifact");
        }
        this.compileRequest = compileRequest;
        this.artifact = artifact;
    }

    public static ShaderTargetVerifyRequest of(ShaderTargetCompileRequest request,
            ShaderTargetArtifact artifact) {
        return new ShaderTargetVerifyRequest(request, artifact);
    }

    public ShaderTargetCompileRequest compileRequest() {
        return compileRequest;
    }

    public ShaderTargetArtifact artifact() {
        return artifact;
    }

    public ShaderTargetEnvironment environment() {
        return artifact.environment();
    }
}
