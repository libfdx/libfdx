package io.github.libfdx.runtime.core.shader;

/**
 * One concrete resource slot emitted by a runtime shader target writer.
 *
 * @author xpenatan
 */
public final class RuntimeShaderTargetBinding {
    private final String namespace;
    private final int group;
    private final int binding;
    private final String role;
    private final String name;

    RuntimeShaderTargetBinding(String namespace, int group, int binding, String role, String name) {
        this.namespace = namespace;
        this.group = group;
        this.binding = binding;
        this.role = role;
        this.name = name;
    }

    /**
     * Creates one target binding slot.
     *
     * @param namespace the target resource namespace
     * @param group the target group, set, or register space
     * @param binding the target binding or register
     * @param role the slot role
     * @param name the translated name, or an empty string
     * @return the target binding
     */
    public static RuntimeShaderTargetBinding of(String namespace, int group, int binding,
            String role, String name) {
        if (namespace == null || namespace.trim().length() == 0 || group < 0 || binding < 0
                || role == null || role.trim().length() == 0) {
            throw new IllegalArgumentException("Runtime shader target binding is invalid");
        }
        return new RuntimeShaderTargetBinding(namespace.trim(), group, binding,
                role.trim(), name != null ? name : "");
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
}
