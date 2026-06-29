package io.github.libfdx.graphics;

/**
 * Represents a shader reflection.
 *
 * @author xpenatan
 */
public final class ShaderReflection {
    private static final ShaderBinding[] EMPTY_BINDINGS = new ShaderBinding[0];
    private static final ShaderAttribute[] EMPTY_ATTRIBUTES = new ShaderAttribute[0];
    private static final ShaderReflection EMPTY = new ShaderReflection(EMPTY_BINDINGS, EMPTY_ATTRIBUTES);

    private final ShaderBinding[] bindings;
    private final ShaderAttribute[] attributes;

    private ShaderReflection(ShaderBinding[] bindings, ShaderAttribute[] attributes) {
        this.bindings = bindings != null ? bindings.clone() : EMPTY_BINDINGS;
        this.attributes = attributes != null ? attributes.clone() : EMPTY_ATTRIBUTES;
    }

    /**
     * Creates a shader reflection.
     *
     * @return a new shader reflection
     */
    public static ShaderReflection empty() {
        return EMPTY;
    }

    /**
     * Creates a shader reflection from the supplied values.
     *
     * @param bindings the bindings
     * @param attributes the attributes
     * @return a new shader reflection
     */
    public static ShaderReflection of(ShaderBinding[] bindings, ShaderAttribute[] attributes) {
        return new ShaderReflection(bindings, attributes);
    }

    /**
     * Returns the bindings.
     *
     * @return the bindings
     */
    public ShaderBinding[] bindings() {
        return bindings.clone();
    }

    /**
     * Returns the attributes.
     *
     * @return the attributes
     */
    public ShaderAttribute[] attributes() {
        return attributes.clone();
    }
}
