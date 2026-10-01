package io.github.libfdx.backend.desktopc;

import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

import io.github.libfdx.core.FdxException;

import java.nio.ByteBuffer;

/**
 * Represents a desktop C vulkan.
 *
 * @author xpenatan
 */
@Include("libfdx_desktop_bridge.h")
final class DesktopCVulkan {
    private DesktopCVulkan() {}

    static void waitPreparations(long context) {

        native_waitPreparations(context);
    }

    @Import(name = "fdx_desktop_vulkan_wait_preparations")
    private static native void native_waitPreparations(long context);

    static long createCompute(long context, int[] words, String entry, int[] bindings) {
        byte[] entryBytes = entry.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return native_createCompute(context, words == null ? Address.fromLong(0) : Address.ofData(words), words == null ? 0 : words.length, Address.ofData(entryBytes), entryBytes.length, bindings == null ? Address.fromLong(0) : Address.ofData(bindings), bindings == null ? 0 : bindings.length);
    }

    @Import(name = "fdx_c_vulkan_create_compute")
    private static native long native_createCompute(long context, Address wordsData, int wordsLength, Address entryData, int entryLength, Address bindingsData, int bindingsLength);

    static void dispatchCompute(
            long context, long pipeline, long[] resources, int count, int x, int y, int z) {

        native_dispatchCompute(context, pipeline, resources == null ? Address.fromLong(0) : Address.ofData(resources), resources == null ? 0 : resources.length, count, x, y, z);
    }

    @Import(name = "fdx_c_vulkan_dispatch_compute")
    private static native void native_dispatchCompute(long context, long pipeline, Address resourcesData, int resourcesLength, int count, int x, int y, int z);

    static void copyBuffer(
            long context,
            long source,
            int sourceOffset,
            long destination,
            int destinationOffset,
            int size) {

        native_copyBuffer(context, source, sourceOffset, destination, destinationOffset, size);
    }

    @Import(name = "fdx_desktop_vulkan_copy_buffer")
    private static native void native_copyBuffer(long context, long source, int sourceOffset, long destination, int destinationOffset, int size);

    static void readBuffer(long source, int offset, ByteBuffer output, int size) {

        native_readBuffer(source, offset, output, output == null ? 0 : output.remaining(), size);
    }

