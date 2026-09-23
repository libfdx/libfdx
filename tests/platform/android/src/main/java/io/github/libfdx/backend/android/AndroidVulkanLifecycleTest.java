package io.github.libfdx.backend.android;

import android.os.Handler;
import android.os.Looper;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Direct Vulkan lifecycle probe. Select with the Android launcher and vulkanLifecycle=pause or surface.
 * Surface mode waits for external HOME/reopen/BACK actions; workers are held in source generation,
 * not inside a native driver call. No extra device reference is retained by this test. */
public final class AndroidVulkanLifecycleTest extends ApplicationAdapter {
    private static final String SHADER = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;
    private final Handler owner = new Handler(Looper.getMainLooper());
    private final String mode = System.getProperty("libfdx.test.vulkanLifecycle", "surface");
    private int sessions;
    private Session session;

    @Override
    public void create(Fdx fdx) {
        require(mode.equals("pause") || mode.equals("surface"), "Unknown Vulkan lifecycle mode");
        session = new Session(fdx, ++sessions);
        log("SESSION_CREATE id=" + sessions + " mode=" + mode);
    }
    @Override
    public void render() { session.render(); }
    @Override
    public void pause() { log("PAUSE session=" + sessions); }
    @Override
    public void resume() { log("RESUME session=" + sessions); }
    @Override
    public void dispose() { session.close(); }

    private final class Session {
        final GraphicsAttachment graphics;
        final GraphicsDevice device;
        final RenderTargetLayout target;
        final Object resourceDomain;
        final Application application;
        final Thread applicationThread = Thread.currentThread();
        final int id;
        final AtomicInteger queuedSources = new AtomicInteger();
        final CountDownLatch started;
        final CountDownLatch release = new CountDownLatch(1);
        final ShaderPreparationOperation[] held;
        final ShaderPreparationOperation display, unpublished;
        final RenderPassDescriptor renderPass = new RenderPassDescriptor().colorLoadOp(LoadOp.clear(0, 0, 0, 1));
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        ShaderPreparationOperation queued;
        ShaderPreparedResult ready;
        int frames, stage;
        boolean resumed, closed;

        Session(Fdx fdx, int id) {
            this.id = id;
            graphics = (GraphicsAttachment) fdx.graphics().main();
            require(graphics.providerId().equals(AndroidVulkanProvider.ID), "Expected direct Android Vulkan");
            device = graphics.device();
            target = RenderTargetLayout.color(graphics.surfaceFormat());
            application = fdx.app();
            resourceDomain = device.resourceDomain();
            var capabilities = device.shaderPreparationCapabilities();
            require(capabilities.cpuExecution() == ShaderPreparationCapabilities.Execution.WORKERS
                    && capabilities.nativeExecution() == ShaderPreparationCapabilities.Execution.WORKERS
                    && capabilities.runtimeNonblocking(), "Expected runtime worker preparation");
            held = new ShaderPreparationOperation[capabilities.workerLimit()];
            require(held.length > 0, "No shader workers");
            started = new CountDownLatch(held.length);
            display = prepare(ShaderModuleSource.fixed(source("display")));
            unpublished = prepare(ShaderModuleSource.fixed(source("unpublished")));
        }

        ShaderPreparationOperation prepare(ShaderModuleSource source) {
            return device.prepareRenderPipeline(new ShaderPipelineRequest(source,
                    new RenderPipelineDescriptor().renderTargetLayout(target)
                            .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0));
        }

        void render() {
            require(System.nanoTime() < deadline, "Lifecycle scenario timed out");
            if (stage == 0 && display.isDone() && unpublished.isDone()) {
                ready = display.finish(); display.dispose();
                require(unpublished.phase() == ShaderPreparationPhase.PUBLICATION_WAIT, "Missing unpublished result");
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
                if (ready != null) { pass.setPipeline(ready.pass().pipeline()); pass.draw(3, 1, 0, 0); }
            } finally { pass.end(); }
            if (stage == 2 && ++frames == 5) {
                capture("ready");
                log("LIFECYCLE_READY session=" + id + " held=" + held.length);
                if (mode.equals("pause")) owner.post(this::pauseWhileWorking);
            }
            if (resumed) {
                for (ShaderPreparationOperation job : held) { job.finish().dispose(); job.dispose(); }
                unpublished.finish().dispose(); unpublished.dispose();
                expectCancelled(queued); queued.dispose();
                require(queuedSources.get() == 0, "Cancelled queued source ran");
                capture("resumed");
                log("PAUSE_RESUME_PASS session=" + id);
                resumed = false;
                application.requestExit();
            }
        }

