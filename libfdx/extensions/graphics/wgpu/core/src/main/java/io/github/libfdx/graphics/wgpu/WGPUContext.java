package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.jParser.api.NativeObject;

import com.github.xpenatan.webgpu.WGPU;
import com.github.xpenatan.webgpu.WGPUAdapter;
import com.github.xpenatan.webgpu.WGPUBindGroup;
import com.github.xpenatan.webgpu.WGPUBindGroupDescriptor;
import com.github.xpenatan.webgpu.WGPUBindGroupEntry;
import com.github.xpenatan.webgpu.WGPUBindGroupLayout;
import com.github.xpenatan.webgpu.WGPUBuffer;
import com.github.xpenatan.webgpu.WGPUBufferDescriptor;
import com.github.xpenatan.webgpu.WGPUBufferMapCallback;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUCallbackMode;
import com.github.xpenatan.webgpu.WGPUChainedStruct;
import com.github.xpenatan.webgpu.WGPUCommandBuffer;
import com.github.xpenatan.webgpu.WGPUCommandBufferDescriptor;
import com.github.xpenatan.webgpu.WGPUCommandEncoder;
import com.github.xpenatan.webgpu.WGPUCommandEncoderDescriptor;
import com.github.xpenatan.webgpu.WGPUCompositeAlphaMode;
import com.github.xpenatan.webgpu.WGPUDevice;
import com.github.xpenatan.webgpu.WGPUDeviceDescriptor;
import com.github.xpenatan.webgpu.WGPUDeviceLostCallback;
import com.github.xpenatan.webgpu.WGPUDeviceLostReason;
import com.github.xpenatan.webgpu.WGPUErrorType;
import com.github.xpenatan.webgpu.WGPUExtent3D;
import com.github.xpenatan.webgpu.WGPUFeatureName;
import com.github.xpenatan.webgpu.WGPUInstance;
import com.github.xpenatan.webgpu.WGPULoadOp;
import com.github.xpenatan.webgpu.WGPUMapAsyncStatus;
import com.github.xpenatan.webgpu.WGPUMapMode;
import com.github.xpenatan.webgpu.WGPUPlatformType;
import com.github.xpenatan.webgpu.WGPUPowerPreference;
import com.github.xpenatan.webgpu.WGPUPresentMode;
import com.github.xpenatan.webgpu.WGPUQuerySet;
import com.github.xpenatan.webgpu.WGPUQueue;
import com.github.xpenatan.webgpu.WGPURenderPassColorAttachment;
import com.github.xpenatan.webgpu.WGPURenderPassDescriptor;
import com.github.xpenatan.webgpu.WGPURenderPassEncoder;
import com.github.xpenatan.webgpu.WGPURequestAdapterCallback;
import com.github.xpenatan.webgpu.WGPURequestAdapterOptions;
import com.github.xpenatan.webgpu.WGPURequestAdapterStatus;
import com.github.xpenatan.webgpu.WGPURequestDeviceCallback;
import com.github.xpenatan.webgpu.WGPURequestDeviceStatus;
import com.github.xpenatan.webgpu.WGPUStoreOp;
import com.github.xpenatan.webgpu.WGPUSurface;
import com.github.xpenatan.webgpu.WGPUSurfaceCapabilities;
import com.github.xpenatan.webgpu.WGPUSurfaceConfiguration;
import com.github.xpenatan.webgpu.WGPUSurfaceGetCurrentTextureStatus;
import com.github.xpenatan.webgpu.WGPUSurfaceTexture;
import com.github.xpenatan.webgpu.WGPUTexelCopyBufferInfo;
import com.github.xpenatan.webgpu.WGPUTexelCopyBufferLayout;
import com.github.xpenatan.webgpu.WGPUTexelCopyTextureInfo;
import com.github.xpenatan.webgpu.WGPUTexture;
import com.github.xpenatan.webgpu.WGPUTextureAspect;
import com.github.xpenatan.webgpu.WGPUTextureDescriptor;
import com.github.xpenatan.webgpu.WGPUTextureDimension;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.github.xpenatan.webgpu.WGPUTextureUsage;
import com.github.xpenatan.webgpu.WGPUTextureView;
import com.github.xpenatan.webgpu.WGPUTextureViewDescriptor;
import com.github.xpenatan.webgpu.WGPUTextureViewDimension;
import com.github.xpenatan.webgpu.WGPUUncapturedErrorCallback;
import com.github.xpenatan.webgpu.WGPUVectorRenderPassColorAttachment;
import com.github.xpenatan.webgpu.WGPUVectorBindGroupEntry;
import com.github.xpenatan.webgpu.WGPUVectorFeatureName;
import com.github.xpenatan.webgpu.WGPUVectorTextureFormat;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.TextureFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;
import io.github.libfdx.collections.Array;

/**
 * Represents a WGPU context.
 *
 * @author xpenatan
 */
public final class WGPUContext implements GraphicsContext, Disposable {
    private static final long INIT_TIMEOUT_NANOS = 10L * 1000L * 1000L * 1000L;
    private static final long READBACK_TIMEOUT_NANOS = 10L * 1000L * 1000L * 1000L;
    private static final int COPY_BYTES_PER_ROW_ALIGNMENT = 256;
    // Dawn's published webgpu.h extension value. jWebGPU exposes extension enums through CUSTOM.
    // https://dawn.googlesource.com/dawn/+/refs/heads/main/docs/dawn/features/implicit_device_synchronization.md
    private static final int DAWN_IMPLICIT_DEVICE_SYNCHRONIZATION = 0x00050004;
    static final WGPUTextureFormat DEPTH_FORMAT = WGPUTextureFormat.Depth32Float;

    private final WGPUConfiguration configuration;
    private final WGPUInstance instance;
    private WGPUSurface surface;
    private Object surfaceOwner;
    private NativeWindow pendingWindow;
    private final boolean surfaceCopySrc;
    private final boolean ownsDevice;
    private final WGPUResourceDomain resourceDomain;
    private final WGPUCreationErrors creationErrors;
    private WGPUPreparation preparation;
    private WGPUAdapter adapter;
    private WGPUDevice device;
    private WGPUQueue queue;
    private WGPUTextureFormat surfaceFormat;
    private WGPUCommandEncoder frameEncoder;
    private WGPUCommandBuffer frameCommandBuffer;
    private WGPUTexture frameTexture;
    private WGPUTextureView frameTextureView;
    private WGPUTexture offscreenColorTexture;
    private WGPUTextureView offscreenColorRenderTextureView;
    private WGPUTexture depthTexture;
    private WGPUTextureView depthTextureView;
    private WGPURenderPassEncoder clearPassEncoder;
    private final Array<OffscreenDepthResources> offscreenDepthResources =
            new Array<OffscreenDepthResources>();
    private final Array<WGPUTextureBindGroupResource> textureBindGroups =
            new Array<WGPUTextureBindGroupResource>();
    private final Array<WGPUBuffer> submittedBuffers = new Array<WGPUBuffer>();
    private final WGPURecordedResources recordedResources = new WGPURecordedResources();
    private WGPUUniformArena uniformArena;
    private WGPUGraphicsDevice graphicsDevice;
    private WGPUCommandEncoderHandle commandEncoder;
    private WGPUTextureViewHandle colorAttachment;
    private WGPUFrameBuffer frameBuffer;
    private WGPUGraphicsFrame currentFrame;
    private boolean surfaceConfigured;
    private boolean frameStarted;
    private boolean frameTextureAcquired;
    private boolean pendingResize;
    private boolean disposed;
    private boolean initializationStarted;
    private boolean ready;
    private InitState initState;
    private WGPURequestAdapterCallback adapterCallback;
    private WGPURequestDeviceCallback deviceCallback;
    private WGPUUncapturedErrorCallback errorCallback;
    private WGPUDeviceLostCallback deviceLostCallback;
    private Consumer<Runnable> deferDeviceLossRetirement;
    private volatile boolean deviceLossNotified;
    private boolean deviceLossRequested;
    private boolean nativeDeviceReleased;
    private boolean adapterCallbackComplete;
    private boolean deviceCallbackComplete;
    private int width;
    private int height;
    private int pendingResizeWidth;
    private int pendingResizeHeight;

