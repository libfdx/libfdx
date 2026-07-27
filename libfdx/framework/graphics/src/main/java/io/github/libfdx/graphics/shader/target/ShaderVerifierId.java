package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.internal.ShaderStableId;

/**
 * Stable identity for a shader target verifier implementation.
 *
 * @author xpenatan
 */
public final class ShaderVerifierId implements Comparable<ShaderVerifierId> {
    private final String value;

    private ShaderVerifierId(String value) {
        this.value = ShaderStableId.normalize(value, "Shader verifier");
    }

    /**
     * Creates a verifier ID.
     *
     * @param value the stable value
     * @return the ID
     */
    public static ShaderVerifierId of(String value) {
        return new ShaderVerifierId(value);
    }

    /**
     * Returns the stable value.
     *
     * @return the value
     */
    public String value() {
        return value;
    }

    @Override
    public int compareTo(ShaderVerifierId other) {
        return other != null ? value.compareTo(other.value) : 1;
    }

    @Override
    public boolean equals(Object object) {
        return object == this || object instanceof ShaderVerifierId
                && value.equals(((ShaderVerifierId)object).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
