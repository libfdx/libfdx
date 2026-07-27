package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable color and alpha blend state.
 */
public final class BlendState {
    private final BlendComponent color;
    private final BlendComponent alpha;

    private BlendState(BlendComponent color, BlendComponent alpha) {
        if (color == null || alpha == null) {
            throw new FdxException("Blend state components cannot be null");
        }
        this.color = color;
        this.alpha = alpha;
    }

    public static BlendState of(BlendComponent color, BlendComponent alpha) {
        return new BlendState(color, alpha);
    }

    public static BlendState opaque() {
        return of(BlendComponent.replace(), BlendComponent.replace());
    }

    public static BlendState alphaBlend() {
        return of(BlendComponent.of(BlendFactor.SOURCE_ALPHA,
                        BlendFactor.ONE_MINUS_SOURCE_ALPHA, BlendOperation.ADD),
                BlendComponent.of(BlendFactor.ONE,
                        BlendFactor.ONE_MINUS_SOURCE_ALPHA, BlendOperation.ADD));
    }

    public BlendComponent color() {
        return color;
    }

    public BlendComponent alpha() {
        return alpha;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof BlendState other
                && color.equals(other.color) && alpha.equals(other.alpha);
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, alpha);
    }
}
