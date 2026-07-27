package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceUse;
import io.github.libfdx.core.FdxException;

import java.util.Arrays;

/**
 * Canonical and translated shader interface plus explicit identity remaps.
 *
 * @author xpenatan
 */
public final class ShaderTranslatedInterface {
    private final ShaderReflection canonical;
    private final ShaderReflection target;
    private final ShaderEntryPointRemap[] entryPoints;
    private final ShaderBindingRemap[] bindings;

    private ShaderTranslatedInterface(ShaderReflection canonical, ShaderReflection target,
            ShaderEntryPointRemap[] entryPoints, ShaderBindingRemap[] bindings) {
        this.canonical = canonical != null ? canonical : ShaderReflection.empty();
        this.target = target != null ? target : this.canonical;
        this.entryPoints = entryPoints != null ? entryPoints.clone() : new ShaderEntryPointRemap[0];
        this.bindings = bindings != null ? bindings.clone() : new ShaderBindingRemap[0];
        requireNonNullElements();
        Arrays.sort(this.entryPoints);
        Arrays.sort(this.bindings);
        validate();
    }

    /**
     * Creates a translated interface.
     *
     * @param canonical the canonical interface
     * @param target the target-reflected interface
     * @param entryPoints the entry-point remaps
     * @param bindings the binding remaps
     * @return the translated interface
     */
    public static ShaderTranslatedInterface of(ShaderReflection canonical, ShaderReflection target,
            ShaderEntryPointRemap[] entryPoints, ShaderBindingRemap[] bindings) {
        return new ShaderTranslatedInterface(canonical, target, entryPoints, bindings);
    }

    /**
     * Creates identity remaps for a translation that preserves canonical names and bindings.
     *
     * @param canonical the canonical interface
     * @param selections the compiled entries
     * @return the translated interface
     */
    public static ShaderTranslatedInterface identity(ShaderReflection canonical,
            ShaderEntryPointSelection[] selections) {
        ShaderReflection actual = canonical != null ? canonical : ShaderReflection.empty();
        ShaderEntryPointSelection[] selected = selections != null
                ? selections : new ShaderEntryPointSelection[0];
        ShaderEntryPointRemap[] entries = new ShaderEntryPointRemap[selected.length];
        for (int i = 0; i < selected.length; i++) {
            ShaderEntryPointSelection selection = selected[i];
            entries[i] = ShaderEntryPointRemap.of(selection.stage(), selection.entryPoint(),
                    selection.entryPoint());
        }
        ShaderBinding[] reflectedBindings = actual.bindings();
        ShaderBindingRemap[] remaps = new ShaderBindingRemap[reflectedBindings.length];
        for (int i = 0; i < reflectedBindings.length; i++) {
            remaps[i] = ShaderBindingRemap.identity(reflectedBindings[i]);
        }
        return new ShaderTranslatedInterface(actual, actual, entries, remaps);
    }

    public ShaderReflection canonical() {
        return canonical;
    }

    public ShaderReflection target() {
        return target;
    }

    public ShaderEntryPointRemap[] entryPoints() {
        return entryPoints.clone();
    }

    public ShaderBindingRemap[] bindings() {
        return bindings.clone();
    }

    private void requireNonNullElements() {
        for (ShaderEntryPointRemap entryPoint : entryPoints) {
            if (entryPoint == null) {
                throw new FdxException("Shader translated entry-point remap cannot be null");
            }
        }
        for (ShaderBindingRemap binding : bindings) {
            if (binding == null) {
                throw new FdxException("Shader translated binding remap cannot be null");
            }
        }
    }

    /**
     * Resolves a canonical binding remap.
     *
     * @param group the canonical group
     * @param binding the canonical binding
     * @return the remap, or null
     */
    public ShaderBindingRemap findBinding(int group, int binding) {
        ShaderBindingRemap fallback = null;
        for (ShaderBindingRemap remap : bindings) {
            if (remap.sourceGroup() == group && remap.sourceBinding() == binding) {
                if (remap.stage() == ShaderArtifactStage.MODULE) {
                    return remap;
                }
                if (fallback == null) {
                    fallback = remap;
                }
            }
        }
        return fallback;
    }

