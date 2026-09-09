package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Texture;

/** Immutable borrowed texture-valued material attribute. */
public final class TextureMaterialAttribute implements MaterialAttribute {
    private final MaterialAttributeType<TextureMaterialAttribute> type;
    private final Texture texture;
    private final TextureCoordinates coordinates;

    public TextureMaterialAttribute(
            MaterialAttributeType<TextureMaterialAttribute> type,
            Texture texture) {
        this(type, texture, TextureCoordinates.UV0);
    }

    /** Borrows the texture and immutable coordinates; neither is disposed by the material. */
    public TextureMaterialAttribute(MaterialAttributeType<TextureMaterialAttribute> type,
                                    Texture texture, TextureCoordinates coordinates) {
        if (type == null) {
            throw new IllegalArgumentException("Attribute type cannot be null");
        }
        this.type = type;
        this.texture = texture;
        if (coordinates == null) throw new IllegalArgumentException("Texture coordinates cannot be null");
        this.coordinates = coordinates;
    }

    @Override
    public MaterialAttributeType<TextureMaterialAttribute> type() {
        return type;
    }

    public Texture texture() {
        return texture;
    }

    public TextureCoordinates coordinates() { return coordinates; }

}
