package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.IntArray;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.TextureUsage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class D3D12FfmContext implements AutoCloseable {
    private static final int RGBA16_FLOAT_FORMAT = 10; // DXGI_FORMAT_R16G16B16A16_FLOAT

    boolean supportsHdr() {
        requireOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment support = arena.allocate(12, 4); // D3D12_FEATURE_DATA_FORMAT_SUPPORT
            support.set(D3D12Ffm.INT, 0, RGBA16_FLOAT_FORMAT);
            int result = D3D12Ffm.comIntAIAI(device, D3D12Ffm.SLOT_DEVICE_CHECK_FEATURE_SUPPORT, 3, support, 12);
            int required = 0x20 | 0x200 | 0x1000 | 0x4000 | 0x8000;
            return result >= 0 && (support.get(D3D12Ffm.INT, 4) & required) == required;
        }
    }
    private static final int CPU_DESCRIPTOR_CAPACITY = 8192;
    boolean supportsMultisample() {
        requireOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment support = arena.allocate(16, 4);
            for (int format : new int[] {28, 29, 87, 91, 10, 41, 40}) {
                support.fill((byte) 0);
                support.set(D3D12Ffm.INT, 0, format);
                support.set(D3D12Ffm.INT, 4, 4);
                if (D3D12Ffm.comIntAIAI(device, D3D12Ffm.SLOT_DEVICE_CHECK_FEATURE_SUPPORT, 4, support, 16) < 0
                        || support.get(D3D12Ffm.INT, 12) == 0) return false;
            }
            return true;
        }
    }
    private static final int FRAME_DESCRIPTOR_CAPACITY = 8192;
    private static final int FRAME_SAMPLER_DESCRIPTOR_CAPACITY = 2048;
    private static final long UNIFORM_CAPACITY = 16L * 1024L * 1024L;
    private static final int MAX_VERTEX_BUFFERS = 16;
    private static final int MAX_TEXTURES = 32;
    private static final int INVALID_DESCRIPTOR = -1;

    private final MemorySegment window;
    private final boolean vSync;
    private final boolean validation;
    private final boolean optimizeShaders;
    private final int frameCount;
    private final Arena nativeArena = Arena.ofShared();
    private final Array<FrameSlot> frames = new Array<FrameSlot>();
    private final IntArray freeRtvs = new IntArray();
    private final IntArray freeDsvs = new IntArray();
    private final IntArray freeSrvs = new IntArray();
    private final IntArray freeSamplers = new IntArray();
    private final Array<BufferAllocation> recordedBuffers = new Array<BufferAllocation>();
    private final Array<TextureAllocation> recordedTextures = new Array<TextureAllocation>();
    private final Array<RetiredResource> retired = new Array<RetiredResource>();
    private final BufferAllocation[] vertexBuffers = new BufferAllocation[MAX_VERTEX_BUFFERS];
    private final Texture[] textures = new Texture[MAX_TEXTURES];
    private final long[] textureDescriptorKeys = new long[MAX_TEXTURES];
    private final long[] samplerDescriptorKeys = new long[MAX_TEXTURES];
    private final ResourceRegistry resources = new ResourceRegistry();
    private final TextureAllocation[] multipleColors = new TextureAllocation[8];
    private final TextureAllocation[] multipleResolves = new TextureAllocation[8];
    private final int[] multipleColorLevels = new int[8];
    private final int[] multipleResolveLevels = new int[8];
    private final int[] multipleFormats = new int[8];
    private int multipleColorCount;
    private final LinkedHashMap<ShaderSource, MemorySegment> shaderBytecode =
            new LinkedHashMap<>(32, 0.75f, true);

    private D3D12DxcCompiler shaderCompiler;

    // Compiler distribution and effective options are immutable for this cache's context lifetime.
    private record ShaderSource(String source, String entryPoint, String target) { }

    private final MemorySegment frameBarrier = nativeArena.allocate(D3D12Ffm.SIZE_RESOURCE_BARRIER, 8);
    private final MemorySegment frameViewport = nativeArena.allocate(D3D12Ffm.SIZE_VIEWPORT, 4);
    private final MemorySegment zeroBlendFactor = nativeArena.allocate(16, 4);
    private final MemorySegment frameRect = nativeArena.allocate(D3D12Ffm.SIZE_RECT, 4);
    private final MemorySegment frameColor = nativeArena.allocate(16, 4);
    private final MemorySegment framePointerArray = nativeArena.allocate(16, 8);
    private final MemorySegment frameRtvHandle = nativeArena.allocate(8 * 8, 8);
    private final MemorySegment frameDsvHandle = nativeArena.allocate(8, 8);
    private final MemorySegment frameVertexView = nativeArena.allocate(D3D12Ffm.SIZE_VERTEX_BUFFER_VIEW, 8);
    private final MemorySegment frameIndexView = nativeArena.allocate(D3D12Ffm.SIZE_INDEX_BUFFER_VIEW, 8);
    private final MemorySegment frameHandleResult = nativeArena.allocate(8, 8);

    private int width;
    private int height;
    private String adapterName = "Unknown Direct3D 12 adapter";
    private String pipelineCacheIdentity;
    private MemorySegment factory = D3D12Ffm.NULL;
    private MemorySegment adapter = D3D12Ffm.NULL;
    private MemorySegment device = D3D12Ffm.NULL;
    private MemorySegment queue = D3D12Ffm.NULL;
    private MemorySegment swapChain = D3D12Ffm.NULL;
    private MemorySegment commands = D3D12Ffm.NULL;
    private MemorySegment fence = D3D12Ffm.NULL;
    private MemorySegment fenceEvent = D3D12Ffm.NULL;
    private MemorySegment rtvHeap = D3D12Ffm.NULL;
    private MemorySegment dsvHeap = D3D12Ffm.NULL;
    private MemorySegment cpuSrvHeap = D3D12Ffm.NULL;
    private MemorySegment cpuSamplerHeap = D3D12Ffm.NULL;
    private int rtvSize;
    private int dsvSize;
    private int srvSize;
    private int samplerSize;
    private int nextRtv;
    private int nextDsv;
    private int nextSrv;
    private int nextSampler;
    private long nextFence = 1L;
    private int frameIndex;
    private FrameSlot frame;
    private boolean frameOpen;
    private boolean passOpen;
    private boolean closed;
    private int removalReason;
    private Texture renderTarget;
    private TextureAllocation renderTargetAllocation;
    private int renderTargetHeight;
    private Pipeline pipeline;
    private BufferAllocation indexBuffer;

    D3D12FfmContext(long windowHandle, int width, int height, boolean vSync,
            boolean validation, boolean optimizeShaders, int framesInFlight) {
        if (windowHandle == 0L) {
            throw new FdxException("Direct3D 12 window handle is null");
        }
        if (framesInFlight < 2 || framesInFlight > 3) {
            throw new FdxException("Direct3D 12 requires two or three swap-chain frames");
        }
        window = MemorySegment.ofAddress(windowHandle);
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.vSync = vSync;
        this.validation = validation;
        this.optimizeShaders = optimizeShaders;
        frameCount = framesInFlight;
    }

    void initialize() {
        requireOpen();
        try (Arena arena = Arena.ofConfined()) {
            if (validation) {
                MemorySegment output = pointerOutput(arena);
                D3D12Ffm.check(D3D12Ffm.getDebugInterface(output),
                        "Could not enable the Direct3D 12 debug layer");
                MemorySegment debug = D3D12Ffm.pointer(output);
                try {
                    D3D12Ffm.comVoidA(debug, D3D12Ffm.SLOT_DEBUG_ENABLE);
                } finally {
                    D3D12Ffm.release(debug);
                }
            }

            MemorySegment output = pointerOutput(arena);
            D3D12Ffm.check(D3D12Ffm.createFactory(
                    validation ? D3D12Ffm.DXGI_CREATE_FACTORY_DEBUG : 0, output),
                    "Could not create a DXGI factory");
            factory = D3D12Ffm.pointer(output);
            selectAdapter(arena);
            shaderCompiler = new D3D12DxcCompiler();
            System.out.println("[libfdx-d3d12] runtime compiler: DXC " + D3D12DxcCompiler.version() + " / Shader Model 6.0");
            createQueue(arena);
            createDescriptorHeaps(arena);
            createFrames();
            createSwapChain(arena);
            createFrameTargets();

            MemorySegment commandOutput = pointerOutput(arena);
            D3D12Ffm.check(D3D12Ffm.comIntAIIAAAA(device,
                    D3D12Ffm.SLOT_DEVICE_CREATE_COMMAND_LIST,
                    0, D3D12Ffm.D3D12_COMMAND_LIST_TYPE_DIRECT,
                    frames.get(0).allocator, D3D12Ffm.NULL,
                    D3D12Ffm.IID_ID3D12_GRAPHICS_COMMAND_LIST, commandOutput),
                    "Could not create a Direct3D 12 command list");
            commands = D3D12Ffm.pointer(commandOutput);
            D3D12Ffm.check(D3D12Ffm.comIntA(commands, D3D12Ffm.SLOT_COMMANDS_CLOSE),
                    "Could not close the initial Direct3D 12 command list");

            MemorySegment fenceOutput = pointerOutput(arena);
            D3D12Ffm.check(D3D12Ffm.comIntALIAA(device, D3D12Ffm.SLOT_DEVICE_CREATE_FENCE,
                    0L, D3D12Ffm.D3D12_FENCE_FLAG_NONE, D3D12Ffm.IID_ID3D12_FENCE, fenceOutput),
                    "Could not create a Direct3D 12 fence");
            fence = D3D12Ffm.pointer(fenceOutput);
            fenceEvent = D3D12Ffm.createEvent();
            if (D3D12Ffm.isNull(fenceEvent)) {
                throw new FdxException("Could not create a Direct3D 12 fence event");
            }
        } catch (RuntimeException | Error error) {
            close();
            throw error;
        }
    }

    String adapterName() {
        requireOpen();
        return adapterName;
    }

    String pipelineCacheIdentity() { requireOpen(); return pipelineCacheIdentity; }

    void resize(int newWidth, int newHeight) {
        requireOpen();
        if (frameOpen) {
            throw new FdxException("Cannot resize Direct3D 12 during a frame");
        }
        int targetWidth = Math.max(1, newWidth);
        int targetHeight = Math.max(1, newHeight);
        if (targetWidth == width && targetHeight == height) {
            return;
        }
        waitIdle();
        releaseFrameTargets();
        D3D12Ffm.check(D3D12Ffm.comIntAIIIII(swapChain, D3D12Ffm.SLOT_SWAP_RESIZE_BUFFERS,
                frameCount, targetWidth, targetHeight, D3D12Ffm.DXGI_FORMAT_B8G8R8A8_UNORM, 0),
                "Could not resize the Direct3D 12 swap chain");
        width = targetWidth;
        height = targetHeight;
        createFrameTargets();
    }

    boolean beginFrame() {
        requireOpen();
        if (frameOpen) {
            throw new FdxException("A Direct3D 12 frame is already open");
        }
        frameIndex = D3D12Ffm.comIntA(swapChain, D3D12Ffm.SLOT_SWAP_GET_CURRENT_BACK_BUFFER_INDEX);
        frame = frames.get(frameIndex);
        waitForFence(frame.fenceValue);
        collectRetired();
        D3D12Ffm.check(D3D12Ffm.comIntA(frame.allocator, D3D12Ffm.SLOT_ALLOCATOR_RESET),
                "Could not reset a Direct3D 12 command allocator");
        D3D12Ffm.check(D3D12Ffm.comIntAAA(commands, D3D12Ffm.SLOT_COMMANDS_RESET,
                frame.allocator, D3D12Ffm.NULL), "Could not reset a Direct3D 12 command list");
        frame.descriptorTables.clear();
        frame.uniformCursor = 0L;
        pipeline = null;
        Arrays.fill(textures, null);
        Arrays.fill(vertexBuffers, null);
        indexBuffer = null;
        recordedBuffers.clear();
        recordedTextures.clear();

        transition(frameBarrier, frame.backBuffer,
                D3D12Ffm.D3D12_RESOURCE_STATE_PRESENT,
                D3D12Ffm.D3D12_RESOURCE_STATE_RENDER_TARGET);
        D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, frameBarrier);
        setFrameDescriptorHeaps();
        frameOpen = true;
        return true;
    }

    void endFrame() {
        requireOpen();
        if (!frameOpen) {
            return;
        }
        if (passOpen) {
            throw new FdxException("A Direct3D 12 render pass is still open");
        }
        transition(frameBarrier, frame.backBuffer,
                D3D12Ffm.D3D12_RESOURCE_STATE_RENDER_TARGET,
                D3D12Ffm.D3D12_RESOURCE_STATE_PRESENT);
        D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, frameBarrier);
        submitFrame(true);
    }

    void beginPass(long textureHandle, int mipLevel, long depthTextureHandle, boolean clear, float red, float green, float blue, float alpha,
            boolean store, boolean depthEnabled, boolean depthClear, float depthClearValue) {
        requireOpen();
        if (!frameOpen || passOpen) {
            throw new FdxException("Cannot begin the Direct3D 12 render pass");
        }
        Texture target = textureHandle != 0L ? resource(textureHandle, Texture.class, "render texture") : null;
        renderTarget = target;
        renderTargetAllocation = null;
        renderTargetHeight = target != null ? Math.max(1, target.height >> mipLevel) : height;
        long rtv = frame.rtv;
        long dsv = frame.dsv;
        if (target != null) {
            TextureAllocation allocation = target.allocations.get(target.current);
            if (allocation.rtvIndex == INVALID_DESCRIPTOR) {
                throw new FdxException("Texture is not a render attachment");
            }
            if (allocation.state != D3D12Ffm.D3D12_RESOURCE_STATE_RENDER_TARGET) {
                transition(frameBarrier, allocation.resource, allocation.state,
                        D3D12Ffm.D3D12_RESOURCE_STATE_RENDER_TARGET);
                D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, frameBarrier);
                allocation.state = D3D12Ffm.D3D12_RESOURCE_STATE_RENDER_TARGET;
            }
            rtv = allocation.mipRtvs[mipLevel];
            dsv = target.dsv;
            renderTargetAllocation = allocation;
            markRecorded(allocation);
        }
        if (depthTextureHandle != 0) {
            Texture explicitDepth = resource(depthTextureHandle, Texture.class, "depth texture");
            if (explicitDepth.format != D3D12Ffm.DXGI_FORMAT_D32_FLOAT || explicitDepth.dsvIndex == INVALID_DESCRIPTOR) {
                throw new FdxException("Direct3D 12 depth attachment is not a depth texture");
            }
            dsv = explicitDepth.dsv;
        }
        frameRtvHandle.set(D3D12Ffm.LONG, 0, rtv);
        frameDsvHandle.set(D3D12Ffm.LONG, 0, dsv);
        D3D12Ffm.comVoidAIAIA(commands, D3D12Ffm.SLOT_COMMANDS_OM_SET_RENDER_TARGETS,
                1, frameRtvHandle, 0, depthEnabled ? frameDsvHandle : D3D12Ffm.NULL);
        if (clear) {
            frameColor.set(D3D12Ffm.FLOAT, 0, red);
            frameColor.set(D3D12Ffm.FLOAT, 4, green);
            frameColor.set(D3D12Ffm.FLOAT, 8, blue);
            frameColor.set(D3D12Ffm.FLOAT, 12, alpha);
            D3D12Ffm.comVoidALAIA(commands, D3D12Ffm.SLOT_COMMANDS_CLEAR_RENDER_TARGET,
                    rtv, frameColor, 0, D3D12Ffm.NULL);
        }
        if (depthEnabled && depthClear) {
            D3D12Ffm.comVoidALIFBIA(commands, D3D12Ffm.SLOT_COMMANDS_CLEAR_DEPTH,
                    dsv, D3D12Ffm.D3D12_CLEAR_FLAG_DEPTH, depthClearValue,
                    (byte)0, 0, D3D12Ffm.NULL);
        }
        setViewport(frameViewport, 0.0f, 0.0f,
                target != null ? Math.max(1, target.width >> mipLevel) : width, renderTargetHeight);
        setRect(frameRect, 0, 0, target != null ? Math.max(1, target.width >> mipLevel) : width, renderTargetHeight);
        D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RS_SET_VIEWPORTS, 1, frameViewport);
        D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RS_SET_SCISSORS, 1, frameRect);
        pipeline = null;
        Arrays.fill(textures, null);
        passOpen = true;
        if (!store) {
            // StoreOp discard is currently represented by the same D3D12 transition as store.
        }
    }

    void beginMultiplePass(RenderPassDescriptor descriptor) {
        var colors = descriptor.colorAttachments();
        D3D12TextureView first = (D3D12TextureView) colors[0].view();
        var depth = descriptor.depthStencilAttachment();
        long depthHandle = depth == null ? 0 : ((D3D12TextureView) depth.view()).nativeHandle();
        var firstLoad = colors[0].loadOp();
        beginPass(first.nativeHandle(), first.mipLevel, depthHandle, firstLoad.isClear(),
                firstLoad.red(), firstLoad.green(), firstLoad.blue(), firstLoad.alpha(), colors[0].storeOp().isStore(),
                descriptor.depthEnabled(), depth != null ? depth.depthLoadOp().isClear() : descriptor.depthClearEnabled(),
                descriptor.depthClearValue());
        multipleColorCount = colors.length;
        for (int i = 0; i < colors.length; i++) {
            D3D12TextureView view = (D3D12TextureView) colors[i].view();
            Texture texture = resource(view.nativeHandle(), Texture.class, "color attachment");
            TextureAllocation allocation = texture.allocations.get(texture.current);
            multipleColors[i] = allocation;
            multipleColorLevels[i] = view.mipLevel;
            multipleFormats[i] = texture.format;
            transitionTexture(allocation, D3D12Ffm.D3D12_RESOURCE_STATE_RENDER_TARGET);
            markRecorded(allocation);
            long rtv = allocation.mipRtvs[view.mipLevel];
            frameRtvHandle.set(D3D12Ffm.LONG, 8L * i, rtv);
            var load = colors[i].loadOp();
            if (i > 0 && load.isClear()) {
                frameColor.set(D3D12Ffm.FLOAT, 0, load.red());
                frameColor.set(D3D12Ffm.FLOAT, 4, load.green());
                frameColor.set(D3D12Ffm.FLOAT, 8, load.blue());
                frameColor.set(D3D12Ffm.FLOAT, 12, load.alpha());
                D3D12Ffm.comVoidALAIA(commands, D3D12Ffm.SLOT_COMMANDS_CLEAR_RENDER_TARGET, rtv, frameColor, 0, D3D12Ffm.NULL);
            }
            if (colors[i].resolveView() != null) {
                D3D12TextureView resolve = (D3D12TextureView) colors[i].resolveView();
                Texture resolveTexture = resource(resolve.nativeHandle(), Texture.class, "resolve attachment");
                multipleResolves[i] = resolveTexture.allocations.get(resolveTexture.current);
                multipleResolveLevels[i] = resolve.mipLevel;
                markRecorded(multipleResolves[i]);
            }
        }
        D3D12Ffm.comVoidAIAIA(commands, D3D12Ffm.SLOT_COMMANDS_OM_SET_RENDER_TARGETS,
                colors.length, frameRtvHandle, 0, descriptor.depthEnabled() ? frameDsvHandle : D3D12Ffm.NULL);
    }

    private void transitionTexture(TextureAllocation allocation, int state) {
        if (allocation.state == state) return;
        transition(frameBarrier, allocation.resource, allocation.state, state);
        D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, frameBarrier);
        allocation.state = state;
    }

    void endPass() {
        requireOpen();
        if (!passOpen) {
            return;
        }
        if (multipleColorCount > 0) {
            for (int i = 0; i < multipleColorCount; i++) {
                TextureAllocation color = multipleColors[i];
                TextureAllocation resolve = multipleResolves[i];
                if (resolve != null) {
                    transitionTexture(color, 0x2000); // D3D12_RESOURCE_STATE_RESOLVE_SOURCE
                    transitionTexture(resolve, 0x1000); // D3D12_RESOURCE_STATE_RESOLVE_DEST
                    D3D12Ffm.comVoidAAIAII(commands, D3D12Ffm.SLOT_COMMANDS_RESOLVE_SUBRESOURCE,
                            resolve.resource, multipleResolveLevels[i], color.resource, multipleColorLevels[i], multipleFormats[i]);
                    transitionTexture(resolve, D3D12Ffm.D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE);
                }
                transitionTexture(color, D3D12Ffm.D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE);
                multipleColors[i] = null;
                multipleResolves[i] = null;
            }
            multipleColorCount = 0;
            renderTargetAllocation = null;
        }
        if (renderTargetAllocation != null) {
            transition(frameBarrier, renderTargetAllocation.resource,
                    D3D12Ffm.D3D12_RESOURCE_STATE_RENDER_TARGET,
                    D3D12Ffm.D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE);
            D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, frameBarrier);
            renderTargetAllocation.state = D3D12Ffm.D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE;
        }
        renderTarget = null;
        renderTargetAllocation = null;
        pipeline = null;
        passOpen = false;
    }

    void setPipeline(long handle) {
        Pipeline value = resource(handle, Pipeline.class, "pipeline");
        if (!passOpen) {
            throw new FdxException("Invalid Direct3D 12 pipeline");
        }
        pipeline = value;
        Arrays.fill(textures, null);
        D3D12Ffm.comVoidAA(commands, D3D12Ffm.SLOT_COMMANDS_SET_PIPELINE_STATE, value.state);
        D3D12Ffm.comVoidAA(commands, D3D12Ffm.SLOT_COMMANDS_OM_SET_BLEND_FACTOR, zeroBlendFactor);
        D3D12Ffm.comVoidAA(commands, D3D12Ffm.SLOT_COMMANDS_SET_GRAPHICS_ROOT_SIGNATURE,
                value.rootSignature);
        D3D12Ffm.comVoidAI(commands, D3D12Ffm.SLOT_COMMANDS_IA_SET_PRIMITIVE_TOPOLOGY,
                value.topology);
        setFrameDescriptorHeaps();
    }

    void setVertexBuffer(int slot, long handle) {
        Buffer buffer = resource(handle, Buffer.class, "vertex buffer");
        if (!passOpen || pipeline == null || slot < 0 || slot >= pipeline.strides.length
                || slot >= MAX_VERTEX_BUFFERS || buffer.usage != 0) {
            throw new FdxException("Invalid Direct3D 12 vertex buffer slot");
        }
        BufferAllocation allocation = buffer.allocations.get(buffer.current);
        frameVertexView.fill((byte)0);
        frameVertexView.set(D3D12Ffm.LONG, 0,
                D3D12Ffm.comLongA(allocation.resource, D3D12Ffm.SLOT_RESOURCE_GET_GPU_VIRTUAL_ADDRESS));
        frameVertexView.set(D3D12Ffm.INT, 8, buffer.size);
        frameVertexView.set(D3D12Ffm.INT, 12, pipeline.strides[slot]);
        D3D12Ffm.comVoidAIIA(commands, D3D12Ffm.SLOT_COMMANDS_IA_SET_VERTEX_BUFFERS,
                slot, 1, frameVertexView);
        vertexBuffers[slot] = allocation;
        markRecorded(allocation);
    }

    void setIndexBuffer(long handle) {
        Buffer buffer = resource(handle, Buffer.class, "index buffer");
        if (!passOpen || buffer.usage != 1) {
            throw new FdxException("Invalid Direct3D 12 index buffer");
        }
        BufferAllocation allocation = buffer.allocations.get(buffer.current);
        frameIndexView.fill((byte)0);
        frameIndexView.set(D3D12Ffm.LONG, 0,
                D3D12Ffm.comLongA(allocation.resource, D3D12Ffm.SLOT_RESOURCE_GET_GPU_VIRTUAL_ADDRESS));
        frameIndexView.set(D3D12Ffm.INT, 8, buffer.size);
        frameIndexView.set(D3D12Ffm.INT, 12, D3D12Ffm.DXGI_FORMAT_R16_UINT);
        D3D12Ffm.comVoidAA(commands, D3D12Ffm.SLOT_COMMANDS_IA_SET_INDEX_BUFFER, frameIndexView);
        indexBuffer = allocation;
        markRecorded(allocation);
    }

    void setTexture(int slot, long handle) {
        Texture texture = resource(handle, Texture.class, "sampled texture");
        if (!passOpen || pipeline == null || slot < 0 || slot >= pipeline.sampledTextureCount) {
            throw new FdxException("Invalid Direct3D 12 texture slot");
        }
        TextureAllocation allocation = texture.allocations.get(texture.current);
        if (allocation.srvIndex == INVALID_DESCRIPTOR) {
            throw new FdxException("Texture cannot be sampled by this Direct3D 12 context");
        }
        textures[slot] = texture;
    }

    void setScissor(int x, int y, int width, int height) {
        if (!passOpen || width <= 0 || height <= 0) {
            throw new FdxException("Invalid Direct3D 12 scissor rectangle");
        }
        int top = renderTargetHeight - y - height;
        setRect(frameRect, x, top, x + width, top + height);
        D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RS_SET_SCISSORS, 1, frameRect);
    }

    void setViewport(int x, int y, int width, int height) {
        if (!passOpen || width <= 0 || height <= 0) {
            throw new FdxException("Invalid Direct3D 12 viewport");
        }
        setViewport(frameViewport, x, renderTargetHeight - y - height, width, height);
        D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RS_SET_VIEWPORTS, 1, frameViewport);
    }

    void bindUniforms(MemorySegment source, int size) {
        if (!passOpen || pipeline == null || pipeline.uniformRoot < 0) {
            throw new FdxException("The active Direct3D 12 pipeline has no uniform buffer");
        }
        MemorySegment data = dataSegment(source, size, "Uniform source");
        long offset = align(frame.uniformCursor, D3D12Ffm.D3D12_CONSTANT_BUFFER_DATA_PLACEMENT_ALIGNMENT);
        if (offset + size > UNIFORM_CAPACITY) {
            throw new FdxException("Direct3D 12 uniform arena is exhausted");
        }
        MemorySegment.copy(data, 0, frame.uniformMapped, offset, size);
        long gpuAddress = D3D12Ffm.comLongA(frame.uniformBuffer,
                D3D12Ffm.SLOT_RESOURCE_GET_GPU_VIRTUAL_ADDRESS) + offset;
        D3D12Ffm.comVoidAIL(commands, D3D12Ffm.SLOT_COMMANDS_SET_GRAPHICS_ROOT_CBV,
                pipeline.uniformRoot, gpuAddress);
        frame.uniformCursor = offset + align(size,
                D3D12Ffm.D3D12_CONSTANT_BUFFER_DATA_PLACEMENT_ALIGNMENT);
    }

    void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        if (!passOpen || pipeline == null || vertexCount < 0 || instanceCount < 0) {
            throw new FdxException("Invalid Direct3D 12 draw command");
        }
        bindTextures();
        D3D12Ffm.comVoidAIIII(commands, D3D12Ffm.SLOT_COMMANDS_DRAW_INSTANCED,
                vertexCount, instanceCount, firstVertex, firstInstance);
    }

    void drawIndexed(int indexCount, int instanceCount, int firstIndex,
            int baseVertex, int firstInstance) {
        if (!passOpen || pipeline == null || indexBuffer == null
                || indexCount < 0 || instanceCount < 0) {
            throw new FdxException("Invalid Direct3D 12 indexed draw command");
        }
        bindTextures();
        D3D12Ffm.comVoidAIIIII(commands, D3D12Ffm.SLOT_COMMANDS_DRAW_INDEXED_INSTANCED,
                indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }

    private void selectAdapter(Arena arena) {
        MemorySegment description = arena.allocate(D3D12Ffm.SIZE_DXGI_ADAPTER_DESC1, 8);
        MemorySegment output = pointerOutput(arena);
        for (int index = 0;; index++) {
            output.set(D3D12Ffm.ADDRESS, 0, D3D12Ffm.NULL);
            int result = D3D12Ffm.comIntAIA(factory, D3D12Ffm.SLOT_FACTORY_ENUM_ADAPTERS1,
                    index, output);
            if (result == D3D12Ffm.DXGI_ERROR_NOT_FOUND) {
                break;
            }
            D3D12Ffm.check(result, "Could not enumerate DXGI adapters");
            MemorySegment candidate = D3D12Ffm.pointer(output);
            boolean accepted = false;
            try {
                description.fill((byte)0);
                D3D12Ffm.check(D3D12Ffm.comIntAA(candidate, D3D12Ffm.SLOT_ADAPTER_GET_DESC1,
                        description), "Could not query a DXGI adapter");
                if ((description.get(D3D12Ffm.INT, D3D12Ffm.OFF_DXGI_ADAPTER_FLAGS)
                        & D3D12Ffm.DXGI_ADAPTER_FLAG_SOFTWARE) != 0) {
                    continue;
                }
                MemorySegment deviceOutput = pointerOutput(arena);
                int createResult = D3D12Ffm.createDevice(candidate, deviceOutput);
                if (D3D12Ffm.failed(createResult)) {
                    continue;
                }
                MemorySegment selectedDevice = D3D12Ffm.pointer(deviceOutput);
                MemorySegment shaderModel = arena.allocate(D3D12Ffm.INT);
                shaderModel.set(D3D12Ffm.INT, 0, 0x60);
                int shaderModelResult = D3D12Ffm.comIntAIAI(selectedDevice,
                        D3D12Ffm.SLOT_DEVICE_CHECK_FEATURE_SUPPORT, 7, shaderModel, 4);
                if (!supportsShaderModel6(shaderModelResult, shaderModel.get(D3D12Ffm.INT, 0))) {
                    D3D12Ffm.release(selectedDevice);
                    continue;
                }
                adapter = candidate;
                device = D3D12Ffm.pointer(deviceOutput);
                adapterName = utf16(description, 0, 128);
                MemorySegment driverVersion = arena.allocate(D3D12Ffm.LONG);
                int driverResult = D3D12Ffm.comIntAAA(candidate,
                        D3D12Ffm.SLOT_ADAPTER_CHECK_INTERFACE_SUPPORT, D3D12Ffm.IID_IDXGI_DEVICE, driverVersion);
                if (!D3D12Ffm.failed(driverResult)) {
                    pipelineCacheIdentity = description.get(D3D12Ffm.INT, 256) + ":"
                            + description.get(D3D12Ffm.INT, 260) + ":" + description.get(D3D12Ffm.INT, 264)
                            + ":" + description.get(D3D12Ffm.INT, 268) + ":" + description.get(D3D12Ffm.LONG, 296)
                            + ":" + driverVersion.get(D3D12Ffm.LONG, 0);
                }
                accepted = true;
                return;
            } finally {
                if (!accepted) {
                    D3D12Ffm.release(candidate);
                }
            }
        }
        throw new FdxException("No hardware adapter supports Direct3D 12 feature level 11_0 and Shader Model 6.0. Update the graphics driver or use another graphics provider");
    }

    static boolean supportsShaderModel6(int result, int highestShaderModel) {
        return result >= 0 && highestShaderModel >= 0x60;
    }

    private void createQueue(Arena arena) {
        MemorySegment descriptor = arena.allocate(D3D12Ffm.SIZE_COMMAND_QUEUE_DESC, 4);
        descriptor.set(D3D12Ffm.INT, 0, D3D12Ffm.D3D12_COMMAND_LIST_TYPE_DIRECT);
        descriptor.set(D3D12Ffm.INT, 4, D3D12Ffm.D3D12_COMMAND_QUEUE_PRIORITY_NORMAL);
        descriptor.set(D3D12Ffm.INT, 8, D3D12Ffm.D3D12_COMMAND_QUEUE_FLAG_NONE);
        MemorySegment output = pointerOutput(arena);
        D3D12Ffm.check(D3D12Ffm.comIntAAAA(device, D3D12Ffm.SLOT_DEVICE_CREATE_COMMAND_QUEUE,
                descriptor, D3D12Ffm.IID_ID3D12_COMMAND_QUEUE, output),
                "Could not create a Direct3D 12 command queue");
        queue = D3D12Ffm.pointer(output);
    }

    private void createDescriptorHeaps(Arena arena) {
        rtvHeap = createDescriptorHeap(arena, D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_RTV,
                CPU_DESCRIPTOR_CAPACITY, D3D12Ffm.D3D12_DESCRIPTOR_HEAP_FLAG_NONE,
                "Could not create the Direct3D 12 RTV heap");
        dsvHeap = createDescriptorHeap(arena, D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_DSV,
                CPU_DESCRIPTOR_CAPACITY, D3D12Ffm.D3D12_DESCRIPTOR_HEAP_FLAG_NONE,
                "Could not create the Direct3D 12 DSV heap");
        cpuSrvHeap = createDescriptorHeap(arena, D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV,
                CPU_DESCRIPTOR_CAPACITY, D3D12Ffm.D3D12_DESCRIPTOR_HEAP_FLAG_NONE,
                "Could not create the Direct3D 12 SRV heap");
        cpuSamplerHeap = createDescriptorHeap(arena, D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_SAMPLER,
                CPU_DESCRIPTOR_CAPACITY, D3D12Ffm.D3D12_DESCRIPTOR_HEAP_FLAG_NONE,
                "Could not create the Direct3D 12 sampler heap");
        rtvSize = D3D12Ffm.comIntAI(device, D3D12Ffm.SLOT_DEVICE_GET_DESCRIPTOR_INCREMENT,
                D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_RTV);
        dsvSize = D3D12Ffm.comIntAI(device, D3D12Ffm.SLOT_DEVICE_GET_DESCRIPTOR_INCREMENT,
                D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_DSV);
        srvSize = D3D12Ffm.comIntAI(device, D3D12Ffm.SLOT_DEVICE_GET_DESCRIPTOR_INCREMENT,
                D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV);
        samplerSize = D3D12Ffm.comIntAI(device, D3D12Ffm.SLOT_DEVICE_GET_DESCRIPTOR_INCREMENT,
                D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_SAMPLER);
    }

    private MemorySegment createDescriptorHeap(Arena arena, int type, int count, int flags,
            String failure) {
        MemorySegment descriptor = arena.allocate(D3D12Ffm.SIZE_DESCRIPTOR_HEAP_DESC, 4);
        descriptor.set(D3D12Ffm.INT, 0, type);
        descriptor.set(D3D12Ffm.INT, 4, count);
        descriptor.set(D3D12Ffm.INT, 8, flags);
        MemorySegment output = pointerOutput(arena);
        D3D12Ffm.check(D3D12Ffm.comIntAAAA(device, D3D12Ffm.SLOT_DEVICE_CREATE_DESCRIPTOR_HEAP,
                descriptor, D3D12Ffm.IID_ID3D12_DESCRIPTOR_HEAP, output), failure);
        return D3D12Ffm.pointer(output);
    }

    private void createFrames() {
        for (int index = 0; index < frameCount; index++) {
            FrameSlot slot = new FrameSlot();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment output = pointerOutput(arena);
                D3D12Ffm.check(D3D12Ffm.comIntAIAA(device,
                        D3D12Ffm.SLOT_DEVICE_CREATE_COMMAND_ALLOCATOR,
                        D3D12Ffm.D3D12_COMMAND_LIST_TYPE_DIRECT,
                        D3D12Ffm.IID_ID3D12_COMMAND_ALLOCATOR, output),
                        "Could not create a Direct3D 12 command allocator");
                slot.allocator = D3D12Ffm.pointer(output);
                slot.srvHeap = createDescriptorHeap(arena,
                        D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV,
                        FRAME_DESCRIPTOR_CAPACITY, D3D12Ffm.D3D12_DESCRIPTOR_HEAP_FLAG_SHADER_VISIBLE,
                        "Could not create a shader-visible Direct3D 12 SRV heap");
                slot.samplerHeap = createDescriptorHeap(arena,
                        D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_SAMPLER,
                        FRAME_SAMPLER_DESCRIPTOR_CAPACITY,
                        D3D12Ffm.D3D12_DESCRIPTOR_HEAP_FLAG_SHADER_VISIBLE,
                        "Could not create a shader-visible Direct3D 12 sampler heap");
                slot.uniformBuffer = createCommittedResource(arena, D3D12Ffm.D3D12_HEAP_TYPE_UPLOAD,
                        bufferDescriptor(arena, UNIFORM_CAPACITY),
                        D3D12Ffm.D3D12_RESOURCE_STATE_GENERIC_READ, D3D12Ffm.NULL,
                        "Could not create a Direct3D 12 uniform arena");
                MemorySegment noRead = range(arena, 0L, 0L);
                MemorySegment mappedOutput = pointerOutput(arena);
                D3D12Ffm.check(D3D12Ffm.comMap(slot.uniformBuffer, 0, noRead, mappedOutput),
                        "Could not map a Direct3D 12 uniform arena");
                slot.uniformMapped = D3D12Ffm.pointer(mappedOutput).reinterpret(UNIFORM_CAPACITY);
                frames.add(slot);
            } catch (RuntimeException | Error error) {
                slot.close();
                throw error;
            }
        }
    }

    private void createSwapChain(Arena arena) {
        MemorySegment descriptor = arena.allocate(D3D12Ffm.SIZE_SWAP_CHAIN_DESC1, 4);
        descriptor.set(D3D12Ffm.INT, 0, width);
        descriptor.set(D3D12Ffm.INT, 4, height);
        descriptor.set(D3D12Ffm.INT, 8, D3D12Ffm.DXGI_FORMAT_B8G8R8A8_UNORM);
        descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_SWAP_SAMPLE_COUNT, 1);
        descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_SWAP_BUFFER_USAGE,
                D3D12Ffm.DXGI_USAGE_RENDER_TARGET_OUTPUT);
        descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_SWAP_BUFFER_COUNT, frameCount);
        descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_SWAP_SCALING, D3D12Ffm.DXGI_SCALING_STRETCH);
        descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_SWAP_EFFECT,
                D3D12Ffm.DXGI_SWAP_EFFECT_FLIP_DISCARD);
        descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_SWAP_ALPHA_MODE,
                D3D12Ffm.DXGI_ALPHA_MODE_UNSPECIFIED);
        MemorySegment output = pointerOutput(arena);
        D3D12Ffm.check(D3D12Ffm.comIntAAAAAAA(factory,
                D3D12Ffm.SLOT_FACTORY_CREATE_SWAP_CHAIN_FOR_HWND,
                queue, window, descriptor, D3D12Ffm.NULL, D3D12Ffm.NULL, output),
                "Could not create a Direct3D 12 swap chain");
        MemorySegment created = D3D12Ffm.pointer(output);
        try {
            MemorySegment swapOutput = pointerOutput(arena);
            D3D12Ffm.check(D3D12Ffm.comIntAAA(created, 0,
                    D3D12Ffm.IID_IDXGI_SWAP_CHAIN3, swapOutput),
                    "Could not access the Direct3D 12 swap chain");
            swapChain = D3D12Ffm.pointer(swapOutput);
        } finally {
            D3D12Ffm.release(created);
        }
        D3D12Ffm.check(D3D12Ffm.comIntAAI(factory,
                D3D12Ffm.SLOT_FACTORY_MAKE_WINDOW_ASSOCIATION,
                window, D3D12Ffm.DXGI_MWA_NO_ALT_ENTER),
                "Could not configure Direct3D 12 window association");
    }

    private void createFrameTargets() {
        for (int index = 0; index < frameCount; index++) {
            FrameSlot slot = frames.get(index);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment output = pointerOutput(arena);
                D3D12Ffm.check(D3D12Ffm.comIntAIAA(swapChain,
                        D3D12Ffm.SLOT_SWAP_GET_BUFFER, index,
                        D3D12Ffm.IID_ID3D12_RESOURCE, output),
                        "Could not access a Direct3D 12 swap-chain image");
                slot.backBuffer = D3D12Ffm.pointer(output);
                slot.rtv = rtvHandle(index);
                D3D12Ffm.comVoidAAAL(device, D3D12Ffm.SLOT_DEVICE_CREATE_RENDER_TARGET_VIEW,
                        slot.backBuffer, D3D12Ffm.NULL, slot.rtv);
                slot.dsv = dsvHandle(index);
                slot.depth = createDepth(arena, width, height, slot.dsv);
            }
        }
        nextRtv = Math.max(nextRtv, frameCount);
        nextDsv = Math.max(nextDsv, frameCount);
    }

    private MemorySegment createDepth(Arena arena, int targetWidth, int targetHeight, long handle) {
        return createDepth(arena, targetWidth, targetHeight, handle, 1);
    }

    private MemorySegment createDepth(Arena arena, int targetWidth, int targetHeight, long handle, int samples) {
        MemorySegment descriptor = textureDescriptor(arena, targetWidth, targetHeight,
                D3D12Ffm.DXGI_FORMAT_D32_FLOAT,
                D3D12Ffm.D3D12_RESOURCE_FLAG_ALLOW_DEPTH_STENCIL);
        descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_SAMPLE_COUNT, samples);
        MemorySegment clear = arena.allocate(D3D12Ffm.SIZE_CLEAR_VALUE, 4);
        clear.set(D3D12Ffm.INT, 0, D3D12Ffm.DXGI_FORMAT_D32_FLOAT);
        clear.set(D3D12Ffm.FLOAT, 4, 1.0f);
        MemorySegment depth = createCommittedResource(arena, D3D12Ffm.D3D12_HEAP_TYPE_DEFAULT,
                descriptor, D3D12Ffm.D3D12_RESOURCE_STATE_DEPTH_WRITE, clear,
                "Could not create a Direct3D 12 depth texture");
        MemorySegment view = arena.allocate(D3D12Ffm.SIZE_DSV_DESC, 4);
        view.set(D3D12Ffm.INT, 0, D3D12Ffm.DXGI_FORMAT_D32_FLOAT);
        view.set(D3D12Ffm.INT, 4, samples > 1 ? 5 : D3D12Ffm.D3D12_DSV_DIMENSION_TEXTURE2D);
        D3D12Ffm.comVoidAAAL(device, D3D12Ffm.SLOT_DEVICE_CREATE_DEPTH_STENCIL_VIEW,
                depth, view, handle);
        return depth;
    }

    private MemorySegment createCommittedResource(Arena arena, int heapType, MemorySegment descriptor,
            int initialState, MemorySegment clear, String failure) {
        MemorySegment heap = heapProperties(arena, heapType);
        MemorySegment output = pointerOutput(arena);
        D3D12Ffm.check(D3D12Ffm.comIntAAIAIAAA(device,
                D3D12Ffm.SLOT_DEVICE_CREATE_COMMITTED_RESOURCE,
                heap, D3D12Ffm.D3D12_HEAP_FLAG_NONE, descriptor, initialState,
                clear, D3D12Ffm.IID_ID3D12_RESOURCE, output), failure);
        return D3D12Ffm.pointer(output);
    }

    private long submitFrame(boolean present) {
        D3D12Ffm.check(D3D12Ffm.comIntA(commands, D3D12Ffm.SLOT_COMMANDS_CLOSE),
                "Could not close a Direct3D 12 command list");
        framePointerArray.set(D3D12Ffm.ADDRESS, 0, commands);
        D3D12Ffm.comVoidAIA(queue, D3D12Ffm.SLOT_QUEUE_EXECUTE_COMMAND_LISTS, 1, framePointerArray);
        if (present) {
            D3D12Ffm.check(D3D12Ffm.comIntAII(swapChain, D3D12Ffm.SLOT_SWAP_PRESENT,
                    vSync ? 1 : 0, 0), "Could not present Direct3D 12");
        }
        long value = nextFence++;
        D3D12Ffm.check(D3D12Ffm.comIntAAL(queue, D3D12Ffm.SLOT_QUEUE_SIGNAL, fence, value),
                "Could not signal a Direct3D 12 frame");
        frame.fenceValue = value;
        for (int index = 0; index < recordedBuffers.size(); index++) {
            BufferAllocation allocation = recordedBuffers.get(index);
            allocation.recording = false;
            allocation.lastFence = value;
        }
        recordedBuffers.clear();
        for (int index = 0; index < recordedTextures.size(); index++) {
            TextureAllocation allocation = recordedTextures.get(index);
            allocation.recording = false;
            allocation.lastFence = value;
        }
        recordedTextures.clear();
        frameOpen = false;
        frame = null;
        return value;
    }

    private void waitForFence(long value) {
        long completed = D3D12Ffm.comLongA(fence, D3D12Ffm.SLOT_FENCE_GET_COMPLETED_VALUE);
        if (completed == -1L) D3D12Ffm.check(deviceRemovedReason(), "Direct3D 12 device was removed");
        if (value == 0L || completed >= value) {
            return;
        }
        D3D12Ffm.check(D3D12Ffm.comIntALA(fence,
                D3D12Ffm.SLOT_FENCE_SET_EVENT_ON_COMPLETION, value, fenceEvent),
                "Could not wait for a Direct3D 12 fence");
        if (D3D12Ffm.waitForSingleObject(fenceEvent, 10000) != D3D12Ffm.WAIT_OBJECT_0) {
            throw new FdxException("Timed out waiting for Direct3D 12 GPU work");
        }
    }

    private void waitIdle() {
        if (D3D12Ffm.isNull(queue) || D3D12Ffm.isNull(fence)) {
            return;
        }
        if (deviceRemovedReason() != 0) return;
        long value = nextFence++;
        D3D12Ffm.check(D3D12Ffm.comIntAAL(queue, D3D12Ffm.SLOT_QUEUE_SIGNAL, fence, value),
                "Could not signal the Direct3D 12 queue");
        waitForFence(value);
        collectRetired();
    }

    private void collectRetired() {
        long completed = D3D12Ffm.isNull(fence) ? Long.MAX_VALUE
                : D3D12Ffm.comLongA(fence, D3D12Ffm.SLOT_FENCE_GET_COMPLETED_VALUE);
        int output = 0;
        for (int index = 0; index < retired.size(); index++) {
            RetiredResource resource = retired.get(index);
            if (resource.fenceValue <= completed) {
                resource.resource.close();
            } else {
                if (output != index) {
                    retired.set(output, resource);
                }
                output++;
            }
        }
        while (retired.size() > output) {
            retired.removeIndex(retired.size() - 1);
        }
    }

    private void setFrameDescriptorHeaps() {
        framePointerArray.set(D3D12Ffm.ADDRESS, 0, frame.srvHeap);
        framePointerArray.set(D3D12Ffm.ADDRESS, 8, frame.samplerHeap);
        D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_SET_DESCRIPTOR_HEAPS,
                2, framePointerArray);
    }

    private void releaseFrameTargets() {
        for (int index = 0; index < frames.size(); index++) {
            FrameSlot slot = frames.get(index);
            D3D12Ffm.release(slot.backBuffer);
            D3D12Ffm.release(slot.depth);
            slot.backBuffer = D3D12Ffm.NULL;
            slot.depth = D3D12Ffm.NULL;
        }
    }

    private long descriptorHeapCpuHandle(MemorySegment heap) {
        frameHandleResult.fill((byte)0);
        D3D12Ffm.comAddressAA(heap, D3D12Ffm.SLOT_HEAP_GET_CPU_HANDLE, frameHandleResult);
        return frameHandleResult.get(D3D12Ffm.LONG, 0);
    }

    private long descriptorHeapGpuHandle(MemorySegment heap) {
        frameHandleResult.fill((byte)0);
        D3D12Ffm.comAddressAA(heap, D3D12Ffm.SLOT_HEAP_GET_GPU_HANDLE, frameHandleResult);
        return frameHandleResult.get(D3D12Ffm.LONG, 0);
    }

    private long rtvHandle(int index) {
        return descriptorHeapCpuHandle(rtvHeap) + (long)index * rtvSize;
    }

    private long dsvHandle(int index) {
        return descriptorHeapCpuHandle(dsvHeap) + (long)index * dsvSize;
    }

    private long srvHandle(int index) {
        return descriptorHeapCpuHandle(cpuSrvHeap) + (long)index * srvSize;
    }

    private long samplerHandle(int index) {
        return descriptorHeapCpuHandle(cpuSamplerHeap) + (long)index * samplerSize;
    }

    private int allocateDescriptor(IntArray freeList, int type, String name) {
        if (!freeList.isEmpty()) {
            return freeList.pop();
        }
        int value;
        switch (type) {
            case D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_RTV -> value = nextRtv++;
            case D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_DSV -> value = nextDsv++;
            case D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV -> value = nextSrv++;
            case D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_SAMPLER -> value = nextSampler++;
            default -> throw new FdxException("Unknown Direct3D 12 descriptor heap type");
        }
        if (value >= CPU_DESCRIPTOR_CAPACITY) {
            throw new FdxException("Direct3D 12 " + name + " descriptor heap is exhausted");
        }
        return value;
    }

    private void bindTextures() {
        if (pipeline == null || pipeline.sampledTextureCount == 0) {
            return;
        }
        int textureCount = pipeline.sampledTextureCount;
        int samplerCount = pipeline.samplerCount;
        for (int index = 0; index < textureCount; index++) {
            Texture texture = textures[index];
            if (texture == null) {
                throw new FdxException("Direct3D 12 texture slot is not bound");
            }
            TextureAllocation allocation = texture.allocations.get(texture.current);
            if (allocation.srvIndex == INVALID_DESCRIPTOR) {
                throw new FdxException("Direct3D 12 texture slot is not sampleable");
            }
            textureDescriptorKeys[index] = allocation.srv;
            samplerDescriptorKeys[index] = texture.sampler;
            markRecorded(allocation);
        }
        int table = frame.descriptorTables.acquire(textureDescriptorKeys, textureCount, samplerDescriptorKeys, samplerCount);
        int srvStart = frame.descriptorTables.textureStart(table);
        int samplerStart = frame.descriptorTables.samplerStart(table);
        long srvCpu = descriptorHeapCpuHandle(frame.srvHeap) + (long)srvStart * srvSize;
        long srvGpu = descriptorHeapGpuHandle(frame.srvHeap) + (long)srvStart * srvSize;
        long samplerCpu = samplerCount == 0 ? 0L : descriptorHeapCpuHandle(frame.samplerHeap)
                + (long)samplerStart * samplerSize;
        long samplerGpu = samplerCount == 0 ? 0L : descriptorHeapGpuHandle(frame.samplerHeap)
                + (long)samplerStart * samplerSize;
        for (int index = 0; frame.descriptorTables.inserted() && index < textureCount; index++) {
            D3D12Ffm.comVoidAILLI(device, D3D12Ffm.SLOT_DEVICE_COPY_DESCRIPTORS_SIMPLE,
                    1, srvCpu + (long)index * srvSize, textureDescriptorKeys[index],
                    D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV);
            if (index < samplerCount) {
                D3D12Ffm.comVoidAILLI(device, D3D12Ffm.SLOT_DEVICE_COPY_DESCRIPTORS_SIMPLE,
                        1, samplerCpu + (long)index * samplerSize, samplerDescriptorKeys[index],
                        D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_SAMPLER);
            }
        }
        D3D12Ffm.comVoidAIL(commands, D3D12Ffm.SLOT_COMMANDS_SET_GRAPHICS_ROOT_DESCRIPTOR_TABLE,
                pipeline.textureRoot, srvGpu);
        if (samplerCount > 0) {
            D3D12Ffm.comVoidAIL(commands, D3D12Ffm.SLOT_COMMANDS_SET_GRAPHICS_ROOT_DESCRIPTOR_TABLE,
                    pipeline.samplerRoot, samplerGpu);
        }
    }

    private void transition(MemorySegment barrier, MemorySegment resource, int before, int after) {
        barrier.fill((byte)0);
        barrier.set(D3D12Ffm.INT, 0, D3D12Ffm.D3D12_RESOURCE_BARRIER_TYPE_TRANSITION);
        barrier.set(D3D12Ffm.INT, 4, D3D12Ffm.D3D12_RESOURCE_BARRIER_FLAG_NONE);
        barrier.set(D3D12Ffm.ADDRESS, D3D12Ffm.OFF_BARRIER_RESOURCE, resource);
        barrier.set(D3D12Ffm.INT, D3D12Ffm.OFF_BARRIER_SUBRESOURCE,
                D3D12Ffm.D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES);
        barrier.set(D3D12Ffm.INT, D3D12Ffm.OFF_BARRIER_STATE_BEFORE, before);
        barrier.set(D3D12Ffm.INT, D3D12Ffm.OFF_BARRIER_STATE_AFTER, after);
    }

    private static void setViewport(MemorySegment viewport, float x, float y, float width, float height) {
        viewport.set(D3D12Ffm.FLOAT, 0, x);
        viewport.set(D3D12Ffm.FLOAT, 4, y);
        viewport.set(D3D12Ffm.FLOAT, 8, width);
        viewport.set(D3D12Ffm.FLOAT, 12, height);
        viewport.set(D3D12Ffm.FLOAT, 16, 0.0f);
        viewport.set(D3D12Ffm.FLOAT, 20, 1.0f);
    }

    private static void setRect(MemorySegment rect, int left, int top, int right, int bottom) {
        rect.set(D3D12Ffm.INT, 0, left);
        rect.set(D3D12Ffm.INT, 4, top);
        rect.set(D3D12Ffm.INT, 8, right);
        rect.set(D3D12Ffm.INT, 12, bottom);
    }

    private static MemorySegment pointerOutput(Arena arena) {
        return arena.allocate(D3D12Ffm.ADDRESS);
    }

    private static MemorySegment range(Arena arena, long begin, long end) {
        MemorySegment value = arena.allocate(D3D12Ffm.SIZE_RANGE, 8);
        value.set(D3D12Ffm.LONG, 0, begin);
        value.set(D3D12Ffm.LONG, 8, end);
        return value;
    }

    private static MemorySegment heapProperties(Arena arena, int type) {
        MemorySegment value = arena.allocate(D3D12Ffm.SIZE_HEAP_PROPERTIES, 4);
        value.set(D3D12Ffm.INT, 0, type);
        value.set(D3D12Ffm.INT, 4, D3D12Ffm.D3D12_CPU_PAGE_PROPERTY_UNKNOWN);
        value.set(D3D12Ffm.INT, 8, D3D12Ffm.D3D12_MEMORY_POOL_UNKNOWN);
        value.set(D3D12Ffm.INT, 12, 1);
        value.set(D3D12Ffm.INT, 16, 1);
        return value;
    }

    private static MemorySegment bufferDescriptor(Arena arena, long size) {
        MemorySegment value = arena.allocate(D3D12Ffm.SIZE_RESOURCE_DESC, 8);
        value.set(D3D12Ffm.INT, 0, D3D12Ffm.D3D12_RESOURCE_DIMENSION_BUFFER);
        value.set(D3D12Ffm.LONG, D3D12Ffm.OFF_RESOURCE_DESC_WIDTH, size);
        value.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_HEIGHT, 1);
        value.set(D3D12Ffm.SHORT, D3D12Ffm.OFF_RESOURCE_DESC_DEPTH_OR_ARRAY, (short)1);
        value.set(D3D12Ffm.SHORT, D3D12Ffm.OFF_RESOURCE_DESC_MIP_LEVELS, (short)1);
        value.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_FORMAT, D3D12Ffm.DXGI_FORMAT_UNKNOWN);
        value.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_SAMPLE_COUNT, 1);
        value.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_LAYOUT,
                D3D12Ffm.D3D12_TEXTURE_LAYOUT_ROW_MAJOR);
        value.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_FLAGS,
                D3D12Ffm.D3D12_RESOURCE_FLAG_NONE);
        return value;
    }

    private static MemorySegment textureDescriptor(Arena arena, int width, int height,
            int format, int flags) {
        MemorySegment value = arena.allocate(D3D12Ffm.SIZE_RESOURCE_DESC, 8);
        value.set(D3D12Ffm.INT, 0, D3D12Ffm.D3D12_RESOURCE_DIMENSION_TEXTURE2D);
        value.set(D3D12Ffm.LONG, D3D12Ffm.OFF_RESOURCE_DESC_WIDTH, width);
        value.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_HEIGHT, height);
        value.set(D3D12Ffm.SHORT, D3D12Ffm.OFF_RESOURCE_DESC_DEPTH_OR_ARRAY, (short)1);
        value.set(D3D12Ffm.SHORT, D3D12Ffm.OFF_RESOURCE_DESC_MIP_LEVELS, (short)1);
        value.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_FORMAT, format);
        value.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_SAMPLE_COUNT, 1);
        value.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_LAYOUT,
                D3D12Ffm.D3D12_TEXTURE_LAYOUT_UNKNOWN);
        value.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_FLAGS, flags);
        return value;
    }

    private static long align(long value, long alignment) {
        return (value + alignment - 1L) & ~(alignment - 1L);
    }

    private static String utf16(MemorySegment source, long offset, int maxCharacters) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < maxCharacters; index++) {
            char value = (char)(source.get(D3D12Ffm.SHORT, offset + index * 2L) & 0xffff);
            if (value == 0) {
                break;
            }
            result.append(value);
        }
        return result.length() == 0 ? "Unknown Direct3D 12 adapter" : result.toString();
    }

    private static MemorySegment directBuffer(ByteBuffer buffer, int requiredSize, String name) {
        if (buffer == null || !buffer.isDirect()) {
            throw new FdxException(name + " must be a direct byte buffer");
        }
        if (requiredSize < 0 || buffer.remaining() < requiredSize) {
            throw new FdxException(name + " buffer is smaller than the requested operation");
        }
        return MemorySegment.ofBuffer(buffer);
    }

    private static MemorySegment dataSegment(MemorySegment source, int requiredSize, String name) {
        if (source == null) {
            throw new FdxException(name + " is null");
        }
        if (requiredSize < 0 || source.byteSize() < requiredSize) {
            throw new FdxException(name + " buffer is smaller than the requested operation");
        }
        return source;
    }

    private void requireOpen() {
        if (closed) {
            throw new FdxException("Direct3D 12 context is closed");
        }
    }

    long createComputePipeline(String source, String entry, int[] types, int[] bindings, int[] groups) {
        requireOpen();
        ComputePipeline created = new ComputePipeline(types.clone());
        MemorySegment bytecode = D3D12Ffm.NULL;
        try (Arena arena = Arena.ofConfined()) {
            bytecode = compileShader(source, entry, "cs_6_0", "compute");
            int count = types.length;
            MemorySegment parameters = count == 0 ? D3D12Ffm.NULL : arena.allocate((long)count * D3D12Ffm.SIZE_ROOT_PARAMETER, 8);
            for (int i = 0; i < count; i++) {
                var parameter = parameters.asSlice((long)i * D3D12Ffm.SIZE_ROOT_PARAMETER, D3D12Ffm.SIZE_ROOT_PARAMETER);
                if (types[i] == 3) {
                    var range = arena.allocate(20, 4);
                    range.set(D3D12Ffm.INT, 0, 1); // UAV range
                    range.set(D3D12Ffm.INT, 4, 1);
                    range.set(D3D12Ffm.INT, 8, bindings[i]);
                    range.set(D3D12Ffm.INT, 12, groups[i]);
                    fillDescriptorTable(parameter, 1, range);
                } else {
                    parameter.set(D3D12Ffm.INT, 0, types[i] == 0 ? 4 : types[i] == 1 ? 3 : 2);
                    parameter.set(D3D12Ffm.INT, D3D12Ffm.OFF_ROOT_PARAMETER_UNION, bindings[i]);
                    parameter.set(D3D12Ffm.INT, D3D12Ffm.OFF_ROOT_PARAMETER_UNION + 4, groups[i]);
                }
                parameter.set(D3D12Ffm.INT, D3D12Ffm.OFF_ROOT_PARAMETER_VISIBILITY, 0);
            }
            var root = arena.allocate(D3D12Ffm.SIZE_ROOT_SIGNATURE_DESC, 8);
            root.set(D3D12Ffm.INT, 0, count);
            root.set(D3D12Ffm.ADDRESS, D3D12Ffm.OFF_ROOT_SIGNATURE_PARAMETERS, parameters);
            var serializedOutput = pointerOutput(arena);
            var errorsOutput = pointerOutput(arena);
            int result = D3D12Ffm.serializeRootSignature(root, serializedOutput, errorsOutput);
            var errors = D3D12Ffm.pointer(errorsOutput);
            String details = blobText(errors);
            D3D12Ffm.release(errors);
            D3D12Ffm.check(result, "Could not serialize compute root signature: " + details);
            var serialized = D3D12Ffm.pointer(serializedOutput);
            var output = pointerOutput(arena);
            try {
                D3D12Ffm.check(D3D12Ffm.comIntAIALAA(device, D3D12Ffm.SLOT_DEVICE_CREATE_ROOT_SIGNATURE, 0,
                        D3D12Ffm.comAddressA(serialized, D3D12Ffm.SLOT_BLOB_GET_BUFFER_POINTER),
                        D3D12Ffm.comLongA(serialized, D3D12Ffm.SLOT_BLOB_GET_BUFFER_SIZE), D3D12Ffm.IID_ID3D12_ROOT_SIGNATURE, output),
                        "Could not create compute root signature");
                created.root = D3D12Ffm.pointer(output);
            } finally { D3D12Ffm.release(serialized); }
            var descriptor = arena.allocate(56, 8); // D3D12_COMPUTE_PIPELINE_STATE_DESC, Windows x64
            descriptor.set(D3D12Ffm.ADDRESS, 0, created.root);
            descriptor.set(D3D12Ffm.ADDRESS, 8, D3D12Ffm.comAddressA(bytecode, D3D12Ffm.SLOT_BLOB_GET_BUFFER_POINTER));
            descriptor.set(D3D12Ffm.LONG, 16, D3D12Ffm.comLongA(bytecode, D3D12Ffm.SLOT_BLOB_GET_BUFFER_SIZE));
            output.set(D3D12Ffm.ADDRESS, 0, D3D12Ffm.NULL);
            D3D12Ffm.check(D3D12Ffm.comIntAAAA(device, 11, descriptor, D3D12Ffm.IID_ID3D12_PIPELINE_STATE, output), "Could not create compute pipeline");
            created.state = D3D12Ffm.pointer(output);
            return register(created);
        } catch (RuntimeException | Error failure) { created.close(); throw failure; }
        finally { D3D12Ffm.release(bytecode); }
    }

    void destroyComputePipeline(long handle) { retire(removeResource(handle, ComputePipeline.class, "compute pipeline")); }

    void dispatchCompute(long handle, long[] handles, long[] offsets, int x, int y, int z) {
        requireOpen();
        if (!frameOpen || passOpen) throw new FdxException("D3D12 compute requires a frame outside a render pass");
        ComputePipeline value = resource(handle, ComputePipeline.class, "compute pipeline");
        D3D12Ffm.comVoidAA(commands, D3D12Ffm.SLOT_COMMANDS_SET_PIPELINE_STATE, value.state);
        D3D12Ffm.comVoidAA(commands, 29, value.root);
        int images = 0;
        for (int i = 0; i < value.types.length; i++) {
            if (value.types[i] == 3) {
                Texture texture = resource(handles[i], Texture.class, "compute texture");
                TextureAllocation allocation = texture.allocations.get(texture.current);
                if (allocation.uavIndex == INVALID_DESCRIPTOR) throw new FdxException("Texture has no UAV binding");
                textureDescriptorKeys[images++] = allocation.uav;
                transitionTexture(allocation, 8); // UNORDERED_ACCESS
                markRecorded(allocation);
            } else {
                Buffer buffer = resource(handles[i], Buffer.class, "compute buffer");
                BufferAllocation allocation = buffer.allocations.get(buffer.current);
                if (value.types[i] != 2 && buffer.usage != 3) throw new FdxException("Compute storage requires a storage buffer");
                if (value.types[i] != 2) transitionBuffer(allocation, value.types[i] == 0 ? 8 : 64);
                markRecorded(allocation);
                long address = D3D12Ffm.comLongA(allocation.resource, D3D12Ffm.SLOT_RESOURCE_GET_GPU_VIRTUAL_ADDRESS) + offsets[i];
                D3D12Ffm.comVoidAIL(commands, value.types[i] == 0 ? 41 : value.types[i] == 1 ? 39 : 37, i, address);
            }
        }
        if (images > 0) {
            int table = frame.descriptorTables.acquire(textureDescriptorKeys, images, samplerDescriptorKeys, 0);
            int start = frame.descriptorTables.textureStart(table);
            long cpu = descriptorHeapCpuHandle(frame.srvHeap) + (long)start * srvSize;
            long gpu = descriptorHeapGpuHandle(frame.srvHeap) + (long)start * srvSize;
            if (frame.descriptorTables.inserted()) for (int i = 0; i < images; i++) {
                D3D12Ffm.comVoidAILLI(device, D3D12Ffm.SLOT_DEVICE_COPY_DESCRIPTORS_SIMPLE, 1,
                        cpu + (long)i * srvSize, textureDescriptorKeys[i], D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV);
            }
            int image = 0;
            for (int i = 0; i < value.types.length; i++) if (value.types[i] == 3) {
                D3D12Ffm.comVoidAIL(commands, 31, i, gpu + (long)image++ * srvSize);
            }
        }
        D3D12Ffm.comVoidAIII(commands, 14, x, y, z);
        frameBarrier.fill((byte)0);
        frameBarrier.set(D3D12Ffm.INT, 0, 2); // global UAV barrier
        D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, frameBarrier);
        for (int i = 0; i < value.types.length; i++) if (value.types[i] == 3) {
            Texture texture = resource(handles[i], Texture.class, "compute texture");
            if (texture.samplerIndex != INVALID_DESCRIPTOR) transitionTexture(texture.allocations.get(texture.current),
                    D3D12Ffm.D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE);
        }
    }

    private void transitionBuffer(BufferAllocation allocation, int target) {
        if (allocation.state == target) return;
        transition(frameBarrier, allocation.resource, allocation.state, target);
        D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, frameBarrier);
        allocation.state = target;
    }

    void copyBuffer(long source, int sourceOffset, long destination, int destinationOffset, int size) {
        requireOpen();
        if (!frameOpen || passOpen) throw new FdxException("D3D12 buffer copies require a frame outside a render pass");
        Buffer from = resource(source, Buffer.class, "source buffer");
        Buffer to = resource(destination, Buffer.class, "destination buffer");
        BufferAllocation fromAllocation = from.allocations.get(from.current);
        BufferAllocation toAllocation = to.allocations.get(to.current);
        if (from.usage == 3) transitionBuffer(fromAllocation, 2048); // COPY_SOURCE
        else if (from.usage == 4) throw new FdxException("Cannot copy from a readback buffer");
        if (to.usage == 3) transitionBuffer(toAllocation, 1024);
        else if (to.usage != 4) throw new FdxException("D3D12 buffer copy destination must use storage or readback usage");
        markRecorded(fromAllocation); markRecorded(toAllocation);
        D3D12Ffm.comVoidAALALL(commands, 15, toAllocation.resource, (long)destinationOffset,
                fromAllocation.resource, (long)sourceOffset, (long)size);
    }

    ByteBuffer readBuffer(long handle, int offset, int size) {
        Buffer buffer = resource(handle, Buffer.class, "readback buffer");
        if (buffer.usage != 4) throw new FdxException("D3D12 CPU reads require a readback buffer");
        waitIdle();
        return ByteBuffer.allocateDirect(size).put(buffer.allocations.get(buffer.current).mapped.asSlice(offset, size).asByteBuffer()).flip();
    }

    private static final class ComputePipeline implements Resource {
        final int[] types;
        MemorySegment root = D3D12Ffm.NULL, state = D3D12Ffm.NULL;
        ComputePipeline(int[] types) { this.types = types; }
        @Override
        public void close() { D3D12Ffm.release(state); D3D12Ffm.release(root); state = root = D3D12Ffm.NULL; }
    }

    long createBuffer(int size, int usage) {
        requireOpen();
        if (size <= 0) {
            throw new FdxException("Direct3D 12 buffer size must be positive");
        }
        if (usage < 0 || usage > 4) {
            throw new FdxException("Unsupported Direct3D 12 buffer usage");
        }
        Buffer buffer = new Buffer(this, size, usage);
        try {
            buffer.allocations.add(createBufferAllocation(size, usage));
            return register(buffer);
        } catch (RuntimeException | Error error) {
            buffer.close();
            throw error;
        }
    }

    void writeBuffer(long handle, MemorySegment source, int size) {
        requireOpen();
        Buffer buffer = resource(handle, Buffer.class, "buffer");
        if (size < 0 || size > buffer.size) {
            throw new FdxException("Direct3D 12 buffer write exceeds the destination");
        }
        MemorySegment data = dataSegment(source, size, "Buffer source");
        BufferAllocation allocation = writableAllocation(buffer);
        if (buffer.usage == 4) throw new FdxException("Cannot upload to a Direct3D 12 readback buffer");
        if (buffer.usage == 3) {
            try (BufferAllocation upload = createBufferAllocation(size, 0); Arena arena = Arena.ofConfined()) {
                MemorySegment.copy(data, 0, upload.mapped, 0, size);
                MemorySegment barrier = arena.allocate(D3D12Ffm.SIZE_RESOURCE_BARRIER, 8);
                executeImmediate(list -> {
                    if (allocation.state != D3D12Ffm.D3D12_RESOURCE_STATE_COPY_DEST) {
                        transition(barrier, allocation.resource, allocation.state, D3D12Ffm.D3D12_RESOURCE_STATE_COPY_DEST);
                        D3D12Ffm.comVoidAIA(list, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, barrier);
                    }
                    D3D12Ffm.comVoidAALALL(list, 15, allocation.resource, 0L, upload.resource, 0L, (long)size);
                });
                allocation.state = D3D12Ffm.D3D12_RESOURCE_STATE_COPY_DEST;
            }
        } else MemorySegment.copy(data, 0, allocation.mapped, 0, size);
    }

    void initializeBufferRange(long handle, int offset, MemorySegment source, int size) {
        requireOpen();
        Buffer buffer = resource(handle, Buffer.class, "buffer");
        BufferAllocation allocation = buffer.allocations.get(buffer.current);
        if (buffer.usage > 1 || offset < 0 || size <= 0 || offset > buffer.size - size
                || (offset & 3) != 0 || (size & 3) != 0)
            throw new FdxException("Invalid Direct3D 12 initial buffer range");
        if (allocation.recording || allocation.lastFence != 0)
            throw new FdxException("Cannot initialize a buffer referenced by GPU commands");
        MemorySegment.copy(dataSegment(source, size, "Buffer source"), 0, allocation.mapped, offset, size);
    }

    void destroyBuffer(long handle) {
        retire(removeResource(handle, Buffer.class, "buffer"));
    }

    long createTexture(int textureWidth, int textureHeight, int format, int usage,
            int filter, int wrapS, int wrapT, int magFilter, int mipFilter, int mipCount, int samples) {
        requireOpen();
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new FdxException("Direct3D 12 texture size must be positive");
        }
        if (usage < 0 || usage > 6) {
            throw new FdxException("Unsupported Direct3D 12 texture usage");
        }
        Texture texture = new Texture(this, textureWidth, textureHeight, textureFormat(format), usage, mipCount, samples);
        boolean sampled = TextureUsage.values()[usage].sampled();
        boolean renderAttachment = TextureUsage.values()[usage].renderAttachment();
        try (Arena arena = Arena.ofConfined()) {
            if (sampled) {
                texture.samplerIndex = allocateDescriptor(freeSamplers,
                        D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_SAMPLER, "sampler");
                texture.sampler = samplerHandle(texture.samplerIndex);
                MemorySegment sampler = arena.allocate(D3D12Ffm.SIZE_SAMPLER_DESC, 4);
                sampler.set(D3D12Ffm.INT, 0, (filter == 0 ? 0 : 0x10)
                        | (magFilter == 0 ? 0 : 0x4) | (mipFilter == 2 ? 1 : 0));
                sampler.set(D3D12Ffm.INT, 4, addressMode(wrapS));
                sampler.set(D3D12Ffm.INT, 8, addressMode(wrapT));
                sampler.set(D3D12Ffm.INT, 12, D3D12Ffm.D3D12_TEXTURE_ADDRESS_MODE_CLAMP);
                sampler.set(D3D12Ffm.INT, 20, 1);
                sampler.set(D3D12Ffm.INT, 24, D3D12Ffm.D3D12_COMPARISON_FUNC_ALWAYS);
                sampler.set(D3D12Ffm.FLOAT, 48, mipFilter == 0 ? 0 : mipCount - 1);
                D3D12Ffm.comVoidAAL(device, D3D12Ffm.SLOT_DEVICE_CREATE_SAMPLER,
                        sampler, texture.sampler);
            }
            if (renderAttachment) {
                texture.dsvIndex = allocateDescriptor(freeDsvs,
                        D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_DSV, "DSV");
                texture.dsv = dsvHandle(texture.dsvIndex);
                texture.depth = createDepth(arena, textureWidth, textureHeight, texture.dsv, samples);
            }
            if (texture.format != D3D12Ffm.DXGI_FORMAT_D32_FLOAT) texture.allocations.add(createTextureAllocation(texture));
            return register(texture);
        } catch (RuntimeException | Error error) {
            texture.close();
            throw error;
        }
    }

    void writeTexture(long handle, MemorySegment source, int size) {
        requireOpen();
        Texture texture = resource(handle, Texture.class, "texture");
        if (size < 0) {
            throw new FdxException("Direct3D 12 texture upload size cannot be negative");
        }
        long requiredSize = 0;
        for (int level = 0; level < texture.mipCount; level++) {
            requiredSize += (long)Math.max(1, texture.width >> level) * Math.max(1, texture.height >> level) * texture.texelBytes();
        }
        if (size < requiredSize) {
            throw new FdxException("Direct3D 12 texture upload is too small");
        }
        MemorySegment sourceData = dataSegment(source, size, "Texture source");
        TextureAllocation allocation = writableAllocation(texture);
        uploadTexture(texture, allocation, sourceData);
    }

    void destroyTexture(long handle) {
        retire(removeResource(handle, Texture.class, "texture"));
    }

    private BufferAllocation createBufferAllocation(int size, int usage) {
        BufferAllocation allocation = new BufferAllocation();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment descriptor = bufferDescriptor(arena, size);
            if (usage == 3) descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_FLAGS, 4); // ALLOW_UNORDERED_ACCESS
            allocation.state = usage >= 3 ? D3D12Ffm.D3D12_RESOURCE_STATE_COPY_DEST : D3D12Ffm.D3D12_RESOURCE_STATE_GENERIC_READ;
            allocation.resource = createCommittedResource(arena, usage == 3 ? D3D12Ffm.D3D12_HEAP_TYPE_DEFAULT
                            : usage == 4 ? D3D12Ffm.D3D12_HEAP_TYPE_READBACK : D3D12Ffm.D3D12_HEAP_TYPE_UPLOAD,
                    descriptor, allocation.state,
                    D3D12Ffm.NULL, "Could not create a Direct3D 12 buffer");
            if (usage == 3) return allocation;
            MemorySegment output = pointerOutput(arena);
            D3D12Ffm.check(D3D12Ffm.comMap(allocation.resource, 0, range(arena, 0, usage == 4 ? size : 0), output),
                    "Could not map a Direct3D 12 buffer");
            allocation.mapped = D3D12Ffm.pointer(output).reinterpret(size);
            return allocation;
        } catch (RuntimeException | Error error) {
            allocation.close();
            throw error;
        }
    }

    private BufferAllocation writableAllocation(Buffer buffer) {
        long completed = D3D12Ffm.comLongA(fence, D3D12Ffm.SLOT_FENCE_GET_COMPLETED_VALUE);
        BufferAllocation current = buffer.allocations.get(buffer.current);
        if (!current.recording && current.lastFence <= completed) {
            return current;
        }
        for (int index = 0; index < buffer.allocations.size(); index++) {
            BufferAllocation candidate = buffer.allocations.get(index);
            if (!candidate.recording && candidate.lastFence <= completed) {
                buffer.current = index;
                return candidate;
            }
        }
        BufferAllocation allocation = createBufferAllocation(buffer.size, buffer.usage);
        buffer.allocations.add(allocation);
        buffer.current = buffer.allocations.size() - 1;
        return allocation;
    }

    private void markRecorded(BufferAllocation allocation) {
        if (!allocation.recording) {
            allocation.recording = true;
            recordedBuffers.add(allocation);
        }
    }

    private TextureAllocation writableAllocation(Texture texture) {
        long completed = D3D12Ffm.comLongA(fence, D3D12Ffm.SLOT_FENCE_GET_COMPLETED_VALUE);
        TextureAllocation current = texture.allocations.get(texture.current);
        if (!current.recording && current.lastFence <= completed) {
            return current;
        }
        for (int index = 0; index < texture.allocations.size(); index++) {
            TextureAllocation candidate = texture.allocations.get(index);
            if (!candidate.recording && candidate.lastFence <= completed) {
                texture.current = index;
                return candidate;
            }
        }
        TextureAllocation allocation = createTextureAllocation(texture);
        texture.allocations.add(allocation);
        texture.current = texture.allocations.size() - 1;
        return allocation;
    }

    private void markRecorded(TextureAllocation allocation) {
        if (!allocation.recording) {
            allocation.recording = true;
            recordedTextures.add(allocation);
        }
    }

    private TextureAllocation createTextureAllocation(Texture texture) {
        TextureAllocation allocation = new TextureAllocation();
        boolean sampled = TextureUsage.values()[texture.usage].sampled();
        boolean renderAttachment = TextureUsage.values()[texture.usage].renderAttachment();
        boolean storage = texture.usage >= 3;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment descriptor = textureDescriptor(arena, texture.width, texture.height,
                    texture.format, (storage ? 4 : 0) | (renderAttachment
                            ? D3D12Ffm.D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET
                            : D3D12Ffm.D3D12_RESOURCE_FLAG_NONE));
            descriptor.set(D3D12Ffm.SHORT, D3D12Ffm.OFF_RESOURCE_DESC_MIP_LEVELS, (short)texture.mipCount);
            descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_RESOURCE_DESC_SAMPLE_COUNT, texture.samples);
            MemorySegment clear = D3D12Ffm.NULL;
            if (renderAttachment) {
                clear = arena.allocate(D3D12Ffm.SIZE_CLEAR_VALUE, 4);
                clear.set(D3D12Ffm.INT, 0, texture.format);
                clear.set(D3D12Ffm.FLOAT, 16, 1.0f);
            }
            allocation.resource = createCommittedResource(arena, D3D12Ffm.D3D12_HEAP_TYPE_DEFAULT,
                    descriptor, allocation.state, clear,
                    "Could not create a Direct3D 12 texture allocation");
            if (storage) {
                allocation.uavIndex = allocateDescriptor(freeSrvs, D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV, "UAV");
                allocation.uav = srvHandle(allocation.uavIndex);
                MemorySegment view = arena.allocate(40, 8);
                view.set(D3D12Ffm.INT, 0, texture.format);
                view.set(D3D12Ffm.INT, 4, 4); // D3D12_UAV_DIMENSION_TEXTURE2D
                D3D12Ffm.comVoidAAAAL(device, 19, allocation.resource, D3D12Ffm.NULL, view, allocation.uav);
            }
            if (sampled) {
                allocation.srvIndex = allocateDescriptor(freeSrvs,
                        D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV, "SRV");
                allocation.srv = srvHandle(allocation.srvIndex);
                MemorySegment view = arena.allocate(D3D12Ffm.SIZE_SRV_DESC, 8);
                view.set(D3D12Ffm.INT, 0, texture.format);
                view.set(D3D12Ffm.INT, 4, texture.samples > 1 ? 6 : D3D12Ffm.D3D12_SRV_DIMENSION_TEXTURE2D);
                view.set(D3D12Ffm.INT, D3D12Ffm.OFF_SRV_COMPONENT_MAPPING,
                        D3D12Ffm.D3D12_DEFAULT_SHADER_4_COMPONENT_MAPPING);
                if (texture.samples == 1) view.set(D3D12Ffm.INT, D3D12Ffm.OFF_SRV_TEXTURE2D + 4, texture.mipCount);
                D3D12Ffm.comVoidAAAL(device, D3D12Ffm.SLOT_DEVICE_CREATE_SHADER_RESOURCE_VIEW,
                        allocation.resource, view, allocation.srv);
            }
            if (renderAttachment) {
                allocation.mipRtvIndices = new int[texture.mipCount];
                Arrays.fill(allocation.mipRtvIndices, INVALID_DESCRIPTOR);
                allocation.mipRtvs = new long[texture.mipCount];
                for (int level = 0; level < texture.mipCount; level++) {
                int rtvIndex = allocateDescriptor(freeRtvs,
                        D3D12Ffm.D3D12_DESCRIPTOR_HEAP_TYPE_RTV, "RTV");
                allocation.mipRtvIndices[level] = rtvIndex;
                allocation.mipRtvs[level] = rtvHandle(rtvIndex);
                MemorySegment view = arena.allocate(24, 8);
                view.set(D3D12Ffm.INT, 0, texture.format);
                view.set(D3D12Ffm.INT, 4, texture.samples > 1 ? 6 : 4); // TEXTURE2DMS or TEXTURE2D
                view.set(D3D12Ffm.INT, 8, level);
                D3D12Ffm.comVoidAAAL(device, D3D12Ffm.SLOT_DEVICE_CREATE_RENDER_TARGET_VIEW,
                        allocation.resource, view, allocation.mipRtvs[level]);
                }
                allocation.rtvIndex = allocation.mipRtvIndices[0];
                allocation.rtv = allocation.mipRtvs[0];
            }
            return allocation;
        } catch (RuntimeException | Error error) {
            if (allocation.uavIndex != INVALID_DESCRIPTOR) {
                freeSrvs.add(allocation.uavIndex);
                allocation.uavIndex = INVALID_DESCRIPTOR;
            }
            if (allocation.srvIndex != INVALID_DESCRIPTOR) {
                freeSrvs.add(allocation.srvIndex);
                allocation.srvIndex = INVALID_DESCRIPTOR;
            }
            if (allocation.mipRtvIndices != null) for (int index : allocation.mipRtvIndices) {
                if (index != INVALID_DESCRIPTOR) freeRtvs.add(index);
            }
            allocation.close();
            throw error;
        }
    }

    private void uploadTexture(Texture texture, TextureAllocation allocation, MemorySegment source) {
        MemorySegment upload = D3D12Ffm.NULL;
        boolean mapped = false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment textureDescriptor = arena.allocate(D3D12Ffm.SIZE_RESOURCE_DESC, 8);
            D3D12Ffm.comAddressAA(allocation.resource, D3D12Ffm.SLOT_RESOURCE_GET_DESC,
                    textureDescriptor);
            MemorySegment footprint = arena.allocate((long)D3D12Ffm.SIZE_PLACED_FOOTPRINT * texture.mipCount, 8);
            MemorySegment rows = arena.allocate(D3D12Ffm.INT, texture.mipCount);
            MemorySegment rowSize = arena.allocate(D3D12Ffm.LONG, texture.mipCount);
            MemorySegment uploadSizeOutput = arena.allocate(D3D12Ffm.LONG);
            D3D12Ffm.comVoidAAIILAAAA(device, D3D12Ffm.SLOT_DEVICE_GET_COPYABLE_FOOTPRINTS,
                    textureDescriptor, 0, texture.mipCount, 0L, footprint, rows, rowSize, uploadSizeOutput);
            long uploadSize = uploadSizeOutput.get(D3D12Ffm.LONG, 0);
            upload = createCommittedResource(arena, D3D12Ffm.D3D12_HEAP_TYPE_UPLOAD,
                    bufferDescriptor(arena, uploadSize), D3D12Ffm.D3D12_RESOURCE_STATE_GENERIC_READ,
                    D3D12Ffm.NULL, "Could not create a Direct3D 12 texture upload buffer");
            MemorySegment output = pointerOutput(arena);
            D3D12Ffm.check(D3D12Ffm.comMap(upload, 0, range(arena, 0, 0), output),
                    "Could not map a Direct3D 12 texture upload buffer");
            MemorySegment uploadData = D3D12Ffm.pointer(output).reinterpret(uploadSize);
            mapped = true;
            long sourceOffset = 0;
            for (int level = 0; level < texture.mipCount; level++) {
            long nativeOffset = (long)level * D3D12Ffm.SIZE_PLACED_FOOTPRINT;
            long footprintOffset = footprint.get(D3D12Ffm.LONG, nativeOffset + D3D12Ffm.OFF_FOOTPRINT_OFFSET);
            int rowPitch = footprint.get(D3D12Ffm.INT, nativeOffset + D3D12Ffm.OFF_FOOTPRINT_ROW_PITCH);
            long tightRowSize = (long)Math.max(1, texture.width >> level) * texture.texelBytes();
            int levelHeight = Math.max(1, texture.height >> level);
            for (int row = 0; row < levelHeight; row++) {
                MemorySegment.copy(source, sourceOffset + (long)row * tightRowSize,
                        uploadData, footprintOffset + (long)row * rowPitch, tightRowSize);
            }
            sourceOffset += tightRowSize * levelHeight;
            }
            D3D12Ffm.comUnmap(upload, 0, range(arena, 0, uploadSize));
            mapped = false;

            int previousState = allocation.state;
            MemorySegment barrier = arena.allocate(D3D12Ffm.SIZE_RESOURCE_BARRIER, 8);
            MemorySegment target = arena.allocate(D3D12Ffm.SIZE_TEXTURE_COPY_LOCATION, 8);
            target.set(D3D12Ffm.ADDRESS, 0, allocation.resource);
            target.set(D3D12Ffm.INT, D3D12Ffm.OFF_COPY_LOCATION_TYPE,
                    D3D12Ffm.D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX);
            MemorySegment uploadLocation = arena.allocate(D3D12Ffm.SIZE_TEXTURE_COPY_LOCATION, 8);
            uploadLocation.set(D3D12Ffm.ADDRESS, 0, upload);
            uploadLocation.set(D3D12Ffm.INT, D3D12Ffm.OFF_COPY_LOCATION_TYPE,
                    D3D12Ffm.D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT);
            MemorySegment.copy(footprint, 0, uploadLocation,
                    D3D12Ffm.OFF_COPY_LOCATION_UNION, D3D12Ffm.SIZE_PLACED_FOOTPRINT);
            executeImmediate(list -> {
                if (previousState != D3D12Ffm.D3D12_RESOURCE_STATE_COPY_DEST) {
                    transition(barrier, allocation.resource, previousState,
                            D3D12Ffm.D3D12_RESOURCE_STATE_COPY_DEST);
                    D3D12Ffm.comVoidAIA(list, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, barrier);
                }
                for (int level = 0; level < texture.mipCount; level++) {
                target.set(D3D12Ffm.INT, D3D12Ffm.OFF_COPY_LOCATION_UNION, level);
                MemorySegment.copy(footprint, (long)level * D3D12Ffm.SIZE_PLACED_FOOTPRINT,
                        uploadLocation, D3D12Ffm.OFF_COPY_LOCATION_UNION, D3D12Ffm.SIZE_PLACED_FOOTPRINT);
                D3D12Ffm.comVoidAAIIIAA(list, D3D12Ffm.SLOT_COMMANDS_COPY_TEXTURE_REGION,
                        target, 0, 0, 0, uploadLocation, D3D12Ffm.NULL);
                }
                transition(barrier, allocation.resource,
                        D3D12Ffm.D3D12_RESOURCE_STATE_COPY_DEST,
                        D3D12Ffm.D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE);
                D3D12Ffm.comVoidAIA(list, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, barrier);
            });
            allocation.state = D3D12Ffm.D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE;
        } finally {
            if (mapped) {
                D3D12Ffm.comUnmap(upload, 0, D3D12Ffm.NULL);
            }
            D3D12Ffm.release(upload);
        }
    }

    long createShader(String vertexSource, String fragmentSource,
            String vertexEntryPoint, String fragmentEntryPoint, String label) {
        requireOpen();
        Shader shader = new Shader();
        shader.vertexSource = vertexSource;
        shader.fragmentSource = fragmentSource;
        shader.vertexEntry = vertexEntryPoint;
        shader.fragmentEntry = fragmentEntryPoint;
        shader.label = label != null ? label : "Direct3D 12 shader";
        return register(shader);
    }

    void destroyShader(long handle) {
        retire(removeResource(handle, Shader.class, "shader"));
    }

    void prepareShaders(long[] handles) {
        requireOpen();
        var shaders = new Shader[handles.length];
        var pending = new LinkedHashMap<ShaderSource, String>();
        for (int i = 0; i < handles.length; i++) {
            Shader shader = shaders[i] = resource(handles[i], Shader.class, "shader");
            if (D3D12Ffm.isNull(shader.vertex)) {
                queueStage(pending, shader.vertexSource, shader.vertexEntry, "vs_6_0", shader.label);
            }
            if (D3D12Ffm.isNull(shader.fragment)) {
                queueStage(pending, shader.fragmentSource, shader.fragmentEntry, "ps_6_0", shader.label);
            }
        }
        var jobs = new ArrayList<Callable<MemorySegment>>(pending.size());
        for (var entry : pending.entrySet()) {
            ShaderSource source = entry.getKey();
            String label = entry.getValue();
            jobs.add(() -> D3D12DxcCompiler.workerCompiler().compile(source.source(), source.entryPoint(),
                    source.target(), label, validation, optimizeShaders));
        }
        var bytecode = D3D12ShaderCompilationBatch.compile(jobs, D3D12Ffm::release,
                D3D12DxcCompiler.workerThreads());
        try {
            var compiled = new HashMap<ShaderSource, MemorySegment>();
            int index = 0;
            for (ShaderSource source : pending.keySet()) compiled.put(source, bytecode.get(index++));
            // Only the caller touches shader resources and the context cache. Adopt references
            // before cache insertion can evict entries needed by another shader in this batch.
            for (Shader shader : shaders) {
                if (D3D12Ffm.isNull(shader.vertex)) shader.vertex = acquireStage(compiled,
                        shader.vertexSource, shader.vertexEntry, "vs_6_0");
                if (D3D12Ffm.isNull(shader.fragment)) shader.fragment = acquireStage(compiled,
                        shader.fragmentSource, shader.fragmentEntry, "ps_6_0");
            }
            for (var entry : compiled.entrySet()) cacheBytecode(entry.getKey(), entry.getValue());
        } finally {
            for (MemorySegment blob : bytecode) D3D12Ffm.release(blob);
        }
    }

    private void queueStage(Map<ShaderSource, String> pending,
            String source, String entry, String target, String label) {
        ShaderSource key = shaderSource(source, entry, target);
        if (!shaderBytecode.containsKey(key)) pending.putIfAbsent(key, label);
    }

    private MemorySegment acquireStage(Map<ShaderSource, MemorySegment> compiled,
            String source, String entry, String target) {
        ShaderSource key = shaderSource(source, entry, target);
        MemorySegment blob = compiled.get(key);
        if (blob == null) blob = shaderBytecode.get(key);
        if (blob == null) throw new FdxException("Missing compiled Direct3D 12 shader stage");
        D3D12Ffm.comIntA(blob, 1);
        return blob;
    }

    private static ShaderSource shaderSource(String source, String entry, String target) {
        return new ShaderSource(source != null ? source : "", entry != null ? entry : "", target);
    }

    private MemorySegment compileShader(String sourceValue, String entryPointValue,
            String targetValue, String labelValue) {
        ShaderSource key = shaderSource(sourceValue, entryPointValue, targetValue);
        MemorySegment cached = shaderBytecode.get(key);
        if (cached != null) {
            D3D12Ffm.comIntA(cached, 1); // IUnknown::AddRef: each shader owns its bytecode reference.
            return cached;
        }
        MemorySegment shader = compileUncached(key.source(), key.entryPoint(), targetValue, labelValue);
        try {
            cacheBytecode(key, shader);
            return shader;
        } catch (RuntimeException | Error failure) {
            D3D12Ffm.release(shader);
            throw failure;
        }
    }

    private void cacheBytecode(ShaderSource key, MemorySegment shader) {
        D3D12Ffm.comIntA(shader, 1); // The bounded context cache owns an independent reference.
        MemorySegment previous = shaderBytecode.put(key, shader);
        if (previous != null) D3D12Ffm.release(previous);
        if (shaderBytecode.size() > 32) {
            var iterator = shaderBytecode.entrySet().iterator();
            D3D12Ffm.release(iterator.next().getValue());
            iterator.remove();
        }
    }

    private MemorySegment compileUncached(String sourceText, String entryPointText,
            String targetValue, String labelValue) {
        return shaderCompiler.compile(sourceText, entryPointText, targetValue, labelValue, validation, optimizeShaders);
    }

    long createPipeline(long shaderHandle, int colorFormat, int topology,
            boolean depthTest, boolean depthWrite, boolean alphaBlend, int sampledTextureCount,
            int uniformGroup, int uniformBinding,
            int[] layoutStridesValue, int[] layoutStepModesValue,
            int[] attributeLocationsValue, int[] attributeFormatsValue,
            int[] attributeOffsetsValue, int[] attributeSlotsValue,
            int[] textureGroupsValue, int[] textureBindingsValue,
            int[] samplerGroupsValue, int[] samplerBindingsValue,
            RenderPipelineDescriptor state) {
        requireOpen();
        Shader shader = resource(shaderHandle, Shader.class, "shader");
        // Reflection is available at module creation; compile only stages a pipeline actually uses.
        if (D3D12Ffm.isNull(shader.vertex)) {
            shader.vertex = compileShader(shader.vertexSource, shader.vertexEntry, "vs_6_0", shader.label);
        }
        if (D3D12Ffm.isNull(shader.fragment)) {
            shader.fragment = compileShader(shader.fragmentSource, shader.fragmentEntry, "ps_6_0", shader.label);
        }
        return register(createPreparedPipeline(device, shader.vertex, shader.fragment, colorFormat, topology,
                depthTest, depthWrite, alphaBlend, sampledTextureCount, uniformGroup, uniformBinding,
                layoutStridesValue, layoutStepModesValue, attributeLocationsValue, attributeFormatsValue,
                attributeOffsetsValue, attributeSlotsValue, textureGroupsValue, textureBindingsValue,
                samplerGroupsValue, samplerBindingsValue, state, null));
    }

    /** Isolated COM creation. All native temporary storage belongs to this calling worker. */
    static Pipeline createPreparedPipeline(MemorySegment device, MemorySegment vertex, MemorySegment fragment,
            int colorFormat, int topology, boolean depthTest, boolean depthWrite, boolean alphaBlend,
            int sampledTextureCount, int uniformGroup, int uniformBinding,
            int[] layoutStridesValue, int[] layoutStepModesValue,
            int[] attributeLocationsValue, int[] attributeFormatsValue,
            int[] attributeOffsetsValue, int[] attributeSlotsValue,
            int[] textureGroupsValue, int[] textureBindingsValue,
            int[] samplerGroupsValue, int[] samplerBindingsValue,
            RenderPipelineDescriptor state, D3D12PipelineCache.Entry cached) {
        int[] layoutStrides = array(layoutStridesValue);
        int[] layoutStepModes = array(layoutStepModesValue);
        int[] attributeLocations = array(attributeLocationsValue);
        int[] attributeFormats = array(attributeFormatsValue);
        int[] attributeOffsets = array(attributeOffsetsValue);
        int[] attributeSlots = array(attributeSlotsValue);
        int[] textureGroups = array(textureGroupsValue);
        int[] textureBindings = array(textureBindingsValue);
        int[] samplerGroups = array(samplerGroupsValue);
        int[] samplerBindings = array(samplerBindingsValue);
        int samplerCount = samplerGroups.length;
        if (sampledTextureCount < 0 || sampledTextureCount > MAX_TEXTURES) {
            throw new FdxException("Unsupported Direct3D 12 sampled texture count");
        }
        if (layoutStrides.length != layoutStepModes.length
                || layoutStrides.length > MAX_VERTEX_BUFFERS) {
            throw new FdxException("Invalid Direct3D 12 vertex layout arrays");
        }
        int attributeCount = attributeLocations.length;
        if (attributeFormats.length != attributeCount || attributeOffsets.length != attributeCount
                || attributeSlots.length != attributeCount) {
            throw new FdxException("Invalid Direct3D 12 vertex attribute arrays");
        }
        if (textureGroups.length != sampledTextureCount
                || textureBindings.length != sampledTextureCount
                || samplerBindings.length != samplerCount
                || (samplerCount != 0 && samplerCount != sampledTextureCount)) {
            throw new FdxException("Direct3D 12 reflection does not match the sampled texture count");
        }
        for (int stride : layoutStrides) {
            if (stride <= 0) {
                throw new FdxException("Direct3D 12 vertex stride must be positive");
            }
        }

        Pipeline created = new Pipeline();
        created.topology = primitiveTopology(topology);
        created.sampledTextureCount = sampledTextureCount;
        created.samplerCount = samplerCount;
        created.strides = Arrays.copyOf(layoutStrides, layoutStrides.length);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment textureRanges = sampledTextureCount == 0 ? D3D12Ffm.NULL
                    : arena.allocate((long)sampledTextureCount * D3D12Ffm.SIZE_DESCRIPTOR_RANGE, 4);
            MemorySegment samplerRanges = samplerCount == 0 ? D3D12Ffm.NULL
                    : arena.allocate((long)samplerCount * D3D12Ffm.SIZE_DESCRIPTOR_RANGE, 4);
            for (int index = 0; index < sampledTextureCount; index++) {
                long offset = (long)index * D3D12Ffm.SIZE_DESCRIPTOR_RANGE;
                fillDescriptorRange(textureRanges.asSlice(offset, D3D12Ffm.SIZE_DESCRIPTOR_RANGE),
                        D3D12Ffm.D3D12_DESCRIPTOR_RANGE_TYPE_SRV,
                        textureBindings[index], textureGroups[index], index);
            }
            for (int index = 0; index < samplerCount; index++) {
                long offset = (long)index * D3D12Ffm.SIZE_DESCRIPTOR_RANGE;
                fillDescriptorRange(samplerRanges.asSlice(offset, D3D12Ffm.SIZE_DESCRIPTOR_RANGE),
                        D3D12Ffm.D3D12_DESCRIPTOR_RANGE_TYPE_SAMPLER,
                        samplerBindings[index], samplerGroups[index], index);
            }

            int rootCount = (uniformGroup >= 0 && uniformBinding >= 0 ? 1 : 0)
                    + (sampledTextureCount > 0 ? 1 : 0)
                    + (samplerCount > 0 ? 1 : 0);
            if ((uniformGroup >= 0) != (uniformBinding >= 0)) {
                throw new FdxException("Incomplete Direct3D 12 uniform binding");
            }
            MemorySegment rootParameters = rootCount == 0 ? D3D12Ffm.NULL
                    : arena.allocate((long)rootCount * D3D12Ffm.SIZE_ROOT_PARAMETER, 8);
            int rootIndex = 0;
            if (uniformGroup >= 0) {
                created.uniformRoot = rootIndex;
                MemorySegment parameter = rootParameters.asSlice(
                        (long)rootIndex++ * D3D12Ffm.SIZE_ROOT_PARAMETER,
                        D3D12Ffm.SIZE_ROOT_PARAMETER);
                parameter.set(D3D12Ffm.INT, 0, D3D12Ffm.D3D12_ROOT_PARAMETER_TYPE_CBV);
                parameter.set(D3D12Ffm.INT, D3D12Ffm.OFF_ROOT_PARAMETER_UNION, uniformBinding);
                parameter.set(D3D12Ffm.INT, D3D12Ffm.OFF_ROOT_PARAMETER_UNION + 4, uniformGroup);
                parameter.set(D3D12Ffm.INT, D3D12Ffm.OFF_ROOT_PARAMETER_VISIBILITY,
                        D3D12Ffm.D3D12_SHADER_VISIBILITY_ALL);
            }
            if (sampledTextureCount > 0) {
                created.textureRoot = rootIndex;
                fillDescriptorTable(rootParameters.asSlice(
                                (long)rootIndex++ * D3D12Ffm.SIZE_ROOT_PARAMETER,
                                D3D12Ffm.SIZE_ROOT_PARAMETER),
                        sampledTextureCount, textureRanges);
            }
            if (samplerCount > 0) {
                created.samplerRoot = rootIndex;
                fillDescriptorTable(rootParameters.asSlice(
                                (long)rootIndex * D3D12Ffm.SIZE_ROOT_PARAMETER,
                                D3D12Ffm.SIZE_ROOT_PARAMETER),
                        samplerCount, samplerRanges);
            }

            MemorySegment rootDescriptor = arena.allocate(D3D12Ffm.SIZE_ROOT_SIGNATURE_DESC, 8);
            rootDescriptor.set(D3D12Ffm.INT, 0, rootCount);
            rootDescriptor.set(D3D12Ffm.ADDRESS, D3D12Ffm.OFF_ROOT_SIGNATURE_PARAMETERS,
                    rootParameters);
            rootDescriptor.set(D3D12Ffm.ADDRESS, D3D12Ffm.OFF_ROOT_SIGNATURE_STATIC_SAMPLERS,
                    D3D12Ffm.NULL);
            rootDescriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_ROOT_SIGNATURE_FLAGS,
                    D3D12Ffm.D3D12_ROOT_SIGNATURE_FLAG_ALLOW_INPUT_ASSEMBLER_INPUT_LAYOUT);
            MemorySegment serializedOutput = pointerOutput(arena);
            MemorySegment rootErrorsOutput = pointerOutput(arena);
            int rootResult = D3D12Ffm.serializeRootSignature(
                    rootDescriptor, serializedOutput, rootErrorsOutput);
            MemorySegment rootErrors = D3D12Ffm.pointer(rootErrorsOutput);
            if (D3D12Ffm.failed(rootResult)) {
                String details = blobText(rootErrors);
                D3D12Ffm.release(rootErrors);
                throw new FdxException("Could not serialize the Direct3D 12 root signature: "
                        + (details.isEmpty()
                                ? "HRESULT 0x" + String.format("%08X", rootResult)
                                : details));
            }
            D3D12Ffm.release(rootErrors);
            MemorySegment serialized = D3D12Ffm.pointer(serializedOutput);
            try {
                MemorySegment output = pointerOutput(arena);
                D3D12Ffm.check(D3D12Ffm.comIntAIALAA(device,
                        D3D12Ffm.SLOT_DEVICE_CREATE_ROOT_SIGNATURE, 0,
                        D3D12Ffm.comAddressA(serialized, D3D12Ffm.SLOT_BLOB_GET_BUFFER_POINTER),
                        D3D12Ffm.comLongA(serialized, D3D12Ffm.SLOT_BLOB_GET_BUFFER_SIZE),
                        D3D12Ffm.IID_ID3D12_ROOT_SIGNATURE, output),
                        "Could not create the Direct3D 12 root signature");
                created.rootSignature = D3D12Ffm.pointer(output);
            } finally {
                D3D12Ffm.release(serialized);
            }

            MemorySegment inputElements = attributeCount == 0 ? D3D12Ffm.NULL
                    : arena.allocate((long)attributeCount * D3D12Ffm.SIZE_INPUT_ELEMENT_DESC, 8);
            MemorySegment semantic = attributeCount == 0
                    ? D3D12Ffm.NULL : arena.allocateFrom("TEXCOORD");
            for (int index = 0; index < attributeCount; index++) {
                int slot = attributeSlots[index];
                if (slot < 0 || slot >= layoutStrides.length) {
                    throw new FdxException("Direct3D 12 vertex attribute uses an invalid slot");
                }
                MemorySegment input = inputElements.asSlice(
                        (long)index * D3D12Ffm.SIZE_INPUT_ELEMENT_DESC,
                        D3D12Ffm.SIZE_INPUT_ELEMENT_DESC);
                input.set(D3D12Ffm.ADDRESS, 0, semantic);
                input.set(D3D12Ffm.INT, 8, attributeLocations[index]);
                input.set(D3D12Ffm.INT, 12, vertexFormat(attributeFormats[index]));
                input.set(D3D12Ffm.INT, 16, slot);
                input.set(D3D12Ffm.INT, 20, attributeOffsets[index]);
                boolean perInstance = layoutStepModes[slot] == 1;
                input.set(D3D12Ffm.INT, 24, perInstance
                        ? D3D12Ffm.D3D12_INPUT_CLASSIFICATION_PER_INSTANCE_DATA
                        : D3D12Ffm.D3D12_INPUT_CLASSIFICATION_PER_VERTEX_DATA);
                input.set(D3D12Ffm.INT, 28, perInstance ? 1 : 0);
            }

            MemorySegment descriptor = arena.allocate(D3D12Ffm.SIZE_GRAPHICS_PIPELINE_DESC, 8);
            descriptor.set(D3D12Ffm.ADDRESS, 0, created.rootSignature);
            setShaderBytecode(descriptor, D3D12Ffm.OFF_PIPELINE_VS, vertex);
            setShaderBytecode(descriptor, D3D12Ffm.OFF_PIPELINE_PS, fragment);
            long blend = D3D12Ffm.OFF_PIPELINE_BLEND + 8L;
            descriptor.set(D3D12Ffm.INT, blend, alphaBlend ? 1 : 0);
            descriptor.set(D3D12Ffm.INT, blend + 8, D3D12Ffm.D3D12_BLEND_SRC_ALPHA);
            descriptor.set(D3D12Ffm.INT, blend + 12, D3D12Ffm.D3D12_BLEND_INV_SRC_ALPHA);
            descriptor.set(D3D12Ffm.INT, blend + 16, D3D12Ffm.D3D12_BLEND_OP_ADD);
            descriptor.set(D3D12Ffm.INT, blend + 20, D3D12Ffm.D3D12_BLEND_ONE);
            descriptor.set(D3D12Ffm.INT, blend + 24, D3D12Ffm.D3D12_BLEND_INV_SRC_ALPHA);
            descriptor.set(D3D12Ffm.INT, blend + 28, D3D12Ffm.D3D12_BLEND_OP_ADD);
            descriptor.set(D3D12Ffm.INT, blend + 32, D3D12Ffm.D3D12_LOGIC_OP_NOOP);
            descriptor.set(D3D12Ffm.BYTE, blend + 36, D3D12Ffm.D3D12_COLOR_WRITE_ENABLE_ALL);
            descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_PIPELINE_SAMPLE_MASK, -1);
            long rasterizer = D3D12Ffm.OFF_PIPELINE_RASTERIZER;
            descriptor.set(D3D12Ffm.INT, rasterizer, D3D12Ffm.D3D12_FILL_MODE_SOLID);
            descriptor.set(D3D12Ffm.INT, rasterizer + 4, D3D12Ffm.D3D12_CULL_MODE_NONE);
            descriptor.set(D3D12Ffm.INT, rasterizer + 24, 1);
            long depth = D3D12Ffm.OFF_PIPELINE_DEPTH_STENCIL;
            descriptor.set(D3D12Ffm.INT, depth, depthTest ? 1 : 0);
            descriptor.set(D3D12Ffm.INT, depth + 4, depthWrite
                    ? D3D12Ffm.D3D12_DEPTH_WRITE_MASK_ALL
                    : D3D12Ffm.D3D12_DEPTH_WRITE_MASK_ZERO);
            descriptor.set(D3D12Ffm.INT, depth + 8, D3D12Ffm.D3D12_COMPARISON_FUNC_LESS_EQUAL);
            descriptor.set(D3D12Ffm.BYTE, depth + 16, D3D12Ffm.D3D12_DEFAULT_STENCIL_READ_MASK);
            descriptor.set(D3D12Ffm.BYTE, depth + 17, D3D12Ffm.D3D12_DEFAULT_STENCIL_WRITE_MASK);
            D3D12PipelineState.write(descriptor, state);
            descriptor.set(D3D12Ffm.ADDRESS, D3D12Ffm.OFF_PIPELINE_INPUT_LAYOUT, inputElements);
            descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_PIPELINE_INPUT_LAYOUT + 8L, attributeCount);
            descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_PIPELINE_PRIMITIVE_TOPOLOGY,
                    primitiveTopologyType(topology));
            var colorTargets = state.colorTargets();
            descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_PIPELINE_NUM_RENDER_TARGETS, colorTargets.length);
            for (int i = 0; i < colorTargets.length; i++) {
                descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_PIPELINE_RTV_FORMATS + 4L * i,
                        textureFormat(colorTargets[i].format().ordinal()));
            }
            descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_PIPELINE_DSV_FORMAT,
                    D3D12Ffm.DXGI_FORMAT_D32_FLOAT);
            descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_PIPELINE_SAMPLE_DESC, state.multisampleState().count());
            descriptor.set(D3D12Ffm.INT, D3D12Ffm.OFF_PIPELINE_FLAGS,
                    D3D12Ffm.D3D12_PIPELINE_STATE_FLAG_NONE);
            created.state = D3D12PipelineCache.create(device, descriptor, arena, cached);
            return created;
        } catch (RuntimeException | Error error) {
            created.close();
            throw error;
        }
    }

    void destroyPipeline(long handle) {
        retire(removeResource(handle, Pipeline.class, "pipeline"));
    }

    MemorySegment retainPreparationDevice() {
        requireOpen();
        D3D12Ffm.comIntA(device, 1); // IUnknown.AddRef: lifetime extends past context teardown.
        return MemorySegment.ofAddress(device.address());
    }

    long publishPreparedPipeline(Pipeline pipeline) {
        requireOpen();
        D3D12Ffm.check(deviceRemovedReason(), "Cannot publish a pipeline after Direct3D 12 device removal");
        return register(pipeline);
    }

    int deviceRemovedReason() {
        if (removalReason == 0 && !D3D12Ffm.isNull(device)) {
            removalReason = D3D12Ffm.comIntA(device, D3D12Ffm.SLOT_DEVICE_GET_REMOVED_REASON);
        }
        return removalReason;
    }

    private static void fillDescriptorRange(MemorySegment range, int type,
            int binding, int group, int offset) {
        range.set(D3D12Ffm.INT, 0, type);
        range.set(D3D12Ffm.INT, 4, 1);
        range.set(D3D12Ffm.INT, 8, binding);
        range.set(D3D12Ffm.INT, 12, group);
        range.set(D3D12Ffm.INT, 16, offset);
    }

    private static void fillDescriptorTable(MemorySegment parameter,
            int count, MemorySegment ranges) {
        parameter.set(D3D12Ffm.INT, 0, D3D12Ffm.D3D12_ROOT_PARAMETER_TYPE_DESCRIPTOR_TABLE);
        parameter.set(D3D12Ffm.INT, D3D12Ffm.OFF_ROOT_PARAMETER_UNION, count);
        parameter.set(D3D12Ffm.ADDRESS, D3D12Ffm.OFF_ROOT_PARAMETER_UNION + 8L, ranges);
        parameter.set(D3D12Ffm.INT, D3D12Ffm.OFF_ROOT_PARAMETER_VISIBILITY,
                D3D12Ffm.D3D12_SHADER_VISIBILITY_ALL);
    }

    private static void setShaderBytecode(MemorySegment descriptor, long offset, MemorySegment blob) {
        descriptor.set(D3D12Ffm.ADDRESS, offset,
                D3D12Ffm.comAddressA(blob, D3D12Ffm.SLOT_BLOB_GET_BUFFER_POINTER));
        descriptor.set(D3D12Ffm.LONG, offset + 8,
                D3D12Ffm.comLongA(blob, D3D12Ffm.SLOT_BLOB_GET_BUFFER_SIZE));
    }

    private static String blobText(MemorySegment blob) {
        if (D3D12Ffm.isNull(blob)) {
            return "";
        }
        long size = D3D12Ffm.comLongA(blob, D3D12Ffm.SLOT_BLOB_GET_BUFFER_SIZE);
        if (size <= 0L || size > Integer.MAX_VALUE) {
            return "";
        }
        MemorySegment data = D3D12Ffm.comAddressA(blob,
                D3D12Ffm.SLOT_BLOB_GET_BUFFER_POINTER).reinterpret(size);
        return new String(data.toArray(D3D12Ffm.BYTE), StandardCharsets.UTF_8)
                .replace("\u0000", "").trim();
    }

    void readPixels(ByteBuffer destination) {
        requireOpen();
        if (!frameOpen || passOpen) {
            throw new FdxException("Cannot read the Direct3D 12 framebuffer now");
        }
        long requiredSize = (long)width * height * 4L;
        MemorySegment output = directBuffer(destination, Math.toIntExact(requiredSize),
                "Framebuffer destination");
        MemorySegment readback = D3D12Ffm.NULL;
        boolean mapped = false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sourceDescriptor = arena.allocate(D3D12Ffm.SIZE_RESOURCE_DESC, 8);
            D3D12Ffm.comAddressAA(frame.backBuffer, D3D12Ffm.SLOT_RESOURCE_GET_DESC,
                    sourceDescriptor);
            MemorySegment footprint = arena.allocate(D3D12Ffm.SIZE_PLACED_FOOTPRINT, 8);
            MemorySegment rows = arena.allocate(D3D12Ffm.INT);
            MemorySegment rowSize = arena.allocate(D3D12Ffm.LONG);
            MemorySegment totalSizeOutput = arena.allocate(D3D12Ffm.LONG);
            D3D12Ffm.comVoidAAIILAAAA(device, D3D12Ffm.SLOT_DEVICE_GET_COPYABLE_FOOTPRINTS,
                    sourceDescriptor, 0, 1, 0L, footprint, rows, rowSize, totalSizeOutput);
            long totalSize = totalSizeOutput.get(D3D12Ffm.LONG, 0);
            readback = createCommittedResource(arena, D3D12Ffm.D3D12_HEAP_TYPE_READBACK,
                    bufferDescriptor(arena, totalSize), D3D12Ffm.D3D12_RESOURCE_STATE_COPY_DEST,
                    D3D12Ffm.NULL, "Could not create a Direct3D 12 readback buffer");

            transition(frameBarrier, frame.backBuffer,
                    D3D12Ffm.D3D12_RESOURCE_STATE_RENDER_TARGET,
                    D3D12Ffm.D3D12_RESOURCE_STATE_COPY_SOURCE);
            D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, frameBarrier);
            MemorySegment source = arena.allocate(D3D12Ffm.SIZE_TEXTURE_COPY_LOCATION, 8);
            source.set(D3D12Ffm.ADDRESS, 0, frame.backBuffer);
            source.set(D3D12Ffm.INT, D3D12Ffm.OFF_COPY_LOCATION_TYPE,
                    D3D12Ffm.D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX);
            MemorySegment target = arena.allocate(D3D12Ffm.SIZE_TEXTURE_COPY_LOCATION, 8);
            target.set(D3D12Ffm.ADDRESS, 0, readback);
            target.set(D3D12Ffm.INT, D3D12Ffm.OFF_COPY_LOCATION_TYPE,
                    D3D12Ffm.D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT);
            MemorySegment.copy(footprint, 0, target,
                    D3D12Ffm.OFF_COPY_LOCATION_UNION, D3D12Ffm.SIZE_PLACED_FOOTPRINT);
            D3D12Ffm.comVoidAAIIIAA(commands, D3D12Ffm.SLOT_COMMANDS_COPY_TEXTURE_REGION,
                    target, 0, 0, 0, source, D3D12Ffm.NULL);
            transition(frameBarrier, frame.backBuffer,
                    D3D12Ffm.D3D12_RESOURCE_STATE_COPY_SOURCE,
                    D3D12Ffm.D3D12_RESOURCE_STATE_PRESENT);
            D3D12Ffm.comVoidAIA(commands, D3D12Ffm.SLOT_COMMANDS_RESOURCE_BARRIER, 1, frameBarrier);
            long submitted = submitFrame(true);
            waitForFence(submitted);

            MemorySegment mappedOutput = pointerOutput(arena);
            D3D12Ffm.check(D3D12Ffm.comMap(readback, 0, range(arena, 0, totalSize), mappedOutput),
                    "Could not map Direct3D 12 readback data");
            MemorySegment input = D3D12Ffm.pointer(mappedOutput).reinterpret(totalSize);
            mapped = true;
            long footprintOffset = footprint.get(D3D12Ffm.LONG, D3D12Ffm.OFF_FOOTPRINT_OFFSET);
            int rowPitch = footprint.get(D3D12Ffm.INT, D3D12Ffm.OFF_FOOTPRINT_ROW_PITCH);
            for (int outputY = 0; outputY < height; outputY++) {
                long inputRow = footprintOffset + (long)(height - 1 - outputY) * rowPitch;
                long outputRow = (long)outputY * width * 4L;
                for (int x = 0; x < width; x++) {
                    long inputPixel = inputRow + (long)x * 4L;
                    long outputPixel = outputRow + (long)x * 4L;
                    output.set(D3D12Ffm.BYTE, outputPixel, input.get(D3D12Ffm.BYTE, inputPixel + 2));
                    output.set(D3D12Ffm.BYTE, outputPixel + 1, input.get(D3D12Ffm.BYTE, inputPixel + 1));
                    output.set(D3D12Ffm.BYTE, outputPixel + 2, input.get(D3D12Ffm.BYTE, inputPixel));
                    output.set(D3D12Ffm.BYTE, outputPixel + 3, input.get(D3D12Ffm.BYTE, inputPixel + 3));
                }
            }
            D3D12Ffm.comUnmap(readback, 0, range(arena, 0, 0));
            mapped = false;
        } finally {
            if (mapped) {
                D3D12Ffm.comUnmap(readback, 0, D3D12Ffm.NULL);
            }
            D3D12Ffm.release(readback);
        }
    }

    private void executeImmediate(CommandRecorder recorder) {
        MemorySegment allocator = D3D12Ffm.NULL;
        MemorySegment list = D3D12Ffm.NULL;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment output = pointerOutput(arena);
            D3D12Ffm.check(D3D12Ffm.comIntAIAA(device,
                    D3D12Ffm.SLOT_DEVICE_CREATE_COMMAND_ALLOCATOR,
                    D3D12Ffm.D3D12_COMMAND_LIST_TYPE_DIRECT,
                    D3D12Ffm.IID_ID3D12_COMMAND_ALLOCATOR, output),
                    "Could not create a Direct3D 12 upload allocator");
            allocator = D3D12Ffm.pointer(output);
            output.set(D3D12Ffm.ADDRESS, 0, D3D12Ffm.NULL);
            D3D12Ffm.check(D3D12Ffm.comIntAIIAAAA(device,
                    D3D12Ffm.SLOT_DEVICE_CREATE_COMMAND_LIST,
                    0, D3D12Ffm.D3D12_COMMAND_LIST_TYPE_DIRECT,
                    allocator, D3D12Ffm.NULL,
                    D3D12Ffm.IID_ID3D12_GRAPHICS_COMMAND_LIST, output),
                    "Could not create a Direct3D 12 upload list");
            list = D3D12Ffm.pointer(output);
            recorder.record(list);
            D3D12Ffm.check(D3D12Ffm.comIntA(list, D3D12Ffm.SLOT_COMMANDS_CLOSE),
                    "Could not close a Direct3D 12 upload list");
            MemorySegment lists = arena.allocate(D3D12Ffm.ADDRESS);
            lists.set(D3D12Ffm.ADDRESS, 0, list);
            D3D12Ffm.comVoidAIA(queue, D3D12Ffm.SLOT_QUEUE_EXECUTE_COMMAND_LISTS, 1, lists);
            long value = nextFence++;
            D3D12Ffm.check(D3D12Ffm.comIntAAL(queue, D3D12Ffm.SLOT_QUEUE_SIGNAL, fence, value),
                    "Could not signal a Direct3D 12 upload");
            waitForFence(value);
        } finally {
            D3D12Ffm.release(list);
            D3D12Ffm.release(allocator);
        }
    }

    private long register(Resource resource) {
        return resources.add(resource);
    }

    private <T extends Resource> T resource(long handle, Class<T> type, String name) {
        requireOpen();
        Resource value = resources.get(handle);
        if (!type.isInstance(value)) {
            throw new FdxException("Direct3D 12 " + name + " handle is invalid");
        }
        return type.cast(value);
    }

    private <T extends Resource> T removeResource(long handle, Class<T> type, String name) {
        T value = resource(handle, type, name);
        resources.remove(handle);
        return value;
    }

    private void retire(Resource resource) {
        if (deviceRemovedReason() != 0) {
            resource.close();
        } else if (frameOpen) {
            retired.add(new RetiredResource(nextFence, resource));
        } else {
            waitIdle();
            resource.close();
        }
    }

    private static int[] array(int[] value) {
        return value != null ? value : new int[0];
    }

    private static int textureFormat(int format) {
        return switch (format) {
            case 1 -> D3D12Ffm.DXGI_FORMAT_R8G8B8A8_UNORM;
            case 2 -> D3D12Ffm.DXGI_FORMAT_R8G8B8A8_UNORM_SRGB;
            case 3 -> D3D12Ffm.DXGI_FORMAT_B8G8R8A8_UNORM;
            case 4 -> D3D12Ffm.DXGI_FORMAT_B8G8R8A8_UNORM_SRGB;
            case 5 -> RGBA16_FLOAT_FORMAT;
            case 6 -> 41; // DXGI_FORMAT_R32_FLOAT
            case 8 -> D3D12Ffm.DXGI_FORMAT_D32_FLOAT;
            default -> throw new FdxException("Unsupported Direct3D 12 texture format");
        };
    }

    private static int vertexFormat(int format) {
        return switch (format) {
            case 0 -> D3D12Ffm.DXGI_FORMAT_R32_FLOAT;
            case 1 -> D3D12Ffm.DXGI_FORMAT_R32G32_FLOAT;
            case 2 -> D3D12Ffm.DXGI_FORMAT_R32G32B32_FLOAT;
            case 3 -> D3D12Ffm.DXGI_FORMAT_R32G32B32A32_FLOAT;
            case 4 -> D3D12Ffm.DXGI_FORMAT_R8G8B8A8_UNORM;
            default -> throw new FdxException("Unsupported Direct3D 12 vertex format");
        };
    }

    private static int primitiveTopology(int topology) {
        return switch (topology) {
            case 0 -> D3D12Ffm.D3D_PRIMITIVE_TOPOLOGY_LINELIST;
            case 2 -> D3D12Ffm.D3D_PRIMITIVE_TOPOLOGY_TRIANGLESTRIP;
            default -> D3D12Ffm.D3D_PRIMITIVE_TOPOLOGY_TRIANGLELIST;
        };
    }

    private static int primitiveTopologyType(int topology) {
        return topology == 0
                ? D3D12Ffm.D3D12_PRIMITIVE_TOPOLOGY_TYPE_LINE
                : D3D12Ffm.D3D12_PRIMITIVE_TOPOLOGY_TYPE_TRIANGLE;
    }

    private static int addressMode(int wrap) {
        return switch (wrap) {
            case 1 -> D3D12Ffm.D3D12_TEXTURE_ADDRESS_MODE_WRAP;
            case 2 -> D3D12Ffm.D3D12_TEXTURE_ADDRESS_MODE_MIRROR;
            default -> D3D12Ffm.D3D12_TEXTURE_ADDRESS_MODE_CLAMP;
        };
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (deviceRemovedReason() != 0) {
            passOpen = false;
            frameOpen = false;
        }
        try {
            if (passOpen) {
                endPass();
            }
            if (frameOpen) {
                endFrame();
            }
        } catch (RuntimeException ignored) {
            passOpen = false;
            frameOpen = false;
        }
        try {
            waitIdle();
        } catch (RuntimeException ignored) {
            // Teardown continues so partially initialized native state is not leaked.
        }
        for (int index = retired.size() - 1; index >= 0; index--) {
            retired.get(index).resource.close();
        }
        retired.clear();
        resources.closeAll();
        for (MemorySegment bytecode : shaderBytecode.values()) D3D12Ffm.release(bytecode);
        shaderBytecode.clear();
        if (shaderCompiler != null) { shaderCompiler.close(); shaderCompiler = null; }
        releaseFrameTargets();
        for (int index = frames.size() - 1; index >= 0; index--) {
            frames.get(index).close();
        }
        frames.clear();
        D3D12Ffm.release(commands);
        D3D12Ffm.release(fence);
        D3D12Ffm.closeHandle(fenceEvent);
        D3D12Ffm.release(swapChain);
        D3D12Ffm.release(cpuSamplerHeap);
        D3D12Ffm.release(cpuSrvHeap);
        D3D12Ffm.release(dsvHeap);
        D3D12Ffm.release(rtvHeap);
        D3D12Ffm.release(queue);
        D3D12Ffm.release(device);
        D3D12Ffm.release(adapter);
        D3D12Ffm.release(factory);
        commands = D3D12Ffm.NULL;
        fence = D3D12Ffm.NULL;
        fenceEvent = D3D12Ffm.NULL;
        swapChain = D3D12Ffm.NULL;
        cpuSamplerHeap = D3D12Ffm.NULL;
        cpuSrvHeap = D3D12Ffm.NULL;
        dsvHeap = D3D12Ffm.NULL;
        rtvHeap = D3D12Ffm.NULL;
        queue = D3D12Ffm.NULL;
        device = D3D12Ffm.NULL;
        adapter = D3D12Ffm.NULL;
        factory = D3D12Ffm.NULL;
        closed = true;
        nativeArena.close();
    }

    @FunctionalInterface
    private interface CommandRecorder {
        void record(MemorySegment commandList);
    }

    private interface Resource extends AutoCloseable {
        @Override
        void close();
    }

    private static final class ResourceRegistry {
        private Resource[] values = new Resource[64];
        private int[] generations = new int[64];
        private int[] freeSlots = new int[64];
        private int freeCount;
        private int nextSlot = 1;

        long add(Resource resource) {
            int index = freeCount > 0 ? freeSlots[--freeCount] : nextSlot++;
            ensureCapacity(index);
            int generation = generations[index];
            if (generation == 0) {
                generation = 1;
                generations[index] = generation;
            }
            values[index] = resource;
            return ((long)generation << 32) | (index & 0xffffffffL);
        }

        Resource get(long handle) {
            int index = (int)handle;
            int generation = (int)(handle >>> 32);
            if (index <= 0 || index >= values.length || generations[index] != generation) {
                return null;
            }
            return values[index];
        }

        void remove(long handle) {
            int index = (int)handle;
            int generation = (int)(handle >>> 32);
            if (index <= 0 || index >= values.length || generations[index] != generation
                    || values[index] == null) {
                throw new FdxException("Direct3D 12 resource handle is invalid");
            }
            values[index] = null;
            int nextGeneration = generation + 1;
            generations[index] = nextGeneration != 0 ? nextGeneration : 1;
            if (freeCount == freeSlots.length) {
                freeSlots = Arrays.copyOf(freeSlots, freeSlots.length * 2);
            }
            freeSlots[freeCount++] = index;
        }

        void closeAll() {
            for (int index = values.length - 1; index > 0; index--) {
                Resource resource = values[index];
                if (resource != null) {
                    values[index] = null;
                    resource.close();
                }
            }
            freeCount = 0;
            nextSlot = 1;
        }

        private void ensureCapacity(int index) {
            if (index < values.length) {
                return;
            }
            int capacity = values.length;
            while (capacity <= index) {
                capacity *= 2;
            }
            values = Arrays.copyOf(values, capacity);
            generations = Arrays.copyOf(generations, capacity);
            freeSlots = Arrays.copyOf(freeSlots, capacity);
        }
    }

    private static final class FrameSlot implements AutoCloseable {
        private MemorySegment allocator = D3D12Ffm.NULL;
        private MemorySegment backBuffer = D3D12Ffm.NULL;
        private MemorySegment depth = D3D12Ffm.NULL;
        private MemorySegment srvHeap = D3D12Ffm.NULL;
        private MemorySegment samplerHeap = D3D12Ffm.NULL;
        private MemorySegment uniformBuffer = D3D12Ffm.NULL;
        private MemorySegment uniformMapped = D3D12Ffm.NULL;
        private long rtv;
        private long dsv;
        private final D3D12DescriptorTableCache descriptorTables = new D3D12DescriptorTableCache(
                FRAME_DESCRIPTOR_CAPACITY, FRAME_SAMPLER_DESCRIPTOR_CAPACITY);
        private long uniformCursor;
        private long fenceValue;

        @Override
        public void close() {
            if (!D3D12Ffm.isNull(uniformBuffer) && !D3D12Ffm.isNull(uniformMapped)) {
                D3D12Ffm.comUnmap(uniformBuffer, 0, D3D12Ffm.NULL);
            }
            D3D12Ffm.release(uniformBuffer);
            D3D12Ffm.release(samplerHeap);
            D3D12Ffm.release(srvHeap);
            D3D12Ffm.release(depth);
            D3D12Ffm.release(backBuffer);
            D3D12Ffm.release(allocator);
            uniformMapped = D3D12Ffm.NULL;
            uniformBuffer = D3D12Ffm.NULL;
            samplerHeap = D3D12Ffm.NULL;
            srvHeap = D3D12Ffm.NULL;
            depth = D3D12Ffm.NULL;
            backBuffer = D3D12Ffm.NULL;
            allocator = D3D12Ffm.NULL;
        }
    }

    private static final class RetiredResource {
        private final long fenceValue;
        private final Resource resource;

        private RetiredResource(long fenceValue, Resource resource) {
            this.fenceValue = fenceValue;
            this.resource = resource;
        }
    }

    private static final class BufferAllocation implements AutoCloseable {
        private int state;
        private MemorySegment resource = D3D12Ffm.NULL;
        private MemorySegment mapped = D3D12Ffm.NULL;
        private long lastFence;
        private boolean recording;

        @Override
        public void close() {
            if (!D3D12Ffm.isNull(resource) && !D3D12Ffm.isNull(mapped)) {
                D3D12Ffm.comUnmap(resource, 0, D3D12Ffm.NULL);
            }
            D3D12Ffm.release(resource);
            resource = D3D12Ffm.NULL;
            mapped = D3D12Ffm.NULL;
        }
    }

    private static final class Buffer implements Resource {
        private final D3D12FfmContext context;
        private final int size;
        private final int usage;
        private final Array<BufferAllocation> allocations = new Array<BufferAllocation>();
        private int current;

        private Buffer(D3D12FfmContext context, int size, int usage) {
            this.context = context;
            this.size = size;
            this.usage = usage;
        }

        @Override
        public void close() {
            for (int index = allocations.size() - 1; index >= 0; index--) {
                allocations.get(index).close();
            }
            allocations.clear();
        }
    }

    private static final class TextureAllocation implements AutoCloseable {
        private int uavIndex = INVALID_DESCRIPTOR;
        private long uav;
        private int[] mipRtvIndices;
        private long[] mipRtvs;
        private MemorySegment resource = D3D12Ffm.NULL;
        private int state = D3D12Ffm.D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE;
        private int srvIndex = INVALID_DESCRIPTOR;
        private int rtvIndex = INVALID_DESCRIPTOR;
        private long srv;
        private long rtv;
        private long lastFence;
        private boolean recording;

        @Override
        public void close() {
            D3D12Ffm.release(resource);
            resource = D3D12Ffm.NULL;
        }
    }

    private static final class Texture implements Resource {
        private int texelBytes() { return format == RGBA16_FLOAT_FORMAT ? 8 : 4; }
        private final D3D12FfmContext context;
        private final int width;
        private final int height;
        private final int format;
        private final int mipCount;
        private final int samples;
        private final int usage;
        private final Array<TextureAllocation> allocations = new Array<TextureAllocation>();
        private MemorySegment depth = D3D12Ffm.NULL;
        private int current;
        private int samplerIndex = INVALID_DESCRIPTOR;
        private int dsvIndex = INVALID_DESCRIPTOR;
        private long sampler;
        private long dsv;

        private Texture(D3D12FfmContext context, int width, int height, int format, int usage, int mipCount, int samples) {
            this.context = context;
            this.width = width;
            this.height = height;
            this.format = format;
            this.usage = usage;
            this.mipCount = mipCount;
            this.samples = samples;
        }

        @Override
        public void close() {
            for (int index = allocations.size() - 1; index >= 0; index--) {
                TextureAllocation allocation = allocations.get(index);
                if (allocation.uavIndex != INVALID_DESCRIPTOR) context.freeSrvs.add(allocation.uavIndex);
                if (allocation.srvIndex != INVALID_DESCRIPTOR) {
                    context.freeSrvs.add(allocation.srvIndex);
                }
                if (allocation.mipRtvIndices != null) for (int rtv : allocation.mipRtvIndices) {
                    if (rtv != INVALID_DESCRIPTOR) context.freeRtvs.add(rtv);
                }
                allocation.close();
            }
            allocations.clear();
            if (samplerIndex != INVALID_DESCRIPTOR) {
                context.freeSamplers.add(samplerIndex);
                samplerIndex = INVALID_DESCRIPTOR;
            }
            if (dsvIndex != INVALID_DESCRIPTOR) {
                context.freeDsvs.add(dsvIndex);
                dsvIndex = INVALID_DESCRIPTOR;
            }
            D3D12Ffm.release(depth);
            depth = D3D12Ffm.NULL;
        }
    }

    private static final class Shader implements Resource {
        private String vertexSource, fragmentSource, vertexEntry, fragmentEntry, label;
        private MemorySegment vertex = D3D12Ffm.NULL;
        private MemorySegment fragment = D3D12Ffm.NULL;

        @Override
        public void close() {
            D3D12Ffm.release(fragment);
            D3D12Ffm.release(vertex);
            fragment = D3D12Ffm.NULL;
            vertex = D3D12Ffm.NULL;
        }
    }

    static final class Pipeline implements Resource {
        private MemorySegment rootSignature = D3D12Ffm.NULL;
        private MemorySegment state = D3D12Ffm.NULL;
        private int topology = D3D12Ffm.D3D_PRIMITIVE_TOPOLOGY_TRIANGLELIST;
        private int[] strides = new int[0];
        private int sampledTextureCount;
        private int samplerCount;
        private int uniformRoot = -1;
        private int textureRoot = -1;
        private int samplerRoot = -1;

        @Override
        public void close() {
            D3D12Ffm.release(state);
            D3D12Ffm.release(rootSignature);
            state = D3D12Ffm.NULL;
            rootSignature = D3D12Ffm.NULL;
        }
    }
}
