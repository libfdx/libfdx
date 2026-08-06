package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Texture;

/** Immutable borrowed texture-valued material attribute. */
public final class TextureMaterialAttribute implements MaterialAttribute {
    private final MaterialAttributeType<TextureMaterialAttribute> type;
    private final Texture texture;

    public TextureMaterialAttribute(
            MaterialAttributeType<TextureMaterialAttribute> type,
            Texture texture) {
        if (type == null) {
            throw new IllegalArgumentException("Attribute type cannot be null");
        }
        this.type = type;
        this.texture = texture;
    }

    @Override
    public MaterialAttributeType<TextureMaterialAttribute> type() {
        return type;
    }

    public Texture texture() {
        return texture;
    }

}
