package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable typed graph literal. Scalar payloads retain exact raw bits, while
 * composites retain typed child literals.
 */
public final class ShaderGraphLiteral {
    private final ShaderGraphType type;
    private final long bits;
    private final ShaderGraphLiteral[] elements;

    private ShaderGraphLiteral(ShaderGraphType type, long bits,
            ShaderGraphLiteral[] elements) {
        if (type == null || type.kind() == ShaderGraphTypeKind.TEXTURE
                || type.kind() == ShaderGraphTypeKind.SAMPLER
                || type.kind() == ShaderGraphTypeKind.STORAGE_BUFFER
                || type.kind() == ShaderGraphTypeKind.STORAGE_TEXTURE
                || type.kind() == ShaderGraphTypeKind.WORKGROUP_ARRAY) {
            throw new FdxException("Graph literals require a value or structure type");
        }
        this.type = type;
        this.bits = bits;
        this.elements = elements != null ? elements.clone() : new ShaderGraphLiteral[0];
        validate();
    }

    public static ShaderGraphLiteral bool(boolean value) {
        return scalar(ShaderGraphType.scalar(ShaderScalarType.BOOL), value ? 1 : 0);
    }

    public static ShaderGraphLiteral i32(int value) {
        return scalar(ShaderGraphType.scalar(ShaderScalarType.I32), value);
    }

    public static ShaderGraphLiteral u32(long value) {
        if (value < 0 || value > 0xffffffffL) {
            throw new FdxException("Unsigned graph literal is outside the u32 range");
        }
        return scalar(ShaderGraphType.scalar(ShaderScalarType.U32), value);
    }

    public static ShaderGraphLiteral f32(float value) {
        if (!Float.isFinite(value)) {
            throw new FdxException("Floating graph literal must be finite");
        }
        return scalar(ShaderGraphType.scalar(ShaderScalarType.F32),
                Float.floatToRawIntBits(value) & 0xffffffffL);
    }

    public static ShaderGraphLiteral scalar(ShaderGraphType type, long bits) {
        return new ShaderGraphLiteral(type, bits, null);
    }

    public static ShaderGraphLiteral composite(ShaderGraphType type,
            ShaderGraphLiteral... elements) {
        return new ShaderGraphLiteral(type, 0, elements);
    }

    public static ShaderGraphLiteral zero(ShaderGraphType type) {
        if (type == null) {
            throw new FdxException("Graph literal type cannot be null");
        }
        if (type.kind() == ShaderGraphTypeKind.STRUCT) {
            ShaderGraphLiteral[] fields =
                    new ShaderGraphLiteral[type.structType().fieldCount()];
            for (int i = 0; i < fields.length; i++) {
                fields[i] = zero(type.structType().field(i).type());
            }
            return composite(type, fields);
        }
        if (type.kind() != ShaderGraphTypeKind.VALUE) {
            throw new FdxException("Resource graph types have no literal zero");
        }
        ShaderValueKind kind = type.valueType().kind();
        if (kind == ShaderValueKind.SCALAR) {
            return scalar(type, 0);
        }
        int count = kind == ShaderValueKind.ARRAY
                ? Math.toIntExact(type.valueType().arrayCount())
                : type.valueType().componentCount();
        ShaderGraphType elementType;
        if (kind == ShaderValueKind.ARRAY) {
            elementType = ShaderGraphType.value(type.valueType().elementType());
        } else {
            elementType = ShaderGraphType.scalar(type.valueType().scalarType());
        }
        ShaderGraphLiteral[] values = new ShaderGraphLiteral[count];
        for (int i = 0; i < count; i++) {
            values[i] = zero(elementType);
        }
        return composite(type, values);
    }

    public ShaderGraphType type() {
        return type;
    }

    public long bits() {
        return bits;
    }

    public int elementCount() {
        return elements.length;
    }

    public ShaderGraphLiteral element(int index) {
        return elements[index];
    }

    public ShaderGraphLiteral[] elements() {
        return elements.clone();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphLiteral other
                && type.equals(other.type) && bits == other.bits
                && Arrays.equals(elements, other.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, bits, Arrays.hashCode(elements));
    }

    private void validate() {
        if (type.kind() == ShaderGraphTypeKind.STRUCT) {
            if (elements.length != type.structType().fieldCount()) {
                throw new FdxException("Structure literal field count does not match its type");
            }
            for (int i = 0; i < elements.length; i++) {
                requireElement(i, type.structType().field(i).type());
            }
            return;
        }
        ShaderValueKind kind = type.valueType().kind();
        if (kind == ShaderValueKind.SCALAR) {
            if (elements.length != 0) {
                throw new FdxException("Scalar graph literal cannot contain elements");
            }
            return;
        }
        int expected = kind == ShaderValueKind.ARRAY
                ? Math.toIntExact(type.valueType().arrayCount())
                : type.valueType().componentCount();
        if (elements.length != expected) {
            throw new FdxException("Composite graph literal element count does not match its type");
        }
        ShaderGraphType expectedType = kind == ShaderValueKind.ARRAY
                ? ShaderGraphType.value(type.valueType().elementType())
                : ShaderGraphType.scalar(type.valueType().scalarType());
        for (int i = 0; i < elements.length; i++) {
            requireElement(i, expectedType);
        }
    }

    private void requireElement(int index, ShaderGraphType expected) {
        if (elements[index] == null || !expected.equals(elements[index].type)) {
            throw new FdxException("Graph literal element " + index
                    + " does not match its declared type");
        }
    }
}
