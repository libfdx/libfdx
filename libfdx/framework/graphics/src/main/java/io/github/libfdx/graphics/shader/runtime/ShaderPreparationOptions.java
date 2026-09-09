package io.github.libfdx.graphics.shader.runtime;

/**
 * Immutable service budgets. These bound queued submission/publication, not the duration of a
 * native compiler call. Worker limits are configured by the selected provider.
 */
public record ShaderPreparationOptions(int maxInFlight, int maxPublicationsPerUpdate, int idleCapacity) {
    public static final ShaderPreparationOptions DEFAULT = new ShaderPreparationOptions(8, 32, 128);

    public ShaderPreparationOptions {
        if (maxInFlight < 1 || maxPublicationsPerUpdate < 1 || idleCapacity < 0) {
            throw new IllegalArgumentException("Preparation budgets must be positive; idleCapacity may be zero");
        }
    }
}

