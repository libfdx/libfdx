package io.github.libfdx.graphics;

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

    public static ShaderReflection empty() {
        return EMPTY;
    }

    public static ShaderReflection of(ShaderBinding[] bindings, ShaderAttribute[] attributes) {
        return new ShaderReflection(bindings, attributes);
    }

    public ShaderBinding[] bindings() {
        return bindings.clone();
    }

    public ShaderAttribute[] attributes() {
        return attributes.clone();
    }
}
