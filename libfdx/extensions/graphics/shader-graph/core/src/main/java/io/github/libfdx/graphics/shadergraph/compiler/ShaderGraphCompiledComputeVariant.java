package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeVariant;
import io.github.libfdx.core.FdxException;

/**
 * One compute variant paired with its compiled WGSL program.
 */
public final class ShaderGraphCompiledComputeVariant {
    private final ShaderGraphComputeVariant variant;
    private final ShaderGraphComputeCompileResult compilation;

    ShaderGraphCompiledComputeVariant(ShaderGraphComputeVariant variant,
            ShaderGraphComputeCompileResult compilation) {
        if (variant == null || compilation == null) {
            throw new FdxException(
                    "Compiled compute variant is incomplete");
        }
        this.variant = variant;
        this.compilation = compilation;
    }

    public ShaderGraphComputeVariant variant() {
        return variant;
    }

    public ShaderGraphComputeCompileResult compilation() {
        return compilation;
    }
}
