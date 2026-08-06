package io.github.libfdx.graphics.g3d;

/**
 * Stable, typed identity for one material attribute semantic.
 *
 * @param <T> attribute implementation
 */
public final class MaterialAttributeType<T extends MaterialAttribute> {
    private final String id;
    private final Class<T> attributeClass;

    /**
     * Creates an attribute type.
     *
     * @param id stable non-empty identifier
     * @param attributeClass expected implementation class
     */
    public MaterialAttributeType(String id, Class<T> attributeClass) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Material attribute type ID cannot be empty");
        }
        if (attributeClass == null) {
            throw new IllegalArgumentException(
                    "Material attribute class cannot be null");
        }
        this.id = id;
        this.attributeClass = attributeClass;
    }

    /** @return stable identifier */
    public String id() {
        return id;
    }

    /** @return expected attribute class */
    public Class<T> attributeClass() {
        return attributeClass;
    }

    T cast(MaterialAttribute attribute) {
        if (attribute == null) {
            return null;
        }
        if (!attributeClass.isInstance(attribute)) {
            throw new IllegalArgumentException(
                    "Material attribute '" + id + "' requires "
                            + attributeClass.getName() + " but received "
                            + attribute.getClass().getName());
        }
        return attributeClass.cast(attribute);
    }

    @Override
    public boolean equals(Object value) {
        return value == this || value instanceof MaterialAttributeType<?>
                && id.equals(((MaterialAttributeType<?>)value).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
