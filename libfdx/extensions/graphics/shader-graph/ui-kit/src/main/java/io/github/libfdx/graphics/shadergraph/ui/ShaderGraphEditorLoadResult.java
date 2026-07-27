package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCacheCodec;

/**
 * Result of loading one shader graph document and its optional editor block.
 */
public final class ShaderGraphEditorLoadResult {
    private final ShaderGraphEditorDocument document;
    private final ShaderGraphEditorLayout layout;
    private final String editorWarning;
    private final ShaderGraphCompiledCacheCodec.Rejection[]
            cacheRejections;

    ShaderGraphEditorLoadResult(ShaderGraphEditorDocument document,
            ShaderGraphEditorLayout layout, String editorWarning,
            ShaderGraphCompiledCacheCodec.Rejection[] cacheRejections) {
        this.document = document;
        this.layout = layout;
        this.editorWarning = editorWarning != null ? editorWarning : "";
        this.cacheRejections = cacheRejections.clone();
    }

    public ShaderGraphEditorDocument document() {
        return document;
    }

    public ShaderGraphEditorLayout layout() {
        return layout;
    }

    public String editorWarning() {
        return editorWarning;
    }

    public boolean recoveredEditorState() {
        return !editorWarning.isEmpty();
    }

    /**
     * Returns corrupt/stale optional cache entries ignored during load.
     */
    public ShaderGraphCompiledCacheCodec.Rejection[]
            cacheRejections() {
        return cacheRejections.clone();
    }
}
