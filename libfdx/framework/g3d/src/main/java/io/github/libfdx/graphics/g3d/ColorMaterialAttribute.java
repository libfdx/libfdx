package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;

/** Immutable color-valued material attribute. */
public final class ColorMaterialAttribute implements MaterialAttribute {
    private final MaterialAttributeType<ColorMaterialAttribute> type;
    private final Color value;

    public ColorMaterialAttribute(
            MaterialAttributeType<ColorMaterialAttribute> type,
            Color value) {
        if (type == null) {
            throw new IllegalArgumentException("Attribute type cannot be null");
        }
        this.type = type;
        this.value = value != null ? value : Color.WHITE;
    }

    @Override
    public MaterialAttributeType<ColorMaterialAttribute> type() {
        return type;
    }

    public Color value() {
        return value;
    }
}
