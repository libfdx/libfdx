package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;

/**
 * Stores configuration values for a WGPU.
 *
 * @author xpenatan
 */
public final class WGPUConfiguration {
    private WGPULoaderBackend loaderBackend = WGPULoaderBackend.WGPU;
    private WGPUBackend backend = WGPUBackend.DEFAULT;
    private boolean vSync = true;
    private boolean processEventsEachFrame = true;
    private boolean offscreenReadback;
    private int preparationWorkerLimit;
    private ShaderArtifactCache shaderCache;

    /** Borrowed application cache for validated WGSL/reflection during preparation; null disables it.
     * Configure before attachment. The application owns the store and closes it after preparation drains.
     * Native shader modules/pipelines are still created on each launch; their binaries are not persisted. */
    public ShaderArtifactCache shaderCache() { return shaderCache; }

    /** Sets the optional borrowed artifact cache before starting the provider. */
    public WGPUConfiguration shaderCache(ShaderArtifactCache value) { shaderCache = value; return this; }

    /**
     * Returns the explicit worker limit, or the desktop default capped at eight available processors.
     * Android uses a default capped at two when no limit is set. The device's preparation capabilities
     * report the actual worker limit and support.
     */
    public int preparationWorkerLimit() { return preparationWorkerLimitOrDefault(8); }

    int preparationWorkerLimitOrDefault(int defaultMaximum) {
        return preparationWorkerLimit != 0 ? preparationWorkerLimit
                : Math.max(1, Math.min(defaultMaximum, Runtime.getRuntime().availableProcessors()));
    }

    /** Sets an explicit worker limit (1 through 64), overriding the platform default. */
    public WGPUConfiguration preparationWorkerLimit(int value) {
        if (value < 1 || value > 64) throw new IllegalArgumentException("WGPU preparation workers must be between 1 and 64");
        preparationWorkerLimit = value;
        return this;
    }

    /**
     * Returns the loader backend.
     *
     * @return the created value
     */
    public WGPULoaderBackend loaderBackend() {
        return loaderBackend;
    }

    /**
     * Sets the loader backend and returns this WGPU configuration.
     * Native Dawn requires its implicit device synchronization feature for concurrent preparation
     * and rendering; device creation rejects an adapter that does not support it.
     *
     * @param loaderBackend the loader backend
     * @return this WGPU configuration for chaining
     */
    public WGPUConfiguration loaderBackend(WGPULoaderBackend loaderBackend) {
        this.loaderBackend = loaderBackend;
        return this;
    }

    /**
     * Returns the backend.
     *
     * @return the backend
     */
    public WGPUBackend backend() {
        return backend;
    }

    /**
     * Sets the backend and returns this WGPU configuration.
     *
     * @param backend the backend
     * @return this WGPU configuration for chaining
     */
    public WGPUConfiguration backend(WGPUBackend backend) {
        this.backend = backend;
        return this;
    }

    /**
     * Returns the v sync.
     *
     * @return true if v sync succeeds or is active; false otherwise
     */
    public boolean vSync() {
        return vSync;
    }

    /**
     * Sets the v sync and returns this WGPU configuration.
     *
     * @param vSync the v sync
     * @return this WGPU configuration for chaining
     */
    public WGPUConfiguration vSync(boolean vSync) {
        this.vSync = vSync;
        return this;
    }

    /**
     * Returns the process events each frame.
     *
     * @return true if process events each frame succeeds or is active; false otherwise
     */
    public boolean processEventsEachFrame() {
        return processEventsEachFrame;
    }

    /**
     * Sets the process events each frame and returns this WGPU configuration.
     * Initialization and registered device-loss notifications are still pumped when disabled.
     *
     * @param processEventsEachFrame the process events each frame
     * @return this WGPU configuration for chaining
     */
    public WGPUConfiguration processEventsEachFrame(boolean processEventsEachFrame) {
        this.processEventsEachFrame = processEventsEachFrame;
        return this;
    }

    /**
     * Returns whether frames should render to an offscreen readback texture.
     *
     * @return true when offscreen readback is enabled; false otherwise
     */
    public boolean offscreenReadback() {
        return offscreenReadback;
    }

    /**
     * Sets whether frames should render to an offscreen readback texture.
     * Configure before attachment. Used when the surface is not configured with CopySrc usage,
     * including Android surfaces. In this mode the offscreen frame is available for capture but
     * is not presented to the window. Without it, such framebuffers report readback unsupported.
     *
     * @param offscreenReadback true to render frames into a copyable offscreen texture
     * @return this WGPU configuration for chaining
     */
    public WGPUConfiguration offscreenReadback(boolean offscreenReadback) {
        this.offscreenReadback = offscreenReadback;
        return this;
    }
}
