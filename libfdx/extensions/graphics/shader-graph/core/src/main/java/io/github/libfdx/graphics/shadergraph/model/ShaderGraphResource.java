package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable bound or workgroup-scoped graph resource declaration.
 */
public final class ShaderGraphResource implements Comparable<ShaderGraphResource> {
    private final ShaderGraphId id;
    private final ShaderGraphType type;
    private final int group;
    private final int binding;

    private ShaderGraphResource(ShaderGraphId id, ShaderGraphType type,
            int group, int binding) {
        if (id == null || type == null || !isResourceType(type.kind())) {
            throw new FdxException("Graph resource has an unsupported type");
        }
        boolean workgroup =
                type.kind() == ShaderGraphTypeKind.WORKGROUP_ARRAY;
        if (workgroup ? group != -1 || binding != -1
                : group < 0 || binding < 0) {
            throw new FdxException("Graph resource group and binding cannot be negative");
        }
        this.id = id;
        this.type = type;
        this.group = group;
        this.binding = binding;
    }

    public static ShaderGraphResource of(String id, ShaderGraphType type,
            int group, int binding) {
        return new ShaderGraphResource(ShaderGraphId.of(id), type, group, binding);
    }

    public static ShaderGraphResource workgroup(String id,
            ShaderGraphType type) {
        if (type == null
                || type.kind() != ShaderGraphTypeKind.WORKGROUP_ARRAY) {
            throw new FdxException(
                    "Workgroup resource requires a workgroup-array type");
        }
        return new ShaderGraphResource(ShaderGraphId.of(id), type, -1, -1);
    }

    public ShaderGraphId id() {
        return id;
    }

    public ShaderGraphType type() {
        return type;
    }

    public int group() {
        return group;
    }

    public int binding() {
        return binding;
    }

    public boolean bound() {
        return group >= 0;
    }

    @Override
    public int compareTo(ShaderGraphResource other) {
        return id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphResource other
                && id.equals(other.id) && type.equals(other.type)
                && group == other.group && binding == other.binding;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, group, binding);
    }

    private static boolean isResourceType(ShaderGraphTypeKind kind) {
        return kind == ShaderGraphTypeKind.TEXTURE
                || kind == ShaderGraphTypeKind.SAMPLER
                || kind == ShaderGraphTypeKind.STORAGE_BUFFER
                || kind == ShaderGraphTypeKind.STORAGE_TEXTURE
                || kind == ShaderGraphTypeKind.WORKGROUP_ARRAY;
    }
}
