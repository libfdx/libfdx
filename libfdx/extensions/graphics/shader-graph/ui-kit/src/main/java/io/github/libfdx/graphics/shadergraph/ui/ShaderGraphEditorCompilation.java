package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnosticSeverity;
import java.util.Arrays;

/**
 * Immutable canonical/target compilation result tagged with its request
 * generation.
 */
public final class ShaderGraphEditorCompilation {
    private final long generation;
    private final long semanticRevision;
    private final String semanticHash;
    private final String canonicalWgsl;
    private final ShaderGraphDiagnostic[] diagnostics;
    private final ShaderGraphEditorArtifact[] artifacts;

    public ShaderGraphEditorCompilation(long generation, long semanticRevision,
            String semanticHash, String canonicalWgsl,
            ShaderGraphDiagnostic[] diagnostics,
            ShaderGraphEditorArtifact[] artifacts) {
        if (generation <= 0 || semanticRevision < 0 || semanticHash == null
                || canonicalWgsl == null || diagnostics == null || artifacts == null) {
            throw new FdxException("Shader graph editor compilation is incomplete");
        }
        this.generation = generation;
        this.semanticRevision = semanticRevision;
        this.semanticHash = semanticHash;
        this.canonicalWgsl = canonicalWgsl;
        this.diagnostics = diagnostics.clone();
        Arrays.sort(this.diagnostics);
        this.artifacts = artifacts.clone();
    }

    public long generation() {
        return generation;
    }

    public long semanticRevision() {
        return semanticRevision;
    }

    public String semanticHash() {
        return semanticHash;
    }

    public String canonicalWgsl() {
        return canonicalWgsl;
    }

    public ShaderGraphDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }

    public ShaderGraphEditorArtifact[] artifacts() {
        return artifacts.clone();
    }

    public boolean success() {
        if (canonicalWgsl.isEmpty()) {
            return false;
        }
        for (ShaderGraphDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == ShaderGraphDiagnosticSeverity.ERROR) {
                return false;
            }
        }
        return true;
    }
}
