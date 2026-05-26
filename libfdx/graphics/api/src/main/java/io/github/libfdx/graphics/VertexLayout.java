package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

public final class VertexLayout {
    private final int arrayStride;
    private final VertexStepMode stepMode;
    private final VertexAttribute[] attributes;

    private VertexLayout(int arrayStride, VertexStepMode stepMode, VertexAttribute[] attributes) {
        if (arrayStride <= 0) {
            throw new FdxException("Vertex layout stride must be greater than zero");
        }
        if (stepMode == null) {
            throw new FdxException("Vertex layout step mode cannot be null");
        }
        if (attributes == null || attributes.length == 0) {
            throw new FdxException("Vertex layout must contain at least one attribute");
        }
        for (int i = 0; i < attributes.length; i++) {
            if (attributes[i] == null) {
                throw new FdxException("Vertex layout attribute cannot be null");
            }
        }
        this.arrayStride = arrayStride;
        this.stepMode = stepMode;
        this.attributes = copy(attributes);
    }

    public static VertexLayout of(int arrayStride, VertexAttribute... attributes) {
        return new VertexLayout(arrayStride, VertexStepMode.VERTEX, attributes);
    }

    public static VertexLayout of(int arrayStride, VertexStepMode stepMode, VertexAttribute... attributes) {
        return new VertexLayout(arrayStride, stepMode, attributes);
    }

    public static VertexLayout instance(int arrayStride, VertexAttribute... attributes) {
        return new VertexLayout(arrayStride, VertexStepMode.INSTANCE, attributes);
    }

    public int arrayStride() {
        return arrayStride;
    }

    public VertexStepMode stepMode() {
        return stepMode;
    }

    public VertexAttribute[] attributes() {
        return copy(attributes);
    }

    private static VertexAttribute[] copy(VertexAttribute[] source) {
        VertexAttribute[] copy = new VertexAttribute[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i];
        }
        return copy;
    }
}
