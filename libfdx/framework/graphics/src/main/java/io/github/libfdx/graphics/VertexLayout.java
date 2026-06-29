package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Represents a vertex layout.
 *
 * @author xpenatan
 */
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

    /**
     * Creates a vertex layout from the supplied values.
     *
     * @param arrayStride the array stride
     * @param attributes the attributes
     * @return a new vertex layout
     */
    public static VertexLayout of(int arrayStride, VertexAttribute... attributes) {
        return new VertexLayout(arrayStride, VertexStepMode.VERTEX, attributes);
    }

    /**
     * Creates a vertex layout from the supplied values.
     *
     * @param arrayStride the array stride
     * @param stepMode the step mode
     * @param attributes the attributes
     * @return a new vertex layout
     */
    public static VertexLayout of(int arrayStride, VertexStepMode stepMode, VertexAttribute... attributes) {
        return new VertexLayout(arrayStride, stepMode, attributes);
    }

    /**
     * Creates a vertex layout.
     *
     * @param arrayStride the array stride
     * @param attributes the attributes
     * @return a new vertex layout
     */
    public static VertexLayout instance(int arrayStride, VertexAttribute... attributes) {
        return new VertexLayout(arrayStride, VertexStepMode.INSTANCE, attributes);
    }

    /**
     * Returns the array stride.
     *
     * @return the array stride
     */
    public int arrayStride() {
        return arrayStride;
    }

    /**
     * Returns the step mode.
     *
     * @return the step mode
     */
    public VertexStepMode stepMode() {
        return stepMode;
    }

    /**
     * Returns the attributes.
     *
     * @return the attributes
     */
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
