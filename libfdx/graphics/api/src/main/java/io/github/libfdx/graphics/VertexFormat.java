package io.github.libfdx.graphics;

public enum VertexFormat {
    FLOAT32(1, 4),
    FLOAT32X2(2, 8),
    FLOAT32X3(3, 12),
    FLOAT32X4(4, 16);

    private final int componentCount;
    private final int byteSize;

    VertexFormat(int componentCount, int byteSize) {
        this.componentCount = componentCount;
        this.byteSize = byteSize;
    }

    public int componentCount() {
        return componentCount;
    }

    public int byteSize() {
        return byteSize;
    }
}
