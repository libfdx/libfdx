package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderStorageTextureFormat;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureDimension;
import io.github.libfdx.graphics.shader.reflection.ShaderTextureSampleType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;

import java.util.Objects;

/**
 * Strict semantic type used by graph ports, parameters, literals, and IR
 * values.
 */
public final class ShaderGraphType {
    private final ShaderGraphTypeKind kind;
    private final ShaderValueType valueType;
    private final ShaderStructType structType;
    private final ShaderTextureDimension textureDimension;
    private final ShaderTextureSampleType textureSampleType;
    private final boolean multisampled;
    private final ShaderSamplerKind samplerKind;
    private final ShaderGraphType elementType;
    private final ShaderResourceAccess resourceAccess;
    private final ShaderStorageTextureFormat storageFormat;
    private final int elementCount;

    private ShaderGraphType(ShaderGraphTypeKind kind, ShaderValueType valueType,
            ShaderStructType structType, ShaderTextureDimension textureDimension,
            ShaderTextureSampleType textureSampleType, boolean multisampled,
            ShaderSamplerKind samplerKind, ShaderGraphType elementType,
            ShaderResourceAccess resourceAccess,
            ShaderStorageTextureFormat storageFormat, int elementCount) {
        this.kind = kind;
        this.valueType = valueType;
        this.structType = structType;
        this.textureDimension = textureDimension;
        this.textureSampleType = textureSampleType;
        this.multisampled = multisampled;
        this.samplerKind = samplerKind;
        this.elementType = elementType;
        this.resourceAccess = resourceAccess;
        this.storageFormat = storageFormat;
        this.elementCount = elementCount;
    }

    public static ShaderGraphType value(ShaderValueType type) {
        if (type == null || type.kind() == ShaderValueKind.UNKNOWN
                || type.kind() == ShaderValueKind.BUFFER
                || type.kind() == ShaderValueKind.STRUCT) {
            throw new FdxException("Graph value type must be a concrete non-aggregate value");
        }
        return new ShaderGraphType(ShaderGraphTypeKind.VALUE, type, null,
                null, null, false, null, null, null, null, 0);
    }

    public static ShaderGraphType scalar(ShaderScalarType type) {
        return value(ShaderValueType.scalar(type));
    }

    public static ShaderGraphType vector(ShaderScalarType type, int width) {
        return value(ShaderValueType.vector(type, width));
    }

    public static ShaderGraphType matrix(ShaderScalarType type, int columns, int rows) {
        return value(ShaderValueType.matrix(type, columns, rows));
    }

    public static ShaderGraphType array(ShaderGraphType elementType, int count) {
        if (elementType == null || elementType.kind != ShaderGraphTypeKind.VALUE) {
            throw new FdxException("Phase 4 graph arrays require a value element type");
        }
        return value(ShaderValueType.array(elementType.valueType, count));
    }

    public static ShaderGraphType structure(ShaderStructType type) {
        if (type == null) {
            throw new FdxException("Graph structure type cannot be null");
        }
        return new ShaderGraphType(ShaderGraphTypeKind.STRUCT, null, type,
                null, null, false, null, null, null, null, 0);
    }

    public static ShaderGraphType texture(ShaderTextureDimension dimension,
            ShaderTextureSampleType sampleType, boolean multisampled) {
        if (dimension == null || sampleType == null) {
            throw new FdxException("Graph texture type requires dimension and sample type");
        }
        if (multisampled && dimension != ShaderTextureDimension.D2) {
            throw new FdxException("Only 2D graph textures may be multisampled");
        }
        return new ShaderGraphType(ShaderGraphTypeKind.TEXTURE, null, null,
                dimension, sampleType, multisampled, null, null, null, null, 0);
    }

    public static ShaderGraphType sampler(ShaderSamplerKind kind) {
        if (kind == null) {
            throw new FdxException("Graph sampler kind cannot be null");
        }
        return new ShaderGraphType(ShaderGraphTypeKind.SAMPLER, null, null,
                null, null, false, kind, null, null, null, 0);
    }

    public static ShaderGraphType storageBuffer(ShaderGraphType elementType,
            ShaderResourceAccess access) {
        requireStorageElement(elementType, "Storage-buffer");
        if (access != ShaderResourceAccess.READ
                && access != ShaderResourceAccess.READ_WRITE) {
            throw new FdxException(
                    "Storage-buffer graph type requires explicit access");
        }
        return new ShaderGraphType(ShaderGraphTypeKind.STORAGE_BUFFER, null,
                null, null, null, false, null, elementType, access, null, -1);
    }

