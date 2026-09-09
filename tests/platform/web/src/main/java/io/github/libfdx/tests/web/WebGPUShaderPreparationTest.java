package io.github.libfdx.tests.web;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.graphics.wgpu.WGPUWebPreparationChecks;

/** Native browser callback and cancellation validation, selected explicitly by the web launcher. */
public final class WebGPUShaderPreparationTest extends ApplicationAdapter {
    private WGPUWebPreparationChecks checks;
    @Override public void create(Fdx fdx) { checks = new WGPUWebPreparationChecks(fdx.graphics().main()); }
    @Override public void render() { checks.render(); }
    @Override public void dispose() { if (checks != null) checks.dispose(); }
}
