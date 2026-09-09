package io.github.libfdx.graphics.wgpu;

import android.os.Handler;
import android.os.Looper;
import com.github.xpenatan.jParser.api.NativeObject;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.android.AndroidApplicationBackend;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.target.ShaderCompilerId;
import io.github.libfdx.graphics.shader.target.ShaderStageArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetVerification;
import io.github.libfdx.graphics.shader.target.ShaderTranslatedInterface;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import java.lang.reflect.Field;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** On-device pause/publication and surface recreation regression; selected only by the Android launcher. */
public final class WGPUAndroidLifecycleTest extends ApplicationAdapter {
    private static final String SHADER = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;
    private final Handler owner = new Handler(Looper.getMainLooper());
    private final String mode = System.getProperty("libfdx.test.wgpuLifecycle", "surface");
    private int sessions;
    private Session session;

    @Override public void create(Fdx fdx) {
        session = new Session(fdx, ++sessions);
        System.out.println("[wgpu-android] SESSION_CREATE id=" + sessions + " mode=" + mode
                + " capabilities=" + session.graphics.device().shaderPreparationCapabilities());
    }

    @Override public void render() { session.render(); }
    @Override public void pause() { System.out.println("[wgpu-android] PAUSE session=" + sessions); }
    @Override public void resume() { System.out.println("[wgpu-android] RESUME session=" + sessions); }
    @Override public void dispose() { session.close(); }

    private final class Session {
        final GraphicsContext graphics;
        final WGPUContext context;
        final NativeObject lossCallback;
        final Application application;
        final int id;
        final Thread applicationThread = Thread.currentThread();
        final AtomicInteger queuedSources = new AtomicInteger();
        final CountDownLatch started;
        final CountDownLatch release = new CountDownLatch(1);
        final ShaderPreparationOperation[] held;
        final boolean loadingOnly;
        final RenderPassDescriptor renderPass = new RenderPassDescriptor().colorLoadOp(LoadOp.clear(0, 0, 0, 1));
        ShaderPreparationOperation display, unpublished, queued, waitingForLoading, invalidNative;
        ShaderPreparedResult ready;
        ShaderModule synchronousShader;
        RenderPipeline synchronousPipeline;
        int originalWidth, originalHeight;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        int frames, stage, loadingWaitFrames;
        boolean resumed, closed;

        Session(Fdx fdx, int id) {
            this.id = id;
            graphics = fdx.graphics().main(); context = graphics.as(); application = fdx.app();
            lossCallback = lossCallback(context);
            require(lossCallback != null && !lossCallback.isDisposed(), "Device loss callback was not registered");
            var capabilities = graphics.device().shaderPreparationCapabilities();
            loadingOnly = capabilities.nativeExecution() == ShaderPreparationCapabilities.Execution.OWNER_THREAD;
            if (mode.equals("unsupported")) {
                require(!capabilities.runtimeNonblocking(), "Unvalidated Android backend advertises workers");
                held = new ShaderPreparationOperation[0]; started = new CountDownLatch(0);
                return;
            }
            if (mode.equals("surface-sync")) {
                require(!capabilities.runtimeNonblocking(), "GLES unexpectedly advertises async preparation");
                held = new ShaderPreparationOperation[0]; started = new CountDownLatch(0);
                synchronousShader = graphics.device().createShaderModule(source("surface"));
                synchronousPipeline = graphics.device().createRenderPipeline(new RenderPipelineDescriptor()
                        .shaderModule(synchronousShader).renderTargetLayout(RenderTargetLayout.color(graphics.surfaceFormat()))
                        .depthTestEnabled(false).depthWriteEnabled(false));
                stage = 2;
                return;
            }
            require(capabilities.runtimeNonblocking() || loadingOnly, "Android WGPU preparation unavailable");
            if (loadingOnly) {
                require(context.configuration().backend() == WGPUBackend.OPENGL_ES, "Unexpected loading-only backend");
                require(capabilities.cpuExecution() == ShaderPreparationCapabilities.Execution.WORKERS,
                        "GLES source preparation must use workers");
                require(!capabilities.runtimeNonblocking(), "GLES incorrectly advertises nonblocking runtime preparation");
            }
            int requested = Integer.getInteger("libfdx.test.shaderWorkers", 0);
            require(capabilities.workerLimit() == (requested != 0 ? requested
                    : Math.min(2, Runtime.getRuntime().availableProcessors())), "Wrong mobile worker limit");
            held = new ShaderPreparationOperation[capabilities.workerLimit()];
            started = new CountDownLatch(held.length);
            display = prepare(ShaderModuleSource.fixed(source("display")));
            unpublished = prepare(ShaderModuleSource.fixed(source("unpublished")));
            if (loadingOnly) {
                waitingForLoading = prepare(ShaderModuleSource.fixed(source("cancel-before-native")));
                invalidNative = graphics.device().prepareRenderPipeline(new ShaderPipelineRequest(invalidNativeSource(),
                        new RenderPipelineDescriptor().renderTargetLayout(RenderTargetLayout.color(graphics.surfaceFormat()))
                                .vertexEntryPoint("missingVertex").depthTestEnabled(false).depthWriteEnabled(false),
                        ShaderPassId.FORWARD, 0));
            }
        }