    /**
     * Creates a WGPU context.
     *
     * @param configuration the configuration
     * @param instance the instance
     * @param surface the surface
     */
    public WGPUContext(WGPUConfiguration configuration, WGPUInstance instance, WGPUSurface surface) {
        this(configuration, instance, surface, null);
    }

    /**
     * Creates a WGPU context.
     *
     * @param configuration the configuration
     * @param instance the instance
     * @param surface the surface
     * @param surfaceOwner the surface owner
     */
    public WGPUContext(WGPUConfiguration configuration, WGPUInstance instance, WGPUSurface surface,
            Object surfaceOwner) {
        this(configuration, instance, surface, surfaceOwner, true);
    }

    /**
     * Creates a WGPU context.
     *
     * @param configuration the configuration
     * @param instance the instance
     * @param surface the surface
     * @param surfaceOwner the surface owner
     * @param surfaceCopySrc whether the surface may be configured for copy-source readback
     */
    WGPUContext(WGPUConfiguration configuration, WGPUInstance instance, WGPUSurface surface,
            Object surfaceOwner, boolean surfaceCopySrc) {
        this(configuration, instance, surface, surfaceOwner, surfaceCopySrc, null);
    }

    WGPUContext(WGPUConfiguration configuration, WGPUInstance instance, WGPUSurface surface,
            Object surfaceOwner, boolean surfaceCopySrc, WGPUContext sharedContext) {
        this(configuration, instance, surface, surfaceOwner, surfaceCopySrc, sharedContext, null);
    }

    WGPUContext(WGPUConfiguration configuration, WGPUInstance instance, NativeWindow window, boolean surfaceCopySrc) {
        this(configuration, instance, null, null, surfaceCopySrc, null, window);
    }

    private WGPUContext(WGPUConfiguration configuration, WGPUInstance instance, WGPUSurface surface,
            Object surfaceOwner, boolean surfaceCopySrc, WGPUContext sharedContext, NativeWindow pendingWindow) {
        if (configuration == null) {
            throw new FdxException("WGPUConfiguration cannot be null");
        }
        if (instance == null || !instance.isValid()) {
            throw new FdxException("WGPU instance is not valid");
        }
        if (surface == null && pendingWindow == null) {
            throw new FdxException("WGPU surface is not valid");
        }
        this.configuration = configuration;
        this.instance = instance;
        this.surface = surface;
        this.surfaceOwner = surfaceOwner;
        this.pendingWindow = pendingWindow;
        this.surfaceCopySrc = surfaceCopySrc;
        ownsDevice = sharedContext == null;
        resourceDomain = sharedContext != null ? sharedContext.resourceDomain : new WGPUResourceDomain();
        creationErrors = sharedContext != null ? sharedContext.creationErrors : new WGPUCreationErrors();
        if (sharedContext != null) {
            if (sharedContext.disposed || !sharedContext.ready) {
                throw new FdxException("The shared WGPU context is not ready");
            }
            adapter = sharedContext.adapter;
            device = sharedContext.device;
            queue = sharedContext.queue;
        } else {
            resourceDomain.setNativeRelease(this::releaseOwnedDevice);
        }
        resourceDomain.retainContext();
        resourceDomain.registerContext(this);
    }

    /**
     * Runs the initialize blocking step.
     */
    public void initializeBlocking() {
        requireNotDisposed("initialize");
        try {
            startInitialization();
            waitFor(initState);
            finishInitialization();
        } finally {
            releaseInitializationCallbacks();
        }
    }

    /**
     * Runs the initialize async step.
     */
    public void initializeAsync() {
        requireNotDisposed("initialize");
        startInitialization();
        finishInitializationIfReady();
    }

    void initializeShared() {
        requireNotDisposed("initialize");
        if (ownsDevice) {
            throw new FdxException("This WGPU context does not share a device");
        }
        if (ready) {
            return;
        }
        initializationStarted = true;
        finishInitialization();
    }

    private void startInitialization() {
        if (initializationStarted) {
            return;
        }
        initializationStarted = true;
        initState = new InitState();
        debugInit("adapter-request");
        WGPURequestAdapterOptions options = WGPURequestAdapterOptions.obtain();
        options.setPowerPreference(WGPUPowerPreference.HighPerformance);
        options.setBackendType(configuration.backend().toNative());
        if (surface != null) {
            options.setCompatibleSurface(surface);
        }

        adapterCallback = new WGPURequestAdapterCallback() {
            @Override
            protected void onCallback(WGPURequestAdapterStatus status, WGPUAdapter selectedAdapter, String message) {
                adapterCallbackComplete = true;
                debugInit("adapter-callback " + status + (message != null ? ": " + message : ""));
                if (status != WGPURequestAdapterStatus.Success) {
                    selectedAdapter.dispose();
                    initState.fail("Could not request WGPU adapter: " + message);
                    return;
                }
                adapter = selectedAdapter;
                requestDevice(initState, selectedAdapter);
            }
        };
        instance.requestAdapter(options, WGPUCallbackMode.AllowProcessEvents, adapterCallback);
    }

    private void finishInitializationIfReady() {
        releaseInitializationCallbacks();
        if (ready || initState == null || !initState.complete) {
            return;
        }
        finishInitialization();
    }

    private void finishInitialization() {
        checkDeviceLoss();
        if (ready) {
            return;
        }
        if (initState != null && initState.error != null) {
            throw new FdxException(initState.error);
        }
        if (pendingWindow != null) {
            WGPUNativeSurface.SurfaceHandle created = WGPUNativeSurface.create(instance, pendingWindow);
            surface = created.surface();
            surfaceOwner = created.owner();
            pendingWindow = null;
        }
        selectSurfaceFormat();
        frameEncoder = new WGPUCommandEncoder();
        frameCommandBuffer = new WGPUCommandBuffer();
        frameTexture = new WGPUTexture();
        frameTextureView = new WGPUTextureView();
        clearPassEncoder = new WGPURenderPassEncoder();
        if (usesOffscreenFrame()) {
            offscreenColorRenderTextureView = new WGPUTextureView();
        }
        graphicsDevice = new WGPUGraphicsDevice(this);
        uniformArena = new WGPUUniformArena(this);
        commandEncoder = new WGPUCommandEncoderHandle(this);
        colorAttachment = new WGPUTextureViewHandle(this, activeColorAttachmentView(),
                WGPUTextureFormats.toCommon(surfaceFormat));
        frameBuffer = new WGPUFrameBuffer(this, colorAttachment);
        currentFrame = new WGPUGraphicsFrame(this, commandEncoder, frameBuffer, colorAttachment);
        ready = true;
        debugInit("ready");
        applyPendingResize();
    }

