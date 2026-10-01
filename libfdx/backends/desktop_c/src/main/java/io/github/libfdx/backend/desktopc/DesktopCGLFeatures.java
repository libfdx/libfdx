package io.github.libfdx.backend.desktopc;

import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

import java.nio.ByteBuffer;

/** Native entry points for compute, multiple targets, and complete pipeline state. */
@Include("libfdx_desktop_bridge.h")
final class DesktopCGLFeatures {
    private DesktopCGLFeatures() {}

    static boolean supported(int feature) {

        return native_supported(feature);
    }

    @Import(name = "fdx_c_gl_features_supported")
    private static native boolean native_supported(int feature);

    static int computeProgram(String source) {
        byte[] sourceBytes = source.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return native_computeProgram(Address.ofData(sourceBytes), sourceBytes.length);
    }

    @Import(name = "fdx_c_gl_compute_program")
    private static native int native_computeProgram(Address sourceData, int sourceLength);

    static void computeBuffer(int slot, int buffer, int offset, int size, boolean uniform) {

        native_computeBuffer(slot, buffer, offset, size, uniform);
    }

    @Import(name = "fdx_c_gl_compute_buffer")
    private static native void native_computeBuffer(int slot, int buffer, int offset, int size, boolean uniform);

    static void storageImage(int slot, int texture, int format) {

        native_storageImage(slot, texture, format);
    }

    @Import(name = "fdx_c_gl_storage_image")
    private static native void native_storageImage(int slot, int texture, int format);

    static void dispatch(int x, int y, int z) {

        native_dispatch(x, y, z);
    }

    @Import(name = "fdx_c_gl_dispatch_compute")
    private static native void native_dispatch(int x, int y, int z);

    static void barrier() {

        native_barrier();
    }

    @Import(name = "fdx_c_gl_compute_barrier")
    private static native void native_barrier();

    static void copyBuffer(
            int source, int sourceOffset, int destination, int destinationOffset, int size) {

        native_copyBuffer(source, sourceOffset, destination, destinationOffset, size);
    }

    @Import(name = "fdx_c_gl_copy_buffer")
    private static native void native_copyBuffer(int source, int sourceOffset, int destination, int destinationOffset, int size);

    static void readBuffer(int source, int offset, ByteBuffer output, int size) {
        if (output != null && output.position() != 0) output = output.slice();
        native_readBuffer(source, offset, output, output == null ? 0 : output.remaining(), size);
    }

    @Import(name = "fdx_c_gl_read_buffer")
    private static native void native_readBuffer(int source, int offset, ByteBuffer output, int outputLength, int size);

    static void textureMultisample(
            int texture, int format, int width, int height, int samples) {

        native_textureMultisample(texture, format, width, height, samples);
    }

    @Import(name = "fdx_c_gl_texture_multisample")
    private static native void native_textureMultisample(int texture, int format, int width, int height, int samples);

    static void framebufferTexture(int index, int texture, int level, int samples) {

        native_framebufferTexture(index, texture, level, samples);
    }

    @Import(name = "fdx_c_gl_framebuffer_texture")
    private static native void native_framebufferTexture(int index, int texture, int level, int samples);

    static void drawBuffers(int count) {

        native_drawBuffers(count);
    }

    @Import(name = "fdx_c_gl_draw_buffers")
    private static native void native_drawBuffers(int count);

    static void clearAttachment(int index, float red, float green, float blue, float alpha) {

        native_clearAttachment(index, red, green, blue, alpha);
    }

    @Import(name = "fdx_c_gl_clear_attachment")
    private static native void native_clearAttachment(int index, float red, float green, float blue, float alpha);

    static void depthMultisample(int width, int height, int samples) {

        native_depthMultisample(width, height, samples);
    }

    @Import(name = "fdx_c_gl_depth_multisample")
    private static native void native_depthMultisample(int width, int height, int samples);

    static void resolve(int source, int index, int destination, int width, int height) {

        native_resolve(source, index, destination, width, height);
    }

    @Import(name = "fdx_c_gl_resolve_framebuffer")
    private static native void native_resolve(int source, int index, int destination, int width, int height);

    static void pipelineState(int[] state) {

        native_pipelineState(state == null ? Address.fromLong(0) : Address.ofData(state), state == null ? 0 : state.length);
    }

    @Import(name = "fdx_c_gl_pipeline_state")
    private static native void native_pipelineState(Address stateData, int stateLength);

    static void colorTargets(int[] state, int count) {

        native_colorTargets(state == null ? Address.fromLong(0) : Address.ofData(state), state == null ? 0 : state.length, count);
    }

    @Import(name = "fdx_c_gl_color_targets")
    private static native void native_colorTargets(Address stateData, int stateLength, int count);

    static void resetMasks() {

        native_resetMasks();
    }

    @Import(name = "fdx_c_gl_reset_masks")
    private static native void native_resetMasks();
}
