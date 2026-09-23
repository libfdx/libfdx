package io.github.libfdx.backend.desktop;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.gl.GLApi;
import io.github.libfdx.graphics.gl.GLProgramBinary;
import io.github.libfdx.graphics.gl.GLShaderType;
import io.github.libfdx.graphics.GraphicsFrameMetrics;
import io.github.libfdx.graphics.MultisampleState;
import io.github.libfdx.graphics.PrimitiveState;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureMipmapFilter;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import org.lwjgl.opengl.ARBComputeShader;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.ARBGetProgramBinary;
import org.lwjgl.opengl.ARBParallelShaderCompile;
import org.lwjgl.opengl.ARBPipelineStatisticsQuery;
import org.lwjgl.opengl.ARBRobustness;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.KHRParallelShaderCompile;
import org.lwjgl.opengl.KHRRobustness;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Exposes API access for desktop GL.
 *
 * @author xpenatan
 */
final class DesktopGLApi implements GLApi {
    private Boolean computeSupported;
    private final int preparationWorkers;
    private DesktopAssetExecutor preparationExecutor;
    private boolean preparationClosed;
    private Boolean parallelCompilationSupported;
    private int[] binaryFormats;
    private String binaryIdentity;
    private final boolean resetStatusCore, resetStatusArb;
    private boolean contextLost;

    DesktopGLApi(int preparationWorkers) {
        this.preparationWorkers = preparationWorkers;
        var capabilities = GL.getCapabilities();
        resetStatusCore = capabilities.OpenGL45 || capabilities.GL_KHR_robustness;
        resetStatusArb = capabilities.GL_ARB_robustness;
    }

    @Override public boolean isContextLost() {
        if (!contextLost) {
            int status = resetStatusCore ? KHRRobustness.glGetGraphicsResetStatus()
                    : resetStatusArb ? ARBRobustness.glGetGraphicsResetStatusARB() : GL11.GL_NO_ERROR;
            contextLost = status != GL11.GL_NO_ERROR;
        }
        return contextLost;
    }

    @Override public int shaderPreparationWorkers() { return preparationWorkers; }
    @Override public synchronized void executeShaderPreparation(Runnable task) {
        if (preparationClosed) throw new FdxException("GL shader preparation is closed");
        if (preparationExecutor == null) preparationExecutor = new DesktopAssetExecutor(preparationWorkers, 256);
        if (!preparationExecutor.submit(task)) throw new FdxException("GL shader preparation queue is full");
    }
    @Override public synchronized void closeShaderPreparation() {
        preparationClosed = true;
        if (preparationExecutor != null) preparationExecutor.dispose();
    }
    @Override public boolean supportsParallelShaderCompilation() {
        if (parallelCompilationSupported != null) return parallelCompilationSupported;
        var caps = GL.getCapabilities();
        if (caps.GL_KHR_parallel_shader_compile) {
            KHRParallelShaderCompile.glMaxShaderCompilerThreadsKHR(preparationWorkers);
            return parallelCompilationSupported = true;
        }
        if (caps.GL_ARB_parallel_shader_compile) {
            ARBParallelShaderCompile.glMaxShaderCompilerThreadsARB(preparationWorkers);
            return parallelCompilationSupported = true;
        }
        return parallelCompilationSupported = false;
    }
    @Override public boolean programCompilationComplete(int program) {
        return GL20.glGetProgrami(program, KHRParallelShaderCompile.GL_COMPLETION_STATUS_KHR) != 0;
    }

