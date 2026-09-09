package io.github.libfdx.graphics.wgpu;

/** Android JNI preparation. Dawn/Vulkan uses shared native async completion;
 * wgpu-native Vulkan uses workers and GLES advances native creation during loading. */
public final class WGPUAndroidPreparation extends WGPUWorkerPreparation {
    @Override boolean supports(WGPUConfiguration configuration) {
        // Explicit adapter requests cannot silently select a GLES backend with different threading rules.
        return configuration.backend() == WGPUBackend.VULKAN
                || configuration.loaderBackend() == WGPULoaderBackend.WGPU
                && configuration.backend() == WGPUBackend.OPENGL_ES;
    }

    @Override void initialize(WGPUContext context, int workers) {
        super.initialize(context, context.configuration().preparationWorkerLimitOrDefault(2),
                context.configuration().backend() == WGPUBackend.OPENGL_ES);
    }
}