    private void requestDevice(final InitState state, WGPUAdapter selectedAdapter) {
        WGPUDeviceDescriptor descriptor = WGPUDeviceDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel("libfdx WGPU Device");
        descriptor.getDefaultQueue().setLabel("libfdx WGPU Queue");

        deviceCallback = new WGPURequestDeviceCallback() {
            @Override
            protected void onCallback(WGPURequestDeviceStatus status, WGPUDevice selectedDevice, String message) {
                deviceCallbackComplete = true;
                debugInit("device-callback " + status + (message != null ? ": " + message : ""));
                if (status != WGPURequestDeviceStatus.Success) {
                    selectedDevice.dispose();
                    state.fail("Could not request WGPU device: " + message);
                    return;
                }
                device = selectedDevice;
                queue = selectedDevice.getQueue();
                state.complete = true;
            }
        };
        errorCallback = new WGPUUncapturedErrorCallback() {
            @Override
            protected void onCallback(WGPUErrorType errorType, String message) {
                boolean recoverable = errorType == WGPUErrorType.Validation || errorType == WGPUErrorType.OutOfMemory;
                if (!recoverable || !creationErrors.capture(errorType + ": " + message)) {
                    state.fail("Uncaptured WGPU error: " + errorType + ": " + message);
                }
            }
        };
        deviceLostCallback = new WGPUDeviceLostCallback() {
            @Override protected void onCallback(WGPUDeviceLostReason reason, String message) {
                resourceDomain.deviceLost("WGPU device lost (" + reason + "): " + message);
                deviceLossNotified = true;
                // Browser notifications can arrive after destroy/release and must return before disposal.
                if (deferDeviceLossRetirement != null)
                    deferDeviceLossRetirement.accept(WGPUContext.this::retireDeviceLossCallback);
            }
        };
        WGPUVectorFeatureName features = null;
        try {
            if (configuration.loaderBackend() == WGPULoaderBackend.DAWN) {
                features = new WGPUVectorFeatureName();
                // CUSTOM is mutable shared storage. Copy the value into the native vector while
                // locked, then restore it before invoking any asynchronous device callback.
                synchronized (WGPUFeatureName.CUSTOM) {
                    int previous = WGPUFeatureName.CUSTOM.getValue();
                    try {
                        WGPUFeatureName feature = WGPUFeatureName.CUSTOM.setValue(DAWN_IMPLICIT_DEVICE_SYNCHRONIZATION);
                        if (!selectedAdapter.hasFeature(feature))
                            throw new FdxException("Dawn requires implicit device synchronization for concurrent preparation");
                        features.push_back(feature);
                    } finally { WGPUFeatureName.CUSTOM.setValue(previous); }
                }
            }
            descriptor.setRequiredFeatures(features != null ? features : WGPUVectorFeatureName.NULL);
            descriptor.setDeviceLostCallback(deferDeviceLossRetirement != null
                    ? WGPUCallbackMode.AllowSpontaneous : WGPUCallbackMode.AllowProcessEvents, deviceLostCallback);
            deviceLossRequested = true;
            selectedAdapter.requestDevice(descriptor, WGPUCallbackMode.AllowProcessEvents, deviceCallback, errorCallback);
        } finally {
            // requestDevice consumes its descriptor synchronously; the callback has separate ownership.
            descriptor.setRequiredFeatures(WGPUVectorFeatureName.NULL);
            descriptor.setDeviceLostCallback(WGPUCallbackMode.AllowProcessEvents, WGPUDeviceLostCallback.NULL);
            if (features != null) features.dispose();
        }
    }

    // Only called after the request/processEvents call has returned, never from inside a callback.
    private void releaseInitializationCallbacks() {
        if(adapterCallbackComplete && adapterCallback != null) {
            adapterCallback.dispose();
            adapterCallback = null;
        }
        if(deviceCallbackComplete && deviceCallback != null) {
            deviceCallback.dispose();
            deviceCallback = null;
        }
    }

    private void waitFor(InitState state) {
        long deadline = System.nanoTime() + INIT_TIMEOUT_NANOS;
        while (!state.complete && state.error == null) {
            processNativeEvents();
            if (System.nanoTime() > deadline) {
                throw new FdxException("Timed out while initializing WGPU");
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FdxException("Interrupted while initializing WGPU", e);
            }
        }
        if (state.error != null) {
            throw new FdxException(state.error);
        }
    }

    private static void debugInit(String message) {
        if (Boolean.getBoolean("libfdx.wgpu.debugInit")) {
            System.out.println("[libfdx-wgpu] " + message);
        }
    }

    private void selectSurfaceFormat() {
        WGPUSurfaceCapabilities capabilities = WGPUSurfaceCapabilities.obtain();
        surface.getCapabilities(adapter, capabilities);
        WGPUVectorTextureFormat formats = capabilities.getFormats();
        if (formats.size() == 0) {
            throw new FdxException("Adapter does not expose any WGPU surface formats");
        }
        surfaceFormat = preferredSurfaceFormat(formats);
        if (Boolean.getBoolean("libfdx.wgpu.debugSurfaceFormat")) {
            System.out.println("[libfdx-wgpu] selected surface format: " + surfaceFormat);
        }
    }

    private WGPUTextureFormat preferredSurfaceFormat(WGPUVectorTextureFormat formats) {
        WGPUTextureFormat fallback = formats.get(0);
        WGPUTextureFormat[] preferred = {
                WGPUTextureFormat.RGBA8Unorm,
                WGPUTextureFormat.BGRA8Unorm,
                WGPUTextureFormat.BGRA8UnormSrgb,
                WGPUTextureFormat.RGBA8UnormSrgb
        };
        for (int preferredIndex = 0; preferredIndex < preferred.length; preferredIndex++) {
            WGPUTextureFormat candidate = preferred[preferredIndex];
            for (int i = 0; i < formats.size(); i++) {
                if (formats.get(i) == candidate) {
                    return candidate;
                }
            }
        }
        return fallback;
    }

