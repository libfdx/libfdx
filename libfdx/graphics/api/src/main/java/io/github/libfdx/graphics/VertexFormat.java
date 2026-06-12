package io.github.libfdx.graphics;

/**
 * Lists the supported vertex format values.
 *
 * @author xpenatan
 */
public enum VertexFormat {
    FLOAT32(1, 4),
    FLOAT32X2(2, 8),
    FLOAT32X3(3, 12),
    FLOAT32X4(4, 16),
    UNORM8X4(4, 4);

    private final int componentCount;
    private final int byteSize;

    VertexFormat(int componentCount, int byteSize) {
        this.componentCount = componentCount;
        this.byteSize = byteSize;
    }

    /**
     * Returns the component count.
     *
     * @return the component count
     */
    public int componentCount() {
        return componentCount;
    }

    /**
     * Returns the byte size.
     *
     * @return the byte size
     */
    public int byteSize() {
        return byteSize;
    }
}
