package io.github.libfdx.graphics.shadergraph.ir;

import io.github.libfdx.core.FdxException;

/**
 * Dependency-ordered typed IR module. The root function is last.
 */
public final class ShaderIrModule {
    private final ShaderIrFunction[] functions;

    public ShaderIrModule(ShaderIrFunction[] functions) {
        if (functions == null || functions.length == 0) {
            throw new FdxException("Shader IR module requires at least one function");
        }
        this.functions = functions.clone();
    }

    public ShaderIrFunction[] functions() {
        return functions.clone();
    }

    public ShaderIrFunction root() {
        return functions[functions.length - 1];
    }
}
