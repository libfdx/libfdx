package io.github.libfdx.graphics.g3d;

/** Immutable float-valued material attribute. */
public final class FloatMaterialAttribute implements MaterialAttribute {
    private final MaterialAttributeType<FloatMaterialAttribute> type;
    private final float value;

    public FloatMaterialAttribute(
            MaterialAttributeType<FloatMaterialAttribute> type,
            float value) {
        if (type == null) {
            throw new IllegalArgumentException("Attribute type cannot be null");
        }
        this.type = type;
        this.value = value;
    }

    @Override
    public MaterialAttributeType<FloatMaterialAttribute> type() {
        return type;
    }

    public float value() {
        return value;
    }
}
