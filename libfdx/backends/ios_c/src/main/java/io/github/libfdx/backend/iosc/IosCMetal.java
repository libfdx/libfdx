package io.github.libfdx.backend.iosc;

import io.github.libfdx.core.FdxException;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Exposes native Metal bridge functions for the iOS C backend.
 *
 * @author xpenatan
 */
@Include("libfdx_ios_metal.h")
final class IosCMetal {
    private IosCMetal() {
    }

    static boolean supported() {
        return libfdxIosMetalSupported() != 0;
    }

    static long create(int width, int height) {
        return requireHandle(libfdxIosMetalCreate(width, height), "Could not create iOS C Metal context");
    }

    static void resize(long context, int width, int height) {
        libfdxIosMetalResize(context, width, height);
    }

    static boolean beginFrame(long context) {
        return libfdxIosMetalBeginFrame(context) != 0;
    }

    static void endFrame(long context) {
        libfdxIosMetalEndFrame(context);
    }

    static void readPixelsRgba8(long context, ByteBuffer target, int byteCount) {
        libfdxIosMetalReadPixelsRgba8(context, target, byteCount);
    }

    static void clear(long context, float red, float green, float blue, float alpha) {
        libfdxIosMetalClear(context, red, green, blue, alpha);
    }

    static long createBuffer(long context, int size, int usage) {
        return requireHandle(libfdxIosMetalCreateBuffer(context, size, usage),
                "Could not create iOS C Metal buffer");
    }

    static void writeBuffer(long buffer, ByteBuffer data, int byteCount) {
        libfdxIosMetalWriteBuffer(buffer, data, byteCount);
    }

    static long createTexture(long context, int width, int height, int wrapS, int wrapT) {
        return requireHandle(libfdxIosMetalCreateTexture(context, width, height, wrapS, wrapT),
                "Could not create iOS C Metal texture");
    }

    static void writeTexture(long texture, ByteBuffer data, int byteCount) {
        libfdxIosMetalWriteTexture(texture, data, byteCount);
    }

    static long createShaderModule(long context, String mslSource) {
        byte[] sourceBytes = mslSource.getBytes(StandardCharsets.UTF_8);
        int[] sourceData = new int[sourceBytes.length];
        for (int i = 0; i < sourceBytes.length; i++) {
            sourceData[i] = sourceBytes[i] & 0xff;
        }
        return requireHandle(libfdxIosMetalCreateShaderModule(context, sourceData.length, Address.ofData(sourceData)),
                "Could not create iOS C Metal shader module");
    }

    static long createRenderPipeline(long context, long shaderModule, int primitiveTopology, int[] vertexStrides,
            int[] vertexStepModes, int[] attributeBindings, int[] attributeLocations, int[] attributeFormats,
            int[] attributeOffsets, int sampledTextureCount, boolean pbrUniformsEnabled, boolean depthTestEnabled,
            boolean depthWriteEnabled) {
        int vertexLayoutCount = vertexStrides != null ? vertexStrides.length : 0;
        int attributeCount = attributeLocations != null ? attributeLocations.length : 0;
        return requireHandle(libfdxIosMetalCreateRenderPipeline(context, shaderModule, primitiveTopology,
                addressOf(vertexStrides, vertexLayoutCount), addressOf(vertexStepModes, vertexLayoutCount),
                vertexLayoutCount, addressOf(attributeBindings, attributeCount),
                addressOf(attributeLocations, attributeCount), addressOf(attributeFormats, attributeCount),
                addressOf(attributeOffsets, attributeCount), attributeCount, sampledTextureCount,
                bool(pbrUniformsEnabled), bool(depthTestEnabled), bool(depthWriteEnabled)),
                "Could not create iOS C Metal render pipeline");
    }

    static void beginRenderPass(long context, boolean clear, float red, float green, float blue, float alpha,
            boolean store, boolean depthEnabled, boolean depthClear, float depthClearValue) {
        libfdxIosMetalBeginRenderPass(context, bool(clear), red, green, blue, alpha, bool(store),
                bool(depthEnabled), bool(depthClear), depthClearValue);
    }

    static void setPipeline(long context, long pipeline) {
        libfdxIosMetalSetPipeline(context, pipeline);
    }

    static void setVertexBuffer(long context, int slot, long buffer) {
        libfdxIosMetalSetVertexBuffer(context, slot, buffer);
    }

    static void setIndexBuffer(long context, long buffer) {
        libfdxIosMetalSetIndexBuffer(context, buffer);
    }

    static void setTexture(long context, int textureSlot, int samplerSlot, long texture) {
        libfdxIosMetalSetTexture(context, textureSlot, samplerSlot, texture);
    }

    static void setUniformBuffer(long context, ByteBuffer data, int byteCount) {
        libfdxIosMetalSetUniformBuffer(context, data, byteCount);
    }

