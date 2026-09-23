package io.github.libfdx.tests.web;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.graphics.wgpu.WGPUWebDeviceLossChecks;

/** Loses only separately owned fixture devices; the application keeps rendering. */
public final class WebGPUDeviceLossTest extends ApplicationAdapter {
    private WGPUWebDeviceLossChecks checks;
    @Override
    public void create(Fdx fdx) { checks = new WGPUWebDeviceLossChecks(); }
    @Override
    public void render() { checks.render(); }
    @Override
    public void dispose() { if (checks != null) checks.dispose(); }
}