        void capture(String suffix) {
            int width = graphics.currentFrame().width(), height = graphics.currentFrame().height();
            var pixels = FramebufferCapture.readPixelsRgba8(graphics);
            int center = ((height / 2) * width + width / 2) * 4;
            require((pixels.get(center) & 255) < 10 && (pixels.get(center + 1) & 255) > 245
                    && (pixels.get(center + 2) & 255) < 10, "Triangle center is not green");
            require((pixels.get(0) & 255) < 10 && (pixels.get(1) & 255) < 10
                    && (pixels.get(2) & 255) < 10, "Clear color is not black");
            String base = System.getProperty("libfdx.test.capture", "");
            if (!base.isEmpty()) {
                try { FramebufferCapture.writePpm(base + "-" + id + "-" + suffix + ".ppm", width, height, pixels); }
                catch (Exception error) { throw new AssertionError(error); }
            }
            log("PIXELS_VERIFIED session=" + id + " frame=" + suffix);
        }

        void pauseWhileWorking() {
            AndroidApplicationBackend backend = application.as();
            backend.pause();
            int pausedFrames = frames;
            release.countDown();
            waitUntilDrained(() -> {
                require(frames == pausedFrames && !graphics.isDisposed(), "Paused session rendered or was destroyed");
                for (ShaderPreparationOperation job : held)
                    require(job.phase() == ShaderPreparationPhase.PUBLICATION_WAIT, "Paused result did not wait for publication");
                log("PAUSED_RESULTS_WAITING session=" + id);
                resumed = true;
                backend.resume();
            });
        }

        void close() {
            if (closed) return;
            closed = true;
            if (ready != null) ready.dispose();
            log("SESSION_DISPOSE id=" + id);
            owner.post(() -> {
                require(graphics.isDisposed(), "Surface did not retire its context");
                require(device.resourceDomain() != resourceDomain, "Old resource domain is still valid");
                try { prepare(ShaderModuleSource.fixed(source("closed"))); throw new AssertionError("Closed context accepted work"); }
                catch (FdxException expected) { }
                if (mode.equals("pause")) {
                    log("CONTEXT_CLOSED_PASS session=" + id);
                    return;
                }
                require(started.getCount() == 0, "Surface closed before workers reached the source barrier");
                expectCancelled(unpublished); unpublished.dispose();
                for (ShaderPreparationOperation job : held) require(!job.isDone(), "Held source unexpectedly returned");
                release.countDown();
                waitUntilDrained(() -> {
                    for (ShaderPreparationOperation job : held) { expectCancelled(job); job.dispose(); }
                    expectCancelled(queued); queued.dispose();
                    require(queuedSources.get() == 0, "Cancelled queued source ran");
                    log("SURFACE_DRAIN_PASS session=" + id + " cancelled=" + (held.length + 2));
                });
            });
        }

        void waitUntilDrained(Runnable continuation) {
            long expires = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            owner.post(new Runnable() {
                @Override
                public void run() {
                    require(System.nanoTime() < expires, "Android Vulkan jobs failed to drain");
                    for (ShaderPreparationOperation job : held) {
                        if (!job.isDone()) { owner.postDelayed(this, 10); return; }
                    }
                    if (!queued.isDone()) { owner.postDelayed(this, 10); return; }
                    continuation.run();
                }
            });
        }
    }

    private static ShaderModuleDescriptor source(String label) { return ShaderModuleDescriptor.wgsl(label, SHADER); }
    private static void expectCancelled(ShaderPreparationOperation job) {
        try { job.finish(); throw new AssertionError("Cancelled result was published"); }
        catch (CancellationException expected) { }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void log(String message) { System.out.println("[vulkan-android] " + message); }
}
