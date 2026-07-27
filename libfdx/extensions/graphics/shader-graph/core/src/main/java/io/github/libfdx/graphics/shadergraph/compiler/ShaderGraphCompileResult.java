package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.ir.ShaderIrModule;
import io.github.libfdx.core.FdxException;

/**
 * Immutable result of graph semantic compilation and canonical WGSL emission.
 */
public final class ShaderGraphCompileResult {
    private final ShaderIrModule module;
    private final String wgsl;
    private final String libraryWgsl;
    private final String semanticHash;
    private final ShaderGraphDiagnostic[] diagnostics;
    private final ShaderSourceSpan[] sourceMap;

    public ShaderGraphCompileResult(ShaderIrModule module, String wgsl,
            String libraryWgsl,
            String semanticHash, ShaderGraphDiagnostic[] diagnostics,
            ShaderSourceSpan[] sourceMap) {
        if (semanticHash == null || diagnostics == null || sourceMap == null) {
            throw new FdxException("Shader graph compile result is incomplete");
        }
        this.module = module;
        this.wgsl = wgsl != null ? wgsl : "";
        this.libraryWgsl = libraryWgsl != null ? libraryWgsl : "";
        this.semanticHash = semanticHash;
        this.diagnostics = diagnostics.clone();
        this.sourceMap = sourceMap.clone();
    }

    public boolean success() {
        for (ShaderGraphDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == ShaderGraphDiagnosticSeverity.ERROR) {
                return false;
            }
        }
        return module != null && !wgsl.isEmpty();
    }

    public ShaderIrModule module() {
        return module;
    }

    public String wgsl() {
        return wgsl;
    }

    /**
     * Returns declarations and functions without validation entry points for
     * structured composition into a renderer-owned template.
     *
     * @return canonical WGSL library source
     */
    public String libraryWgsl() {
        return libraryWgsl;
    }

    public String semanticHash() {
        return semanticHash;
    }

    public ShaderGraphDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }

    public ShaderSourceSpan[] sourceMap() {
        return sourceMap.clone();
    }
}
