package io.github.libfdx.backend.desktopcpp;

import com.github.xpenatan.jnative.interop.NativeImport;

import io.github.libfdx.core.FdxException;

import java.nio.ByteBuffer;

/**
 * Represents a jNative vulkan.
 *
 * @author xpenatan
 */
final class DesktopCppVulkan {
    private DesktopCppVulkan() {}

    @NativeImport("fdx_desktop_vulkan_wait_preparations")
    static native void waitPreparations(long context);

    @NativeImport("fdx_jn_vulkan_create_compute")
    static native long createCompute(long context, int[] words, String entry, int[] bindings);

    @NativeImport("fdx_jn_vulkan_dispatch_compute")
    static native void dispatchCompute(
            long context, long pipeline, long[] resources, int count, int x, int y, int z);

    @NativeImport("fdx_desktop_vulkan_copy_buffer")
    static native void copyBuffer(
            long context,
            long source,
            int sourceOffset,
            long destination,
            int destinationOffset,
            int size);

    @NativeImport("fdx_jn_vulkan_read_buffer")
    static native void readBuffer(long source, int offset, ByteBuffer output, int size);

    static boolean vulkanSupported() {
        return glfwVulkanSupported();
    }

    static int requiredInstanceExtensionCount() {
        return glfwGetRequiredInstanceExtensions();
    }

    static String supportFailureReason() {
        if (!vulkanSupported()) {
            return "Vulkan is not supported by GLFW on this system";
        }
        if (requiredInstanceExtensionCount() <= 0) {
            return "GLFW did not expose required jNative Vulkan instance extensions";
        }
        if (fdxDesktopVulkanProbeInstance() == 0) {
            return "Could not probe a jNative Vulkan instance; see native stderr for Vulkan loader"
                    + " details";
        }
        return null;
    }

    static long create(
            long windowHandle,
            int width,
            int height,
            boolean vSync,
            boolean preferMailboxPresentMode,
            int framesInFlight) {
        return requireHandle(
                fdxDesktopVulkanCreate(
                        windowHandle,
                        width,
                        height,
                        bool(vSync),
                        bool(preferMailboxPresentMode),
                        framesInFlight),
                "Could not create jNative Vulkan context");
    }

    static void resize(long context, int width, int height) {
        fdxDesktopVulkanResize(context, width, height);
    }

    static boolean beginFrame(long context) {
        return fdxDesktopVulkanBeginFrame(context) != 0;
    }

    static void endFrame(long context) {
        fdxDesktopVulkanEndFrame(context);
    }

    static void readPixelsRgba8(long context, ByteBuffer target, int byteCount) {
        fdxDesktopVulkanReadPixelsRgba8(context, target, byteCount);
    }

    static void clear(long context, float red, float green, float blue, float alpha) {
        fdxDesktopVulkanClear(context, red, green, blue, alpha);
    }

    static long createBuffer(long context, int size, int usage) {
        return requireHandle(
                fdxDesktopVulkanCreateBuffer(context, size, usage),
                "Could not create jNative Vulkan buffer");
    }

    static void writeBuffer(long buffer, ByteBuffer data, int byteCount) {
        fdxDesktopVulkanWriteBuffer(buffer, data, byteCount);
    }

    static long createTexture(
            long context,
            int width,
            int height,
            int format,
            int wrapS,
            int wrapT,
            int filter,
            int magFilter,
            int mipFilter,
            int mipLevels,
            int samples,
            int usage) {
        return requireHandle(
                fdxDesktopVulkanCreateTexture(
                        context, width, height, format, wrapS, wrapT, filter, magFilter, mipFilter,
                        mipLevels, samples, usage),
                "Could not create jNative Vulkan texture");
    }

    static void writeTexture(long texture, int level, ByteBuffer data, int byteCount) {
        if (fdxDesktopVulkanWriteTexture(texture, level, data, byteCount) == 0)
            throw new FdxException("Could not upload Vulkan texture; see native stderr");
    }

    static long createShaderModule(
            long context,
            int[] vertexWords,
            int[] fragmentWords,
            String vertexEntry,
            String fragmentEntry) {
        if (vertexWords == null || fragmentWords == null) {
            throw new FdxException("jNative Vulkan requires vertex and fragment SPIR-V words");
        }
        return requireHandle(
                fdxDesktopVulkanCreateShaderModule(
                        context,
                        vertexWords,
                        vertexWords.length,
                        fragmentWords,
                        fragmentWords.length,
                        vertexEntry,
                        fragmentEntry),
                "Could not create jNative Vulkan shader module");
    }

