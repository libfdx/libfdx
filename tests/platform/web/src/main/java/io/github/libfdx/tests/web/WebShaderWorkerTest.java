package io.github.libfdx.tests.web;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.web.ShaderWorkerTestFixture;

/** Runs real browser compiler worker and lifecycle checks for both TeaVM targets. */
public final class WebShaderWorkerTest extends ApplicationAdapter {
    private final ShaderWorkerTestFixture fixture = new ShaderWorkerTestFixture();
    private final boolean automatic;
    private Fdx fdx;
    private long deadline;
    private boolean passed;
    public WebShaderWorkerTest(boolean automatic) { this.automatic = automatic; }
    @Override public void create(Fdx fdx) {
        this.fdx = fdx;
        deadline = System.currentTimeMillis() + 20000;
        fixture.start();
    }
    @Override public void render() {
        fdx.graphics().main().clear(.02f, .04f, .07f, 1);
        if (passed) return;
        if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Shader worker checks timed out");
        passed = fixture.update();
        if (passed && !automatic) fdx.app().requestExit();
    }
    @Override public void dispose() { fixture.dispose(); }
}
