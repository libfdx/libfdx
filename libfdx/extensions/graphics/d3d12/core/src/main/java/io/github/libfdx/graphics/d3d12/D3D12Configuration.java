package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;

/**
 * Stores Direct3D 12 startup configuration.
 * Requires Windows x64 and a hardware adapter supporting Shader Model 6.0.
 * HLSL is compiled at runtime by the provider's packaged DXC compiler; there is
 * no legacy compiler fallback. Native compiler extraction requires a writable
 * {@code java.io.tmpdir}.
 */
public final class D3D12Configuration {
    private boolean validation;
    private boolean optimizeShaders = true;
    private boolean vSync = true;
    private int framesInFlight = 2;
    private int shaderPreparationWorkers = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
    private ShaderArtifactCache shaderCache;

    /** Optional borrowed cache for asynchronous translations, DXIL and machine-local cached PSOs.
     * Null disables persistence. PSOs require an identified adapter/driver/runtime and are retried
     * without cached data if rejected; they never replace the complete pipeline description. */
    public ShaderArtifactCache shaderCache() { return shaderCache; }

    /** Configure before startup; the application owns the cache/store and their final drain. */
    public D3D12Configuration shaderCache(ShaderArtifactCache cache) { shaderCache = cache; return this; }

    /** Maximum workers for nonblocking shader/PSO preparation; defaults to at most eight. */
    public int shaderPreparationWorkers() { return shaderPreparationWorkers; }

    /** Sets the preparation worker limit before provider startup. Native calls are never joined by rendering. */
    public D3D12Configuration shaderPreparationWorkers(int workers) {
        if (workers < 1 || workers > 64) throw new FdxException("D3D12 preparation workers must be between 1 and 64");
        shaderPreparationWorkers = workers;
        return this;
    }

    /**
     * Returns whether the Direct3D 12 debug layer is requested.
     *
     * @return true when validation is requested
     */
    public boolean validation() {
        return validation;
    }

    /**
     * Requests the Direct3D 12 debug layer.
     *
     * @param validation whether validation is requested
     * @return this configuration
     */
    public D3D12Configuration validation(boolean validation) {
        this.validation = validation;
        return this;
    }

    /**
     * Returns whether runtime HLSL compilation uses DXC's highest optimization level.
     * The default is true. Compilation happens at runtime when a pipeline first needs a stage.
     *
     * @return true when runtime compilation prioritizes bytecode optimization
     */
    public boolean optimizeShaders() {
        return optimizeShaders;
    }

    /**
     * Selects the runtime shader compilation tradeoff. False skips DXC optimization;
     * true uses level 3, which can improve GPU execution but substantially delay
     * pipeline creation. Validation mode always uses debug bytecode without optimization.
     * Native HLSL stages are compiled when first required by a pipeline, then cached
     * within the context. Applications choosing true should create their pipelines
     * during an explicit loading phase. Use GraphicsDevice.createRenderPipelines to
     * compile independent stages concurrently with up to eight workers; the call waits
     * for all compiler work and pipeline creation before returning. The single-pipeline
     * call remains synchronous and does not start background work.
     *
     * @param value whether to optimize shader bytecode
     * @return this configuration
     */
    public D3D12Configuration optimizeShaders(boolean value) {
        optimizeShaders = value;
        return this;
    }

    /**
     * Returns whether presentation is synchronized to the display.
     *
     * @return true when vertical synchronization is enabled
     */
    public boolean vSync() {
        return vSync;
    }

    /**
     * Sets vertical synchronization.
     *
     * @param vSync whether vertical synchronization is enabled
     * @return this configuration
     */
    public D3D12Configuration vSync(boolean vSync) {
        this.vSync = vSync;
        return this;
    }

    /**
     * Returns the number of swap-chain frames.
     *
     * @return frames in flight
     */
    public int framesInFlight() {
        return framesInFlight;
    }

    /**
     * Sets the number of swap-chain frames.
     *
     * @param framesInFlight frames in flight, from two through three
     * @return this configuration
     */
    public D3D12Configuration framesInFlight(int framesInFlight) {
        if (framesInFlight < 2 || framesInFlight > 3) {
            throw new FdxException("Direct3D 12 frames in flight must be between 2 and 3");
        }
        this.framesInFlight = framesInFlight;
        return this;
    }
}
