package io.github.libfdx.graphics.shader.runtime;

/** Diagnostic phase; compilation time is not render-thread stall time. */
public enum ShaderPreparationPhase {
    QUEUED, CACHE_LOOKUP, SOURCE, TRANSLATION, COMPILATION, PIPELINE, PUBLICATION_WAIT, PUBLICATION, COMPLETE
}
