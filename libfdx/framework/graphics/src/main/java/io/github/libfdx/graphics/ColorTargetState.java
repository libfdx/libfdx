package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable fragment color-target format, blend, and write state.
 */
public final class ColorTargetState {
    private final TextureFormat format;
    private final BlendState blend;
    private final int writeMask;

    private ColorTargetState(TextureFormat format, BlendState blend, int writeMask) {
        if (format == null || !format.isColor()) {
            throw new FdxException("Color target state requires a concrete color format");
        }
        if ((writeMask & ~ColorWriteMask.ALL) != 0) {
            throw new FdxException("Color target write mask contains unknown bits");
        }
        this.format = format;
        this.blend = blend;
        this.writeMask = writeMask;
    }

    public static ColorTargetState of(TextureFormat format,
            BlendState blend, int writeMask) {
        return new ColorTargetState(format, blend, writeMask);
    }

    public static ColorTargetState alpha(TextureFormat format) {
        return of(format, BlendState.alphaBlend(), ColorWriteMask.ALL);
    }

    public static ColorTargetState opaque(TextureFormat format) {
        return of(format, null, ColorWriteMask.ALL);
    }

    public TextureFormat format() {
        return format;
    }

    /**
     * Returns blend state, or {@code null} when blending is disabled.
     *
     * @return blend state
     */
    public BlendState blend() {
        return blend;
    }

    public int writeMask() {
        return writeMask;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ColorTargetState other
                && format == other.format && Objects.equals(blend, other.blend)
                && writeMask == other.writeMask;
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, blend, writeMask);
    }
}
