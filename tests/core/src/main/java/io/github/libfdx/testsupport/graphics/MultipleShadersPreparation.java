package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.runtime.*;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;

/** Immutable scenario inputs and owned preparation leases, separate from executable tests. */
public final class MultipleShadersPreparation implements ShaderProvider, Disposable {
    private final GraphicsDevice device;
    private final ShaderPipelineRequest[] packets;
    private final PreparedShaderPass[] handles;
    private final ShaderPreparation service;
    private final ShaderPreparationScope scope;
    private final int invalidIndex;
    private final boolean loadingOnly;
    private boolean disposed;

    public MultipleShadersPreparation(GraphicsContext graphics, VertexLayout layout, int count, int seed, int invalidIndex) {
        device = graphics.device();
        loadingOnly = Boolean.getBoolean("libfdx.test.shaderLoadingOnly");
        if (!loadingOnly && !device.shaderPreparationCapabilities().runtimeNonblocking()) {
            throw new FdxException("Selected provider cannot prepare runtime shaders without blocking; "
                    + "use libfdx.test.shaderLoadingOnly=true to validate its explicit loading strategy");
        }
        this.invalidIndex = invalidIndex;
        packets = new ShaderPipelineRequest[count];
        handles = new PreparedShaderPass[count];
        service = new ShaderPreparation(graphics);
        scope = service.createScope("multiple-shaders");
        var target = RenderTargetLayout.color(graphics.surfaceFormat());
        for (int i = 0; i < count; i++) {
            String label = "multiple shader " + (i + 1);
            int index = i;
            ShaderModuleSource source = ShaderModuleSource.deferred("vertexMain", "fragmentMain", () ->
                    ShaderModuleDescriptor.wgsl(label, index == invalidIndex ? "intentionally invalid WGSL"
                            : MultipleShadersFixture.source(index, seed)));
            packets[i] = new ShaderPipelineRequest(source,
                    new RenderPipelineDescriptor().label(label).renderTargetLayout(target)
                            .vertexLayout(layout).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
        }
        for (int i = 0; i < count; i++) {
            ShaderRequest request = ShaderRequest.builder(ShaderPassId.FORWARD)
                    .renderPass(RenderPassCompatibility.layout(target)).vertexLayouts(layout)
                    .variantKey("shader-" + i).build();
            handles[i] = scope.include(this, request);
        }
        service.prepareAsync(scope.seal());
    }

    @Override public GraphicsDevice preparationDevice() { return device; }
    @Override public boolean supports(ShaderRequest request) { return !disposed; }
    @Override public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
        int index = Integer.parseInt(request.variantKey().substring("shader-".length()));
        return device.prepareRenderPipeline(packets[index]);
    }

    public void update() { if (loadingOnly) service.updateLoading(); else service.update(); }
    public RenderPipeline pipeline(int index) {
        var pass = handles[index].readyPass();
        return pass == null ? null : pass.pipeline();
    }
    public void recordDraw(int index) { handles[index].readyPass().recordDraw(); }
    public String timingReport() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < handles.length; i++) {
            ShaderPreparationTimings timing = handles[i].timings();
            if (!timing.phasesAvailable() || (i != invalidIndex && timing.firstDrawNanos() < 0)
                    || (i == invalidIndex && timing.firstDrawNanos() >= 0))
                throw new FdxException("Missing/incorrect shader timing for " + i);
            out.append("SHADER_TIMING index=").append(i).append(" state=").append(handles[i].state())
                    .append(" queue_ms=").append(timing.queueNanos() / 1_000_000.0)
                    .append(" prepare_ms=").append(timing.preparationNanos() / 1_000_000.0)
                    .append(" first_draw_ms=").append(timing.firstDrawNanos() < 0 ? -1 : timing.firstDrawNanos() / 1_000_000.0)
                    .append(" ready_to_draw_ms=").append(timing.readyToFirstDrawNanos() < 0 ? -1 : timing.readyToFirstDrawNanos() / 1_000_000.0)
                    .append(" draw_update=").append(timing.firstDrawUpdate());
            for (ShaderPreparationPhase phase : ShaderPreparationPhase.values())
                out.append(' ').append(phase).append("_ms=").append(timing.phaseNanos(phase) / 1_000_000.0);
            out.append('\n');
            for (ShaderCacheLayer layer : ShaderCacheLayer.values()) {
                out.append("SHADER_TIMING_CACHE index=").append(i).append(" layer=").append(layer)
                        .append(" read_ms=").append(timing.cacheReadNanos(layer) / 1_000_000.0)
                        .append(" write_ms=").append(timing.cacheWriteNanos(layer) / 1_000_000.0)
                        .append(' ').append(timing.cacheMetrics(layer)).append('\n');
            }
        }
        return out.toString();
    }
    public boolean expectedFailure(int index) { return index == invalidIndex; }
    public int readyCount() { return scope.readyCount(); }
    public int failedCount() { return scope.failedCount(); }
    public boolean settled() { return !service.hasPendingWork(); }
    public void verify() {
        if (scope.unsupportedCount() != 0) throw new FdxException("Selected provider has no runtime async preparation");
        if (scope.failedCount() != (invalidIndex >= 0 ? 1 : 0) || scope.readyCount() != handles.length - scope.failedCount()) {
            for (PreparedShaderPass handle : handles) if (handle.failure() != null) {
                throw new FdxException("Unexpected async shader outcome", handle.failure());
            }
            throw new FdxException("Async shaders did not finish");
        }
        if (!device.shaderPreparationCapabilities().runtimeNonblocking()) {
            for (PreparedShaderPass handle : handles) {
                if (handle.readyPass() == null) continue;
                PreparedShaderPass runtime = service.request(this, handle.request());
                try {
                    if (runtime.readyPass() != handle.readyPass()) {
                        throw new FdxException("Runtime did not reuse the preloaded pipeline");
                    }
                } finally { runtime.dispose(); }
            }
            ShaderRequest existing = handles[0].request();
            PreparedShaderPass missing = service.request(this, ShaderRequest.builder(existing.passId())
                    .renderPass(existing.renderPass()).vertexLayouts(existing.vertexLayouts())
                    .variantKey("shader-" + handles.length).build());
            try {
                service.update();
                if (missing.state() != ShaderPreparationState.UNSUPPORTED || missing.readyPass() != null) {
                    throw new FdxException("Loading-only runtime miss was not rejected");
                }
            } finally { missing.dispose(); }
        }
    }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        scope.dispose();
        service.disposeAsync();
        service.update();
    }
    @Override public boolean isDisposed() { return disposed; }
}
