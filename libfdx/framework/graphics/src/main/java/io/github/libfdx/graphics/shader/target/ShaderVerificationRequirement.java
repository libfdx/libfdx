package io.github.libfdx.graphics.shader.target;

/**
 * Selects where exact target verification must occur.
 *
 * @author xpenatan
 */
public enum ShaderVerificationRequirement {
    /**
     * The compiler registry must resolve and run a verifier before returning.
     */
    REQUIRED,
    /**
     * The consuming provider must verify the artifact during native
     * module/pipeline creation.
     */
    PROVIDER_PIPELINE
}
