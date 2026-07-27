package io.github.libfdx.graphics.shader.target;

/**
 * Verifies translated artifacts against exact consumer environments.
 *
 * @author xpenatan
 */
public interface ShaderTargetVerifier {
    ShaderVerifierId id();

    String version();

    ShaderTargetEnvironment[] environments();

    boolean supports(ShaderTargetVerifyRequest request);

    ShaderTargetVerifyResult verify(ShaderTargetVerifyRequest request);
}
