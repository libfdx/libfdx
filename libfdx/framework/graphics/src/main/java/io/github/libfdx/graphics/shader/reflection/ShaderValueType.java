package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable recursive provider-neutral shader value type.
 *
 * <p>Array and matrix nodes preserve their own physical strides so nested arrays do not lose ABI
 * information.</p>
 */
public final class ShaderValueType {
    private static final ShaderValueType UNKNOWN = new ShaderValueType(ShaderValueKind.UNKNOWN,
            ShaderScalarType.UNKNOWN, 0, 0, null, 0, "", 0, 0);

    private final ShaderValueKind kind;
    private final ShaderScalarType scalarType;
    private final int columns;
    private final int rows;
    private final ShaderValueType elementType;
    private final long arrayCount;
    private final String typeName;
    private final long arrayStride;
    private final long matrixStride;

    private ShaderValueType(ShaderValueKind kind, ShaderScalarType scalarType, int columns, int rows,
            ShaderValueType elementType, long arrayCount, String typeName, long arrayStride, long matrixStride) {
        this.kind = kind;
        this.scalarType = scalarType;
        this.columns = columns;
        this.rows = rows;
        this.elementType = elementType;
        this.arrayCount = arrayCount;
        this.typeName = typeName;
        this.arrayStride = arrayStride;
        this.matrixStride = matrixStride;
    }

    public static ShaderValueType unknown() {
        return UNKNOWN;
    }

    public static ShaderValueType scalar(ShaderScalarType scalarType) {
        ShaderScalarType checked = requireScalar(scalarType);
        return new ShaderValueType(ShaderValueKind.SCALAR, checked, 1, 1, null, 0, "", 0, 0);
    }

    public static ShaderValueType atomic(ShaderScalarType scalarType) {
        ShaderScalarType checked = requireScalar(scalarType);
        if (checked != ShaderScalarType.I32 && checked != ShaderScalarType.U32) {
            throw new FdxException("Shader atomic type must be I32 or U32");
        }
        return new ShaderValueType(ShaderValueKind.ATOMIC, checked, 1, 1, null, 0, "", 0, 0);
    }

    public static ShaderValueType vector(ShaderScalarType scalarType, int width) {
        ShaderScalarType checked = requireScalar(scalarType);
        if (width < 2 || width > 4) {
            throw new FdxException("Shader vector width must be between 2 and 4");
        }
        return new ShaderValueType(ShaderValueKind.VECTOR, checked, 1, width, null, 0, "", 0, 0);
    }

    public static ShaderValueType matrix(ShaderScalarType scalarType, int columns, int rows) {
        return matrix(scalarType, columns, rows, 0);
    }

    public static ShaderValueType matrix(ShaderScalarType scalarType, int columns, int rows, long matrixStride) {
        ShaderScalarType checked = requireScalar(scalarType);
        if (checked != ShaderScalarType.F32 && checked != ShaderScalarType.F16) {
            throw new FdxException("Shader matrix scalar type must be F32 or F16");
        }
        if (columns < 2 || columns > 4 || rows < 2 || rows > 4) {
            throw new FdxException("Shader matrix dimensions must be between 2 and 4");
        }
        if (matrixStride < 0) {
            throw new FdxException("Shader matrix stride cannot be negative");
        }
        return new ShaderValueType(ShaderValueKind.MATRIX, checked, columns, rows, null, 0, "", 0,
                matrixStride);
    }

    public static ShaderValueType array(ShaderValueType elementType, long count) {
        return array(elementType, count, 0);
    }

    public static ShaderValueType array(ShaderValueType elementType, long count, long arrayStride) {
        if (count <= 0) {
            throw new FdxException("Shader array count must be positive");
        }
        return arrayType(elementType, count, arrayStride);
    }

    public static ShaderValueType runtimeArray(ShaderValueType elementType) {
        return runtimeArray(elementType, 0);
    }

    public static ShaderValueType runtimeArray(ShaderValueType elementType, long arrayStride) {
        return arrayType(elementType, -1, arrayStride);
    }

    public static ShaderValueType structure(String name) {
        return aggregate(ShaderValueKind.STRUCT, name);
    }