    /**
     * Handles a size change.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    public void resize(int width, int height) {
        if (disposed || width <= 0 || height <= 0) {
            return;
        }
        if (!ready) {
            pendingResize = true;
            pendingResizeWidth = width;
            pendingResizeHeight = height;
            return;
        }
        if (frameStarted) {
            pendingResize = true;
            pendingResizeWidth = width;
            pendingResizeHeight = height;
            return;
        }
        configureSurface(width, height);
    }

    private void configureSurface(int width, int height) {
        WGPUCleanup cleanup = new WGPUCleanup();
        if (surfaceConfigured) {
            surfaceConfigured = false;
            cleanup.run(surface::unconfigure);
        }
        cleanup.run(this::releaseDepthResources);
        cleanup.run(this::releaseOffscreenDepthResources);
        cleanup.run(this::releaseOffscreenColorResources);
        cleanup.throwIfFailed();

        WGPUSurfaceConfiguration surfaceConfiguration = WGPUSurfaceConfiguration.obtain();
        surfaceConfiguration.setWidth(width);
        surfaceConfiguration.setHeight(height);
        surfaceConfiguration.setFormat(surfaceFormat);
        surfaceConfiguration.setViewFormats(WGPUVectorTextureFormat.NULL);
        WGPUTextureUsage usage = WGPUTextureUsage.RenderAttachment;
        if (surfaceCopySrc) {
            usage = usage.or(WGPUTextureUsage.CopySrc);
        }
        surfaceConfiguration.setUsage(usage);
        surfaceConfiguration.setDevice(device);
        surfaceConfiguration.setPresentMode(configuration.vSync() ? WGPUPresentMode.Fifo : WGPUPresentMode.Immediate);
        surfaceConfiguration.setAlphaMode(WGPUCompositeAlphaMode.Auto);
        boolean configured = false;
        try {
            surface.configure(surfaceConfiguration);
            configured = true;
            if (usesOffscreenFrame()) {
                createOffscreenColorResources(width, height);
            }
            createDepthResources(width, height);
            this.width = width;
            this.height = height;
            surfaceConfigured = true;
        }
        catch (RuntimeException | Error failure) {
            suppressRollback(failure, this::releaseDepthResources);
            suppressRollback(failure, this::releaseOffscreenColorResources);
            if (configured) {
                suppressRollback(failure, surface::unconfigure);
            }
            this.width = 0;
            this.height = 0;
            throw failure;
        }
    }

    private void createOffscreenColorResources(int width, int height) {
        WGPUTextureDescriptor textureDescriptor = WGPUTextureDescriptor.obtain();
        textureDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        textureDescriptor.setLabel("libfdx offscreen color texture");
        textureDescriptor.setUsage(WGPUTextureUsage.RenderAttachment
                .or(WGPUTextureUsage.CopySrc));
        textureDescriptor.setDimension(WGPUTextureDimension._2D);
        textureDescriptor.getSize().setWidth(width);
        textureDescriptor.getSize().setHeight(height);
        textureDescriptor.getSize().setDepthOrArrayLayers(1);
        textureDescriptor.setFormat(surfaceFormat);
        textureDescriptor.setMipLevelCount(1);
        textureDescriptor.setSampleCount(1);
        textureDescriptor.setViewFormats(WGPUVectorTextureFormat.NULL);

        WGPUTexture texture = new WGPUTexture();
        try {
            device.createTexture(textureDescriptor, texture);

        WGPUTextureViewDescriptor viewDescriptor = WGPUTextureViewDescriptor.obtain();
        viewDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        viewDescriptor.setLabel("libfdx offscreen color render texture view");
        viewDescriptor.setFormat(surfaceFormat);
        viewDescriptor.setDimension(WGPUTextureViewDimension._2D);
        viewDescriptor.setBaseMipLevel(0);
        viewDescriptor.setMipLevelCount(1);
        viewDescriptor.setBaseArrayLayer(0);
        viewDescriptor.setArrayLayerCount(1);
        viewDescriptor.setAspect(WGPUTextureAspect.All);
        viewDescriptor.setUsage(WGPUTextureUsage.RenderAttachment);

            texture.createView(viewDescriptor, offscreenColorRenderTextureView);
            offscreenColorTexture = texture;
        }
        catch (RuntimeException | Error failure) {
            rollbackTexture(texture, offscreenColorRenderTextureView, false, failure);
            throw failure;
        }
    }

    private void createDepthResources(int width, int height) {
        OffscreenDepthResources resources = createDepthResources(
                "libfdx depth texture", width, height, 1);
        depthTexture = resources.texture;
        depthTextureView = resources.view;
    }

    private void releaseDepthResources() {
        WGPUTextureView view = depthTextureView;
        WGPUTexture texture = depthTexture;
        depthTextureView = null;
        depthTexture = null;
        WGPUCleanup cleanup = new WGPUCleanup();
        if (view != null) {
            cleanup.run(() -> {
                if (view.isValid()) {
                    view.release();
                }
            });
            cleanup.run(view::dispose);
        }
        if (texture != null) {
            cleanup.run(() -> {
                if (texture.isValid()) {
                    texture.destroy();
                }
            });
            cleanup.run(() -> {
                if (texture.isValid()) {
                    texture.release();
                }
            });
            cleanup.run(texture::dispose);
        }
        cleanup.throwIfFailed();
    }

    private OffscreenDepthResources createDepthResources(
            String label, int width, int height, int sampleCount) {
        WGPUTextureDescriptor textureDescriptor = WGPUTextureDescriptor.obtain();
        textureDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        textureDescriptor.setLabel(label);
        textureDescriptor.setUsage(WGPUTextureUsage.RenderAttachment);
        textureDescriptor.setDimension(WGPUTextureDimension._2D);
        textureDescriptor.getSize().setWidth(width);
        textureDescriptor.getSize().setHeight(height);
        textureDescriptor.getSize().setDepthOrArrayLayers(1);
        textureDescriptor.setFormat(DEPTH_FORMAT);
        textureDescriptor.setMipLevelCount(1);
        textureDescriptor.setSampleCount(sampleCount);
        textureDescriptor.setViewFormats(WGPUVectorTextureFormat.NULL);

        WGPUTexture texture = new WGPUTexture();
        WGPUTextureView view = new WGPUTextureView();
        try {
            device.createTexture(textureDescriptor, texture);

        WGPUTextureViewDescriptor viewDescriptor = WGPUTextureViewDescriptor.obtain();
        viewDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        viewDescriptor.setLabel(label + " view");
        viewDescriptor.setFormat(DEPTH_FORMAT);
        viewDescriptor.setDimension(WGPUTextureViewDimension._2D);
        viewDescriptor.setBaseMipLevel(0);
        viewDescriptor.setMipLevelCount(1);
        viewDescriptor.setBaseArrayLayer(0);
        viewDescriptor.setArrayLayerCount(1);
        viewDescriptor.setAspect(WGPUTextureAspect.DepthOnly);
        viewDescriptor.setUsage(WGPUTextureUsage.RenderAttachment);

            texture.createView(viewDescriptor, view);
            return new OffscreenDepthResources(width, height, sampleCount, texture, view);
        }
        catch (RuntimeException | Error failure) {
            rollbackTexture(texture, view, true, failure);
            throw failure;
        }
    }

    private void releaseOffscreenDepthResources() {
        WGPUCleanup cleanup = new WGPUCleanup();
        for (int i = 0; i < offscreenDepthResources.size(); i++) {
            cleanup.run(offscreenDepthResources.get(i)::dispose);
        }
        offscreenDepthResources.clear();
        cleanup.throwIfFailed();
    }

    private void releaseOffscreenColorResources() {
        WGPUTexture texture = offscreenColorTexture;
        offscreenColorTexture = null;
        WGPUCleanup cleanup = new WGPUCleanup();
        if (offscreenColorRenderTextureView != null) {
            cleanup.run(() -> {
                if (offscreenColorRenderTextureView.isValid()) {
                    offscreenColorRenderTextureView.release();
                }
            });
        }
        if (texture != null) {
            cleanup.run(() -> {
                if (texture.isValid()) {
                    texture.destroy();
                }
            });
            cleanup.run(() -> {
                if (texture.isValid()) {
                    texture.release();
                }
            });
            cleanup.run(texture::dispose);
        }
        cleanup.throwIfFailed();
    }

    /**
     * Returns the begin frame.
     *
     * @return true if begin frame succeeds or is active; false otherwise
     */
    public boolean beginFrame() {
        checkDeviceLoss();
        if (disposed || !ready || !surfaceConfigured || width <= 0 || height <= 0) {
            return false;
        }
        if (frameStarted) {
            throw new FdxException("WGPU frame is already started");
        }
        commandEncoder.beginFrame();

        // Browser canvas textures expire when execution yields to the event loop.
        if (WGPU.getPlatformType() != WGPUPlatformType.WGPU_Web && !acquireFrameTexture()) {
            return false;
        }

        WGPUCommandEncoderDescriptor encoderDescriptor = WGPUCommandEncoderDescriptor.obtain();
        encoderDescriptor.setLabel("libfdx frame command encoder");
        device.createCommandEncoder(encoderDescriptor, frameEncoder);

        uniformArena.beginFrame();
        frameStarted = true;
        return true;
    }

    private boolean acquireFrameTexture() {
        if (!usesOffscreenFrame()) {
            WGPUSurfaceTexture surfaceTexture = WGPUSurfaceTexture.obtain();
            surface.getCurrentTexture(surfaceTexture);
            WGPUSurfaceGetCurrentTextureStatus status = surfaceTexture.getStatus();
            if (status == WGPUSurfaceGetCurrentTextureStatus.Timeout) {
                debugSurfaceAcquire(status, "retrying on the next frame");
                return false;
            }
            if (status == WGPUSurfaceGetCurrentTextureStatus.Outdated
                    || status == WGPUSurfaceGetCurrentTextureStatus.Lost) {
                debugSurfaceAcquire(status, "reconfiguring the surface");
                reconfigureSurfaceAfterAcquireFailure();
                return false;
            }
            if (status != WGPUSurfaceGetCurrentTextureStatus.SuccessOptimal
                    && status != WGPUSurfaceGetCurrentTextureStatus.SuccessSuboptimal) {
                throw new FdxException("Unable to acquire WGPU surface texture: "
                        + status + " (" + width + "x" + height + ")");
            }

            surfaceTexture.getTexture(frameTexture);
            if (!frameTexture.isValid()) {
                throw new FdxException("WGPU surface acquisition " + status
                        + " returned an invalid texture (" + width + "x" + height + ")");
            }
            frameTextureAcquired = true;

            if (status == WGPUSurfaceGetCurrentTextureStatus.SuccessSuboptimal
                    && !pendingResize) {
                pendingResize = true;
                pendingResizeWidth = width;
                pendingResizeHeight = height;
            }

            WGPUTextureViewDescriptor viewDescriptor = WGPUTextureViewDescriptor.obtain();
            viewDescriptor.setLabel("libfdx surface texture view");
            viewDescriptor.setFormat(frameTexture.getFormat());
            viewDescriptor.setDimension(WGPUTextureViewDimension._2D);
            viewDescriptor.setBaseMipLevel(0);
            viewDescriptor.setMipLevelCount(1);
            viewDescriptor.setBaseArrayLayer(0);
            viewDescriptor.setArrayLayerCount(1);
            viewDescriptor.setAspect(WGPUTextureAspect.All);
            frameTexture.createView(viewDescriptor, frameTextureView);
        }

        return true;
    }

