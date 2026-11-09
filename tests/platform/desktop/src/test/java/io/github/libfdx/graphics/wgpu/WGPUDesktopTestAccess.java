package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.internal.ShaderRenderBindings;

/** Test-only access for resource-domain and native shader preparation assertions. */
public final class WGPUDesktopTestAccess {
    private WGPUDesktopTestAccess() {
    }

    public static Object preparation(WGPUContext context) {
        return context.preparation();
    }

    public static Object resourceDomain(WGPUContext context) {
        return context.resourceDomain();
    }

    public static boolean isResourceDomainClosed(WGPUContext context) {
        return context.resourceDomain().isClosed();
    }

    public static int contextReferences(WGPUContext context) {
        return context.resourceDomain().contextReferences();
    }

    public static String deviceLoss(WGPUContext context) {
        return context.resourceDomain().deviceLoss();
    }

    public static void processNativeEvents(WGPUContext context) {
        synchronized (context.resourceDomain()) {
            context.nativeInstance().processEvents();
        }
    }

    public static void requirePipeline(RenderPipeline pipeline, WGPUContext context, String name) {
        WGPUResources.requirePipeline(pipeline, context.resourceDomain(), name);
    }

    public static ShaderRenderBindings resourceBindings(RenderPipeline pipeline) {
        WGPURenderPipelineHandle handle = pipeline.as();
        return handle.resourceBindings();
    }

}
