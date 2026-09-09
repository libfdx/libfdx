package io.github.libfdx.backend.android;

import android.view.Surface;
import io.github.libfdx.graphics.GraphicsContextLostException;

import java.nio.ByteBuffer;

/**
 * Provides native bindings for android vulkan.
 *
 * @author xpenatan
 */
final class AndroidVulkanNative {
    private static final String LIBRARY_NAME = "fdx_android_vulkan";
    private static boolean loaded;
    private static boolean instanceProbed;
    private static String instanceProbeFailure;

    private AndroidVulkanNative() {
    }

    static synchronized void load() {
        if (loaded) {
            return;
        }
        System.loadLibrary(LIBRARY_NAME);
        loaded = true;
    }

    static synchronized String instanceProbeFailure() {
        if (instanceProbed) {
            return instanceProbeFailure;
        }
        try {
            load();
            instanceProbeFailure = probeInstance();
        } catch (UnsatisfiedLinkError error) {
            instanceProbeFailure = "Android Vulkan JNI runtime library '" + LIBRARY_NAME
                    + "' is not available: " + error.getMessage();
        }
        instanceProbed = true;
        return instanceProbeFailure;
    }

    private static native String probeInstance();

    /** JNI exception factory; invoked on the thread whose native call reported loss. */
    private static RuntimeException deviceLostException() {
        return new GraphicsContextLostException(AndroidVulkanProvider.ID);
    }

    /** Caller must retain the context; this does not query or wait for the driver. */
    static native boolean isDeviceLost(long context);

    static native long create(Surface surface, int width, int height, boolean vSync,
            boolean preferMailboxPresentMode, int framesInFlight);

    static native void resize(long context, int width, int height);

    static native boolean beginFrame(long context);

    static native void endFrame(long context);

    static native void readPixelsRgba8(long context, ByteBuffer target, int size);

    static native void clear(long context, float red, float green, float blue, float alpha);

    static native long createBuffer(long context, int size, int usage);

    static native void writeBuffer(long buffer, ByteBuffer data, int size);

    static native long createTexture(long context, int width, int height, int format, int wrapS, int wrapT,
            int filter, boolean sampled, boolean renderAttachment);

    static native void writeTexture(long texture, ByteBuffer data, int size);

    static native long createShaderModule(long context, int[] vertexWords, int[] fragmentWords);

    static native long createRenderPipeline(long context, long shaderModule, int colorFormat, int primitiveTopology,
            int[] vertexStrides, int[] vertexStepModes, int[] attributeBindings, int[] attributeLocations,
            int[] attributeFormats, int[] attributeOffsets, int sampledTextureCount, boolean uniformBufferEnabled,
            boolean depthTestEnabled, boolean blendEnabled, boolean depthWriteEnabled,
            String vertexEntryPoint, String fragmentEntryPoint, boolean isolatedPreparation);

    static native void retainPreparationDevice(long context);
    static native void releasePreparationDevice(long context);
    static native byte[] pipelineCacheIdentity(long context);
    static native long[] pipelineCacheStatistics(long context);
    static native int initializePipelineCache(long context, byte[] bytes);
    static native byte[] snapshotPipelineCache(long context);
    static native byte[] mergePipelineCaches(long context, byte[] current, byte[] incoming);
    static native void discardPreparedPipeline(long pipeline);

    static native void beginRenderPass(long context, long colorTexture, int colorFormat, int width, int height,
            boolean clear, float red, float green, float blue, float alpha, boolean store, boolean depthClear,
            float depthClearValue);

    static native void setPipeline(long context, long pipeline);

    static native void setVertexBuffer(long context, int slot, long buffer);

    static native void setIndexBuffer(long context, long buffer);

    static native void setScissor(long context, int x, int y, int width, int height);

    static native void setViewport(long context, int x, int y, int width, int height);

    static native void bindTextures(long context, long pipeline, long[] textures, int count);

    static native void bindUniforms(long context, long pipeline, ByteBuffer data, int size);

    static native void draw(long context, int vertexCount, int instanceCount, int firstVertex, int firstInstance);

    static native void drawIndexed(long context, int indexCount, int instanceCount, int firstIndex,
            int baseVertex, int firstInstance);

    static native void endRenderPass(long context);

    static native int surfaceFormat(long context);

    static native void destroyShaderModule(long shaderModule);

    static native void destroyRenderPipeline(long pipeline);

    static native void destroyBuffer(long buffer);

    static native void destroyTexture(long texture);

    static native void destroy(long context);
}