    static long createRenderPipeline(
            long context,
            long shaderModule,
            int primitiveTopology,
            int[] vertexStrides,
            int[] vertexStepModes,
            int[] attributeBindings,
            int[] attributeLocations,
            int[] attributeFormats,
            int[] attributeOffsets,
            int sampledTextureCount,
            boolean uniformBufferEnabled,
            int[] state) {
        int vertexLayoutCount = vertexStrides != null ? vertexStrides.length : 0;
        int attributeCount = attributeLocations != null ? attributeLocations.length : 0;
        int[] strides = addressOf(vertexStrides, vertexLayoutCount);
        int[] stepModes = addressOf(vertexStepModes, vertexLayoutCount);
        int[] bindings = addressOf(attributeBindings, attributeCount);
        int[] locations = addressOf(attributeLocations, attributeCount);
        int[] formats = addressOf(attributeFormats, attributeCount);
        int[] offsets = addressOf(attributeOffsets, attributeCount);
        return requireHandle(
                fdxDesktopVulkanCreateRenderPipeline(
                        context,
                        shaderModule,
                        primitiveTopology,
                        strides,
                        stepModes,
                        vertexLayoutCount,
                        bindings,
                        locations,
                        formats,
                        offsets,
                        attributeCount,
                        sampledTextureCount,
                        bool(uniformBufferEnabled),
                        state),
                "Could not create jNative Vulkan render pipeline");
    }

    static void beginRenderPass(long context, int[] state, long[] textures, float[] clears) {
        if (fdxDesktopVulkanBeginRenderPass(context, state, textures, clears) == 0)
            throw new FdxException("Could not begin Vulkan render pass; see native stderr");
    }

    static void setPipeline(long context, long pipeline) {
        fdxDesktopVulkanSetPipeline(context, pipeline);
    }

    static void setVertexBuffer(long context, int slot, long buffer) {
        fdxDesktopVulkanSetVertexBuffer(context, slot, buffer);
    }

    static void setIndexBuffer(long context, long buffer) {
        fdxDesktopVulkanSetIndexBuffer(context, buffer);
    }

    static void setScissor(long context, int x, int y, int width, int height) {
        fdxDesktopVulkanSetScissor(context, x, y, width, height);
    }

    static void setViewport(long context, int x, int y, int width, int height) {
        fdxDesktopVulkanSetViewport(context, x, y, width, height);
    }

    static void bindTextures(long context, long pipeline, long[] textures, int count) {
        fdxDesktopVulkanBindTextures(context, pipeline, textures, count);
    }

    static void bindUniforms(long context, long pipeline, ByteBuffer data, int byteCount) {
        if (data == null || byteCount <= 0 || byteCount > data.remaining()) {
            throw new FdxException("jNative Vulkan uniform upload exceeds the ByteBuffer data");
        }
        if (fdxDesktopVulkanBindUniforms(context, pipeline, data, byteCount) == 0) {
            throw new FdxException("jNative Vulkan could not bind the uniform buffer");
        }
    }