    @Override public String programBinaryIdentity() {
        if (binaryFormats != null) return binaryIdentity;
        binaryFormats = new int[0];
        var caps = GL.getCapabilities();
        if (!caps.OpenGL41 && !caps.GL_ARB_get_program_binary) return null;
        int count = GL11.glGetInteger(ARBGetProgramBinary.GL_NUM_PROGRAM_BINARY_FORMATS);
        if (count <= 0 || count > 256) return null;
        binaryFormats = new int[count];
        GL11.glGetIntegerv(ARBGetProgramBinary.GL_PROGRAM_BINARY_FORMATS, binaryFormats);
        String vendor = GL11.glGetString(GL11.GL_VENDOR), renderer = GL11.glGetString(GL11.GL_RENDERER);
        String version = GL11.glGetString(GL11.GL_VERSION), language = GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION);
        if (vendor == null || renderer == null || version == null || language == null) return null;
        binaryIdentity = String.join("\n", "desktop-gl-program:1", vendor, renderer, version, language,
                System.getProperty("os.name", ""), System.getProperty("os.version", ""), System.getProperty("os.arch", ""),
                Integer.toString(GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS)),
                Integer.toString(GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK)), Arrays.toString(binaryFormats));
        return binaryIdentity;
    }

    @Override public void hintProgramBinaryRetrievable(int program) {
        ARBGetProgramBinary.glProgramParameteri(program, ARBGetProgramBinary.GL_PROGRAM_BINARY_RETRIEVABLE_HINT, GL11.GL_TRUE);
    }

    @Override public boolean restoreProgramBinary(int program, GLProgramBinary binary) {
        boolean supported = false;
        for (int format : binaryFormats) if (format == binary.format()) supported = true;
        if (!supported) return false;
        ByteBuffer bytes = MemoryUtil.memAlloc(binary.bytes().length);
        try {
            bytes.put(binary.bytes()).flip();
            ARBGetProgramBinary.glProgramBinary(program, binary.format(), bytes);
            return GL11.glGetError() == GL11.GL_NO_ERROR;
        } finally { MemoryUtil.memFree(bytes); }
    }

    @Override public GLProgramBinary exportProgramBinary(int program, int maximumBytes) {
        int size = GL20.glGetProgrami(program, ARBGetProgramBinary.GL_PROGRAM_BINARY_LENGTH);
        if (size <= 0 || size > maximumBytes) return null;
        ByteBuffer bytes = MemoryUtil.memAlloc(size);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer length = stack.callocInt(1), format = stack.callocInt(1);
            ARBGetProgramBinary.glGetProgramBinary(program, length, format, bytes);
            if (GL11.glGetError() != GL11.GL_NO_ERROR || length.get(0) <= 0 || length.get(0) > size) return null;
            byte[] result = new byte[length.get(0)]; bytes.get(result);
            return new GLProgramBinary(format.get(0), result);
        } finally { MemoryUtil.memFree(bytes); }
    }

    @Override public boolean supportsCompute() {
        if (computeSupported != null) return computeSupported;
        var caps = GL.getCapabilities();
        if (!(caps.OpenGL43 || caps.GL_ARB_compute_shader && caps.GL_ARB_shader_storage_buffer_object
                && caps.GL_ARB_shader_image_load_store)) return computeSupported = false;
        // A 3.3 context can expose later functionality via extensions. Verify the exact
        // language emitted by Tint rather than inferring it from the context version.
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        try {
            GL20.glShaderSource(shader, "#version 460\nlayout(local_size_x=1) in; void main() {}\n");
            GL20.glCompileShader(shader);
            return computeSupported = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0;
        } finally { GL20.glDeleteShader(shader); }
    }

    @Override public int createComputeProgram(String source) {
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        int program = 0;
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
                throw new FdxException("Could not compile GL compute shader: " + GL20.glGetShaderInfoLog(shader));
            }
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, shader);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                throw new FdxException("Could not link GL compute shader: " + GL20.glGetProgramInfoLog(program));
            }
            return program;
        } catch (RuntimeException | Error failure) {
            if (program != 0) GL20.glDeleteProgram(program);
            throw failure;
        } finally { GL20.glDeleteShader(shader); }
    }

    @Override public void bindComputeBuffer(int slot, int buffer, int offset, int size, boolean uniform) {
        GL30.glBindBufferRange(uniform ? GL31.GL_UNIFORM_BUFFER : GL43.GL_SHADER_STORAGE_BUFFER,
                slot, buffer, offset, size);
    }

    @Override public void bindStorageImage(int slot, int texture, TextureFormat format) {
        GL42.glBindImageTexture(slot, texture, 0, false, 0, GL15.GL_READ_WRITE,
                GLApi.colorInternalFormat(format));
    }

    @Override public void dispatchCompute(int x, int y, int z) {
        ARBComputeShader.glDispatchCompute(x, y, z);
    }

    @Override public void computeMemoryBarrier() {
        GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
    }

    @Override public void copyBuffer(int source, int sourceOffset, int destination, int destinationOffset, int size) {
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, source);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, destination);
        GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, sourceOffset, destinationOffset, size);
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
    }

    @Override public void readBuffer(int source, int offset, ByteBuffer destination) {
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, source);
        GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER, offset, destination);
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
    }
    @Override public boolean supportsCompletePipelineState() {
        return GL.getCapabilities().OpenGL46 || GL.getCapabilities().GL_ARB_polygon_offset_clamp
                || GL.getCapabilities().GL_EXT_polygon_offset_clamp;
    }

    @Override public void applyPipelineState(PrimitiveState primitive,
            ColorTargetState color,
            DepthStencilState depth,
            MultisampleState samples) {
        DesktopGLPipelineState.apply(primitive, color, depth, samples);
    }

    @Override public void resetAttachmentWriteMasks() {
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glStencilMask(-1);
    }
    @Override public boolean supportsDepthTextures() { return true; }
    @Override public boolean supportsRgba16FloatTextures() { return true; }
    @Override public void texImageDepth32F(int width, int height) {
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, 0x8CAC, width, height, 0, 0x1902, GL11.GL_FLOAT, (ByteBuffer)null);
    }
    @Override public void framebufferDepthTexture2D(int texture) {
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
    }

    private static final int QUERY_RING_SIZE = 4;
    private static final int[] PIPELINE_QUERY_TARGETS = {
            ARBPipelineStatisticsQuery.GL_VERTEX_SHADER_INVOCATIONS_ARB,
            ARBPipelineStatisticsQuery.GL_FRAGMENT_SHADER_INVOCATIONS_ARB,
            ARBPipelineStatisticsQuery.GL_CLIPPING_INPUT_PRIMITIVES_ARB,
            ARBPipelineStatisticsQuery.GL_CLIPPING_OUTPUT_PRIMITIVES_ARB
    };

    private final boolean metricsEnabled = Boolean.parseBoolean(System.getProperty(
            "libfdx.profileFrames", "false"));
    private final boolean pipelineMetricsEnabled = Boolean.parseBoolean(
            System.getProperty("libfdx.profilePipelineStats", "false"));
    private final DesktopFrameMetrics metrics = new DesktopFrameMetrics();
    private final int[] timerQueries = new int[QUERY_RING_SIZE];
    private final boolean[] timerQueryPending = new boolean[QUERY_RING_SIZE];
    private final long[] timerQueryFrameIds = new long[QUERY_RING_SIZE];
    private final int[][] pipelineQueries =
            new int[PIPELINE_QUERY_TARGETS.length][QUERY_RING_SIZE];
    private final boolean[] pipelineQueryPending = new boolean[QUERY_RING_SIZE];
    private final long[] pipelineQueryFrameIds = new long[QUERY_RING_SIZE];
    private boolean timerQueriesInitialized;
    private boolean timerQueriesSupported;
    private boolean pipelineQueriesSupported;
    private int nextTimerQuery;
    private int activeTimerQuery = -1;
    private int nextPipelineQuery;
    private int activePipelineQuery = -1;
    private long currentFrameId;
    private int drawCalls;
    private long submittedVertices;
    private long submittedPrimitives;
    private int programBinds;
    private int textureBinds;
    private int framebufferBinds;
    private int uniformUpdates;
    private int bufferUploads;
    private long bufferUploadBytes;
    private int textureUploads;
    private long textureUploadBytes;

    @Override
    public void beginFrameMetrics(long frameId) {
        if (!metricsEnabled) {
            return;
        }
        initializeTimerQueries();
        pollTimerQueries();
        currentFrameId = frameId;
        drawCalls = 0;
        submittedVertices = 0L;
        submittedPrimitives = 0L;
        programBinds = 0;
        textureBinds = 0;
        framebufferBinds = 0;
        uniformUpdates = 0;
        bufferUploads = 0;
        bufferUploadBytes = 0L;
        textureUploads = 0;
        textureUploadBytes = 0L;
        activeTimerQuery = -1;
        if (timerQueriesSupported && !timerQueryPending[nextTimerQuery]) {
            activeTimerQuery = nextTimerQuery;
            GL33.glBeginQuery(GL33.GL_TIME_ELAPSED, timerQueries[activeTimerQuery]);
        }
        activePipelineQuery = -1;
        if (pipelineQueriesSupported
                && !pipelineQueryPending[nextPipelineQuery]) {
            activePipelineQuery = nextPipelineQuery;
            for (int targetIndex = 0;
                    targetIndex < PIPELINE_QUERY_TARGETS.length;
                    targetIndex++) {
                GL15.glBeginQuery(PIPELINE_QUERY_TARGETS[targetIndex],
                        pipelineQueries[targetIndex][activePipelineQuery]);
            }
        }
    }

    @Override
    public void endFrameMetrics() {
        if (!metricsEnabled) {
            return;
        }
        if (activeTimerQuery >= 0) {
            GL33.glEndQuery(GL33.GL_TIME_ELAPSED);
            timerQueryPending[activeTimerQuery] = true;
            timerQueryFrameIds[activeTimerQuery] = currentFrameId;
            nextTimerQuery = (activeTimerQuery + 1) % QUERY_RING_SIZE;
            activeTimerQuery = -1;
        }
        if (activePipelineQuery >= 0) {
            for (int target : PIPELINE_QUERY_TARGETS) {
                GL15.glEndQuery(target);
            }
            pipelineQueryPending[activePipelineQuery] = true;
            pipelineQueryFrameIds[activePipelineQuery] = currentFrameId;
            nextPipelineQuery = (activePipelineQuery + 1) % QUERY_RING_SIZE;
            activePipelineQuery = -1;
        }
        metrics.frameId = currentFrameId;
        metrics.drawCalls = drawCalls;
        metrics.submittedVertices = submittedVertices;
        metrics.submittedPrimitives = submittedPrimitives;
        metrics.programBinds = programBinds;
        metrics.textureBinds = textureBinds;
        metrics.framebufferBinds = framebufferBinds;
        metrics.uniformUpdates = uniformUpdates;
        metrics.bufferUploads = bufferUploads;
        metrics.bufferUploadBytes = bufferUploadBytes;
        metrics.textureUploads = textureUploads;
        metrics.textureUploadBytes = textureUploadBytes;
    }

    @Override
    public void disposeFrameMetrics() {
        if (!timerQueriesInitialized) {
            return;
        }
        if (activeTimerQuery >= 0) {
            GL33.glEndQuery(GL33.GL_TIME_ELAPSED);
            activeTimerQuery = -1;
        }
        if (activePipelineQuery >= 0) {
            for (int target : PIPELINE_QUERY_TARGETS) {
                GL15.glEndQuery(target);
            }
            activePipelineQuery = -1;
        }
        for (int query : timerQueries) {
            if (query != 0) {
                GL33.glDeleteQueries(query);
            }
        }
        for (int[] queries : pipelineQueries) {
            for (int query : queries) {
                if (query != 0) {
                    GL15.glDeleteQueries(query);
                }
            }
        }
        timerQueriesInitialized = false;
    }

    @Override
    public GraphicsFrameMetrics frameMetrics() {
        return metricsEnabled ? metrics : GraphicsFrameMetrics.UNAVAILABLE;
    }

    private void initializeTimerQueries() {
        if (timerQueriesInitialized) {
            return;
        }
        timerQueriesInitialized = true;
        timerQueriesSupported = GL.getCapabilities().OpenGL33
                || GL.getCapabilities().GL_ARB_timer_query;
        pipelineQueriesSupported = pipelineMetricsEnabled
                && GL.getCapabilities().GL_ARB_pipeline_statistics_query;
        metrics.available = true;
        String renderer = GL11.glGetString(GL11.GL_RENDERER);
        String vendor = GL11.glGetString(GL11.GL_VENDOR);
        metrics.renderer = (renderer != null ? renderer : "unknown OpenGL renderer")
                + (vendor != null ? " (" + vendor + ")" : "");
        if (timerQueriesSupported) {
            for (int index = 0; index < timerQueries.length; index++) {
                timerQueries[index] = GL15.glGenQueries();
            }
        }
        if (pipelineQueriesSupported) {
            for (int[] queries : pipelineQueries) {
                for (int index = 0; index < queries.length; index++) {
                    queries[index] = GL15.glGenQueries();
                }
            }
        }
    }

    private void pollTimerQueries() {
        if (!timerQueriesSupported) {
            return;
        }
        for (int index = 0; index < timerQueries.length; index++) {
            if (!timerQueryPending[index]
                    || GL15.glGetQueryObjecti(timerQueries[index],
                    GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) {
                continue;
            }
            long elapsedNanos = GL33.glGetQueryObjecti64(timerQueries[index],
                    GL15.GL_QUERY_RESULT);
            metrics.gpuFrameId = timerQueryFrameIds[index];
            metrics.gpuTimeMillis = elapsedNanos / 1_000_000.0;
            timerQueryPending[index] = false;
        }
        pollPipelineQueries();
    }

    private void pollPipelineQueries() {
        if (!pipelineQueriesSupported) {
            return;
        }
        queryLoop:
        for (int index = 0; index < QUERY_RING_SIZE; index++) {
            if (!pipelineQueryPending[index]) {
                continue;
            }
            for (int[] queries : pipelineQueries) {
                if (GL15.glGetQueryObjecti(queries[index],
                        GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) {
                    continue queryLoop;
                }
            }
            metrics.pipelineFrameId = pipelineQueryFrameIds[index];
            metrics.vertexShaderInvocations = queryResult(
                    pipelineQueries[0][index]);
            metrics.fragmentShaderInvocations = queryResult(
                    pipelineQueries[1][index]);
            metrics.clippingInputPrimitives = queryResult(
                    pipelineQueries[2][index]);
            metrics.clippingOutputPrimitives = queryResult(
                    pipelineQueries[3][index]);
            pipelineQueryPending[index] = false;
        }
    }

    private static long queryResult(int query) {
        return GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT);
    }

    private void recordDraw(PrimitiveTopology topology, int elementCount,
            int instanceCount) {
        if (!metricsEnabled) {
            return;
        }
        long instances = Math.max(1, instanceCount);
        long vertices = (long) Math.max(0, elementCount) * instances;
        drawCalls++;
        submittedVertices += vertices;
        if (topology == PrimitiveTopology.LINE_LIST) {
            submittedPrimitives += vertices / 2L;
        }
        else if (topology == PrimitiveTopology.TRIANGLE_STRIP) {
            submittedPrimitives += Math.max(0L, elementCount - 2L) * instances;
        }
        else {
            submittedPrimitives += vertices / 3L;
        }
    }

    private void recordUpload(int bytes) {
        if (metricsEnabled) {
            bufferUploads++;
            bufferUploadBytes += Math.max(0, bytes);
        }
    }

    private void recordTextureUpload(int width, int height, ByteBuffer data) {
        recordTextureUpload(width, height, 4, data);
    }

    private void recordTextureUpload(int width, int height, int bytesPerPixel, ByteBuffer data) {
        if (metricsEnabled && data != null) {
            textureUploads++;
            textureUploadBytes += Math.min(data.remaining(),
                    Math.max(0L, (long) width * height * bytesPerPixel));
        }
    }

    private static final class DesktopFrameMetrics implements GraphicsFrameMetrics {
        boolean available;
        long frameId = -1L;
        int drawCalls;
        long submittedVertices;
        long submittedPrimitives;
        int programBinds;
        int textureBinds;
        int framebufferBinds;
        int uniformUpdates;
        int bufferUploads;
        long bufferUploadBytes;
        int textureUploads;
        long textureUploadBytes;
        long gpuFrameId = -1L;
        double gpuTimeMillis = Double.NaN;
        long pipelineFrameId = -1L;
        long vertexShaderInvocations;
        long fragmentShaderInvocations;
        long clippingInputPrimitives;
        long clippingOutputPrimitives;
        String renderer = "OpenGL";

        @Override public boolean available() { return available; }
        @Override public long frameId() { return frameId; }
        @Override public int drawCalls() { return drawCalls; }
        @Override public long submittedVertices() { return submittedVertices; }
        @Override public long submittedPrimitives() { return submittedPrimitives; }
        @Override public int programBinds() { return programBinds; }
        @Override public int textureBinds() { return textureBinds; }
        @Override public int framebufferBinds() { return framebufferBinds; }
        @Override public int uniformUpdates() { return uniformUpdates; }
        @Override public int bufferUploads() { return bufferUploads; }
        @Override public long bufferUploadBytes() { return bufferUploadBytes; }
        @Override public int textureUploads() { return textureUploads; }
        @Override public long textureUploadBytes() { return textureUploadBytes; }
        @Override public long gpuFrameId() { return gpuFrameId; }
        @Override public double gpuTimeMillis() { return gpuTimeMillis; }
        @Override public long pipelineFrameId() { return pipelineFrameId; }
        @Override public long vertexShaderInvocations() { return vertexShaderInvocations; }
        @Override public long fragmentShaderInvocations() { return fragmentShaderInvocations; }
        @Override public long clippingInputPrimitives() { return clippingInputPrimitives; }
        @Override public long clippingOutputPrimitives() { return clippingOutputPrimitives; }
        @Override public String renderer() { return renderer; }
    }

    /**
     * Returns the create program.
     *
     * @return the created value
     */
    @Override
    public int createProgram() {
        return GL20.glCreateProgram();
    }

    /**
     * Creates a shader.
     *
     * @param type the expected Java type
     * @return the created value
     */
    @Override
    public int createShader(GLShaderType type) {
        if (type == GLShaderType.VERTEX) {
            return GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        }
        if (type == GLShaderType.FRAGMENT) {
            return GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        }
        throw new FdxException("Unsupported GL shader type: " + type);
    }

    /**
     * Runs the shader source step.
     *
     * @param shader the shader
     * @param source the source value
     */
    @Override
    public void shaderSource(int shader, String source) {
        GL20.glShaderSource(shader, source);
    }

    /**
     * Runs the compile shader step.
     *
     * @param shader the shader
     */
    @Override
    public void compileShader(int shader) {
        GL20.glCompileShader(shader);
    }

    /**
     * Runs the shader compile status step.
     *
     * @param shader the shader
     * @return true if shader compile status succeeds or is active; false otherwise
     */
    @Override
    public boolean shaderCompileStatus(int shader) {
        return GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE;
    }

    /**
     * Runs the shader info log step.
     *
     * @param shader the shader
     * @return the shader info log
     */
    @Override
    public String shaderInfoLog(int shader) {
        return GL20.glGetShaderInfoLog(shader);
    }

    /**
     * Runs the delete shader step.
     *
     * @param shader the shader
     */
    @Override
    public void deleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }

    /**
     * Runs the attach shader step.
     *
     * @param program the program
     * @param shader the shader
     */
    @Override
    public void attachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }

    /**
     * Runs the link program step.
     *
     * @param program the program
     */
    @Override
    public void linkProgram(int program) {
        GL20.glLinkProgram(program);
    }

    /**
     * Runs the program link status step.
     *
     * @param program the program
     * @return true if program link status succeeds or is active; false otherwise
     */
    @Override
    public boolean programLinkStatus(int program) {
        return GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_FALSE;
    }

    /**
     * Runs the program info log step.
     *
     * @param program the program
     * @return the program info log
     */
    @Override
    public String programInfoLog(int program) {
        return GL20.glGetProgramInfoLog(program);
    }

    /**
     * Runs the delete program step.
     *
     * @param program the program
     */
    @Override
    public void deleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }

    /**
     * Runs the use program step.
     *
     * @param program the program
     */
    @Override
    public void useProgram(int program) {
        if (metricsEnabled) {
            programBinds++;
        }
        GL20.glUseProgram(program);
    }

    /**
     * Returns the gen vertex array.
     *
     * @return the gen vertex array
     */
    @Override
    public int genVertexArray() {
        return GL30.glGenVertexArrays();
    }

    /**
     * Runs the bind vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void bindVertexArray(int vertexArray) {
        GL30.glBindVertexArray(vertexArray);
    }

    /**
     * Runs the delete vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void deleteVertexArray(int vertexArray) {
        GL30.glDeleteVertexArrays(vertexArray);
    }

    /**
     * Returns the gen buffer.
     *
     * @return the gen buffer
     */
    @Override
    public int genBuffer() {
        return GL15.glGenBuffers();
    }

    /**
     * Runs the bind array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindArrayBuffer(int buffer) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the bind element array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindElementArrayBuffer(int buffer) {
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer);
    }

    /**
     * Runs the buffer data step.
     *
     * @param size the size
     */
    @Override
    public void bufferData(int size) {
        recordUpload(size);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, size, GL15.GL_DYNAMIC_DRAW);
    }

    /**
     * Runs the element buffer data step.
     *
     * @param size the size
     */
    @Override
    public void elementBufferData(int size) {
        recordUpload(size);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, size, GL15.GL_STATIC_DRAW);
    }

    /**
     * Runs the buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void bufferSubData(ByteBuffer data) {
        recordUpload(data != null ? data.remaining() : 0);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, data);
    }

    @Override public boolean supportsBufferRangeInitialization() { return true; }

    @Override public void bufferSubData(int offset, ByteBuffer data) {
        recordUpload(data.remaining()); GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offset, data);
    }

    /**
     * Runs the bind uniform buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBuffer(int buffer) {
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, buffer);
    }

    /**
     * Runs the uniform buffer data step.
     *
     * @param size the size
     */
    @Override
    public void uniformBufferData(int size) {
        recordUpload(size);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, size, GL15.GL_DYNAMIC_DRAW);
    }

    /**
     * Runs the uniform buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void uniformBufferSubData(ByteBuffer data) {
        recordUpload(data != null ? data.remaining() : 0);
        GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, data);
    }

    /**
     * Runs the bind uniform buffer base step.
     *
     * @param binding the binding
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBufferBase(int binding, int buffer) {
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, binding, buffer);
    }

    /**
     * Runs the element buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void elementBufferSubData(ByteBuffer data) {
        recordUpload(data != null ? data.remaining() : 0);
        GL15.glBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, 0, data);
    }

    /**
     * Runs the delete buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void deleteBuffer(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }

    /**
     * Returns the gen texture.
     *
     * @return the gen texture
     */
    @Override
    public int genTexture() {
        return GL11.glGenTextures();
    }

    /**
     * Runs the bind texture2 d step.
     *
     * @param texture the texture
     */
    @Override
    public void bindTexture2D(int texture) {
        if (metricsEnabled) {
            textureBinds++;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    /**
     * Runs the tex image2 d step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param data the data
     */
    @Override
    public void texImage2D(int width, int height, ByteBuffer data) {
        texImage2D(TextureFormat.RGBA8_UNORM, width, height, data);
    }

    @Override
    public void framebufferSrgb(boolean enabled) {
        if (enabled) GL11.glEnable(0x8DB9); else GL11.glDisable(0x8DB9);
    }

    @Override
    public void texImage2D(TextureFormat format, int width, int height, ByteBuffer data) {
        recordTextureUpload(width, height, format.bytesPerPixel(), data);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GLApi.colorInternalFormat(format), width, height, 0,
                GL11.GL_RGBA, GLApi.colorTransferType(format), data);
    }

    /**
     * Runs the tex sub image2 d step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param data the data
     */
    @Override
    public void texSubImage2D(int width, int height, ByteBuffer data) {
        recordTextureUpload(width, height, data);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
    }

    /**
     * Runs the texture wrap2 d step.
     *
     * @param wrapS the horizontal wrap mode
     * @param wrapT the vertical wrap mode
     */
    @Override
    public void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, toNative(wrapS));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, toNative(wrapT));
    }

    /**
     * Runs the texture filter2 d step.
     *
     * @param filter the sampled texture filter
     */
    @Override
    public void textureFilter2D(TextureFilter filter) {
        int nativeFilter = toNative(filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, nativeFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, nativeFilter);
    }

    /**
     * Runs the delete texture step.
     *
     * @param texture the texture
     */
    @Override
    public void deleteTexture(int texture) {
        GL11.glDeleteTextures(texture);
    }

    /**
     * Returns the gen framebuffer.
     *
     * @return the gen framebuffer
     */
    @Override
    public int genFramebuffer() {
        return GL30.glGenFramebuffers();
    }

    /**
     * Runs the bind framebuffer step.
     *
     * @param framebuffer the framebuffer
     */
    @Override
    public void bindFramebuffer(int framebuffer) {
        if (metricsEnabled) {
            framebufferBinds++;
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
    }

    /**
     * Runs the framebuffer texture2 d step.
     *
     * @param texture the texture
     */
    @Override
    public void framebufferTexture2D(int texture) {
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
    }

    /**
     * Returns whether the currently bound framebuffer is complete.
     *
     * @return true if complete
     */
    @Override
    public boolean framebufferComplete() {
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    /**
     * Runs the delete framebuffer step.
     *
     * @param framebuffer the framebuffer
     */
    @Override
    public void deleteFramebuffer(int framebuffer) {
        GL30.glDeleteFramebuffers(framebuffer);
    }

    /**
     * Returns the gen renderbuffer.
     *
     * @return the gen renderbuffer
     */
    @Override
    public int genRenderbuffer() {
        return GL30.glGenRenderbuffers();
    }

    /**
     * Runs the bind renderbuffer step.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void bindRenderbuffer(int renderbuffer) {
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
    }

    /**
     * Runs the renderbuffer depth storage step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void renderbufferStorageDepth(int width, int height) {
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height);
    }

    /**
     * Runs the framebuffer depth renderbuffer attachment step.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void framebufferRenderbufferDepth(int renderbuffer) {
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_RENDERBUFFER, renderbuffer);
    }

    /**
     * Runs the delete renderbuffer step.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void deleteRenderbuffer(int renderbuffer) {
        GL30.glDeleteRenderbuffers(renderbuffer);
    }

    /**
     * Runs the active texture step.
     *
     * @param slot the slot
     */
    @Override
    public void activeTexture(int slot) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + slot);
    }

    /**
     * Runs the uniform location step.
     *
     * @param program the program
     * @param name the name
     * @return the uniform location
     */
    @Override
    public int uniformLocation(int program, String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    /**
     * Runs the uniform1i step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1i(int location, int value) {
        if (metricsEnabled) {
            uniformUpdates++;
        }
        GL20.glUniform1i(location, value);
    }

    /**
     * Runs the uniform block index step.
     *
     * @param program the program
     * @param name the name
     * @return the uniform block index, or -1 when absent
     */
    @Override
    public int uniformBlockIndex(int program, String name) {
        return GL31.glGetUniformBlockIndex(program, name);
    }

    /**
     * Runs the uniform block binding step.
     *
     * @param program the program
     * @param blockIndex the block index
     * @param binding the binding
     */
    @Override
    public void uniformBlockBinding(int program, int blockIndex, int binding) {
        GL31.glUniformBlockBinding(program, blockIndex, binding);
    }

    /**
     * Runs the uniform1f step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1f(int location, float value) {
        if (metricsEnabled) {
            uniformUpdates++;
        }
        GL20.glUniform1f(location, value);
    }

    /**
     * Runs the uniform3f step.
     *
     * @param location the location
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    @Override
    public void uniform3f(int location, float x, float y, float z) {
        if (metricsEnabled) {
            uniformUpdates++;
        }
        GL20.glUniform3f(location, x, y, z);
    }

    /**
     * Runs the uniform4f step.
     *
     * @param location the location
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     */
    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        if (metricsEnabled) {
            uniformUpdates++;
        }
        GL20.glUniform4f(location, x, y, z, w);
    }

    /**
     * Runs the uniform matrix4fv step.
     *
     * @param location the location
     * @param transpose the transpose
     * @param values the values
     */
    @Override
    public void uniformMatrix4fv(int location, boolean transpose, float[] values) {
        if (metricsEnabled) {
            uniformUpdates++;
        }
        GL20.glUniformMatrix4fv(location, transpose, values);
    }

    /**
     * Runs the enable alpha blending step.
     */
    @Override
    public void enableAlphaBlending() {
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void disableAlphaBlending() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * Runs the enable depth test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableDepthTest(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
    }

    /**
     * Runs the depth mask step.
     *
     * @param enabled the enabled
     */
    @Override
    public void depthMask(boolean enabled) {
        GL11.glDepthMask(enabled);
    }

    /**
     * Runs the depth func less equal step.
     */
    @Override
    public void depthFuncLessEqual() {
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    /**
     * Runs the enable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void enableVertexAttribArray(int index) {
        GL20.glEnableVertexAttribArray(index);
    }

    /**
     * Runs the disable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void disableVertexAttribArray(int index) {
        GL20.glDisableVertexAttribArray(index);
    }

    /**
     * Runs the vertex attrib pointer step.
     *
     * @param index the index
     * @param size the size
     * @param stride the stride
     * @param offset the offset
     */
    @Override
    public void vertexAttribPointer(int index, int size, int stride, int offset) {
        GL20.glVertexAttribPointer(index, size, GL11.GL_FLOAT, false, stride, offset);
    }

    /**
     * Runs the vertex attrib pointer step.
     *
     * @param index the index
     * @param format the format
     * @param stride the stride
     * @param offset the offset
     */
    @Override
    public void vertexAttribPointer(int index, VertexFormat format, int stride, int offset) {
        if (format == VertexFormat.UNORM8X4) {
            GL20.glVertexAttribPointer(index, format.componentCount(), GL11.GL_UNSIGNED_BYTE, true, stride, offset);
            return;
        }
        GL20.glVertexAttribPointer(index, format.componentCount(), GL11.GL_FLOAT, false, stride, offset);
    }

    /**
     * Runs the vertex attrib divisor step.
     *
     * @param index the index
     * @param divisor the divisor
     */
    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        GL33.glVertexAttribDivisor(index, divisor);
    }

    /**
     * Runs the viewport step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void viewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    /**
     * Runs the enable scissor test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableScissorTest(boolean enabled) {
        if (enabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    /**
     * Runs the scissor step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void scissor(int x, int y, int width, int height) {
        GL11.glScissor(x, y, width, height);
    }

    /**
     * Runs the clear color step.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    @Override
    public void clearColor(float red, float green, float blue, float alpha) {
        GL11.glClearColor(red, green, blue, alpha);
    }

    /**
     * Runs the clear color buffer step.
     */
    @Override
    public void clearColorBuffer() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    /**
     * Runs the clear depth step.
     *
     * @param depth the depth
     */
    @Override
    public void clearDepth(float depth) {
        GL11.glClearDepth(depth);
    }

    /**
     * Runs the clear depth buffer step.
     */
    @Override
    public void clearDepthBuffer() {
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Draws arrays.
     *
     * @param topology the topology
     * @param firstVertex the first vertex
     * @param vertexCount the vertex count
     */
    @Override
    public void drawArrays(PrimitiveTopology topology, int firstVertex, int vertexCount) {
        recordDraw(topology, vertexCount, 1);
        GL20.glDrawArrays(toNative(topology), firstVertex, vertexCount);
    }

    /**
     * Draws arrays instanced.
     *
     * @param topology the topology
     * @param firstVertex the first vertex
     * @param vertexCount the vertex count
     * @param instanceCount the instance count
     */
    @Override
    public void drawArraysInstanced(PrimitiveTopology topology, int firstVertex, int vertexCount, int instanceCount) {
        recordDraw(topology, vertexCount, instanceCount);
        GL31.glDrawArraysInstanced(toNative(topology), firstVertex, vertexCount, instanceCount);
    }

    /**
     * Runs the read pixels RGBA8 step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the read pixels RGBA8
     */
    @Override
    public ByteBuffer readPixelsRgba8(int width, int height) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadBuffer(GL11.GL_BACK);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        pixels.position(0);
        pixels.limit(width * height * 4);
        return pixels;
    }

    /**
     * Draws elements.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     */
    @Override
    public void drawElements(PrimitiveTopology topology, int indexCount, int offsetBytes) {
        recordDraw(topology, indexCount, 1);
        GL11.glDrawElements(toNative(topology), indexCount, GL11.GL_UNSIGNED_SHORT, offsetBytes);
    }

    /**
     * Draws elements base vertex.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     * @param baseVertex the base vertex
     */
    @Override
    public void drawElementsBaseVertex(PrimitiveTopology topology, int indexCount, int offsetBytes, int baseVertex) {
        recordDraw(topology, indexCount, 1);
        GL32.glDrawElementsBaseVertex(toNative(topology), indexCount, GL11.GL_UNSIGNED_SHORT, offsetBytes, baseVertex);
    }

    /**
     * Draws elements instanced.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     * @param instanceCount the instance count
     */
    @Override
    public void drawElementsInstanced(PrimitiveTopology topology, int indexCount, int offsetBytes, int instanceCount) {
        recordDraw(topology, indexCount, instanceCount);
        GL31.glDrawElementsInstanced(toNative(topology), indexCount, GL11.GL_UNSIGNED_SHORT,
                offsetBytes, instanceCount);
    }

    /**
     * Draws elements instanced base vertex.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     * @param instanceCount the instance count
     * @param baseVertex the base vertex
     */
    @Override
    public void drawElementsInstancedBaseVertex(PrimitiveTopology topology, int indexCount, int offsetBytes,
            int instanceCount, int baseVertex) {
        recordDraw(topology, indexCount, instanceCount);
        GL32.glDrawElementsInstancedBaseVertex(toNative(topology), indexCount, GL11.GL_UNSIGNED_SHORT, offsetBytes,
                instanceCount, baseVertex);
    }

    private int toNative(PrimitiveTopology topology) {
        if (topology == PrimitiveTopology.LINE_LIST) {
            return GL11.GL_LINES;
        }
        if (topology == PrimitiveTopology.TRIANGLE_STRIP) {
            return GL11.GL_TRIANGLE_STRIP;
        }
        return GL11.GL_TRIANGLES;
    }

    private int toNative(TextureWrap wrap) {
        if (wrap == TextureWrap.REPEAT) {
            return GL11.GL_REPEAT;
        }
        if (wrap == TextureWrap.MIRRORED_REPEAT) {
            return GL14.GL_MIRRORED_REPEAT;
        }
        return GL12.GL_CLAMP_TO_EDGE;
    }

    private int toNative(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? GL11.GL_NEAREST : GL11.GL_LINEAR;
    }
    @Override public boolean supportsMipTextures() { return true; }

    @Override public void textureFilters2D(TextureFilter min, TextureFilter mag, TextureMipmapFilter mip) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GLApi.minificationFilter(min, mip));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, toNative(mag));
    }

    @Override public void textureMipRange2D(int levels) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, 0x813C, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, 0x813D, levels-1);
    }

    @Override public void texImage2D(TextureFormat format, int level, int width, int height, ByteBuffer data) {
        if (format == TextureFormat.R32_FLOAT) {
            recordTextureUpload(width, height, format.bytesPerPixel(), data);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL30.GL_R32F, width, height, 0, GL11.GL_RED, GL11.GL_FLOAT, data);
            return;
        }
        if (level == 0) { texImage2D(format, width, height, data); return; }
        recordTextureUpload(width, height, format.bytesPerPixel(), data);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GLApi.colorInternalFormat(format),
                width, height, 0, GL11.GL_RGBA, GLApi.colorTransferType(format), data);
    }

    @Override public void texSubImage2D(TextureFormat format, int level,
            int width, int height, ByteBuffer data) {
        recordTextureUpload(width, height, format.bytesPerPixel(), data);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, width, height,
                format == TextureFormat.R32_FLOAT ? GL11.GL_RED : GL11.GL_RGBA,
                GLApi.colorTransferType(format), data);
    }

    @Override public void texSubImage2D(int level, int width, int height, ByteBuffer data) {
        if (level == 0) { texSubImage2D(width, height, data); return; }
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
    }

    @Override public void framebufferTexture2D(int texture, int level) {
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, level);
    }

    @Override public boolean supportsMultipleTargets() {
        return (GL.getCapabilities().OpenGL40 || GL.getCapabilities().GL_ARB_draw_buffers_blend)
                && GL11.glGetInteger(GL32.GL_MAX_COLOR_TEXTURE_SAMPLES) >= 4
                && GL11.glGetInteger(GL32.GL_MAX_DEPTH_TEXTURE_SAMPLES) >= 4;
    }

    @Override public void texImageMultisample(int texture, TextureFormat format,
            int width, int height, int samples) {
        int target = GL32.GL_TEXTURE_2D_MULTISAMPLE;
        GL11.glBindTexture(target, texture);
        GL32.glTexImage2DMultisample(target, samples,
                format.isDepthStencil() ? GL30.GL_DEPTH_COMPONENT32F : GLApi.colorInternalFormat(format),
                width, height, true);
        GL11.glBindTexture(target, 0);
    }

    @Override public void framebufferTexture(int index, int texture, int level, int samples) {
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                index < 0 ? GL30.GL_DEPTH_ATTACHMENT : GL30.GL_COLOR_ATTACHMENT0 + index,
                samples > 1 ? GL32.GL_TEXTURE_2D_MULTISAMPLE : GL11.GL_TEXTURE_2D,
                texture, level);
    }

    @Override public void drawBuffers(int count) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffers = stack.mallocInt(count);
            for (int i = 0; i < count; i++) buffers.put(i, GL30.GL_COLOR_ATTACHMENT0 + i);
            GL20.glDrawBuffers(buffers);
        }
    }

    @Override public void clearColorAttachment(int index, float red, float green, float blue, float alpha) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL30.glClearBufferfv(GL11.GL_COLOR, index, stack.floats(red, green, blue, alpha));
        }
    }

    @Override public void renderbufferStorageDepth(int width, int height, int samples) {
        GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, samples, GL30.GL_DEPTH_COMPONENT32F, width, height);
    }

    @Override public void resolveColorFramebuffer(int source, int index, int destination, int width, int height) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + index);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    @Override public void applyColorTargets(ColorTargetState[] targets) {
        for (int i = 0; i < targets.length; i++) {
            var color = targets[i];
            int mask = color.writeMask();
            GL30.glColorMaski(i, (mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0);
            var blend = color.blend();
            if (blend == null) GL30.glDisablei(GL11.GL_BLEND, i);
            else {
                GL30.glEnablei(GL11.GL_BLEND, i);
                int sourceColor = DesktopGLPipelineState.factor(blend.color().sourceFactor());
                int destinationColor = DesktopGLPipelineState.factor(blend.color().destinationFactor());
                int sourceAlpha = DesktopGLPipelineState.factor(blend.alpha().sourceFactor());
                int destinationAlpha = DesktopGLPipelineState.factor(blend.alpha().destinationFactor());
                int colorOperation = DesktopGLPipelineState.operation(blend.color().operation());
                int alphaOperation = DesktopGLPipelineState.operation(blend.alpha().operation());
                if (GL.getCapabilities().OpenGL40) {
                    GL40.glBlendFuncSeparatei(i, sourceColor, destinationColor, sourceAlpha, destinationAlpha);
                    GL40.glBlendEquationSeparatei(i, colorOperation, alphaOperation);
                } else {
                    ARBDrawBuffersBlend.glBlendFuncSeparateiARB(i, sourceColor, destinationColor, sourceAlpha, destinationAlpha);
                    ARBDrawBuffersBlend.glBlendEquationSeparateiARB(i, colorOperation, alphaOperation);
                }
            }
        }
    }

}
