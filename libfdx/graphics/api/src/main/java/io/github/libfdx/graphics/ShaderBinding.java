package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

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

    public static ShaderBinding of(int group, int binding, String name, ShaderBindingType type) {
        return new ShaderBinding(group, binding, name, type);
    }

    public int group() {
        return group;
    }

    public int binding() {
        return binding;
    }

    public String name() {
        return name;
    }

    public ShaderBindingType type() {
        return type;
    }
}
