package io.github.libfdx.backend.desktopcpp;

import com.github.xpenatan.jnative.interop.NativeImport;

import java.nio.ByteBuffer;

/** Native entry points for compute, multiple targets, and complete pipeline state. */
final class DesktopCppGLFeatures {
    private DesktopCppGLFeatures() {}

    @NativeImport("fdx_jn_gl_features_supported")
    static native boolean supported(int feature);

    @NativeImport("fdx_jn_gl_compute_program")
    static native int computeProgram(String source);

    @NativeImport("fdx_jn_gl_compute_buffer")
    static native void computeBuffer(int slot, int buffer, int offset, int size, boolean uniform);

    @NativeImport("fdx_jn_gl_storage_image")
    static native void storageImage(int slot, int texture, int format);

    @NativeImport("fdx_jn_gl_dispatch_compute")
    static native void dispatch(int x, int y, int z);

    @NativeImport("fdx_jn_gl_compute_barrier")
    static native void barrier();

    @NativeImport("fdx_jn_gl_copy_buffer")
    static native void copyBuffer(
            int source, int sourceOffset, int destination, int destinationOffset, int size);

    @NativeImport("fdx_jn_gl_read_buffer")
    static native void readBuffer(int source, int offset, ByteBuffer output, int size);

    @NativeImport("fdx_jn_gl_texture_multisample")
    static native void textureMultisample(
            int texture, int format, int width, int height, int samples);

    @NativeImport("fdx_jn_gl_framebuffer_texture")
    static native void framebufferTexture(int index, int texture, int level, int samples);

    @NativeImport("fdx_jn_gl_draw_buffers")
    static native void drawBuffers(int count);

    @NativeImport("fdx_jn_gl_clear_attachment")
    static native void clearAttachment(int index, float red, float green, float blue, float alpha);

    @NativeImport("fdx_jn_gl_depth_multisample")
    static native void depthMultisample(int width, int height, int samples);

    @NativeImport("fdx_jn_gl_resolve_framebuffer")
    static native void resolve(int source, int index, int destination, int width, int height);

    @NativeImport("fdx_jn_gl_pipeline_state")
    static native void pipelineState(int[] state);

    @NativeImport("fdx_jn_gl_color_targets")
    static native void colorTargets(int[] state, int count);

    @NativeImport("fdx_jn_gl_reset_masks")
    static native void resetMasks();
}
