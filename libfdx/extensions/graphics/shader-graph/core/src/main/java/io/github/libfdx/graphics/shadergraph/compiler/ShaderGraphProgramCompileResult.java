package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnosticSeverity;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderSourceSpan;
import io.github.libfdx.graphics.shadergraph.ir.ShaderIrModule;
import io.github.libfdx.core.FdxException;

/**
 * Complete vertex/fragment linkage result.
 */
public final class ShaderGraphProgramCompileResult {
    private final ShaderIrModule module;
    private final String wgsl;
    private final String semanticHash;
    private final String vertexEntryPoint;
    private final String fragmentEntryPoint;
    private final ShaderGraphDiagnostic[] diagnostics;
    private final ShaderSourceSpan[] sourceMap;

    ShaderGraphProgramCompileResult(ShaderIrModule module, String wgsl,
            String semanticHash, String vertexEntryPoint,
            String fragmentEntryPoint, ShaderGraphDiagnostic[] diagnostics,
            ShaderSourceSpan[] sourceMap) {
        if (semanticHash == null || vertexEntryPoint == null
                || fragmentEntryPoint == null || diagnostics == null
                || sourceMap == null) {
            throw new FdxException("Shader graph program result is incomplete");
        }
        this.module = module;
        this.wgsl = wgsl != null ? wgsl : "";
        this.semanticHash = semanticHash;
        this.vertexEntryPoint = vertexEntryPoint;
        this.fragmentEntryPoint = fragmentEntryPoint;
        this.diagnostics = diagnostics.clone();
        this.sourceMap = sourceMap.clone();
    }

    public boolean success() {
        if (module == null || wgsl.isEmpty()) {
            return false;
        }
        for (ShaderGraphDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == ShaderGraphDiagnosticSeverity.ERROR) {
                return false;
            }
        }
        return true;
    }

    public ShaderIrModule module() {
        return module;
    }

    public String wgsl() {
        return wgsl;
    }

    public String semanticHash() {
        return semanticHash;
    }

    public String vertexEntryPoint() {
        return vertexEntryPoint;
    }

    public String fragmentEntryPoint() {
        return fragmentEntryPoint;
    }

    public ShaderGraphDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }

    public ShaderSourceSpan[] sourceMap() {
        return sourceMap.clone();
    }
}
