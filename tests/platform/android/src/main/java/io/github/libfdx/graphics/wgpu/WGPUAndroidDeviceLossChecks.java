package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.jParser.api.NativeObject;
import com.github.xpenatan.webgpu.WGPU;
import com.github.xpenatan.webgpu.WGPUCommandEncoder;
import com.github.xpenatan.webgpu.WGPUCommandEncoderDescriptor;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContextLostException;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Android acceptance of a real upstream callback, without a driver reset or post-loss submission. */
public final class WGPUAndroidDeviceLossChecks {
    private static final String SOURCE = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;
    private final WGPUContext context;
    private final String mode = System.getProperty("libfdx.test.wgpuLoss", "unpublished");
    private final ArrayList<ShaderPreparationOperation> jobs = new ArrayList<>();
    private final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    private final AtomicInteger queuedSources = new AtomicInteger();
    private ShaderPreparedResult ready;

    public WGPUAndroidDeviceLossChecks(WGPUContext context) { this.context = context; }

    public void validate() {
        require(Set.of("source", "unpublished", "published", "open-pass", "ended-pass").contains(mode),
                "Unknown loss case: " + mode);
        boolean dawn = context.configuration().loaderBackend() == WGPULoaderBackend.DAWN;
        require(WGPU.isDawnBackend() == dawn, "Loaded native implementation does not match configuration");
        System.out.println("ANDROID_WGPU_NATIVE_LOSS LOADED loader=" + (dawn ? "DAWN" : "WGPU"));
        NativeObject callback = callback();
        require(callback != null && !callback.isDisposed(), "Native loss callback not registered");
        String backend = context.configuration().backend().name();
        try {
            // Live-device control: this native call must not close the resource domain.
            nativeEncoder();
            require(context.resourceDomain().deviceLoss() == null && context.isReady(), "Live control failed");
            Object identity = context.device().resourceDomain();
            ShaderPipelineRequest request = request(ShaderModuleSource.fixed(source("before-loss")));
            ShaderPreparationOperation completed = prepare(request);
            await(() -> { completed.advanceLoading(); return completed.isDone(); });
            require(completed.phase() == ShaderPreparationPhase.PUBLICATION_WAIT, "Pipeline did not compile");
            boolean published = mode.equals("published") || mode.endsWith("pass");
            if (published) ready = completed.finish();

            ShaderPreparationOperation held = null, queued = null;
            if (mode.equals("source")) {
                require(context.configuration().preparationWorkerLimit() == 1, "Source case requires shaderWorkers=1");
                held = prepare(request(ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                    entered.countDown();
                    awaitLatch(release);
                    return source("held-at-loss");
                })));
                awaitLatch(entered);
                queued = prepare(request(ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                    queuedSources.incrementAndGet();
                    return source("must-not-run");
                })));
            }

            RenderPass pass = null;
            if (mode.endsWith("pass")) {
                require(context.beginFrame(), "Test frame could not start");
                pass = context.currentFrame().commandEncoder().beginRenderPass(new RenderPassDescriptor()
                        .colorAttachment(context.currentFrame().colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
                pass.setPipeline(ready.pass().pipeline());
                pass.draw(3, 1, 0, 0);
                require(Boolean.TRUE.equals(field("frameTextureAcquired")), "Loss case must hold a surface frame");
                if (mode.equals("ended-pass")) pass.end();
            }

            context.nativeDevice().destroy();
            synchronized (context.resourceDomain()) { context.nativeInstance().processEvents(); }
            boolean destroyNotified = context.resourceDomain().deviceLoss() != null;
            if (dawn) {
                await(() -> {
                    synchronized (context.resourceDomain()) { context.nativeInstance().processEvents(); }
                    return context.resourceDomain().deviceLoss() != null;
                });
            } else {
                // wgpu-native's CPU-side error path invokes the registered native callback.
                // The error encoder is released without recording, finishing or submitting it.
                nativeEncoder();
            }
            String loss = context.resourceDomain().deviceLoss();
            require(loss != null && loss.contains("Destroyed"),
                    "Missing real native Destroyed notification: " + loss);
            System.out.println("ANDROID_WGPU_NATIVE_LOSS NOTIFIED backend=" + backend + " mode=" + mode
                    + " loader=" + context.configuration().loaderBackend()
                    + " destroy_notified=" + destroyNotified + " message=" + loss);
            if (!dawn) nativeEncoder();
            require(context.resourceDomain().deviceLoss() == loss, "Repeated loss changed the latched cause");
            require(!context.isReady() && context.device().resourceDomain() != identity, "Lost identity remains usable");
            expect(GraphicsContextLostException.class, context::processEvents);
            expect(GraphicsContextLostException.class, context::beginFrame);
            expect(GraphicsContextLostException.class, () -> context.device().prepareRenderPipeline(request));
            if (!published) expect(CancellationException.class, completed::finish);
            if (ready != null) expect(FdxException.class, () -> WGPUResources.requirePipeline(
                    ready.pass().pipeline(), context.resourceDomain(), "Lost pipeline"));
            if (pass != null) {
                RenderPass lostPass = pass;
                if (mode.equals("open-pass")) expect(GraphicsContextLostException.class, () -> lostPass.draw(3, 1, 0, 0));
                expect(GraphicsContextLostException.class, context::endFrame);
            }
            if (ready != null) { ready.dispose(); ready = null; }
            context.dispose();
            if (held != null) {
                require(!held.isDone() && !context.nativeDevice().isDisposed() && !callback.isDisposed(),
                        "Held source failed to retain its device and callback");
                release.countDown();
                await(held::isDone);
                await(queued::isDone);
                expect(CancellationException.class, held::finish);
                expect(CancellationException.class, queued::finish);
                require(queuedSources.get() == 0, "Cancelled queued source executed");
            }
        } finally {
            release.countDown();
            for (ShaderPreparationOperation job : jobs) {
                job.cancel();
                await(job::isDone);
                job.dispose();
            }
            if (ready != null) { ready.dispose(); ready = null; }
            context.dispose();
        }
        await(() -> context.nativeDevice().isDisposed() && callback.isDisposed());
        require(callback() == null, "Context retained retired callback storage");
        require(context.resourceDomain().contextReferences() == 0, "Context reference leaked");
        require(((NativeObject) context.surfaceOwner()).isDisposed(), "Native window owner leaked");
        System.out.println("ANDROID_WGPU_NATIVE_LOSS PASS backend=" + backend + " mode=" + mode
                + " loader=" + context.configuration().loaderBackend()
                + " native_notification=true device_disposed=true callback_disposed=true queued_sources=" + queuedSources.get());
    }

    private ShaderPreparationOperation prepare(ShaderPipelineRequest request) {
        ShaderPreparationOperation job = context.device().prepareRenderPipeline(request);
        jobs.add(job);
        return job;
    }

    private ShaderPipelineRequest request(ShaderModuleSource source) {
        return new ShaderPipelineRequest(source, new RenderPipelineDescriptor()
                .renderTargetLayout(RenderTargetLayout.color(context.surfaceFormat()))
                .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
    }

    private static ShaderModuleDescriptor source(String label) { return ShaderModuleDescriptor.wgsl(label, SOURCE); }

    private void nativeEncoder() {
        WGPUCommandEncoderDescriptor descriptor = new WGPUCommandEncoderDescriptor();
        WGPUCommandEncoder encoder = new WGPUCommandEncoder();
        try { context.nativeDevice().createCommandEncoder(descriptor, encoder); }
        finally {
            if (encoder.isValid()) encoder.release();
            encoder.dispose();
            descriptor.dispose();
        }
    }

    private NativeObject callback() {
        return (NativeObject) field("deviceLostCallback");
    }

    private Object field(String name) {
        try {
            Field field = WGPUContext.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(context);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void await(BooleanSupplier complete) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!complete.getAsBoolean()) {
            require(System.nanoTime() < deadline, "Native loss check timed out");
            try { Thread.sleep(1); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try { require(latch.await(15, TimeUnit.SECONDS), "Source latch timed out"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Expected " + type.getSimpleName(), failure);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