    /**
     * Resolves a binding remap for one translated entry point, falling back to
     * a module-wide identity/remap when present.
     *
     * @param stage the programmable stage
     * @param entryPoint the canonical entry point
     * @param group the canonical group
     * @param binding the canonical binding
     * @return the scoped remap, or null
     */
    public ShaderBindingRemap findBinding(ShaderArtifactStage stage, String entryPoint,
            int group, int binding) {
        ShaderBindingRemap module = null;
        for (ShaderBindingRemap remap : bindings) {
            if (remap.sourceGroup() != group || remap.sourceBinding() != binding) {
                continue;
            }
            if (remap.stage() == stage && remap.sourceEntryPoint().equals(entryPoint)) {
                return remap;
            }
            if (remap.stage() == ShaderArtifactStage.MODULE) {
                module = remap;
            }
        }
        return module;
    }

    private void validate() {
        for (int i = 0; i < entryPoints.length; i++) {
            if (i > 0 && entryPoints[i - 1].compareTo(entryPoints[i]) == 0) {
                throw new FdxException("Duplicate shader translated entry-point remap: "
                        + entryPoints[i].sourceName());
            }
        }
        for (int i = 0; i < bindings.length; i++) {
            if (i > 0 && bindings[i - 1].compareTo(bindings[i]) == 0) {
                throw new FdxException("Duplicate shader translated binding remap: "
                        + bindings[i].stage() + " " + bindings[i].sourceEntryPoint() + " "
                        + bindings[i].sourceGroup() + ':' + bindings[i].sourceBinding());
            }
            ShaderBindingRemap remap = bindings[i];
            if (canonical.findBinding(remap.sourceGroup(), remap.sourceBinding()) == null) {
                throw new FdxException("Translated shader interface maps an unknown canonical binding "
                        + remap.sourceGroup() + ':' + remap.sourceBinding());
            }
            if (canonical.complete() && remap.stage() != ShaderArtifactStage.MODULE
                    && canonical.findEntryPoint(toShaderStage(remap.stage()),
                    remap.sourceEntryPoint()) == null) {
                throw new FdxException("Translated shader binding remap references an unknown canonical "
                        + "entry point: " + remap.stage() + ' ' + remap.sourceEntryPoint());
            }
        }
        if (canonical.complete()) {
            for (ShaderEntryPointRemap remap : entryPoints) {
                ShaderEntryPoint entryPoint = canonical.findEntryPoint(toShaderStage(remap.stage()),
                        remap.sourceName());
                if (entryPoint == null) {
                    throw new FdxException("Translated shader interface references an unknown canonical "
                            + "entry point: " + remap.stage() + ' ' + remap.sourceName());
                }
                for (ShaderResourceUse use : entryPoint.resources()) {
                    if (findBinding(remap.stage(), remap.sourceName(),
                            use.group(), use.binding()) == null) {
                        throw new FdxException("Translated shader interface is missing resource used by "
                                + remap.sourceName() + ": " + use.group() + ':' + use.binding());
                    }
                }
            }
        } else {
            for (ShaderBinding binding : canonical.bindings()) {
                if (findBinding(binding.group(), binding.binding()) == null) {
                    throw new FdxException("Translated shader interface is missing canonical binding "
                            + binding.group() + ':' + binding.binding());
                }
            }
        }
    }

    private static ShaderStage toShaderStage(ShaderArtifactStage stage) {
        return switch (stage) {
            case VERTEX -> ShaderStage.VERTEX;
            case FRAGMENT -> ShaderStage.FRAGMENT;
            case COMPUTE -> ShaderStage.COMPUTE;
            case MODULE -> throw new FdxException("A module cannot be an entry-point remap");
        };
    }
}