        ShaderModuleDescriptor source(String label) { return ShaderModuleDescriptor.wgsl(label, SHADER); }

        ShaderPreparationOperation prepare(ShaderModuleSource source) {
            return graphics.device().prepareRenderPipeline(new ShaderPipelineRequest(source,
                    new RenderPipelineDescriptor().renderTargetLayout(RenderTargetLayout.color(graphics.surfaceFormat()))
                            .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0));
        }

        void render() {
            require(System.nanoTime() < deadline, "Lifecycle scenario timed out");
            if (mode.equals("unsupported")) {
                System.out.println("[wgpu-android] UNSUPPORTED_PASS backend=" + context.configuration().backend());
                application.requestExit();
                return;
            }
            if (stage == 0 && loadingOnly && display.phase() == ShaderPreparationPhase.COMPILATION
                    && unpublished.phase() == ShaderPreparationPhase.COMPILATION
                    && waitingForLoading.phase() == ShaderPreparationPhase.COMPILATION
                    && invalidNative.phase() == ShaderPreparationPhase.COMPILATION) {
                require(!display.isDone() && !unpublished.isDone() && !waitingForLoading.isDone(),
                        "GLES performed native creation without a loading advance");
                if (++loadingWaitFrames == 12) {
                    waitingForLoading.cancel();
                    waitingForLoading.advanceLoading();
                    require(waitingForLoading.isDone(), "Cancellation left the loading handoff pending");
                    expectCancelled(waitingForLoading); waitingForLoading.dispose();
                    invalidNative.advanceLoading();
                    require(invalidNative.isDone(), "Invalid native pipeline did not settle");
                    try { invalidNative.finish(); throw new AssertionError("Invalid native pipeline succeeded"); }
                    catch (RuntimeException expected) {
                        require(expected.getMessage().contains("wgpuDeviceCreateRenderPipeline"),
                                "Invalid pipeline failed before native validation: " + expected);
                    }
                    invalidNative.dispose();
                    display.advanceLoading(); unpublished.advanceLoading();
                    System.out.println("[wgpu-android] LOADING_HANDOFF_PASS polls=" + loadingWaitFrames
                            + " cancelledBeforeNative=1 nativeRejected=1");
                }
            }
            if (stage == 0 && display.isDone() && unpublished.isDone()) {
                ready = display.finish(); display.dispose();
                for (int i = 0; i < held.length; i++) {
                    held[i] = prepare(ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                        require(Thread.currentThread() != applicationThread, "Source ran on application thread");
                        started.countDown();
                        try { require(release.await(90, TimeUnit.SECONDS), "Held source was not released"); }
                        catch (InterruptedException error) { throw new AssertionError(error); }
                        return source("held");
                    }));
                }
                stage = 1;
            }
            if (stage == 1 && started.getCount() == 0) {
                queued = prepare(ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                    queuedSources.incrementAndGet(); return source("must-not-run");
                }));
                queued.cancel(); stage = 2;
            }
            var frame = graphics.currentFrame();
            var pass = frame.commandEncoder().beginRenderPass(renderPass.colorAttachment(frame.colorAttachment()));
            try {
                RenderPipeline pipeline = ready != null ? ready.pass().pipeline() : synchronousPipeline;
                if (pipeline != null) { pass.setPipeline(pipeline); pass.draw(3, 1, 0, 0); }
            }
            finally { pass.end(); }
            if (synchronousPipeline != null) {
                if (frames == 0) { originalWidth = frame.width(); originalHeight = frame.height(); }
                if (frames == 1) context.resize(originalWidth - 32, originalHeight - 16);
                if (frames == 2) require(frame.width() == originalWidth - 32 && frame.height() == originalHeight - 16,
                        "Surface resize was not applied");
                if (frames == 3) context.resize(originalWidth, originalHeight);
                if (frames == 4) {
                    require(frame.width() == originalWidth && frame.height() == originalHeight, "Surface size was not restored");
                    System.out.println("[wgpu-android] RESIZE_PASS session=" + id);
                }
            }
            if (stage == 2 && ++frames == 5) {
                capture("ready");
                System.out.println("[wgpu-android] LIFECYCLE_READY session=" + id + " held=" + held.length);
                if (mode.equals("pause")) owner.post(this::pauseWhileWorking);
            }
            if (resumed) {
                for (ShaderPreparationOperation job : held) {
                    if (loadingOnly) job.advanceLoading();
                    job.finish().dispose(); job.dispose();
                }
                unpublished.finish().dispose(); unpublished.dispose();
                expectCancelled(queued); queued.dispose();
                require(queuedSources.get() == 0, "Cancelled queued source ran");
                capture("resumed");
                System.out.println("[wgpu-android] PAUSE_RESUME_PASS session=" + id);
                resumed = false;
                application.requestExit();
            }
        }

        void capture(String suffix) {
            String base = System.getProperty("libfdx.test.capture", "");
            if (base.isEmpty()) return;
            try { FramebufferCapture.writePpm(base + "-" + id + "-" + suffix + ".ppm",
                    graphics.currentFrame().width(), graphics.currentFrame().height(), FramebufferCapture.readPixelsRgba8(graphics)); }
            catch (Exception error) { throw new AssertionError(error); }
        }

        void pauseWhileWorking() {
            AndroidApplicationBackend backend = application.as();
            backend.pause();
            int pausedFrames = frames;
            release.countDown();
            waitUntilDrained(() -> {
                require(frames == pausedFrames && !context.isDisposed(), "Paused session rendered or was destroyed");
                for (ShaderPreparationOperation job : held) {
                    require(job.phase() == (loadingOnly ? ShaderPreparationPhase.COMPILATION : ShaderPreparationPhase.PUBLICATION_WAIT),
                            "Paused preparation advanced an unexpected stage: " + job.phase());
                    if (loadingOnly) require(!job.isDone(), "Paused GLES work compiled without a loading update");
                }
                System.out.println("[wgpu-android] PAUSED_RESULTS_WAITING session=" + id);
                resumed = true;
                backend.resume();
            });
        }

        void close() {
            if (closed) return;
            closed = true;
            if (ready != null) ready.dispose();
            if (synchronousPipeline != null) synchronousPipeline.dispose();
            if (synchronousShader != null) synchronousShader.dispose();
            System.out.println("[wgpu-android] SESSION_DISPOSE id=" + id);
            owner.post(() -> {
                require(context.isDisposed() && context.resourceDomain().isClosed(), "Surface did not retire its context");
                require(((NativeObject) context.surfaceOwner()).isDisposed(), "Android window owner leaked");
                if (!mode.equals("surface")) {
                    require(context.nativeDevice().isDisposed(), "Completed session device did not retire");
                    require(lossCallback.isDisposed(), "Completed session loss callback did not retire");
                    System.out.println("[wgpu-android] CALLBACK_DRAIN_PASS session=" + id);
                    System.out.println("[wgpu-android] DEVICE_DRAIN_PASS session=" + id);
                    return;
                }
                require(started.getCount() == 0 && !context.nativeDevice().isDisposed(), "Pending device was released early");
                require(!lossCallback.isDisposed(), "Pending native work lost its device callback");
                expectCancelled(unpublished); unpublished.dispose();
                for (ShaderPreparationOperation job : held) require(!job.isDone(), "Held source unexpectedly returned");
                release.countDown();
                waitUntilDrained(() -> {
                    for (ShaderPreparationOperation job : held) { expectCancelled(job); job.dispose(); }
                    expectCancelled(queued); queued.dispose();
                    require(queuedSources.get() == 0, "Cancelled queued source ran");
                    require(context.nativeDevice().isDisposed(), "Retained device did not drain");
                    require(lossCallback.isDisposed(), "Retained device loss callback did not drain");
                    System.out.println("[wgpu-android] CALLBACK_DRAIN_PASS session=" + id);
                    System.out.println("[wgpu-android] SURFACE_DRAIN_PASS session=" + id);
                });
            });
        }

        void waitUntilDrained(Runnable continuation) {
            long expires = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            owner.post(new Runnable() {
                @Override public void run() {
                    require(System.nanoTime() < expires, "Android jobs failed to drain");
                    for (ShaderPreparationOperation job : held) {
                        if (!job.isDone() && !(loadingOnly && !closed && mode.equals("pause")
                                && job.phase() == ShaderPreparationPhase.COMPILATION)) {
                            owner.postDelayed(this, 10); return;
                        }
                    }
                    if (!queued.isDone()) { owner.postDelayed(this, 10); return; }
                    if (closed && (!context.nativeDevice().isDisposed() || !lossCallback.isDisposed())) {
                        owner.postDelayed(this, 10); return;
                    }
                    continuation.run();
                }
            });
        }
    }

    private static NativeObject lossCallback(WGPUContext context) {
        try {
            Field field = WGPUContext.class.getDeclaredField("deviceLostCallback");
            field.setAccessible(true);
            return (NativeObject) field.get(context);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void expectCancelled(ShaderPreparationOperation job) {
        try { job.finish(); throw new AssertionError("Cancelled result was published"); }
        catch (CancellationException expected) { }
    }

    /** Claim a nonexistent entry only in this fixture, so rejection occurs inside the native pipeline call. */
    private static ShaderModuleDescriptor invalidNativeSource() {
        ShaderTarget target = ShaderTarget.WGPU_WGSL;
        ShaderReflection reflection = ShaderReflection.builder(ShaderProfile.PORTABLE_WEBGPU).complete(true)
                .entryPoints(ShaderEntryPoint.builder("vertexMain", ShaderStage.VERTEX).build(),
                        ShaderEntryPoint.builder("missingVertex", ShaderStage.VERTEX).build(),
                        ShaderEntryPoint.builder("fragmentMain", ShaderStage.FRAGMENT).build()).build();
        ShaderTargetArtifact artifact = ShaderTargetArtifact.compiled(target.id(), target.format(), target.environment(),
                new ShaderStageArtifact[]{ShaderStageArtifact.text(ShaderArtifactStage.MODULE, "", target.format(), SHADER)},
                ShaderTranslatedInterface.identity(reflection, null), ShaderCompilerId.of("test.android-gles-errors"), "1", "");
        artifact = artifact.withVerification(ShaderTargetVerification.providerPipeline(target.environment(), null,
                artifact.compileCacheKey()));
        return ShaderModuleDescriptors.descriptor(artifact, "invalid-native-pipeline", SHADER);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
