package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable render-target layout and dimensions supplied to pipeline
 * selection for an active or external render pass.
 */
public final class RenderPassCompatibility {
    private final RenderTargetLayout targetLayout;
    private final int width;
    private final int height;

    private RenderPassCompatibility(RenderTargetLayout targetLayout, int width, int height) {
        if (targetLayout == null) {
            throw new FdxException("Render pass compatibility target layout cannot be null");
        }
        if (width < 0 || height < 0 || (width == 0) != (height == 0)) {
            throw new FdxException("Render pass compatibility dimensions must both be zero or positive");
        }
        this.targetLayout = targetLayout;
        this.width = width;
        this.height = height;
    }

    public static RenderPassCompatibility of(RenderTargetLayout targetLayout,
            int width, int height) {
        return new RenderPassCompatibility(targetLayout, width, height);
    }

    public static RenderPassCompatibility layout(RenderTargetLayout targetLayout) {
        return new RenderPassCompatibility(targetLayout, 0, 0);
    }

    public RenderTargetLayout targetLayout() {
        return targetLayout;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean hasDimensions() {
        return width > 0;
    }

    public boolean isCompatible(RenderTargetLayout layout) {
        return targetLayout.equals(layout);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof RenderPassCompatibility other
                && width == other.width && height == other.height
                && targetLayout.equals(other.targetLayout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetLayout, width, height);
    }
}
