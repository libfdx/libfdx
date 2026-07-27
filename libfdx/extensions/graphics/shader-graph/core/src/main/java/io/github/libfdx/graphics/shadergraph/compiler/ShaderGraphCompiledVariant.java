package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import io.github.libfdx.core.FdxException;

/**
 * One technique variant paired with its linked program compilation.
 */
public final class ShaderGraphCompiledVariant {
    private final ShaderGraphVariant variant;
    private final ShaderGraphProgramCompileResult compilation;

    ShaderGraphCompiledVariant(ShaderGraphVariant variant,
            ShaderGraphProgramCompileResult compilation) {
        if (variant == null || compilation == null) {
            throw new FdxException(
                    "Compiled shader graph variant is incomplete");
        }
        this.variant = variant;
        this.compilation = compilation;
    }

    public ShaderGraphVariant variant() {
        return variant;
    }

    public ShaderGraphProgramCompileResult compilation() {
        return compilation;
    }
}
