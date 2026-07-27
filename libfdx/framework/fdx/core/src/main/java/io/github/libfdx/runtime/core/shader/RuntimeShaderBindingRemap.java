package io.github.libfdx.runtime.core.shader;

/**
 * Native mapping from one canonical WGSL binding to target resource slots.
 *
 * @author xpenatan
 */
public final class RuntimeShaderBindingRemap {
    private final int sourceGroup;
    private final int sourceBinding;
    private final RuntimeShaderBindingRemapKind kind;
    private final RuntimeShaderTargetBinding[] targets;

    RuntimeShaderBindingRemap(int sourceGroup, int sourceBinding,
            RuntimeShaderBindingRemapKind kind, RuntimeShaderTargetBinding[] targets) {
        this.sourceGroup = sourceGroup;
        this.sourceBinding = sourceBinding;
        this.kind = kind;
        this.targets = targets;
    }

    /**
     * Creates one canonical-to-target binding remap.
     *
     * @param sourceGroup the canonical group
     * @param sourceBinding the canonical binding
     * @param kind the mapping kind
     * @param targets the concrete target slots
     * @return the remap
     */
    public static RuntimeShaderBindingRemap of(int sourceGroup, int sourceBinding,
            RuntimeShaderBindingRemapKind kind, RuntimeShaderTargetBinding... targets) {
        if (sourceGroup < 0 || sourceBinding < 0 || kind == null
                || targets == null || targets.length == 0) {
            throw new IllegalArgumentException("Runtime shader binding remap is invalid");
        }
        RuntimeShaderTargetBinding[] copy = targets.clone();
        for (RuntimeShaderTargetBinding target : copy) {
            if (target == null) {
                throw new IllegalArgumentException("Runtime shader target binding cannot be null");
            }
        }
        return new RuntimeShaderBindingRemap(sourceGroup, sourceBinding, kind, copy);
    }

    public int sourceGroup() {
        return sourceGroup;
    }

    public int sourceBinding() {
        return sourceBinding;
    }

    public RuntimeShaderBindingRemapKind kind() {
        return kind;
    }

    public RuntimeShaderTargetBinding[] targets() {
        return targets.clone();
    }
}