    void ensureFrameTexture() {
        checkDeviceLoss();
        if (frameStarted && !frameTextureAcquired && !usesOffscreenFrame() && !acquireFrameTexture()) {
            throw new FdxException("WGPU surface texture is unavailable for the active frame");
        }
    }

    /**
     * Returns the device.
     *
     * @return the device
     */
    @Override
    public GraphicsDevice device() {
        return graphicsDevice;
    }

    /**
     * Returns the surface format.
     *
     * @return the surface format
     */
    @Override
    public TextureFormat surfaceFormat() {
        return WGPUTextureFormats.toCommon(surfaceFormat);
    }

    /**
     * Returns the current frame.
     *
     * @return the current frame
     */
    @Override
    public GraphicsFrame currentFrame() {
        if (!frameStarted) {
            throw new FdxException("No WGPU frame is active");
        }
        return currentFrame;
    }

    /**
     * Runs the clear step.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    @Override
    public void clear(float red, float green, float blue, float alpha) {
        if (!frameStarted) {
            throw new FdxException("Cannot clear before beginFrame()");
        }

        WGPURenderPassDescriptor passDescriptor = WGPURenderPassDescriptor.obtain();
        passDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        passDescriptor.setLabel("libfdx clear pass");
        passDescriptor.setOcclusionQuerySet(WGPUQuerySet.NULL);

        WGPURenderPassColorAttachment colorAttachment = WGPURenderPassColorAttachment.obtain();
        colorAttachment.setNextInChain(WGPUChainedStruct.NULL);
        colorAttachment.setView(activeColorAttachmentView());
        colorAttachment.setResolveTarget(WGPUTextureView.NULL);
        colorAttachment.setLoadOp(WGPULoadOp.Clear);
        colorAttachment.setStoreOp(WGPUStoreOp.Store);
        colorAttachment.setDepthSlice(-1);
        colorAttachment.getClearValue().setR(red);
        colorAttachment.getClearValue().setG(green);
        colorAttachment.getClearValue().setB(blue);
        colorAttachment.getClearValue().setA(alpha);

        WGPUVectorRenderPassColorAttachment colorAttachments = WGPUVectorRenderPassColorAttachment.obtain();
        colorAttachments.push_back(colorAttachment);
        passDescriptor.setColorAttachments(colorAttachments);

        commandEncoder.ensureNoOpenPass();
        frameEncoder.beginRenderPass(passDescriptor, clearPassEncoder);
        try {
            clearPassEncoder.end();
        } finally {
            clearPassEncoder.release();
        }
    }

    /**
     * Ends frame.
     */
    public void endFrame() {
        checkDeviceLoss();
        if (!frameStarted) {
            return;
        }
        submitCurrentFrame();
    }

    /**
     * Returns the read pixels RGBA8.
     *
     * @return the read pixels RGBA8
     * @throws UnsupportedOperationException when neither surface copies nor offscreen readback are enabled;
     *         the active frame remains usable
     */
    public ByteBuffer readPixelsRgba8() {
        if (!frameStarted) {
            throw new FdxException("Cannot read pixels before beginFrame()");
        }
        if (!supportsReadPixelsRgba8()) {
            throw new UnsupportedOperationException("WGPU surface readback requires CopySrc usage; "
                    + "configure offscreenReadback(true) before attachment for offscreen capture");
        }
        WGPUTexture sourceTexture = readbackTexture();

        int rowBytes = width * 4;
        int bytesPerRow = align(rowBytes, COPY_BYTES_PER_ROW_ALIGNMENT);
        int readbackSize = bytesPerRow * height;
        WGPUBuffer readbackBuffer = createReadbackBuffer(readbackSize);
        try {
            copyTextureToBuffer(sourceTexture, readbackBuffer, bytesPerRow);
            submitCurrentFrame();
            ByteBuffer paddedPixels = mapReadbackBuffer(readbackBuffer, readbackSize);
            return packRows(paddedPixels, rowBytes, bytesPerRow, height, isBgraSurfaceFormat());
        } finally {
            if (readbackBuffer.isValid()) {
                readbackBuffer.destroy();
                readbackBuffer.release();
            }
            readbackBuffer.dispose();
        }
    }

    private void submitCurrentFrame() {
        checkDeviceLoss();
        commandEncoder.ensurePassesEnded();
        frameStarted = false;
        Throwable firstFailure = null;
        boolean commandBufferReady = false;
        WGPUCommandBufferDescriptor commandBufferDescriptor = WGPUCommandBufferDescriptor.obtain();
        commandBufferDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        commandBufferDescriptor.setLabel("libfdx frame command buffer");
        try {
            frameEncoder.finish(commandBufferDescriptor, frameCommandBuffer);
            commandBufferReady = true;
        } catch (RuntimeException | Error failure) {
            firstFailure = WGPUCleanup.merge(firstFailure, failure);
        }
        try {
            if (frameEncoder.isValid()) {
                frameEncoder.release();
            }
        } catch (RuntimeException | Error failure) {
            firstFailure = WGPUCleanup.merge(firstFailure, failure);
        }

        boolean submitted = false;
        if (commandBufferReady) {
            try {
                queue.submit(frameCommandBuffer);
                submitted = true;
            } catch (RuntimeException | Error failure) {
                firstFailure = WGPUCleanup.merge(firstFailure, failure);
            }
        }
        try {
            releaseSubmittedResources();
        } catch (RuntimeException | Error failure) {
            firstFailure = WGPUCleanup.merge(firstFailure, failure);
        }
        try {
            recordedResources.releaseAll();
        } catch (RuntimeException | Error failure) {
            firstFailure = WGPUCleanup.merge(firstFailure, failure);
        }
        try {
            if (frameCommandBuffer.isValid()) {
                frameCommandBuffer.release();
            }
        } catch (RuntimeException | Error failure) {
            firstFailure = WGPUCleanup.merge(firstFailure, failure);
        }

        if (frameTextureAcquired) {
            try {
                if (frameTextureView.isValid()) {
                    frameTextureView.release();
                }
            } catch (RuntimeException | Error failure) {
                firstFailure = WGPUCleanup.merge(firstFailure, failure);
            }
            if (submitted && WGPU.getPlatformType() != WGPUPlatformType.WGPU_Web) {
                try {
                    surface.present();
                } catch (RuntimeException | Error failure) {
                    firstFailure = WGPUCleanup.merge(firstFailure, failure);
                }
            }
            try {
                if (frameTexture.isValid()) {
                    frameTexture.release();
                }
            } catch (RuntimeException | Error failure) {
                firstFailure = WGPUCleanup.merge(firstFailure, failure);
            }
            frameTextureAcquired = false;
        }

        if (submitted) {
            try {
                applyPendingResize();
            } catch (RuntimeException | Error failure) {
                firstFailure = WGPUCleanup.merge(firstFailure, failure);
            }
        }
        WGPUCleanup.rethrow(firstFailure);
    }

