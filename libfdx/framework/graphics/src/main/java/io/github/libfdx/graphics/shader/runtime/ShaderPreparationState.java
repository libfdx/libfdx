package io.github.libfdx.graphics.shader.runtime;

/** Observable preparation outcome. An idle service can still contain failed entries. */
public enum ShaderPreparationState {
    QUEUED, PREPARING, READY, FAILED, UNSUPPORTED, CANCELLED;

    /** Whether this outcome requires no further preparation. */
    public boolean terminal() {
        return this != QUEUED && this != PREPARING;
    }
}

