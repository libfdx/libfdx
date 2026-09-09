package io.github.libfdx.tests.web;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.graphics.gl.web.WebGLContextLossChecks;

/** Selected explicitly; loses only its own fixture contexts, never the host GPU. */
public final class WebGLContextLossTest extends ApplicationAdapter {
    private WebGLContextLossChecks checks;
    @Override public void create(Fdx fdx) { checks = new WebGLContextLossChecks(); }
    @Override public void render() { checks.render(); }
    @Override public void dispose() { if (checks != null) checks.dispose(); }
}