    public static ShaderValueType buffer(String name) {
        return aggregate(ShaderValueKind.BUFFER, name != null && !name.trim().isEmpty() ? name : "$");
    }

    private static ShaderValueType aggregate(ShaderValueKind kind, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new FdxException("Shader aggregate type name cannot be empty");
        }
        return new ShaderValueType(kind, ShaderScalarType.UNKNOWN, 0, 0, null, 0, name, 0, 0);
    }

    private static ShaderValueType arrayType(ShaderValueType elementType, long count, long arrayStride) {
        if (elementType == null || elementType.kind == ShaderValueKind.UNKNOWN) {
            throw new FdxException("Shader array element type cannot be unknown");
        }
        if (arrayStride < 0) {
            throw new FdxException("Shader array stride cannot be negative");
        }
        return new ShaderValueType(ShaderValueKind.ARRAY, ShaderScalarType.UNKNOWN, 0, 0, elementType, count, "",
                arrayStride, 0);
    }

    private static ShaderScalarType requireScalar(ShaderScalarType scalarType) {
        if (scalarType == null || scalarType == ShaderScalarType.UNKNOWN) {
            throw new FdxException("Shader scalar type cannot be unknown");
        }
        return scalarType;
    }

    ShaderValueType withRootStrides(long rootArrayStride, long rootMatrixStride) {
        if (kind == ShaderValueKind.ARRAY) {
            return arrayCount < 0 ? runtimeArray(elementType, rootArrayStride)
                    : array(elementType, arrayCount, rootArrayStride);
        }
        if (kind == ShaderValueKind.MATRIX) {
            return matrix(scalarType, columns, rows, rootMatrixStride);
        }
        return this;
    }

    /**
     * Returns an otherwise identical type carrying its reflected display/source type name.
     *
     * @param typeName the reflected type name, which may be empty
     * @return the named type
     */
    public ShaderValueType named(String typeName) {
        return new ShaderValueType(kind, scalarType, columns, rows, elementType, arrayCount,
                typeName != null ? typeName : "", arrayStride, matrixStride);
    }

    public ShaderValueKind kind() {
        return kind;
    }

    public ShaderScalarType scalarType() {
        return scalarType;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public ShaderValueType elementType() {
        return elementType;
    }

    /**
     * Returns the fixed array count, or {@code -1} for a runtime-sized array.
     *
     * @return the array count
     */
    public long arrayCount() {
        return arrayCount;
    }

    public String structName() {
        return typeName;
    }

    public String typeName() {
        return typeName;
    }

    public long arrayStride() {
        return arrayStride;
    }

    public long matrixStride() {
        return matrixStride;
    }

    public int componentCount() {
        if (kind == ShaderValueKind.SCALAR || kind == ShaderValueKind.ATOMIC) {
            return 1;
        }
        if (kind == ShaderValueKind.VECTOR) {
            return rows;
        }
        if (kind == ShaderValueKind.MATRIX) {
            return columns * rows;
        }
        return 0;
    }

    public boolean isScalar() {
        return kind == ShaderValueKind.SCALAR || kind == ShaderValueKind.ATOMIC;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ShaderValueType other)) {
            return false;
        }
        return kind == other.kind && scalarType == other.scalarType && columns == other.columns
                && rows == other.rows && arrayCount == other.arrayCount && arrayStride == other.arrayStride
                && matrixStride == other.matrixStride && Objects.equals(elementType, other.elementType)
                && typeName.equals(other.typeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, scalarType, columns, rows, elementType, arrayCount, typeName, arrayStride,
                matrixStride);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case SCALAR, ATOMIC -> kind.name().toLowerCase() + '<' + scalarType.name().toLowerCase() + '>';
            case VECTOR -> "vec" + rows + '<' + scalarType.name().toLowerCase() + '>';
            case MATRIX -> "mat" + columns + 'x' + rows + '<' + scalarType.name().toLowerCase() + '>';
            case ARRAY -> "array<" + elementType + (arrayCount < 0 ? ">" : ", " + arrayCount + '>');
            case STRUCT, BUFFER -> typeName;
            case UNKNOWN -> "unknown";
        };
    }
}
