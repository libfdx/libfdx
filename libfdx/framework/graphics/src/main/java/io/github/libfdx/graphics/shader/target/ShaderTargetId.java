package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.internal.ShaderStableId;

/**
 * Stable extensible identity for a shader translation target.
 *
 * <p>Target IDs are value objects rather than enum ordinals so applications and
 * providers can add targets without modifying libFDX core.</p>
 *
 * @author xpenatan
 */
public final class ShaderTargetId implements Comparable<ShaderTargetId> {
    private final String value;

    private ShaderTargetId(String value) {
        this.value = ShaderStableId.normalize(value, "Shader target");
    }

    /**
     * Creates a target ID.
     *
     * @param value the stable value
     * @return the ID
     */
    public static ShaderTargetId of(String value) {
        return new ShaderTargetId(value);
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
    public int compareTo(ShaderTargetId other) {
        if (other == null) {
            return 1;
        }
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object object) {
        return object == this || object instanceof ShaderTargetId
                && value.equals(((ShaderTargetId)object).value);
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
