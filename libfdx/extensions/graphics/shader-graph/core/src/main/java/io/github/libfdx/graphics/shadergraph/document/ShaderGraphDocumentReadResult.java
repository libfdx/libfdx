package io.github.libfdx.graphics.shadergraph.document;

import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCacheCodec;

/**
 * Result of decoding required semantic data and optional compiled-cache data.
 */
public final class ShaderGraphDocumentReadResult {
    private final ShaderGraphDocument document;
    private final ShaderGraphCompiledCacheCodec.Rejection[] cacheRejections;

    ShaderGraphDocumentReadResult(ShaderGraphDocument document,
            ShaderGraphCompiledCacheCodec.Rejection[] cacheRejections) {
        this.document = document;
        this.cacheRejections = cacheRejections.clone();
    }

    public ShaderGraphDocument document() {
        return document;
    }

    public ShaderGraphCompiledCacheCodec.Rejection[] cacheRejections() {
        return cacheRejections.clone();
    }

    public boolean rejectedCompiledEntries() {
        return cacheRejections.length > 0;
    }
}
