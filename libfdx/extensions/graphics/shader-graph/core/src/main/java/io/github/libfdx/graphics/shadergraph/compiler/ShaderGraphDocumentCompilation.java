package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCache;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCacheEntry;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;

/**
 * Headless result shared by runtime loading and editor cache population.
 */
public final class ShaderGraphDocumentCompilation {
    private final ShaderGraphDocument document;
    private final boolean cacheHit;
    private final ShaderGraphCompiledCacheEntry[] entries;
    private final ShaderGraphDiagnostic[] diagnostics;

    ShaderGraphDocumentCompilation(ShaderGraphDocument document,
            boolean cacheHit,
            ShaderGraphCompiledCacheEntry[] entries,
            ShaderGraphDiagnostic[] diagnostics) {
        this.document = document;
        this.cacheHit = cacheHit;
        this.entries = entries.clone();
        this.diagnostics = diagnostics.clone();
    }

    public ShaderGraphDocument document() {
        return document;
    }

    public boolean success() {
        if (entries.length == 0) {
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

    public boolean cacheHit() {
        return cacheHit;
    }

    public boolean cacheMiss() {
        return !cacheHit;
    }

    public ShaderGraphCompiledCacheEntry[] entries() {
        return entries.clone();
    }

    public ShaderGraphCompiledCache cache() {
        return ShaderGraphCompiledCache.of(entries);
    }

    public ShaderGraphDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }

    public ShaderGraphCompiledCacheEntry entry(String compilationUnit,
            String passId, String variantKey) {
        String pass = passId != null ? passId : "";
        String variant = variantKey != null ? variantKey : "";
        for (ShaderGraphCompiledCacheEntry entry : entries) {
            if (entry.key().compilationUnit().equals(compilationUnit)
                    && entry.key().passId().equals(pass)
                    && entry.key().variantKey().equals(variant)) {
                return entry;
            }
        }
        return null;
    }
}
