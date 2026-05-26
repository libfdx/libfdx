package io.github.libfdx.backend.android;

import android.view.Surface;

import java.nio.ByteBuffer;

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

    static native long create(Surface surface, int width, int height, boolean vSync,
            boolean preferMailboxPresentMode, int framesInFlight);

    static native void resize(long context, int width, int height);

    static native boolean beginFrame(long context);

    static native void endFrame(long context);

    static native void readPixelsRgba8(long context, ByteBuffer target, int size);

    static native void clear(long context, float red, float green, float blue, float alpha);

    static native long createBuffer(long context, int size, int usage);

    static native void writeBuffer(long buffer, ByteBuffer data, int size);

    static native long createTexture(long context, int width, int height, int format, int wrapS, int wrapT);

    static native void writeTexture(long texture, ByteBuffer data, int size);

    static native long createShaderModule(long context, int[] vertexWords, int[] fragmentWords);

    static native long createRenderPipeline(long context, long shaderModule, int primitiveTopology, int vertexStride,
            int[] attributeLocations, int[] attributeFormats, int[] attributeOffsets, int sampledTextureCount,
            boolean pbrUniformsEnabled, boolean depthTestEnabled, boolean depthWriteEnabled);

    static native void beginRenderPass(long context, boolean clear, float red, float green, float blue,
            float alpha, boolean store, boolean depthClear, float depthClearValue);

    static native void setPipeline(long context, long pipeline);

    static native void setVertexBuffer(long context, long buffer);

    static native void setIndexBuffer(long context, long buffer);

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
