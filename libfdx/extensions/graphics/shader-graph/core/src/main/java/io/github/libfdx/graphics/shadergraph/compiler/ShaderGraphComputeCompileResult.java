package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnosticSeverity;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderSourceSpan;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrModule;
import io.github.libfdx.core.FdxException;

/**
 * Complete deterministic compute-program compilation result.
 */
public final class ShaderGraphComputeCompileResult {
    private final ShaderGraphComputeProgram program;
    private final ShaderIrModule module;
    private final String wgsl;
    private final ShaderGraphDiagnostic[] diagnostics;
    private final ShaderSourceSpan[] sourceMap;

    ShaderGraphComputeCompileResult(ShaderGraphComputeProgram program,
            ShaderIrModule module, String wgsl,
            ShaderGraphDiagnostic[] diagnostics,
            ShaderSourceSpan[] sourceMap) {
        if (program == null || diagnostics == null || sourceMap == null) {
            throw new FdxException(
                    "Shader graph compute result is incomplete");
        }
        this.program = program;
        this.module = module;
        this.wgsl = wgsl != null ? wgsl : "";
        this.diagnostics = diagnostics.clone();
        this.sourceMap = sourceMap.clone();
    }

    public boolean success() {
        if (module == null || wgsl.isEmpty()) {
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

    public ShaderGraphComputeProgram program() {
        return program;
    }

    public ShaderIrModule module() {
        return module;
    }

    public String wgsl() {
        return wgsl;
    }

    public String semanticHash() {
        return program.semanticHash();
    }

    public String entryPoint() {
        return program.entryPoint();
    }

    public ShaderGraphDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }

    public ShaderSourceSpan[] sourceMap() {
        return sourceMap.clone();
    }
}
