package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.core.FdxException;

/**
 * Immutable, layout-owned parameter location used by allocation-free setters.
 */
public final class ShaderParameterHandle {
    private final ShaderParameterLayout layout;
    private final long layoutIdentity;
    private final String path;
    private final ShaderValueType valueType;
    private final long byteOffset;
    private final long occupiedSize;
    private final long alignment;
    private final long arrayStride;
    private final long matrixStride;
    private volatile ShaderParameterHandle component0;
    private volatile ShaderParameterHandle component1;
    private volatile ShaderParameterHandle component2;
    private volatile ShaderParameterHandle component3;

    ShaderParameterHandle(ShaderParameterLayout layout, String path, ShaderValueType valueType, long byteOffset,
            long occupiedSize, long alignment, long arrayStride, long matrixStride) {
        this.layout = layout;
        this.layoutIdentity = layout.identity();
        this.path = path;
        this.valueType = valueType;
        this.byteOffset = byteOffset;
        this.occupiedSize = occupiedSize;
        this.alignment = alignment;
        this.arrayStride = arrayStride;
        this.matrixStride = matrixStride;
    }

    public long layoutIdentity() {
        return layoutIdentity;
    }

    public String path() {
        return path;
    }

    public ShaderValueType valueType() {
        return valueType;
    }

    public long byteOffset() {
        return byteOffset;
    }

    public long occupiedSize() {
        return occupiedSize;
    }

    public long alignment() {
        return alignment;
    }

    public long arrayStride() {
        return arrayStride;
    }

    public long matrixStride() {
        return matrixStride;
    }

    /**
     * Returns the byte offset as an exact Java buffer index.
     *
     * @return the byte offset
     */
    public int byteOffsetInt() {
        return Math.toIntExact(byteOffset);
    }

    public int arrayStrideInt() {
        return Math.toIntExact(arrayStride);
    }

    public int matrixStrideInt() {
        return Math.toIntExact(matrixStride);
    }

    /**
     * Returns a cached fixed-array element handle.
     *
     * @param index the element index
     * @return the element handle
     */
    public ShaderParameterHandle element(int index) {
        if (valueType.kind() != ShaderValueKind.ARRAY) {
            throw new FdxException("Shader parameter is not an array: " + path);
        }
        return layout.requireArrayElementHandle(path, index);
    }

    /**
     * Returns a cached vector component handle.
     *
     * @param index the component index
     * @return the component handle
     */
    public ShaderParameterHandle component(int index) {
        if (valueType.kind() != ShaderValueKind.VECTOR) {
            throw new FdxException("Shader parameter is not a vector: " + path);
        }
        if (index < 0 || index >= valueType.rows()) {
            throw new FdxException("Shader vector component is out of range: "
                    + path + '[' + index + ']');
        }
        ShaderParameterHandle cached = cachedComponent(index);
        if (cached != null) {
            return cached;
        }
        ShaderParameterHandle resolved =
                layout.requireComponentHandle(path, index);
        cacheComponent(index, resolved);
        return resolved;
    }

    boolean belongsTo(ShaderParameterLayout expected) {
        return layout == expected && layoutIdentity == expected.identity();
    }

    void cacheComponent(int index, ShaderParameterHandle component) {
        switch (index) {
            case 0 -> component0 = component;
            case 1 -> component1 = component;
            case 2 -> component2 = component;
            case 3 -> component3 = component;
            default -> throw new FdxException(
                    "Shader vector component is out of range: "
                            + path + '[' + index + ']');
        }
    }

    private ShaderParameterHandle cachedComponent(int index) {
        return switch (index) {
            case 0 -> component0;
            case 1 -> component1;
            case 2 -> component2;
            case 3 -> component3;
            default -> null;
        };
    }
}
