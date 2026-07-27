package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import java.util.Objects;

/**
 * One deterministic value assigned to a compile-time graph switch.
 */
public final class ShaderGraphStaticValue
        implements Comparable<ShaderGraphStaticValue> {
    private final ShaderGraphId parameterId;
    private final boolean value;

    private ShaderGraphStaticValue(ShaderGraphId parameterId, boolean value) {
        this.parameterId = parameterId;
        this.value = value;
    }

    public static ShaderGraphStaticValue bool(String parameterId,
            boolean value) {
        return new ShaderGraphStaticValue(ShaderGraphId.of(parameterId),
                value);
    }

    public ShaderGraphId parameterId() {
        return parameterId;
    }

    public boolean boolValue() {
        return value;
    }

    public ShaderGraphLiteral literal() {
        return ShaderGraphLiteral.bool(value);
    }

    @Override
    public int compareTo(ShaderGraphStaticValue other) {
        return parameterId.compareTo(other.parameterId);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphStaticValue other
                && parameterId.equals(other.parameterId)
                && value == other.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterId, value);
    }
}
