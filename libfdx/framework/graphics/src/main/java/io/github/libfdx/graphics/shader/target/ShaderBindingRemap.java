package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.core.FdxException;

import java.util.Arrays;

/**
 * Maps one canonical resource binding to one or more translated target slots.
 *
 * @author xpenatan
 */
public final class ShaderBindingRemap implements Comparable<ShaderBindingRemap> {
    private final ShaderArtifactStage stage;
    private final String sourceEntryPoint;
    private final int sourceGroup;
    private final int sourceBinding;
    private final ShaderTargetBinding[] targets;
    private final ShaderBindingRemapKind kind;

    private ShaderBindingRemap(ShaderArtifactStage stage, String sourceEntryPoint,
            int sourceGroup, int sourceBinding,
            ShaderTargetBinding[] targets, ShaderBindingRemapKind kind) {
        if (stage == null) {
            throw new FdxException("Shader binding remap stage cannot be null");
        }
        String entryPoint = sourceEntryPoint != null ? sourceEntryPoint.trim() : "";
        if (stage == ShaderArtifactStage.MODULE && entryPoint.length() != 0
                || stage != ShaderArtifactStage.MODULE && entryPoint.length() == 0) {
            throw new FdxException("Shader binding remap entry-point scope is invalid");
        }
        if (sourceGroup < 0 || sourceBinding < 0) {
            throw new FdxException("Shader binding remap source indices cannot be negative");
        }
        if (targets == null || targets.length == 0) {
            throw new FdxException("Shader binding remap must contain at least one target slot");
        }
        this.stage = stage;
        this.sourceEntryPoint = entryPoint;
        this.sourceGroup = sourceGroup;
        this.sourceBinding = sourceBinding;
        this.targets = targets.clone();
        for (ShaderTargetBinding target : this.targets) {
            if (target == null) {
                throw new FdxException("Shader target binding cannot be null");
            }
        }
        Arrays.sort(this.targets);
        for (int i = 0; i < this.targets.length; i++) {
            if (i > 0 && this.targets[i - 1].compareTo(this.targets[i]) == 0) {
                throw new FdxException("Duplicate shader target binding slot: "
                        + this.targets[i].namespace() + ' ' + this.targets[i].group()
                        + ':' + this.targets[i].binding() + ' ' + this.targets[i].role());
            }
        }
        this.kind = kind != null ? kind : ShaderBindingRemapKind.DIRECT;
    }

    /**
     * Creates a binding remap.
     *
     * @param sourceGroup the canonical group
     * @param sourceBinding the canonical binding
     * @param targetNamespace the target binding namespace
     * @param targetGroup the target group/set/space
     * @param targetBinding the target binding/register
     * @param targetName the translated resource name
     * @param kind the remap kind
     * @return the remap
     */
    public static ShaderBindingRemap of(int sourceGroup, int sourceBinding, String targetNamespace,
            int targetGroup, int targetBinding, String targetName, ShaderBindingRemapKind kind) {
        return new ShaderBindingRemap(ShaderArtifactStage.MODULE, "", sourceGroup, sourceBinding,
                new ShaderTargetBinding[] {
                ShaderTargetBinding.of(targetNamespace, targetGroup, targetBinding, "resource", targetName)
        }, kind);
    }

    /**
     * Creates a remap that can represent target resource expansion.
     *
     * @param sourceGroup the canonical group
     * @param sourceBinding the canonical binding
     * @param targets the concrete target slots
     * @param kind the remap kind
     * @return the remap
     */
    public static ShaderBindingRemap ofTargets(int sourceGroup, int sourceBinding,
            ShaderTargetBinding[] targets, ShaderBindingRemapKind kind) {
        return new ShaderBindingRemap(ShaderArtifactStage.MODULE, "",
                sourceGroup, sourceBinding, targets, kind);
    }

    /**
     * Creates an entry-point-scoped remap.
     *
     * @param stage the programmable stage
     * @param sourceEntryPoint the canonical entry-point name
     * @param sourceGroup the canonical group
     * @param sourceBinding the canonical binding
     * @param targets the concrete target slots
     * @param kind the remap kind
     * @return the remap
     */
    public static ShaderBindingRemap ofEntryPoint(ShaderArtifactStage stage, String sourceEntryPoint,
            int sourceGroup, int sourceBinding, ShaderTargetBinding[] targets,
            ShaderBindingRemapKind kind) {
        if (stage == ShaderArtifactStage.MODULE) {
            throw new FdxException("An entry-point binding remap requires a programmable stage");
        }
        return new ShaderBindingRemap(stage, sourceEntryPoint,
                sourceGroup, sourceBinding, targets, kind);
    }

    /**
     * Creates an identity remap.
     *
     * @param binding the canonical binding
     * @return the remap
     */
    public static ShaderBindingRemap identity(ShaderBinding binding) {
        if (binding == null) {
            throw new FdxException("Shader binding cannot be null");
        }
        return new ShaderBindingRemap(ShaderArtifactStage.MODULE, "",
                binding.group(), binding.binding(), new ShaderTargetBinding[] {
                ShaderTargetBinding.of("group-binding", binding.group(), binding.binding(),
                        "resource", binding.name())
        }, ShaderBindingRemapKind.DIRECT);
    }

    public ShaderArtifactStage stage() {
        return stage;
    }

    public String sourceEntryPoint() {
        return sourceEntryPoint;
    }

    public int sourceGroup() {
        return sourceGroup;
    }

    public int sourceBinding() {
        return sourceBinding;
    }

    public String targetNamespace() {
        return targets[0].namespace();
    }

    public int targetGroup() {
        return targets[0].group();
    }

    public int targetBinding() {
        return targets[0].binding();
    }

    public String targetName() {
        return targets[0].name();
    }

    /**
     * Returns every concrete target slot for this canonical binding.
     *
     * @return a defensive copy of the target slots
     */
    public ShaderTargetBinding[] targets() {
        return targets.clone();
    }

    public int targetCount() {
        return targets.length;
    }

    public ShaderTargetBinding target(int index) {
        return targets[index];
    }

    public ShaderBindingRemapKind kind() {
        return kind;
    }

    @Override
    public int compareTo(ShaderBindingRemap other) {
        if (other == null) {
            return 1;
        }
        int comparison = stage.compareTo(other.stage);
        if (comparison != 0) {
            return comparison;
        }
        comparison = sourceEntryPoint.compareTo(other.sourceEntryPoint);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(sourceGroup, other.sourceGroup);
        return comparison != 0 ? comparison : Integer.compare(sourceBinding, other.sourceBinding);
    }
}