    @Import(name = "fdx_c_vulkan_read_buffer")
    private static native void native_readBuffer(long source, int offset, ByteBuffer output, int outputLength, int size);

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
            return "GLFW did not expose required desktop C Vulkan instance extensions";
        }
        if (fdxDesktopVulkanProbeInstance() == 0) {
            return "Could not probe a desktop C Vulkan instance; see native stderr for Vulkan loader"
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
                "Could not create desktop C Vulkan context");
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
                "Could not create desktop C Vulkan buffer");
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
                "Could not create desktop C Vulkan texture");
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
            throw new FdxException("desktop C Vulkan requires vertex and fragment SPIR-V words");
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
                "Could not create desktop C Vulkan shader module");
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
                "Could not create desktop C Vulkan render pipeline");
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
            throw new FdxException("desktop C Vulkan uniform upload exceeds the ByteBuffer data");
        }
        if (fdxDesktopVulkanBindUniforms(context, pipeline, data, byteCount) == 0) {
            throw new FdxException("desktop C Vulkan could not bind the uniform buffer");
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
            throw new FdxException("desktop C Vulkan vertex attribute arrays are inconsistent");
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

    private static boolean glfwVulkanSupported() {

        return native_glfwVulkanSupported();
    }

    @Import(name = "fdx_c_glfwVulkanSupported")
    private static native boolean native_glfwVulkanSupported();

    private static int glfwGetRequiredInstanceExtensions() {

        return native_glfwGetRequiredInstanceExtensions();
    }

    @Import(name = "fdx_c_glfwGetRequiredInstanceExtensions")
    private static native int native_glfwGetRequiredInstanceExtensions();

    private static int fdxDesktopVulkanProbeInstance() {

        return native_fdxDesktopVulkanProbeInstance();
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_probe_instance")
    private static native int native_fdxDesktopVulkanProbeInstance();

    private static long fdxDesktopVulkanCreate(
            long windowHandle,
            int width,
            int height,
            int vSync,
            int preferMailboxPresentMode,
            int framesInFlight) {

        return native_fdxDesktopVulkanCreate(windowHandle, width, height, vSync, preferMailboxPresentMode, framesInFlight);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_create")
    private static native long native_fdxDesktopVulkanCreate(long windowHandle, int width, int height, int vSync, int preferMailboxPresentMode, int framesInFlight);

    private static void fdxDesktopVulkanResize(long context, int width, int height) {

        native_fdxDesktopVulkanResize(context, width, height);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_resize")
    private static native void native_fdxDesktopVulkanResize(long context, int width, int height);

    private static int fdxDesktopVulkanBeginFrame(long context) {

        return native_fdxDesktopVulkanBeginFrame(context);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_begin_frame")
    private static native int native_fdxDesktopVulkanBeginFrame(long context);

    private static void fdxDesktopVulkanEndFrame(long context) {

        native_fdxDesktopVulkanEndFrame(context);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_end_frame")
    private static native void native_fdxDesktopVulkanEndFrame(long context);

    private static void fdxDesktopVulkanReadPixelsRgba8(
            long context, ByteBuffer target, int byteCount) {

        native_fdxDesktopVulkanReadPixelsRgba8(context, target, target == null ? 0 : target.remaining(), byteCount);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_read_pixels_rgba8")
    private static native void native_fdxDesktopVulkanReadPixelsRgba8(long context, ByteBuffer target, int targetLength, int byteCount);

    private static void fdxDesktopVulkanClear(
            long context, float red, float green, float blue, float alpha) {

        native_fdxDesktopVulkanClear(context, red, green, blue, alpha);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_clear")
    private static native void native_fdxDesktopVulkanClear(long context, float red, float green, float blue, float alpha);

    private static long fdxDesktopVulkanCreateBuffer(long context, int size, int usage) {

        return native_fdxDesktopVulkanCreateBuffer(context, size, usage);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_create_buffer")
    private static native long native_fdxDesktopVulkanCreateBuffer(long context, int size, int usage);

    private static void fdxDesktopVulkanWriteBuffer(
            long buffer, ByteBuffer data, int byteCount) {

        native_fdxDesktopVulkanWriteBuffer(buffer, data, data == null ? 0 : data.remaining(), byteCount);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_write_buffer")
    private static native void native_fdxDesktopVulkanWriteBuffer(long buffer, ByteBuffer data, int dataLength, int byteCount);

    private static long fdxDesktopVulkanCreateTexture(
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

        return native_fdxDesktopVulkanCreateTexture(context, width, height, format, wrapS, wrapT, filter, magFilter, mipFilter, mipLevels, samples, usage);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_create_texture")
    private static native long native_fdxDesktopVulkanCreateTexture(long context, int width, int height, int format, int wrapS, int wrapT, int filter, int magFilter, int mipFilter, int mipLevels, int samples, int usage);

    private static int fdxDesktopVulkanWriteTexture(
            long texture, int level, ByteBuffer data, int byteCount) {

        return native_fdxDesktopVulkanWriteTexture(texture, level, data, data == null ? 0 : data.remaining(), byteCount);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_write_texture")
    private static native int native_fdxDesktopVulkanWriteTexture(long texture, int level, ByteBuffer data, int dataLength, int byteCount);

    private static long fdxDesktopVulkanCreateShaderModule(
            long context,
            int[] vertexWords,
            int vertexWordCount,
            int[] fragmentWords,
            int fragmentWordCount,
            String vertexEntry,
            String fragmentEntry) {
        byte[] vertexEntryBytes = vertexEntry.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] fragmentEntryBytes = fragmentEntry.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return native_fdxDesktopVulkanCreateShaderModule(context, vertexWords == null ? Address.fromLong(0) : Address.ofData(vertexWords), vertexWords == null ? 0 : vertexWords.length, vertexWordCount, fragmentWords == null ? Address.fromLong(0) : Address.ofData(fragmentWords), fragmentWords == null ? 0 : fragmentWords.length, fragmentWordCount, Address.ofData(vertexEntryBytes), vertexEntryBytes.length, Address.ofData(fragmentEntryBytes), fragmentEntryBytes.length);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_create_shader_module")
    private static native long native_fdxDesktopVulkanCreateShaderModule(long context, Address vertexWordsData, int vertexWordsLength, int vertexWordCount, Address fragmentWordsData, int fragmentWordsLength, int fragmentWordCount, Address vertexEntryData, int vertexEntryLength, Address fragmentEntryData, int fragmentEntryLength);

    private static long fdxDesktopVulkanCreateRenderPipeline(
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
            int[] state) {

        return native_fdxDesktopVulkanCreateRenderPipeline(context, shaderModule, primitiveTopology, vertexStrides == null ? Address.fromLong(0) : Address.ofData(vertexStrides), vertexStrides == null ? 0 : vertexStrides.length, vertexStepModes == null ? Address.fromLong(0) : Address.ofData(vertexStepModes), vertexStepModes == null ? 0 : vertexStepModes.length, vertexLayoutCount, attributeBindings == null ? Address.fromLong(0) : Address.ofData(attributeBindings), attributeBindings == null ? 0 : attributeBindings.length, attributeLocations == null ? Address.fromLong(0) : Address.ofData(attributeLocations), attributeLocations == null ? 0 : attributeLocations.length, attributeFormats == null ? Address.fromLong(0) : Address.ofData(attributeFormats), attributeFormats == null ? 0 : attributeFormats.length, attributeOffsets == null ? Address.fromLong(0) : Address.ofData(attributeOffsets), attributeOffsets == null ? 0 : attributeOffsets.length, attributeCount, sampledTextureCount, uniformBufferEnabled, state == null ? Address.fromLong(0) : Address.ofData(state), state == null ? 0 : state.length);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_create_render_pipeline")
    private static native long native_fdxDesktopVulkanCreateRenderPipeline(long context, long shaderModule, int primitiveTopology, Address vertexStridesData, int vertexStridesLength, Address vertexStepModesData, int vertexStepModesLength, int vertexLayoutCount, Address attributeBindingsData, int attributeBindingsLength, Address attributeLocationsData, int attributeLocationsLength, Address attributeFormatsData, int attributeFormatsLength, Address attributeOffsetsData, int attributeOffsetsLength, int attributeCount, int sampledTextureCount, int uniformBufferEnabled, Address stateData, int stateLength);

    private static int fdxDesktopVulkanBeginRenderPass(
            long context, int[] state, long[] textures, float[] clears) {

        return native_fdxDesktopVulkanBeginRenderPass(context, state == null ? Address.fromLong(0) : Address.ofData(state), state == null ? 0 : state.length, textures == null ? Address.fromLong(0) : Address.ofData(textures), textures == null ? 0 : textures.length, clears == null ? Address.fromLong(0) : Address.ofData(clears), clears == null ? 0 : clears.length);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_begin_render_pass")
    private static native int native_fdxDesktopVulkanBeginRenderPass(long context, Address stateData, int stateLength, Address texturesData, int texturesLength, Address clearsData, int clearsLength);

    private static void fdxDesktopVulkanSetPipeline(long context, long pipeline) {

        native_fdxDesktopVulkanSetPipeline(context, pipeline);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_set_pipeline")
    private static native void native_fdxDesktopVulkanSetPipeline(long context, long pipeline);

    private static void fdxDesktopVulkanSetVertexBuffer(long context, int slot, long buffer) {

        native_fdxDesktopVulkanSetVertexBuffer(context, slot, buffer);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_set_vertex_buffer")
    private static native void native_fdxDesktopVulkanSetVertexBuffer(long context, int slot, long buffer);

    private static void fdxDesktopVulkanSetIndexBuffer(long context, long buffer) {

        native_fdxDesktopVulkanSetIndexBuffer(context, buffer);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_set_index_buffer")
    private static native void native_fdxDesktopVulkanSetIndexBuffer(long context, long buffer);

    private static void fdxDesktopVulkanSetScissor(
            long context, int x, int y, int width, int height) {

        native_fdxDesktopVulkanSetScissor(context, x, y, width, height);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_set_scissor")
    private static native void native_fdxDesktopVulkanSetScissor(long context, int x, int y, int width, int height);

    private static void fdxDesktopVulkanSetViewport(
            long context, int x, int y, int width, int height) {

        native_fdxDesktopVulkanSetViewport(context, x, y, width, height);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_set_viewport")
    private static native void native_fdxDesktopVulkanSetViewport(long context, int x, int y, int width, int height);

    private static void fdxDesktopVulkanBindTextures(
            long context, long pipeline, long[] textures, int count) {

        native_fdxDesktopVulkanBindTextures(context, pipeline, textures == null ? Address.fromLong(0) : Address.ofData(textures), textures == null ? 0 : textures.length, count);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_bind_textures")
    private static native void native_fdxDesktopVulkanBindTextures(long context, long pipeline, Address texturesData, int texturesLength, int count);

    private static int fdxDesktopVulkanBindUniforms(
            long context, long pipeline, ByteBuffer data, int byteCount) {

        return native_fdxDesktopVulkanBindUniforms(context, pipeline, data, data == null ? 0 : data.remaining(), byteCount);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_bind_uniforms")
    private static native int native_fdxDesktopVulkanBindUniforms(long context, long pipeline, ByteBuffer data, int dataLength, int byteCount);

    private static void fdxDesktopVulkanDraw(
            long context, int vertexCount, int instanceCount, int firstVertex, int firstInstance) {

        native_fdxDesktopVulkanDraw(context, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_draw")
    private static native void native_fdxDesktopVulkanDraw(long context, int vertexCount, int instanceCount, int firstVertex, int firstInstance);

    private static void fdxDesktopVulkanDrawIndexed(
            long context,
            int indexCount,
            int instanceCount,
            int firstIndex,
            int baseVertex,
            int firstInstance) {

        native_fdxDesktopVulkanDrawIndexed(context, indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_draw_indexed")
    private static native void native_fdxDesktopVulkanDrawIndexed(long context, int indexCount, int instanceCount, int firstIndex, int baseVertex, int firstInstance);

    private static void fdxDesktopVulkanEndRenderPass(long context) {

        native_fdxDesktopVulkanEndRenderPass(context);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_end_render_pass")
    private static native void native_fdxDesktopVulkanEndRenderPass(long context);

    private static int fdxDesktopVulkanSurfaceFormat(long context) {

        return native_fdxDesktopVulkanSurfaceFormat(context);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_surface_format")
    private static native int native_fdxDesktopVulkanSurfaceFormat(long context);

    private static void fdxDesktopVulkanDestroyShaderModule(long shaderModule) {

        native_fdxDesktopVulkanDestroyShaderModule(shaderModule);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_destroy_shader_module")
    private static native void native_fdxDesktopVulkanDestroyShaderModule(long shaderModule);

    private static void fdxDesktopVulkanDestroyRenderPipeline(long pipeline, int published) {

        native_fdxDesktopVulkanDestroyRenderPipeline(pipeline, published);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_destroy_render_pipeline")
    private static native void native_fdxDesktopVulkanDestroyRenderPipeline(long pipeline, int published);

    private static void fdxDesktopVulkanDestroyBuffer(long buffer) {

        native_fdxDesktopVulkanDestroyBuffer(buffer);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_destroy_buffer")
    private static native void native_fdxDesktopVulkanDestroyBuffer(long buffer);

    private static void fdxDesktopVulkanDestroyTexture(long texture) {

        native_fdxDesktopVulkanDestroyTexture(texture);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_destroy_texture")
    private static native void native_fdxDesktopVulkanDestroyTexture(long texture);

    private static void fdxDesktopVulkanDestroy(long context) {

        native_fdxDesktopVulkanDestroy(context);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_destroy")
    private static native void native_fdxDesktopVulkanDestroy(long context);

    private static void fdxDesktopVulkanRetain(long context) {

        native_fdxDesktopVulkanRetain(context);
    }

    @Import(name = "fdx_c_fdx_desktop_vulkan_retain")
    private static native void native_fdxDesktopVulkanRetain(long context);
}