    public static ShaderGraphType storageTexture2D(
            ShaderStorageTextureFormat format, ShaderResourceAccess access) {
        if (format == null || format == ShaderStorageTextureFormat.NONE) {
            throw new FdxException(
                    "Storage-texture graph type requires a concrete format");
        }
        if (access != ShaderResourceAccess.READ
                && access != ShaderResourceAccess.WRITE
                && access != ShaderResourceAccess.READ_WRITE) {
            throw new FdxException(
                    "Storage-texture graph type requires explicit access");
        }
        return new ShaderGraphType(ShaderGraphTypeKind.STORAGE_TEXTURE, null,
                null, ShaderTextureDimension.D2, null, false, null, null,
                access, format, 0);
    }

    public static ShaderGraphType workgroupArray(ShaderGraphType elementType,
            int count) {
        requireStorageElement(elementType, "Workgroup-array");
        if (count <= 0) {
            throw new FdxException(
                    "Workgroup-array element count must be positive");
        }
        return new ShaderGraphType(ShaderGraphTypeKind.WORKGROUP_ARRAY, null,
                null, null, null, false, null, elementType,
                ShaderResourceAccess.READ_WRITE, null, count);
    }

    public ShaderGraphTypeKind kind() {
        return kind;
    }

    public ShaderValueType valueType() {
        return valueType;
    }

    public ShaderStructType structType() {
        return structType;
    }

    public ShaderTextureDimension textureDimension() {
        return textureDimension;
    }

    public ShaderTextureSampleType textureSampleType() {
        return textureSampleType;
    }

    public boolean multisampled() {
        return multisampled;
    }

    public ShaderSamplerKind samplerKind() {
        return samplerKind;
    }

    public ShaderGraphType elementType() {
        return elementType;
    }

    public ShaderResourceAccess resourceAccess() {
        return resourceAccess;
    }

    public ShaderStorageTextureFormat storageFormat() {
        return storageFormat;
    }

    public int elementCount() {
        return elementCount;
    }

    public ShaderGraphType storageTextureTexelType() {
        if (kind != ShaderGraphTypeKind.STORAGE_TEXTURE) {
            throw new FdxException("Graph type is not a storage texture");
        }
        String name = storageFormat.name();
        ShaderScalarType scalar = name.endsWith("_UINT")
                ? ShaderScalarType.U32
                : name.endsWith("_SINT")
                        ? ShaderScalarType.I32 : ShaderScalarType.F32;
        return vector(scalar, 4);
    }

    public boolean isNumeric() {
        if (kind != ShaderGraphTypeKind.VALUE) {
            return false;
        }
        ShaderScalarType scalar = valueType.scalarType();
        return scalar == ShaderScalarType.F32 || scalar == ShaderScalarType.F16
                || scalar == ShaderScalarType.I32 || scalar == ShaderScalarType.U32;
    }

    public boolean isBoolean() {
        return kind == ShaderGraphTypeKind.VALUE
                && valueType.scalarType() == ShaderScalarType.BOOL;
    }

    public int componentCount() {
        if (kind == ShaderGraphTypeKind.STRUCT) {
            return structType.fieldCount();
        }
        return kind == ShaderGraphTypeKind.VALUE ? valueType.componentCount() : 0;
    }

