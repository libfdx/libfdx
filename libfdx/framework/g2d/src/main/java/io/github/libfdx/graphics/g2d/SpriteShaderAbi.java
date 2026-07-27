package io.github.libfdx.graphics.g2d;

import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

/**
 * Stable geometry contracts that a shader provider may expose to
 * {@link SpriteBatch}.
 *
 * <p>Each value has a distinct pass ID because indexed and non-indexed packed
 * vertices interpret the vertex-index builtin differently. Vertex layouts are
 * structural public data; providers must match them exactly.</p>
 */
public enum SpriteShaderAbi {
    ORDINARY(false, false, false, "sprite/ordinary", coloredLayout()),
    ORDINARY_INDEXED(true, false, false, "sprite/ordinary-indexed", coloredLayout()),
    WHITE(false, false, true, "sprite/white", whiteLayout()),
    WHITE_INDEXED(true, false, true, "sprite/white-indexed", whiteLayout()),
    PACKED_INSTANCED(false, true, false, "sprite/packed-instanced", packedLayout()),
    PACKED_INSTANCED_INDEXED(true, true, false, "sprite/packed-instanced-indexed", packedLayout()),
    COMPACT_INSTANCED(false, true, false, "sprite/compact-instanced",
            compactVertexLayout(), compactCenterLayout()),
    COMPACT_INSTANCED_INDEXED(true, true, false, "sprite/compact-instanced-indexed",
            compactVertexLayout(), compactCenterLayout());

    private final boolean indexed;
    private final boolean instanced;
    private final boolean white;
    private final ShaderPassId passId;
    private final VertexLayout[] vertexLayouts;

    SpriteShaderAbi(boolean indexed, boolean instanced, boolean white,
            String passId, VertexLayout... vertexLayouts) {
        this.indexed = indexed;
        this.instanced = instanced;
        this.white = white;
        this.passId = ShaderPassId.of(passId);
        this.vertexLayouts = vertexLayouts;
    }

    /**
     * Returns whether this contract uses an index buffer.
     *
     * @return whether indexed
     */
    public boolean indexed() {
        return indexed;
    }

    /**
     * Returns whether this contract uses per-instance vertex data.
     *
     * @return whether instanced
     */
    public boolean instanced() {
        return instanced;
    }

    /**
     * Returns whether vertex color is fixed to white and omitted.
     *
     * @return whether white optimized
     */
    public boolean white() {
        return white;
    }

    /**
     * Returns the stable technique pass ID.
     *
     * @return pass ID
     */
    public ShaderPassId passId() {
        return passId;
    }

    /**
     * Returns the exact vertex layouts for this contract.
     *
     * @return defensive layout-array copy
     */
    public VertexLayout[] vertexLayouts() {
        return vertexLayouts.clone();
    }

    private static VertexLayout coloredLayout() {
        return VertexLayout.of(32,
                VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
                VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8),
                VertexAttribute.of(2, VertexFormat.FLOAT32X4, 16));
    }

    private static VertexLayout whiteLayout() {
        return VertexLayout.of(16,
                VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
                VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8));
    }

    private static VertexLayout packedLayout() {
        return VertexLayout.instance(56,
                VertexAttribute.of(0, VertexFormat.FLOAT32X4, 0),
                VertexAttribute.of(1, VertexFormat.FLOAT32X4, 16),
                VertexAttribute.of(2, VertexFormat.FLOAT32X4, 32),
                VertexAttribute.of(3, VertexFormat.FLOAT32X2, 48));
    }

    private static VertexLayout compactVertexLayout() {
        return VertexLayout.of(32,
                VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
                VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8),
                VertexAttribute.of(2, VertexFormat.FLOAT32X4, 16));
    }

    private static VertexLayout compactCenterLayout() {
        return VertexLayout.instance(8,
                VertexAttribute.of(3, VertexFormat.FLOAT32X2, 0));
    }
}
