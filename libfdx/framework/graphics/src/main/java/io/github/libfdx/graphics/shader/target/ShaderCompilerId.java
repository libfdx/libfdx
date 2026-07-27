package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.internal.ShaderStableId;

/**
 * Stable identity for a shader target compiler implementation.
 *
 * @author xpenatan
 */
public final class ShaderCompilerId implements Comparable<ShaderCompilerId> {
    private final String value;

    private ShaderCompilerId(String value) {
        this.value = ShaderStableId.normalize(value, "Shader compiler");
    }

    /**
     * Creates a compiler ID.
     *
     * @param value the stable value
     * @return the ID
     */
    public static ShaderCompilerId of(String value) {
        return new ShaderCompilerId(value);
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
    public int compareTo(ShaderCompilerId other) {
        return other != null ? value.compareTo(other.value) : 1;
    }

    @Override
    public boolean equals(Object object) {
        return object == this || object instanceof ShaderCompilerId
                && value.equals(((ShaderCompilerId)object).value);
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
