package io.github.libfdx.graphics.wgpu;

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
     *
     * @param processEventsEachFrame the process events each frame
     * @return this WGPU configuration for chaining
     */
    public WGPUConfiguration processEventsEachFrame(boolean processEventsEachFrame) {
        this.processEventsEachFrame = processEventsEachFrame;
        return this;
    }
}
