package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.reflection.ShaderBindingSemantic;
import io.github.libfdx.core.FdxException;

/**
 * Immutable framework semantics merged onto a Tint-proven physical interface.
 */
public final class ShaderSemanticOverlay {
    private static final ShaderBindingSemantic[] EMPTY_BINDINGS = new ShaderBindingSemantic[0];
    private static final ShaderSemanticOverlay EMPTY = new ShaderSemanticOverlay(EMPTY_BINDINGS);

    private final ShaderBindingSemantic[] bindings;

    private ShaderSemanticOverlay(ShaderBindingSemantic[] bindings) {
        this.bindings = bindings != null ? bindings.clone() : EMPTY_BINDINGS;
        for (int i = 0; i < this.bindings.length; i++) {
            if (this.bindings[i] == null) {
                throw new FdxException("Shader semantic overlay binding cannot be null");
            }
            for (int j = 0; j < i; j++) {
                if (this.bindings[i].group() == this.bindings[j].group()
                        && this.bindings[i].binding() == this.bindings[j].binding()) {
                    throw new FdxException("Duplicate shader semantic binding: group " + this.bindings[i].group()
                            + " binding " + this.bindings[i].binding());
                }
            }
        }
    }

    public static ShaderSemanticOverlay empty() {
        return EMPTY;
    }

    public static ShaderSemanticOverlay of(ShaderBindingSemantic... bindings) {
        return bindings == null || bindings.length == 0 ? EMPTY : new ShaderSemanticOverlay(bindings);
    }

    public ShaderBindingSemantic[] bindings() {
        return bindings.clone();
    }

    public int bindingCount() {
        return bindings.length;
    }

    public ShaderBindingSemantic binding(int index) {
        return bindings[index];
    }
}
