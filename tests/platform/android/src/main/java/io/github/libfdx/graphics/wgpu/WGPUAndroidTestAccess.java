package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;

/** Test-only access to the Android WebGPU resource domain and native shader creation. */
public final class WGPUAndroidTestAccess {
    private WGPUAndroidTestAccess() {
    }

    public static void retainPreparation(WGPUContext context) {
        context.resourceDomain().retainPreparation();
    }

    public static void releasePreparation(WGPUContext context) {
        context.resourceDomain().releasePreparation();
    }

    public static void createAndDisposeShader(WGPUContext context, ShaderModuleDescriptor descriptor) {
        WGPUGraphicsDevice.createNativeShader(context.nativeDevice(), context.resourceDomain(),
                context.creationErrors(), descriptor).dispose();
    }

    public static boolean isResourceDomainClosed(WGPUContext context) {
        return context.resourceDomain().isClosed();
    }

}