    /**
     * Returns the WGSL natural-layout storage size for a workgroup array.
     *
     * @return the storage size in bytes, zero for non-workgroup types, or
     *         {@link Long#MAX_VALUE} when the type cannot be represented
     */
    public long workgroupStorageSize() {
        if (kind != ShaderGraphTypeKind.WORKGROUP_ARRAY) {
            return 0;
        }
        long stride = align(storageTypeSize(elementType),
                storageTypeAlignment(elementType));
        if (stride == Long.MAX_VALUE
                || stride > Long.MAX_VALUE / elementCount) {
            return Long.MAX_VALUE;
        }
        return stride * elementCount;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ShaderGraphType other)) {
            return false;
        }
        return kind == other.kind && Objects.equals(valueType, other.valueType)
                && Objects.equals(structType, other.structType)
                && textureDimension == other.textureDimension
                && textureSampleType == other.textureSampleType
                && multisampled == other.multisampled
                && samplerKind == other.samplerKind
                && Objects.equals(elementType, other.elementType)
                && resourceAccess == other.resourceAccess
                && storageFormat == other.storageFormat
                && elementCount == other.elementCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, valueType, structType, textureDimension,
                textureSampleType, multisampled, samplerKind, elementType,
                resourceAccess, storageFormat, elementCount);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case VALUE -> valueType.toString();
            case STRUCT -> structType.id().value();
            case TEXTURE -> "texture<" + textureDimension + ',' + textureSampleType
                    + (multisampled ? ",multisampled" : "") + '>';
            case SAMPLER -> "sampler<" + samplerKind + '>';
            case STORAGE_BUFFER -> "storage-buffer<" + elementType + ','
                    + resourceAccess + '>';
            case STORAGE_TEXTURE -> "storage-texture<"
                    + textureDimension + ',' + storageFormat + ','
                    + resourceAccess + '>';
            case WORKGROUP_ARRAY -> "workgroup-array<" + elementType + ','
                    + elementCount + '>';
        };
    }

    private static void requireStorageElement(ShaderGraphType type,
            String label) {
        if (type == null || type.kind != ShaderGraphTypeKind.VALUE
                && type.kind != ShaderGraphTypeKind.STRUCT) {
            throw new FdxException(label
                    + " element must be a value or structure type");
        }
    }

    private static long storageTypeSize(ShaderGraphType type) {
        if (type.kind == ShaderGraphTypeKind.STRUCT) {
            long offset = 0;
            long alignment = 1;
            for (ShaderStructField field : type.structType.fields()) {
                long fieldAlignment = storageTypeAlignment(field.type());
                offset = align(offset, fieldAlignment);
                long size = storageTypeSize(field.type());
                if (size == Long.MAX_VALUE
                        || offset > Long.MAX_VALUE - size) {
                    return Long.MAX_VALUE;
                }
                offset += size;
                alignment = Math.max(alignment, fieldAlignment);
            }
            return align(offset, alignment);
        }
        if (type.kind != ShaderGraphTypeKind.VALUE) {
            return Long.MAX_VALUE;
        }
        ShaderValueType value = type.valueType;
        int scalar = value.scalarType() == ShaderScalarType.F16 ? 2 : 4;
        return switch (value.kind()) {
            case SCALAR, ATOMIC -> scalar;
            case VECTOR -> (long)scalar * value.rows();
            case MATRIX -> {
                long columnAlignment = value.rows() == 2
                        ? scalar * 2L : scalar * 4L;
                long columnSize = scalar * (long)value.rows();
                long stride = value.matrixStride() > 0
                        ? value.matrixStride()
                        : align(columnSize, columnAlignment);
                yield stride * value.columns();
            }
            case ARRAY -> {
                if (value.arrayCount() < 0
                        || value.elementType().kind() == ShaderValueKind.STRUCT
                        || value.elementType().kind() == ShaderValueKind.BUFFER) {
                    yield Long.MAX_VALUE;
                }
                ShaderGraphType element = value(value.elementType());
                long stride = value.arrayStride() > 0
                        ? value.arrayStride()
                        : align(storageTypeSize(element),
                                storageTypeAlignment(element));
                yield stride > Long.MAX_VALUE / value.arrayCount()
                        ? Long.MAX_VALUE
                        : stride * value.arrayCount();
            }
            case STRUCT, BUFFER, UNKNOWN -> Long.MAX_VALUE;
        };
    }

    private static long storageTypeAlignment(ShaderGraphType type) {
        if (type.kind == ShaderGraphTypeKind.STRUCT) {
            long result = 1;
            for (ShaderStructField field : type.structType.fields()) {
                result = Math.max(result,
                        storageTypeAlignment(field.type()));
            }
            return result;
        }
        if (type.kind != ShaderGraphTypeKind.VALUE) {
            return 1;
        }
        ShaderValueType value = type.valueType;
        int scalar = value.scalarType() == ShaderScalarType.F16 ? 2 : 4;
        return switch (value.kind()) {
            case SCALAR, ATOMIC -> scalar;
            case VECTOR, MATRIX -> value.rows() == 2
                    ? scalar * 2L : scalar * 4L;
            case ARRAY -> value.elementType().kind() == ShaderValueKind.STRUCT
                    || value.elementType().kind() == ShaderValueKind.BUFFER
                            ? 1
                            : storageTypeAlignment(value(value.elementType()));
            case STRUCT, BUFFER, UNKNOWN -> 1;
        };
    }

    private static long align(long value, long alignment) {
        if (value == Long.MAX_VALUE) {
            return value;
        }
        long remainder = value % alignment;
        return remainder == 0 ? value
                : value + alignment - remainder;
    }
}
