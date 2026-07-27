package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCache;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCacheEntry;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCacheContext;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnosticSeverity;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDocumentCompilation;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDocumentCompiler;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentCodec;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentReadResult;

import java.util.ArrayList;
import java.util.List;

/**
 * One-file string persistence boundary. File selection and I/O remain
 * host-owned.
 */
public final class ShaderGraphEditorPersistence {
    private ShaderGraphEditorPersistence() {
    }

    /**
     * Writes semantic and editor state into one self-contained
     * {@code .fdxgraph}.
     */
    public static String write(ShaderGraphEditorDocument document,
            ShaderGraphEditorLayout layout) {
        return ShaderGraphDocumentCodec.write(
                withEditorState(document, layout));
    }

    /**
     * Atomically compiles every requested context and embeds the resulting
     * entries in the same {@code .fdxgraph}. Existing entries for other exact
     * keys are preserved. If any requested compilation fails, the result has
     * no source and therefore cannot publish partial or stale entries.
     */
    public static ShaderGraphEditorSaveResult writeWithCompiledCache(
            ShaderGraphEditorDocument document,
            ShaderGraphEditorLayout layout,
            ShaderGraphCacheContext... contexts) {
        ShaderGraphDocument value =
                withEditorState(document, layout);
        if (contexts == null || contexts.length == 0) {
            throw new FdxException(
                    "Save with compiled cache requires a target context");
        }
        ShaderGraphDocumentCompiler compiler =
                new ShaderGraphDocumentCompiler();
        List<ShaderGraphCompiledCacheEntry> entries =
                new ArrayList<>();
        List<ShaderGraphDiagnostic> diagnostics =
                new ArrayList<>();
        int hits = 0;
        int misses = 0;
        for (ShaderGraphCacheContext context : contexts) {
            if (context == null) {
                throw new FdxException(
                        "Compiled-cache target context cannot be null");
            }
            ShaderGraphDocumentCompilation compilation;
            try {
                compilation = compiler.compile(value, context);
            } catch (RuntimeException failure) {
                diagnostics.add(failureDiagnostic(document, failure));
                return failed(diagnostics, hits, misses);
            }
            for (ShaderGraphDiagnostic diagnostic
                    : compilation.diagnostics()) {
                diagnostics.add(diagnostic);
            }
            if (!compilation.success()) {
                return failed(diagnostics, hits, misses);
            }
            if (compilation.cacheHit()) {
                hits++;
            } else {
                misses++;
            }
            for (ShaderGraphCompiledCacheEntry entry
                    : compilation.entries()) {
                entries.add(entry);
            }
        }

        ShaderGraphCompiledCache existing =
                value.compiledCache() != null
                        ? value.compiledCache()
                        : ShaderGraphCompiledCache.empty();
        ShaderGraphCompiledCache merged;
        try {
            merged = existing.replacing(entries.toArray(
                    ShaderGraphCompiledCacheEntry[]::new));
        } catch (RuntimeException failure) {
            diagnostics.add(failureDiagnostic(document, failure));
            return failed(diagnostics, hits, misses);
        }
        ShaderGraphDocument saved =
                value.withCompiledCache(merged);
        return new ShaderGraphEditorSaveResult(
                ShaderGraphDocumentCodec.write(saved),
                ShaderGraphEditorDocument.of(saved),
                diagnostics.toArray(ShaderGraphDiagnostic[]::new),
                hits, misses);
    }

    private static ShaderGraphDocument withEditorState(
            ShaderGraphEditorDocument document,
            ShaderGraphEditorLayout layout) {
        if (document == null || layout == null) {
            throw new FdxException("Shader graph editor document cannot be null");
        }
        return document.shaderDocument()
                .withEditorJson(ShaderGraphEditorLayoutCodec.write(
                        layout.reconcile(document)));
    }

    /**
     * Loads required semantic data and then reconciles the optional in-file
     * editor block. Unsupported editor data is replaced by deterministic
     * default layout and never prevents semantic loading.
     *
     * @param source one self-contained shader graph document
     * @return the loaded document and reconciled layout
     */
    public static ShaderGraphEditorLoadResult read(String source) {
        ShaderGraphDocumentReadResult decoded =
                ShaderGraphDocumentCodec.readResult(source);
        ShaderGraphDocument value = decoded.document();
        ShaderGraphEditorDocument document =
                ShaderGraphEditorDocument.of(value);
        if (!value.hasEditor()) {
            return new ShaderGraphEditorLoadResult(document,
                    ShaderGraphEditorLayout.forDocument(document), "",
                    decoded.cacheRejections());
        }
        try {
            ShaderGraphEditorLayout layout =
                    ShaderGraphEditorLayoutCodec.read(value.editorJson())
                            .reconcile(document);
            return new ShaderGraphEditorLoadResult(
                    document, layout, "",
                    decoded.cacheRejections());
        } catch (RuntimeException error) {
            String message = error.getMessage() != null ? error.getMessage()
                    : error.getClass().getSimpleName();
            return new ShaderGraphEditorLoadResult(document,
                    ShaderGraphEditorLayout.forDocument(document),
                    "Editor state was ignored: " + message,
                    decoded.cacheRejections());
        }
    }

    private static ShaderGraphEditorSaveResult failed(
            List<ShaderGraphDiagnostic> diagnostics,
            int hits, int misses) {
        return new ShaderGraphEditorSaveResult(
                null, null,
                diagnostics.toArray(ShaderGraphDiagnostic[]::new),
                hits, misses);
    }

    private static ShaderGraphDiagnostic failureDiagnostic(
            ShaderGraphEditorDocument document,
            RuntimeException failure) {
        String message = failure.getMessage() != null
                ? failure.getMessage()
                : failure.getClass().getSimpleName();
        return new ShaderGraphDiagnostic(
                ShaderGraphDiagnosticSeverity.ERROR,
                "FDXE_CACHE_COMPILE_FAILURE",
                "Compiled-cache save failed: " + message,
                document.graphs()[0].id(), null, null);
    }
}