    static void draw(long context, int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        libfdxIosMetalDraw(context, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    static void drawIndexed(long context, int indexCount, int instanceCount, int firstIndex, int baseVertex,
            int firstInstance) {
        libfdxIosMetalDrawIndexed(context, indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }

    static void endRenderPass(long context) {
        libfdxIosMetalEndRenderPass(context);
    }

    static void destroyShaderModule(long shaderModule) {
        libfdxIosMetalDestroyShaderModule(shaderModule);
    }

    static void destroyRenderPipeline(long pipeline) {
        libfdxIosMetalDestroyRenderPipeline(pipeline);
    }

    static void destroyBuffer(long buffer) {
        libfdxIosMetalDestroyBuffer(buffer);
    }

    static void destroyTexture(long texture) {
        libfdxIosMetalDestroyTexture(texture);
    }

    static void destroy(long context) {
        libfdxIosMetalDestroy(context);
    }

    private static Address addressOf(int[] values, int expectedLength) {
        if (expectedLength <= 0) {
            return Address.fromLong(0L);
        }
        if (values == null || values.length < expectedLength) {
            throw new FdxException("iOS C Metal vertex attribute arrays are inconsistent");
        }
        return Address.ofData(values);
    }

    private static long requireHandle(long handle, String message) {
        if (handle == 0L) {
            throw new FdxException(message + "; see native stderr for details");
        }
        return handle;
    }

    private static int bool(boolean value) {
        return value ? 1 : 0;
    }

    @Import(name = "libfdx_ios_metal_supported")
    private static native int libfdxIosMetalSupported();

    @Import(name = "libfdx_ios_metal_create")
    private static native long libfdxIosMetalCreate(int width, int height);

    @Import(name = "libfdx_ios_metal_resize")
    private static native void libfdxIosMetalResize(long context, int width, int height);

    @Import(name = "libfdx_ios_metal_begin_frame")
    private static native int libfdxIosMetalBeginFrame(long context);

    @Import(name = "libfdx_ios_metal_end_frame")
    private static native void libfdxIosMetalEndFrame(long context);

    @Import(name = "libfdx_ios_metal_read_pixels_rgba8")
    private static native void libfdxIosMetalReadPixelsRgba8(long context, ByteBuffer target, int byteCount);

    @Import(name = "libfdx_ios_metal_clear")
    private static native void libfdxIosMetalClear(long context, float red, float green, float blue, float alpha);

    @Import(name = "libfdx_ios_metal_create_buffer")
    private static native long libfdxIosMetalCreateBuffer(long context, int size, int usage);

    @Import(name = "libfdx_ios_metal_write_buffer")
    private static native void libfdxIosMetalWriteBuffer(long buffer, ByteBuffer data, int byteCount);

    @Import(name = "libfdx_ios_metal_create_texture")
    private static native long libfdxIosMetalCreateTexture(long context, int width, int height, int wrapS, int wrapT);

    @Import(name = "libfdx_ios_metal_write_texture")
    private static native void libfdxIosMetalWriteTexture(long texture, ByteBuffer data, int byteCount);

    @Import(name = "libfdx_ios_metal_create_shader_module")
    private static native long libfdxIosMetalCreateShaderModule(long context, int sourceLength, Address sourceData);

    @Import(name = "libfdx_ios_metal_create_render_pipeline")
    private static native long libfdxIosMetalCreateRenderPipeline(long context, long shaderModule,
            int primitiveTopology, Address vertexStrides, Address vertexStepModes, int vertexLayoutCount,
            Address attributeBindings, Address attributeLocations, Address attributeFormats, Address attributeOffsets,
            int attributeCount, int sampledTextureCount, int pbrUniformsEnabled, int depthTestEnabled,
            int depthWriteEnabled);

    @Import(name = "libfdx_ios_metal_begin_render_pass")
    private static native void libfdxIosMetalBeginRenderPass(long context, int clear, float red, float green,
            float blue, float alpha, int store, int depthEnabled, int depthClear, float depthClearValue);

    @Import(name = "libfdx_ios_metal_set_pipeline")
    private static native void libfdxIosMetalSetPipeline(long context, long pipeline);

    @Import(name = "libfdx_ios_metal_set_vertex_buffer")
    private static native void libfdxIosMetalSetVertexBuffer(long context, int slot, long buffer);

    @Import(name = "libfdx_ios_metal_set_index_buffer")
    private static native void libfdxIosMetalSetIndexBuffer(long context, long buffer);

    @Import(name = "libfdx_ios_metal_set_texture")
    private static native void libfdxIosMetalSetTexture(long context, int textureSlot, int samplerSlot, long texture);

    @Import(name = "libfdx_ios_metal_set_uniform_buffer")
    private static native void libfdxIosMetalSetUniformBuffer(long context, ByteBuffer data, int byteCount);

    @Import(name = "libfdx_ios_metal_draw")
    private static native void libfdxIosMetalDraw(long context, int vertexCount, int instanceCount,
            int firstVertex, int firstInstance);

    @Import(name = "libfdx_ios_metal_draw_indexed")
    private static native void libfdxIosMetalDrawIndexed(long context, int indexCount, int instanceCount,
            int firstIndex, int baseVertex, int firstInstance);

    @Import(name = "libfdx_ios_metal_end_render_pass")
    private static native void libfdxIosMetalEndRenderPass(long context);

    @Import(name = "libfdx_ios_metal_destroy_shader_module")
    private static native void libfdxIosMetalDestroyShaderModule(long shaderModule);

    @Import(name = "libfdx_ios_metal_destroy_render_pipeline")
    private static native void libfdxIosMetalDestroyRenderPipeline(long pipeline);

    @Import(name = "libfdx_ios_metal_destroy_buffer")
    private static native void libfdxIosMetalDestroyBuffer(long buffer);

    @Import(name = "libfdx_ios_metal_destroy_texture")
    private static native void libfdxIosMetalDestroyTexture(long texture);

    @Import(name = "libfdx_ios_metal_destroy")
    private static native void libfdxIosMetalDestroy(long context);
}
