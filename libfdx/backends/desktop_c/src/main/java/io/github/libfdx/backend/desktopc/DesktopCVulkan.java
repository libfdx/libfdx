package io.github.libfdx.backend.desktopc;

import io.github.libfdx.core.FdxException;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

import java.nio.ByteBuffer;

/**
 * Represents a desktop C vulkan.
 *
 * @author xpenatan
 */
@Include("libfdx_desktop_vulkan.h")
final class DesktopCVulkan {
    private DesktopCVulkan() {
    }

    static boolean vulkanSupported() {
        return glfwVulkanSupported();
    }

    static int requiredInstanceExtensionCount() {
        int[] count = new int[1];
        Address extensions = glfwGetRequiredInstanceExtensions(Address.ofData(count));
        if (extensions == null || extensions.toLong() == 0L) {
            return 0;
        }
        return count[0];
    }

    static String supportFailureReason() {
        if (!vulkanSupported()) {
            return "Vulkan is not supported by GLFW on this system";
        }
        if (requiredInstanceExtensionCount() <= 0) {
            return "GLFW did not expose required desktop C Vulkan instance extensions";
        }
        if (fdxDesktopVulkanProbeInstance() == 0) {
            return "Could not probe a desktop C Vulkan instance; see native stderr for Vulkan loader details";
        }
        return null;
    }

    static long create(long windowHandle, int width, int height, boolean vSync, boolean preferMailboxPresentMode,
            int framesInFlight) {
        return requireHandle(fdxDesktopVulkanCreate(windowHandle, width, height, bool(vSync),
                bool(preferMailboxPresentMode), framesInFlight), "Could not create desktop C Vulkan context");
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
        return requireHandle(fdxDesktopVulkanCreateBuffer(context, size, usage),
                "Could not create desktop C Vulkan buffer");
    }

    static void writeBuffer(long buffer, ByteBuffer data, int byteCount) {
        fdxDesktopVulkanWriteBuffer(buffer, data, byteCount);
    }

    static long createTexture(long context, int width, int height, int format, int wrapS, int wrapT) {
        return requireHandle(fdxDesktopVulkanCreateTexture(context, width, height, format, wrapS, wrapT),
                "Could not create desktop C Vulkan texture");
    }

    static void writeTexture(long texture, ByteBuffer data, int byteCount) {
        fdxDesktopVulkanWriteTexture(texture, data, byteCount);
    }

    static long createShaderModule(long context, int[] vertexWords, int[] fragmentWords) {
        if (vertexWords == null || fragmentWords == null) {
            throw new FdxException("desktop C Vulkan requires vertex and fragment SPIR-V words");
        }
        return requireHandle(fdxDesktopVulkanCreateShaderModule(context, Address.ofData(vertexWords),
                vertexWords.length, Address.ofData(fragmentWords), fragmentWords.length),
                "Could not create desktop C Vulkan shader module");
    }

    static long createRenderPipeline(long context, long shaderModule, int primitiveTopology, int[] vertexStrides,
            int[] vertexStepModes, int[] attributeBindings, int[] attributeLocations, int[] attributeFormats,
            int[] attributeOffsets, int sampledTextureCount, boolean pbrUniformsEnabled, boolean depthTestEnabled,
            boolean depthWriteEnabled) {
        int vertexLayoutCount = vertexStrides != null ? vertexStrides.length : 0;
        int attributeCount = attributeLocations != null ? attributeLocations.length : 0;
        Address strides = addressOf(vertexStrides, vertexLayoutCount);
        Address stepModes = addressOf(vertexStepModes, vertexLayoutCount);
        Address bindings = addressOf(attributeBindings, attributeCount);
        Address locations = addressOf(attributeLocations, attributeCount);
        Address formats = addressOf(attributeFormats, attributeCount);
        Address offsets = addressOf(attributeOffsets, attributeCount);
        return requireHandle(fdxDesktopVulkanCreateRenderPipeline(context, shaderModule, primitiveTopology,
                strides, stepModes, vertexLayoutCount, bindings, locations, formats, offsets, attributeCount,
                sampledTextureCount, bool(pbrUniformsEnabled), bool(depthTestEnabled), bool(depthWriteEnabled)),
                "Could not create desktop C Vulkan render pipeline");
    }

