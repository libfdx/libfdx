package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Represents a shader binding.
 *
 * @author xpenatan
 */
public final class ShaderBinding {
    private final int group;
    private final int binding;
    private final String name;
    private final ShaderBindingType type;

    private ShaderBinding(int group, int binding, String name, ShaderBindingType type) {
        if (group < 0) {
            throw new FdxException("Shader binding group cannot be negative");
        }
        if (binding < 0) {
            throw new FdxException("Shader binding index cannot be negative");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new FdxException("Shader binding name cannot be empty");
        }
        this.group = group;
        this.binding = binding;
        this.name = name;
        this.type = type != null ? type : ShaderBindingType.UNKNOWN;
    }

    /**
     * Creates a shader binding from the supplied values.
     *
     * @param group the group
     * @param binding the binding
     * @param name the name
     * @param type the expected Java type
     * @return a new shader binding
     */
    public static ShaderBinding of(int group, int binding, String name, ShaderBindingType type) {
        return new ShaderBinding(group, binding, name, type);
    }

    /**
     * Returns the group.
     *
     * @return the group
     */
    public int group() {
        return group;
    }

    /**
     * Returns the binding.
     *
     * @return the binding
     */
    public int binding() {
        return binding;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public ShaderBindingType type() {
        return type;
    }
}
