package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;

import java.util.Arrays;

/**
 * Atomic result of saving one document with requested compiled-cache entries.
 *
 * <p>A failed result has no source or saved document. Callers may still use
 * {@link ShaderGraphEditorPersistence#write(ShaderGraphEditorDocument,
 * ShaderGraphEditorLayout)} to save an unfinished semantic graph without a
 * compiled block.</p>
 */
public final class ShaderGraphEditorSaveResult {
    private final String source;
    private final ShaderGraphEditorDocument savedDocument;
    private final ShaderGraphDiagnostic[] diagnostics;
    private final int cacheHits;
    private final int cacheMisses;

    ShaderGraphEditorSaveResult(String source,
            ShaderGraphEditorDocument savedDocument,
            ShaderGraphDiagnostic[] diagnostics,
            int cacheHits, int cacheMisses) {
        if ((source == null) != (savedDocument == null)
                || diagnostics == null || cacheHits < 0
                || cacheMisses < 0) {
            throw new FdxException(
                    "Shader graph editor save result is incomplete");
        }
        this.source = source;
        this.savedDocument = savedDocument;
        this.diagnostics = diagnostics.clone();
        Arrays.sort(this.diagnostics);
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
    }

    public boolean success() {
        return source != null;
    }

    public ShaderGraphDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }

    public int cacheHits() {
        return cacheHits;
    }

    public int cacheMisses() {
        return cacheMisses;
    }

    /**
     * Returns the serialized one-file document.
     *
     * @throws FdxException when compilation failed and nothing was saved
     */
    public String source() {
        requireSuccess();
        return source;
    }

    /**
     * Returns the document represented by {@link #source()}.
     *
     * @throws FdxException when compilation failed and nothing was saved
     */
    public ShaderGraphEditorDocument savedDocument() {
        requireSuccess();
        return savedDocument;
    }

    private void requireSuccess() {
        if (!success()) {
            throw new FdxException(
                    "Failed shader graph compilation produced no saved file");
        }
    }
}
