package io.github.libfdx.graphics;

/**
 * Read-only diagnostics for the most recently submitted graphics frame.
 *
 * <p>Backends may reuse the returned object. Callers should read values when
 * needed instead of retaining it as an immutable snapshot.</p>
 */
public interface GraphicsFrameMetrics {
    GraphicsFrameMetrics UNAVAILABLE = new GraphicsFrameMetrics() {
        @Override public boolean available() { return false; }
        @Override public long frameId() { return -1L; }
        @Override public int drawCalls() { return 0; }
        @Override public long submittedVertices() { return 0L; }
        @Override public long submittedPrimitives() { return 0L; }
        @Override public int programBinds() { return 0; }
        @Override public int textureBinds() { return 0; }
        @Override public int framebufferBinds() { return 0; }
        @Override public int uniformUpdates() { return 0; }
        @Override public int bufferUploads() { return 0; }
        @Override public long bufferUploadBytes() { return 0L; }
        @Override public int textureUploads() { return 0; }
        @Override public long textureUploadBytes() { return 0L; }
        @Override public long gpuFrameId() { return -1L; }
        @Override public double gpuTimeMillis() { return Double.NaN; }
        @Override public String renderer() { return "unavailable"; }
    };

    boolean available();

    long frameId();

    int drawCalls();

    long submittedVertices();

    long submittedPrimitives();

    int programBinds();

    int textureBinds();

    int framebufferBinds();

    int uniformUpdates();

    int bufferUploads();

    long bufferUploadBytes();

    int textureUploads();

    long textureUploadBytes();

    long gpuFrameId();

    double gpuTimeMillis();

    /** Frame identifier associated with the latest asynchronous pipeline sample. */
    default long pipelineFrameId() {
        return -1L;
    }

    /** Number of vertex shader invocations measured by the graphics driver. */
    default long vertexShaderInvocations() {
        return 0L;
    }

    /** Number of fragment shader invocations measured by the graphics driver. */
    default long fragmentShaderInvocations() {
        return 0L;
    }

    /** Number of primitives entering the clipping stage. */
    default long clippingInputPrimitives() {
        return 0L;
    }

    /** Number of primitives leaving the clipping stage. */
    default long clippingOutputPrimitives() {
        return 0L;
    }

    String renderer();
}
