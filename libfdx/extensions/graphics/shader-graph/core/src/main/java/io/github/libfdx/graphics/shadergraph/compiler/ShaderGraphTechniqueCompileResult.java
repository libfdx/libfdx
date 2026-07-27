package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnosticSeverity;
import io.github.libfdx.core.FdxException;

/**
 * Complete deterministic compilation result for a whole technique.
 */
public final class ShaderGraphTechniqueCompileResult {
    private final ShaderGraphTechnique technique;
    private final ShaderGraphCompiledPass[] passes;
    private final ShaderGraphDiagnostic[] diagnostics;

    ShaderGraphTechniqueCompileResult(ShaderGraphTechnique technique,
            ShaderGraphCompiledPass[] passes,
            ShaderGraphDiagnostic[] diagnostics) {
        if (technique == null || passes == null || diagnostics == null) {
            throw new FdxException(
                    "Shader graph technique result is incomplete");
        }
        this.technique = technique;
        this.passes = passes.clone();
        this.diagnostics = diagnostics.clone();
    }

    public boolean success() {
        for (ShaderGraphDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity()
                    == ShaderGraphDiagnosticSeverity.ERROR) {
                return false;
            }
        }
        return passes.length == technique.passes().length;
    }

    public ShaderGraphTechnique technique() {
        return technique;
    }

    public ShaderGraphCompiledPass[] passes() {
        return passes.clone();
    }

    public ShaderGraphCompiledPass pass(
            io.github.libfdx.graphics.shader.runtime.ShaderPassId id) {
        for (ShaderGraphCompiledPass pass : passes) {
            if (pass.pass().passId().equals(id)) {
                return pass;
            }
        }
        return null;
    }

    public ShaderGraphDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }
}
