package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechnique;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnosticSeverity;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;

/**
 * Deterministic compilation result for a whole compute technique.
 */
public final class ShaderGraphComputeTechniqueCompileResult {
    private final ShaderGraphComputeTechnique technique;
    private final ShaderGraphCompiledComputePass[] passes;
    private final ShaderGraphDiagnostic[] diagnostics;

    ShaderGraphComputeTechniqueCompileResult(
            ShaderGraphComputeTechnique technique,
            ShaderGraphCompiledComputePass[] passes,
            ShaderGraphDiagnostic[] diagnostics) {
        if (technique == null || passes == null || diagnostics == null) {
            throw new FdxException(
                    "Compute technique result is incomplete");
        }
        this.technique = technique;
        this.passes = passes.clone();
        this.diagnostics = diagnostics.clone();
    }

    public boolean success() {
        if (passes.length != technique.passes().length) {
            return false;
        }
        for (ShaderGraphDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity()
                    == ShaderGraphDiagnosticSeverity.ERROR) {
                return false;
            }
        }
        return true;
    }

    public ShaderGraphComputeTechnique technique() {
        return technique;
    }

    public ShaderGraphCompiledComputePass[] passes() {
        return passes.clone();
    }

    public ShaderGraphCompiledComputePass pass(ShaderPassId id) {
        for (ShaderGraphCompiledComputePass pass : passes) {
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
