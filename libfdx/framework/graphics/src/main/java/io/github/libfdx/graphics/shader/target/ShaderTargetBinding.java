package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * One concrete resource slot in a translated shader target.
 *
 * <p>A canonical WGSL binding can expand to multiple target slots. External
 * textures are the common example: one WGSL binding can become metadata,
 * texture-plane, and sampler resources.</p>
 *
 * @author xpenatan
 */
public final class ShaderTargetBinding implements Comparable<ShaderTargetBinding> {
    private final String namespace;
    private final int group;
    private final int binding;
    private final String role;
    private final String name;

    private ShaderTargetBinding(String namespace, int group, int binding, String role, String name) {
        if (namespace == null || namespace.trim().length() == 0) {
            throw new FdxException("Shader target binding namespace cannot be empty");
        }
        if (group < 0 || binding < 0) {
            throw new FdxException("Shader target binding indices cannot be negative");
        }
        if (role == null || role.trim().length() == 0) {
            throw new FdxException("Shader target binding role cannot be empty");
        }
        this.namespace = namespace.trim();
        this.group = group;
        this.binding = binding;
        this.role = role.trim();
        this.name = name != null ? name : "";
    }

    /**
     * Creates a translated target slot.
     *
     * @param namespace the target resource namespace, such as {@code buffer} or {@code sampler}
     * @param group the target group, descriptor set, or register space
     * @param binding the target binding or register
     * @param role the slot's role within the canonical resource
     * @param name the translated resource name, or an empty string when the target does not expose one
     * @return the target slot
     */
    public static ShaderTargetBinding of(String namespace, int group, int binding, String role, String name) {
        return new ShaderTargetBinding(namespace, group, binding, role, name);
    }

    public String namespace() {
        return namespace;
    }

    public int group() {
        return group;
    }

    public int binding() {
        return binding;
    }

    public String role() {
        return role;
    }

    public String name() {
        return name;
    }

    @Override
    public int compareTo(ShaderTargetBinding other) {
        if (other == null) {
            return 1;
        }
        int comparison = namespace.compareTo(other.namespace);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(group, other.group);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(binding, other.binding);
        return comparison != 0 ? comparison : role.compareTo(other.role);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderTargetBinding other
                && group == other.group && binding == other.binding
                && namespace.equals(other.namespace) && role.equals(other.role)
                && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, group, binding, role, name);
    }
}
