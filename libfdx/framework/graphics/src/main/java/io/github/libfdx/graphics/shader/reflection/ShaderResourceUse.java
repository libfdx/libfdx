package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable reference from an entry point to a shader resource.
 */
public final class ShaderResourceUse {
    private final int group;
    private final int binding;
    private final long minimumBindingSize;

    private ShaderResourceUse(int group, int binding, long minimumBindingSize) {
        if (group < 0 || binding < 0) {
            throw new FdxException("Shader resource-use group and binding cannot be negative");
        }
        if (minimumBindingSize < 0) {
            throw new FdxException("Shader resource-use minimum binding size cannot be negative");
        }
        this.group = group;
        this.binding = binding;
        this.minimumBindingSize = minimumBindingSize;
    }

    public static ShaderResourceUse of(int group, int binding, long minimumBindingSize) {
        return new ShaderResourceUse(group, binding, minimumBindingSize);
    }

    public int group() {
        return group;
    }

    public int binding() {
        return binding;
    }

    public long minimumBindingSize() {
        return minimumBindingSize;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderResourceUse other && group == other.group && binding == other.binding
                && minimumBindingSize == other.minimumBindingSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, binding, minimumBindingSize);
    }
}