    static void draw(
            long context, int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        fdxDesktopVulkanDraw(context, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    static void drawIndexed(
            long context,
            int indexCount,
            int instanceCount,
            int firstIndex,
            int baseVertex,
            int firstInstance) {
        fdxDesktopVulkanDrawIndexed(
                context, indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }

    static void endRenderPass(long context) {
        fdxDesktopVulkanEndRenderPass(context);
    }

    static int surfaceFormat(long context) {
        return fdxDesktopVulkanSurfaceFormat(context);
    }

    static void destroyShaderModule(long shaderModule) {
        fdxDesktopVulkanDestroyShaderModule(shaderModule);
    }

    static void destroyRenderPipeline(long pipeline, boolean published) {
        fdxDesktopVulkanDestroyRenderPipeline(pipeline, bool(published));
    }

    static void destroyBuffer(long buffer) {
        fdxDesktopVulkanDestroyBuffer(buffer);
    }

    static void destroyTexture(long texture) {
        fdxDesktopVulkanDestroyTexture(texture);
    }

    static void destroy(long context) {
        fdxDesktopVulkanDestroy(context);
    }

    static void retain(long context) {
        fdxDesktopVulkanRetain(context);
    }

    private static int[] addressOf(int[] values, int expectedLength) {
        if (expectedLength <= 0) {
            return null;
        }
        if (values == null || values.length < expectedLength) {
            throw new FdxException("jNative Vulkan vertex attribute arrays are inconsistent");
        }
        return values;
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

    @NativeImport("fdx_jn_glfwVulkanSupported")
    private static native boolean glfwVulkanSupported();

    @NativeImport("fdx_jn_glfwGetRequiredInstanceExtensions")
    private static native int glfwGetRequiredInstanceExtensions();

    @NativeImport("fdx_jn_fdx_desktop_vulkan_probe_instance")
    private static native int fdxDesktopVulkanProbeInstance();

    @NativeImport("fdx_jn_fdx_desktop_vulkan_create")
    private static native long fdxDesktopVulkanCreate(
            long windowHandle,
            int width,
            int height,
            int vSync,
            int preferMailboxPresentMode,
            int framesInFlight);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_resize")
    private static native void fdxDesktopVulkanResize(long context, int width, int height);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_begin_frame")
    private static native int fdxDesktopVulkanBeginFrame(long context);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_end_frame")
    private static native void fdxDesktopVulkanEndFrame(long context);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_read_pixels_rgba8")
    private static native void fdxDesktopVulkanReadPixelsRgba8(
            long context, ByteBuffer target, int byteCount);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_clear")
    private static native void fdxDesktopVulkanClear(
            long context, float red, float green, float blue, float alpha);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_create_buffer")
    private static native long fdxDesktopVulkanCreateBuffer(long context, int size, int usage);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_write_buffer")
    private static native void fdxDesktopVulkanWriteBuffer(
            long buffer, ByteBuffer data, int byteCount);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_create_texture")
    private static native long fdxDesktopVulkanCreateTexture(
            long context,
            int width,
            int height,
            int format,
            int wrapS,
            int wrapT,
            int filter,
            int magFilter,
            int mipFilter,
            int mipLevels,
            int samples,
            int usage);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_write_texture")
    private static native int fdxDesktopVulkanWriteTexture(
            long texture, int level, ByteBuffer data, int byteCount);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_create_shader_module")
    private static native long fdxDesktopVulkanCreateShaderModule(
            long context,
            int[] vertexWords,
            int vertexWordCount,
            int[] fragmentWords,
            int fragmentWordCount,
            String vertexEntry,
            String fragmentEntry);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_create_render_pipeline")
    private static native long fdxDesktopVulkanCreateRenderPipeline(
            long context,
            long shaderModule,
            int primitiveTopology,
            int[] vertexStrides,
            int[] vertexStepModes,
            int vertexLayoutCount,
            int[] attributeBindings,
            int[] attributeLocations,
            int[] attributeFormats,
            int[] attributeOffsets,
            int attributeCount,
            int sampledTextureCount,
            int uniformBufferEnabled,
            int[] state);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_begin_render_pass")
    private static native int fdxDesktopVulkanBeginRenderPass(
            long context, int[] state, long[] textures, float[] clears);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_set_pipeline")
    private static native void fdxDesktopVulkanSetPipeline(long context, long pipeline);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_set_vertex_buffer")
    private static native void fdxDesktopVulkanSetVertexBuffer(long context, int slot, long buffer);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_set_index_buffer")
    private static native void fdxDesktopVulkanSetIndexBuffer(long context, long buffer);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_set_scissor")
    private static native void fdxDesktopVulkanSetScissor(
            long context, int x, int y, int width, int height);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_set_viewport")
    private static native void fdxDesktopVulkanSetViewport(
            long context, int x, int y, int width, int height);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_bind_textures")
    private static native void fdxDesktopVulkanBindTextures(
            long context, long pipeline, long[] textures, int count);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_bind_uniforms")
    private static native int fdxDesktopVulkanBindUniforms(
            long context, long pipeline, ByteBuffer data, int byteCount);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_draw")
    private static native void fdxDesktopVulkanDraw(
            long context, int vertexCount, int instanceCount, int firstVertex, int firstInstance);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_draw_indexed")
    private static native void fdxDesktopVulkanDrawIndexed(
            long context,
            int indexCount,
            int instanceCount,
            int firstIndex,
            int baseVertex,
            int firstInstance);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_end_render_pass")
    private static native void fdxDesktopVulkanEndRenderPass(long context);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_surface_format")
    private static native int fdxDesktopVulkanSurfaceFormat(long context);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_destroy_shader_module")
    private static native void fdxDesktopVulkanDestroyShaderModule(long shaderModule);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_destroy_render_pipeline")
    private static native void fdxDesktopVulkanDestroyRenderPipeline(long pipeline, int published);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_destroy_buffer")
    private static native void fdxDesktopVulkanDestroyBuffer(long buffer);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_destroy_texture")
    private static native void fdxDesktopVulkanDestroyTexture(long texture);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_destroy")
    private static native void fdxDesktopVulkanDestroy(long context);

    @NativeImport("fdx_jn_fdx_desktop_vulkan_retain")
    private static native void fdxDesktopVulkanRetain(long context);
}
