package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCacheCodec;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCache;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDocumentCompilation;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;

/**
 * Runtime-ready result of loading one self-contained shader graph document.
 */
public final class ShaderGraphRuntimeAsset {
    private final ShaderGraphDocumentCompilation compilation;
    private final ShaderGraphCompiledCacheCodec.Rejection[] cacheRejections;
    private final ShaderGraphRuntimeGraph graph;
    private final ShaderGraphRenderTechnique renderTechnique;
    private final ShaderGraphComputeRuntimeTechnique computeTechnique;

    ShaderGraphRuntimeAsset(
            ShaderGraphDocumentCompilation compilation,
            ShaderGraphCompiledCacheCodec.Rejection[] cacheRejections,
            ShaderGraphRuntimeGraph graph,
            ShaderGraphRenderTechnique renderTechnique,
            ShaderGraphComputeRuntimeTechnique computeTechnique) {
        this.compilation = compilation;
        this.cacheRejections = cacheRejections.clone();
        this.graph = graph;
        this.renderTechnique = renderTechnique;
        this.computeTechnique = computeTechnique;
    }

    public ShaderGraphDocument document() {
        return compilation.document();
    }

    public boolean cacheHit() {
        return compilation.cacheHit();
    }

    public boolean cacheMiss() {
        return compilation.cacheMiss();
    }

    public ShaderGraphCompiledCacheCodec.Rejection[] cacheRejections() {
        return cacheRejections.clone();
    }

    public ShaderGraphRuntimeGraph graph() {
        return graph;
    }

    public ShaderGraphRenderTechnique renderTechnique() {
        return renderTechnique;
    }

    public ShaderGraphComputeRuntimeTechnique computeTechnique() {
        return computeTechnique;
    }

    /**
     * Returns the same semantic/editor document with the current successful
     * compilation embedded. Runtime loading itself never writes it.
     */
    public ShaderGraphDocument documentWithCompiledCache() {
        ShaderGraphCompiledCache existing =
                document().compiledCache() != null
                        ? document().compiledCache()
                        : ShaderGraphCompiledCache.empty();
        return document().withCompiledCache(
                existing.replacing(compilation.entries()));
    }
}