    static void beginRenderPass(long context, boolean clear, float red, float green, float blue, float alpha,
            boolean store, boolean depthClear, float depthClearValue) {
        fdxDesktopVulkanBeginRenderPass(context, bool(clear), red, green, blue, alpha, bool(store),
                bool(depthClear), depthClearValue);
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

    static void bindTextures(long context, long pipeline, long[] textures, int count) {
        fdxDesktopVulkanBindTextures(context, pipeline, Address.ofData(textures), count);
    }

    static void bindUniforms(long context, long pipeline, ByteBuffer data, int byteCount) {
        fdxDesktopVulkanBindUniforms(context, pipeline, data, byteCount);
    }

    static void draw(long context, int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        fdxDesktopVulkanDraw(context, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    static void drawIndexed(long context, int indexCount, int instanceCount, int firstIndex, int baseVertex,
            int firstInstance) {
        fdxDesktopVulkanDrawIndexed(context, indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
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

    static void destroyRenderPipeline(long pipeline) {
        fdxDesktopVulkanDestroyRenderPipeline(pipeline);
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

    private static Address addressOf(int[] values, int expectedLength) {
        if (expectedLength <= 0) {
            return Address.fromLong(0L);
        }
        if (values == null || values.length < expectedLength) {
            throw new FdxException("desktop C Vulkan vertex attribute arrays are inconsistent");
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

    @Import(name = "glfwVulkanSupported")
    private static native boolean glfwVulkanSupported();

    @Import(name = "glfwGetRequiredInstanceExtensions")
    private static native Address glfwGetRequiredInstanceExtensions(Address count);

    @Import(name = "fdx_desktop_vulkan_probe_instance")
    private static native int fdxDesktopVulkanProbeInstance();

    @Import(name = "fdx_desktop_vulkan_create")
    private static native long fdxDesktopVulkanCreate(long windowHandle, int width, int height, int vSync,
            int preferMailboxPresentMode, int framesInFlight);

    @Import(name = "fdx_desktop_vulkan_resize")
    private static native void fdxDesktopVulkanResize(long context, int width, int height);

    @Import(name = "fdx_desktop_vulkan_begin_frame")
    private static native int fdxDesktopVulkanBeginFrame(long context);

    @Import(name = "fdx_desktop_vulkan_end_frame")
    private static native void fdxDesktopVulkanEndFrame(long context);

    @Import(name = "fdx_desktop_vulkan_read_pixels_rgba8")
    private static native void fdxDesktopVulkanReadPixelsRgba8(long context, ByteBuffer target, int byteCount);

    @Import(name = "fdx_desktop_vulkan_clear")
    private static native void fdxDesktopVulkanClear(long context, float red, float green, float blue, float alpha);

    @Import(name = "fdx_desktop_vulkan_create_buffer")
    private static native long fdxDesktopVulkanCreateBuffer(long context, int size, int usage);

    @Import(name = "fdx_desktop_vulkan_write_buffer")
    private static native void fdxDesktopVulkanWriteBuffer(long buffer, ByteBuffer data, int byteCount);

    @Import(name = "fdx_desktop_vulkan_create_texture")
    private static native long fdxDesktopVulkanCreateTexture(long context, int width, int height, int format,
            int wrapS, int wrapT);

    @Import(name = "fdx_desktop_vulkan_write_texture")
    private static native void fdxDesktopVulkanWriteTexture(long texture, ByteBuffer data, int byteCount);

    @Import(name = "fdx_desktop_vulkan_create_shader_module")
    private static native long fdxDesktopVulkanCreateShaderModule(long context, Address vertexWords,
            int vertexWordCount, Address fragmentWords, int fragmentWordCount);

    @Import(name = "fdx_desktop_vulkan_create_render_pipeline")
    private static native long fdxDesktopVulkanCreateRenderPipeline(long context, long shaderModule,
            int primitiveTopology, Address vertexStrides, Address vertexStepModes, int vertexLayoutCount,
            Address attributeBindings, Address attributeLocations, Address attributeFormats,
            Address attributeOffsets, int attributeCount, int sampledTextureCount, int pbrUniformsEnabled,
            int depthTestEnabled, int depthWriteEnabled);

    @Import(name = "fdx_desktop_vulkan_begin_render_pass")
    private static native void fdxDesktopVulkanBeginRenderPass(long context, int clear, float red, float green,
            float blue, float alpha, int store, int depthClear, float depthClearValue);

    @Import(name = "fdx_desktop_vulkan_set_pipeline")
    private static native void fdxDesktopVulkanSetPipeline(long context, long pipeline);

    @Import(name = "fdx_desktop_vulkan_set_vertex_buffer")
    private static native void fdxDesktopVulkanSetVertexBuffer(long context, int slot, long buffer);

    @Import(name = "fdx_desktop_vulkan_set_index_buffer")
    private static native void fdxDesktopVulkanSetIndexBuffer(long context, long buffer);

    @Import(name = "fdx_desktop_vulkan_set_scissor")
    private static native void fdxDesktopVulkanSetScissor(long context, int x, int y, int width, int height);

    @Import(name = "fdx_desktop_vulkan_bind_textures")
    private static native void fdxDesktopVulkanBindTextures(long context, long pipeline, Address textures, int count);

    @Import(name = "fdx_desktop_vulkan_bind_uniforms")
    private static native void fdxDesktopVulkanBindUniforms(long context, long pipeline, ByteBuffer data, int byteCount);

    @Import(name = "fdx_desktop_vulkan_draw")
    private static native void fdxDesktopVulkanDraw(long context, int vertexCount, int instanceCount,
            int firstVertex, int firstInstance);

    @Import(name = "fdx_desktop_vulkan_draw_indexed")
    private static native void fdxDesktopVulkanDrawIndexed(long context, int indexCount, int instanceCount,
            int firstIndex, int baseVertex, int firstInstance);

    @Import(name = "fdx_desktop_vulkan_end_render_pass")
    private static native void fdxDesktopVulkanEndRenderPass(long context);

    @Import(name = "fdx_desktop_vulkan_surface_format")
    private static native int fdxDesktopVulkanSurfaceFormat(long context);

    @Import(name = "fdx_desktop_vulkan_destroy_shader_module")
    private static native void fdxDesktopVulkanDestroyShaderModule(long shaderModule);

    @Import(name = "fdx_desktop_vulkan_destroy_render_pipeline")
    private static native void fdxDesktopVulkanDestroyRenderPipeline(long pipeline);

    @Import(name = "fdx_desktop_vulkan_destroy_buffer")
    private static native void fdxDesktopVulkanDestroyBuffer(long buffer);

    @Import(name = "fdx_desktop_vulkan_destroy_texture")
    private static native void fdxDesktopVulkanDestroyTexture(long texture);

    @Import(name = "fdx_desktop_vulkan_destroy")
    private static native void fdxDesktopVulkanDestroy(long context);
}
