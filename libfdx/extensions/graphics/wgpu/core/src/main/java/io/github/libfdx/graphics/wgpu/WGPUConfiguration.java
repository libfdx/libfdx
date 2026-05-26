package io.github.libfdx.graphics.wgpu;

public final class WGPUConfiguration {
    private WGPULoaderBackend loaderBackend = WGPULoaderBackend.WGPU;
    private WGPUBackend backend = WGPUBackend.DEFAULT;
    private boolean vSync = true;
    private boolean processEventsEachFrame = true;

    public WGPULoaderBackend loaderBackend() {
        return loaderBackend;
    }

    public WGPUConfiguration loaderBackend(WGPULoaderBackend loaderBackend) {
        this.loaderBackend = loaderBackend;
        return this;
    }

    public WGPUBackend backend() {
        return backend;
    }

    public WGPUConfiguration backend(WGPUBackend backend) {
        this.backend = backend;
        return this;
    }

    public boolean vSync() {
        return vSync;
    }

    public WGPUConfiguration vSync(boolean vSync) {
        this.vSync = vSync;
        return this;
    }

    public boolean processEventsEachFrame() {
        return processEventsEachFrame;
    }

    public WGPUConfiguration processEventsEachFrame(boolean processEventsEachFrame) {
        this.processEventsEachFrame = processEventsEachFrame;
        return this;
    }
}