    private WGPUBuffer createReadbackBuffer(int size) {
        WGPUBufferDescriptor descriptor = WGPUBufferDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel("libfdx surface readback");
        descriptor.setSize(size);
        descriptor.setUsage(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.MapRead));
        descriptor.setMappedAtCreation(false);

        WGPUBuffer buffer = new WGPUBuffer();
        try {
            device.createBuffer(descriptor, buffer);
            buffer.native_setAddress(buffer.native_getAddressLong());
            return buffer;
        }
        catch (RuntimeException | Error failure) {
            suppressRollback(failure,
                    () -> new WGPUBufferAllocation(resourceDomain, buffer).retire());
            throw failure;
        }
    }

    private void copyTextureToBuffer(WGPUTexture sourceTexture, WGPUBuffer readbackBuffer, int bytesPerRow) {
        WGPUTexelCopyTextureInfo source = WGPUTexelCopyTextureInfo.obtain();
        source.setTexture(sourceTexture);
        source.setMipLevel(0);
        source.getOrigin().setX(0);
        source.getOrigin().setY(0);
        source.getOrigin().setZ(0);
        source.setAspect(WGPUTextureAspect.All);

        WGPUTexelCopyBufferInfo destination = WGPUTexelCopyBufferInfo.obtain();
        destination.setBuffer(readbackBuffer);
        WGPUTexelCopyBufferLayout layout = destination.getLayout();
        layout.setOffset(0);
        layout.setBytesPerRow(bytesPerRow);
        layout.setRowsPerImage(height);

        WGPUExtent3D copySize = WGPUExtent3D.obtain();
        copySize.setWidth(width);
        copySize.setHeight(height);
        copySize.setDepthOrArrayLayers(1);
        frameEncoder.copyTextureToBuffer(source, destination, copySize);
    }

    private ByteBuffer mapReadbackBuffer(WGPUBuffer readbackBuffer, int readbackSize) {
        return mapReadbackBuffer(readbackBuffer, 0, readbackSize);
    }

    ByteBuffer mapReadbackBuffer(WGPUBuffer readbackBuffer, int offset, int readbackSize) {
        if (frameTextureAcquired && WGPU.getPlatformType() == WGPUPlatformType.WGPU_Web) {
            throw new FdxException("Browser WGPU buffer readback must occur before using the frame's surface attachment");
        }
        final MapState state = new MapState();
        readbackBuffer.mapAsync(WGPUMapMode.Read, offset, readbackSize, WGPUCallbackMode.AllowProcessEvents,
                new WGPUBufferMapCallback() {
                    @Override
                    protected void onCallback(WGPUMapAsyncStatus status, String message) {
                        state.status = status;
                        state.message = message;
                        state.complete = true;
                    }
                });
        waitForMap(state);
        if (state.status != WGPUMapAsyncStatus.Success) {
            String message = state.message != null && state.message.length() > 0 ? ": " + state.message : "";
            throw new FdxException("Could not map WGPU readback buffer: " + state.status + message);
        }
        ByteBuffer pixels = ByteBuffer.allocateDirect(readbackSize).order(ByteOrder.nativeOrder());
        readbackBuffer.getConstMappedRange(offset, readbackSize, pixels);
        readbackBuffer.unmap();
        pixels.position(0);
        pixels.limit(readbackSize);
        return pixels;
    }

    private void waitForMap(MapState state) {
        long deadline = System.nanoTime() + READBACK_TIMEOUT_NANOS;
        while (!state.complete) {
            processNativeEvents();
            if (System.nanoTime() > deadline) {
                throw new FdxException("Timed out while reading WGPU framebuffer");
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FdxException("Interrupted while reading WGPU framebuffer", e);
            }
        }
    }

    private ByteBuffer packRows(ByteBuffer paddedPixels, int rowBytes, int bytesPerRow, int rows,
            boolean bgraToRgba) {
        ByteBuffer packed = ByteBuffer.allocateDirect(rowBytes * rows).order(ByteOrder.nativeOrder());
        // Keep raw row order aligned with GL backend readback so shared test capture logic can stay uniform.
        for (int y = rows - 1; y >= 0; y--) {
            ByteBuffer source = paddedPixels.duplicate();
            int sourceOffset = y * bytesPerRow;
            source.position(sourceOffset);
            source.limit(sourceOffset + rowBytes);
            if (!bgraToRgba) {
                packed.put(source);
            } else {
                while (source.hasRemaining()) {
                    byte blue = source.get();
                    byte green = source.get();
                    byte red = source.get();
                    byte alpha = source.get();
                    packed.put(red);
                    packed.put(green);
                    packed.put(blue);
                    packed.put(alpha);
                }
            }
        }
        packed.flip();
        return packed;
    }

    private boolean isBgraSurfaceFormat() {
        return surfaceFormat == WGPUTextureFormat.BGRA8Unorm
                || surfaceFormat == WGPUTextureFormat.BGRA8UnormSrgb;
    }

    private int align(int value, int alignment) {
        return ((value + alignment - 1) / alignment) * alignment;
    }

    /**
     * Returns whether frame started is enabled or true.
     *
     * @return true if frame started is enabled or true; false otherwise
     */
    public boolean isFrameStarted() {
        return frameStarted;
    }

    /**
     * Returns the frame encoder.
     *
     * @return the frame encoder
     */
    public WGPUCommandEncoder frameEncoder() {
        requireDeviceUsable("record frame commands");
        return frameEncoder;
    }

    /**
     * Returns the frame texture view.
     *
     * @return the frame texture view
     */
    public WGPUTextureView frameTextureView() {
        ensureFrameTexture();
        return frameTextureView;
    }

    WGPUTextureView depthTextureView() {
        return depthTextureView;
    }

    WGPUTextureView depthTextureView(int attachmentWidth, int attachmentHeight) {
        return depthTextureView(attachmentWidth, attachmentHeight, 1);
    }

    WGPUTextureView depthTextureView(int attachmentWidth, int attachmentHeight,
            int sampleCount) {
        if (attachmentWidth <= 0 || attachmentHeight <= 0
                || (attachmentWidth == width && attachmentHeight == height
                && sampleCount == 1)) {
            return depthTextureView;
        }
        for (int i = 0; i < offscreenDepthResources.size(); i++) {
            OffscreenDepthResources resources = offscreenDepthResources.get(i);
            if (resources.width == attachmentWidth
                    && resources.height == attachmentHeight
                    && resources.sampleCount == sampleCount) {
                return resources.view;
            }
        }
        OffscreenDepthResources resources = createDepthResources("libfdx offscreen depth texture",
                attachmentWidth, attachmentHeight, sampleCount);
        offscreenDepthResources.add(resources);
        return resources.view;
    }

    WGPUTextureBindGroupResource textureBindGroup(WGPURenderPipelineHandle pipeline,
            WGPUTextureAllocation[] allocations, int count,
            WGPUSamplerAllocation[] samplerAllocations,
            WGPUTextureAllocation[] samplerTextureAllocations, int samplerCount) {
        for (int i = 0; i < textureBindGroups.size(); i++) {
            WGPUTextureBindGroupResource existing = textureBindGroups.get(i);
            if (existing.matches(pipeline, allocations, count,
                    samplerAllocations, samplerTextureAllocations, samplerCount)) {
                markRecordedResource(existing);
                return existing;
            }
        }

        WGPUVectorBindGroupEntry entries = WGPUVectorBindGroupEntry.obtain();
        for (int slot = 0; slot < count; slot++) {
            WGPUTextureAllocation allocation = allocations[slot];
            WGPUBindGroupEntry textureEntry = WGPUBindGroupEntry.obtain();
            textureEntry.setNextInChain(WGPUChainedStruct.NULL);
            textureEntry.setBinding(pipeline.resourceBindings().reflected()
                    ? pipeline.resourceBindings().texture(slot).binding() : slot * 2);
            textureEntry.setTextureView(allocation.nativeView());
            entries.push_back(textureEntry);

        }
        for (int slot = 0; slot < samplerCount; slot++) {
            WGPUBindGroupEntry samplerEntry = WGPUBindGroupEntry.obtain();
            samplerEntry.setNextInChain(WGPUChainedStruct.NULL);
            samplerEntry.setBinding(pipeline.resourceBindings().reflected()
                    ? pipeline.resourceBindings().sampler(slot).binding() : slot * 2 + 1);
            WGPUSamplerAllocation samplerAllocation = samplerAllocations[slot];
            WGPUTextureAllocation samplerTextureAllocation =
                    samplerTextureAllocations[slot];
            samplerEntry.setSampler(samplerAllocation != null
                    ? samplerAllocation.nativeSampler()
                    : samplerTextureAllocation.nativeSampler());
            entries.push_back(samplerEntry);
        }

        WGPUBindGroupDescriptor descriptor = WGPUBindGroupDescriptor.obtain();
        descriptor.setNextInChain(WGPUChainedStruct.NULL);
        descriptor.setLabel("libfdx cached texture bind group");
        descriptor.setLayout(pipeline.textureBindGroupLayout());
        descriptor.setEntries(entries);
        WGPUBindGroup bindGroup = new WGPUBindGroup();
        WGPUTextureBindGroupResource resource = null;
        try {
            nativeDevice().createBindGroup(descriptor, bindGroup);
            if (!bindGroup.isValid()) {
                throw new FdxException("Could not create WGPU texture bind group");
            }
            resource = new WGPUTextureBindGroupResource(this, pipeline,
                    allocations, count, samplerAllocations,
                    samplerTextureAllocations, samplerCount, bindGroup);
            textureBindGroups.add(resource);
            resource.attach();
            markRecordedResource(resource);
            return resource;
        } catch (RuntimeException | Error failure) {
            if (resource != null) {
                try {
                    resource.invalidate();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            } else {
                try {
                    if (bindGroup.isValid()) {
                        bindGroup.release();
                    }
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                try {
                    bindGroup.dispose();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    void removeTextureBindGroup(WGPUTextureBindGroupResource resource) {
        for (int i = 0; i < textureBindGroups.size(); i++) {
            if (textureBindGroups.get(i) == resource) {
                textureBindGroups.removeIndex(i);
                return;
            }
        }
    }

    private void releaseTextureBindGroups() {
        WGPUCleanup cleanup = new WGPUCleanup();
        while (!textureBindGroups.isEmpty()) {
            WGPUTextureBindGroupResource resource = textureBindGroups.get(textureBindGroups.size() - 1);
            cleanup.run(resource::invalidate);
        }
        cleanup.throwIfFailed();
    }

    void destroyAfterSubmit(WGPUBuffer buffer) {
        if (buffer != null) {
            submittedBuffers.add(buffer);
        }
    }

    void markRecordedResource(WGPURecordedResource resource) {
        recordedResources.mark(resource);
    }

    int bindUniforms(WGPURenderPassEncoder pass,
            WGPURenderPipelineHandle pipeline, int uniformIndex,
            ByteBuffer data, int allocationIndex) {
        return uniformArena.bind(pass, pipeline, uniformIndex, data,
                allocationIndex);
    }

    void releaseUniformBindGroups(WGPUBindGroupLayout layout) {
        if (uniformArena != null) {
            uniformArena.releaseLayout(layout);
        }
    }

    private void releaseSubmittedResources() {
        Throwable firstFailure = null;
        for (int i = 0; i < submittedBuffers.size(); i++) {
            WGPUBuffer buffer = submittedBuffers.get(i);
            try {
                if (buffer.isValid()) {
                    buffer.destroy();
                }
            } catch (RuntimeException | Error failure) {
                firstFailure = WGPUCleanup.merge(firstFailure, failure);
            }
            try {
                if (buffer.isValid()) {
                    buffer.release();
                }
            } catch (RuntimeException | Error failure) {
                firstFailure = WGPUCleanup.merge(firstFailure, failure);
            }
            try {
                buffer.dispose();
            } catch (RuntimeException | Error failure) {
                firstFailure = WGPUCleanup.merge(firstFailure, failure);
            }
        }
        submittedBuffers.clear();
        WGPUCleanup.rethrow(firstFailure);
    }

    private boolean usesOffscreenFrame() {
        return !surfaceCopySrc && configuration.offscreenReadback();
    }

    boolean supportsReadPixelsRgba8() {
        return surfaceCopySrc || usesOffscreenFrame();
    }

    private WGPUTextureView activeColorAttachmentView() {
        ensureFrameTexture();
        return usesOffscreenFrame() ? offscreenColorRenderTextureView : frameTextureView;
    }

    private WGPUTexture readbackTexture() {
        ensureFrameTexture();
        WGPUTexture texture = usesOffscreenFrame() ? offscreenColorTexture : frameTexture;
        if (texture == null || !texture.isValid()) {
            throw new FdxException("WGPU readback texture is not valid");
        }
        return texture;
    }

    private void applyPendingResize() {
        if (!pendingResize) {
            return;
        }
        int resizeWidth = pendingResizeWidth;
        int resizeHeight = pendingResizeHeight;
        pendingResize = false;
        configureSurface(resizeWidth, resizeHeight);
    }

    private void reconfigureSurfaceAfterAcquireFailure() {
        int resizeWidth = pendingResize ? pendingResizeWidth : width;
        int resizeHeight = pendingResize ? pendingResizeHeight : height;
        pendingResize = false;
        if (resizeWidth > 0 && resizeHeight > 0) {
            configureSurface(resizeWidth, resizeHeight);
        }
    }

    private void debugSurfaceAcquire(WGPUSurfaceGetCurrentTextureStatus status, String action) {
        if (Boolean.getBoolean("libfdx.wgpu.debugSurfaceStatus")) {
            System.err.println("[libfdx-wgpu] surface acquisition " + status
                    + " at " + width + "x" + height + "; " + action);
        }
    }

    private void rollbackTexture(WGPUTexture texture, WGPUTextureView view, boolean disposeView,
            Throwable failure) {
        suppressRollback(failure, () -> {
            WGPUCleanup cleanup = new WGPUCleanup();
            if (view != null) {
                cleanup.run(() -> {
                    if (view.isValid()) {
                        view.release();
                    }
                });
                if (disposeView) {
                    cleanup.run(view::dispose);
                }
            }
            if (texture != null) {
                cleanup.run(() -> {
                    if (texture.isValid()) {
                        texture.destroy();
                    }
                });
                cleanup.run(() -> {
                    if (texture.isValid()) {
                        texture.release();
                    }
                });
                cleanup.run(texture::dispose);
            }
            cleanup.throwIfFailed();
        });
    }

    private void suppressRollback(Throwable failure, Runnable rollback) {
        try {
            rollback.run();
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * Runs the process events step.
     */
    public void processEvents() {
        if (disposed) {
            return;
        }
        checkDeviceLoss();
        if (initializationStarted && (!ready || configuration.processEventsEachFrame()
                || deviceLostCallback != null || !ownsDevice)) {
            processNativeEvents();
            checkDeviceLoss();
            if (initState != null && initState.error != null) {
                throw new FdxException(initState.error);
            }
            finishInitializationIfReady();
        }
    }

    /**
     * Returns whether ready is enabled or true.
     *
     * @return true if ready is enabled or true; false otherwise
     */
    public boolean isReady() {
        return ready && !disposed && !resourceDomain.isClosed();
    }

    /**
     * Returns the configuration.
     *
     * @return the configuration
     */
    public WGPUConfiguration configuration() {
        return configuration;
    }

    /**
     * Returns the instance.
     *
     * @return the instance
     */
    public WGPUInstance instance() {
        return instance;
    }

    /**
     * Returns the surface.
     *
     * @return the surface
     */
    public WGPUSurface surface() {
        return surface;
    }

    /**
     * Returns the surface owner.
     *
     * @return the surface owner
     */
    public Object surfaceOwner() {
        return surfaceOwner;
    }

    /**
     * Returns the adapter.
     *
     * @return the adapter
     */
    public WGPUAdapter adapter() {
        return adapter;
    }

    /**
     * Returns the native device.
     *
     * @return the native device
     */
    public WGPUDevice nativeDevice() {
        return device;
    }

    /**
     * Returns the native queue.
     *
     * @return the native queue
     */
    public WGPUQueue nativeQueue() {
        return queue;
    }

    WGPUInstance nativeInstance() {
        return instance;
    }

    private void processNativeEvents() {
        // Native Dawn preparation retires callbacks only after every concurrent event pump
        // has returned. Shared contexts and the background drain use the same domain lock.
        synchronized (resourceDomain) { instance.processEvents(); }
    }

    /**
     * Returns the native surface format.
     *
     * @return the native surface format
     */
    public WGPUTextureFormat nativeSurfaceFormat() {
        return surfaceFormat;
    }

    /**
     * Returns the width.
     *
     * @return the width
     */
    public int width() {
        return width;
    }

    /**
     * Returns the height.
     *
     * @return the height
     */
    public int height() {
        return height;
    }

    WGPUResourceDomain resourceDomain() {
        return resourceDomain;
    }

    // Installed by the browser attachment before initialization; native event pumps retire inline after release.
    void deferDeviceLossRetirement(Consumer<Runnable> defer) {
        if (initializationStarted) throw new FdxException("Device loss dispatch must be set before initialization");
        deferDeviceLossRetirement = defer;
    }

    void cancelLostDevicePreparation() {
        ready = false;
        if (preparation != null) preparation.close();
    }

    private void checkDeviceLoss() {
        if (resourceDomain.deviceLoss() == null) return;
        resourceDomain.handleDeviceLoss();
        throw resourceDomain.lossException();
    }

    private void retireDeviceLossCallback() {
        if (!nativeDeviceReleased || deviceLostCallback == null) return;
        if (deferDeviceLossRetirement != null && deviceLossRequested && !deviceLossNotified) return;
        deviceLostCallback.dispose();
        deviceLostCallback = null;
    }

    void initializePreparation(WGPUPreparation value, int workers) {
        requireDeviceUsable("initialize shader preparation");
        value.initialize(this, workers);
        preparation = value;
    }

    WGPUPreparation preparation() { return preparation; }

    WGPUCreationErrors creationErrors() { return creationErrors; }

    WGPUCreationErrors.Scope beginNativeCreation(String operation) {
        return configuration.loaderBackend() == WGPULoaderBackend.WGPU
                && WGPU.getPlatformType() != WGPUPlatformType.WGPU_Web
                ? creationErrors.begin(operation) : null;
    }

    void requireDeviceUsable(String action) {
        if (resourceDomain.deviceLoss() != null) throw resourceDomain.lossException();
        if (disposed || !ready || resourceDomain.isClosed() || initState != null && initState.error != null) {
            throw new FdxException("Cannot " + action + " with an unavailable WGPU context");
        }
    }

    private void requireNotDisposed(String action) {
        if (disposed) {
            throw new FdxException("Cannot " + action + " a disposed WGPU context");
        }
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        ready = false;
        boolean abandoningFrame = frameStarted;
        frameStarted = false;
        WGPUCleanup cleanup = new WGPUCleanup();
        if (preparation != null) cleanup.run(preparation::close);
        if (commandEncoder != null) {
            cleanup.run(commandEncoder::dispose);
            commandEncoder = null;
        }
        cleanup.run(recordedResources::releaseAll);
        cleanup.run(this::releaseTextureBindGroups);
        if (surfaceConfigured) {
            surfaceConfigured = false;
            cleanup.run(surface::unconfigure);
        }
        cleanup.run(this::releaseSubmittedResources);
        cleanup.run(this::releaseDepthResources);
        cleanup.run(this::releaseOffscreenDepthResources);
        cleanup.run(this::releaseOffscreenColorResources);
        if (uniformArena != null) {
            cleanup.run(uniformArena::dispose);
            uniformArena = null;
        }
        if (frameCommandBuffer != null) {
            cleanup.run(frameCommandBuffer::dispose);
        }
        if (clearPassEncoder != null) {
            cleanup.run(clearPassEncoder::dispose);
            clearPassEncoder = null;
        }
        if (frameEncoder != null) {
            if (abandoningFrame) {
                cleanup.run(() -> {
                    frameEncoder.release();
                });
            }
            cleanup.run(frameEncoder::dispose);
        }
        if (frameTextureView != null) {
            if (frameTextureAcquired) {
                cleanup.run(() -> {
                    frameTextureView.release();
                });
            }
            cleanup.run(frameTextureView::dispose);
        }
        if (offscreenColorRenderTextureView != null) {
            cleanup.run(offscreenColorRenderTextureView::dispose);
        }
        if (frameTexture != null) {
            if (frameTextureAcquired) {
                cleanup.run(() -> {
                    frameTexture.release();
                });
            }
            cleanup.run(frameTexture::dispose);
        }
        frameTextureAcquired = false;
        if (surface != null) {
            cleanup.run(surface::release);
            cleanup.run(surface::dispose);
        }
        if (surfaceOwner instanceof NativeObject owner) {
            cleanup.run(owner::dispose);
        }
        cleanup.run(() -> resourceDomain.unregisterContext(this));
        cleanup.run(resourceDomain::releaseContext);
        cleanup.throwIfFailed();
    }

    private void releaseOwnedDevice() {
        WGPUCleanup cleanup = new WGPUCleanup();
        cleanup.run(this::releaseInitializationCallbacks);
        if (queue != null) {
            cleanup.run(queue::release);
        }
        if (device != null) {
            cleanup.run(device::destroy);
            cleanup.run(device::release);
            cleanup.run(device::dispose);
        }
        if (adapter != null) {
            cleanup.run(adapter::release);
            cleanup.run(adapter::dispose);
        }
        if (instance != null) {
            cleanup.run(instance::release);
            cleanup.run(instance::dispose);
        }
        if (errorCallback != null) {
            cleanup.run(errorCallback::dispose);
            errorCallback = null;
        }
        nativeDeviceReleased = true;
        if (deferDeviceLossRetirement != null) {
            cleanup.run(() -> deferDeviceLossRetirement.accept(this::retireDeviceLossCallback));
        } else cleanup.run(this::retireDeviceLossCallback);
        cleanup.throwIfFailed();
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Represents an init state.
     *
     * @author xpenatan
     */
    private static final class InitState {
        volatile boolean complete;
        volatile String error;

        void fail(String error) {
            if (this.error != null) {
                return;
            }
            this.error = error;
            this.complete = true;
        }
    }

    /**
     * Represents a map state.
     *
     * @author xpenatan
     */
    private static final class MapState {
        volatile boolean complete;
        WGPUMapAsyncStatus status;
        String message;
    }

    private static final class OffscreenDepthResources {
        private final int width;
        private final int height;
        private final int sampleCount;
        private final WGPUTexture texture;
        private final WGPUTextureView view;

        OffscreenDepthResources(int width, int height, int sampleCount,
                WGPUTexture texture, WGPUTextureView view) {
            this.width = width;
            this.height = height;
            this.sampleCount = sampleCount;
            this.texture = texture;
            this.view = view;
        }

        void dispose() {
            WGPUCleanup cleanup = new WGPUCleanup();
            if (view != null) {
                cleanup.run(() -> {
                    if (view.isValid()) {
                        view.release();
                    }
                });
                cleanup.run(view::dispose);
            }
            if (texture != null) {
                cleanup.run(() -> {
                    if (texture.isValid()) {
                        texture.destroy();
                    }
                });
                cleanup.run(() -> {
                    if (texture.isValid()) {
                        texture.release();
                    }
                });
                cleanup.run(texture::dispose);
            }
            cleanup.throwIfFailed();
        }
    }
}
