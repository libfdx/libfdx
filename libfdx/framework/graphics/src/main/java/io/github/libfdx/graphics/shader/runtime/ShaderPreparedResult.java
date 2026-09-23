package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import java.util.Objects;

/**
 * Owned preparation result. Its pass is borrowed. The resource owner must retain modules,
 * pipelines and defaults, and defer their destruction until recorded GPU use is finished.
 */
public final class ShaderPreparedResult implements Disposable {
    private final ResolvedShaderPass pass;
    private final Disposable resources;
    private boolean disposed;

    public ShaderPreparedResult(ResolvedShaderPass pass, Disposable resources) {
        this.pass = Objects.requireNonNull(pass, "pass");
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public ResolvedShaderPass pass() {
        if (disposed) throw new FdxException("Prepared shader result is disposed");
        return pass;
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        resources.dispose();
    }

    @Override
    public boolean isDisposed() { return disposed; }
}

