package io.github.libfdx.tests.android;

import android.os.Handler;
import android.os.Looper;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsContextLostException;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationState;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import io.github.libfdx.testsupport.android.VulkanLossFixture;
import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Requires -PlibfdxVulkanLossTests=true and shaderWorkers=1. Injected VkResult values,
 * not actual device loss. Every case drains/abandons frame work before owner disposal. */
public final class AndroidVulkanDeviceLossTest extends ApplicationAdapter {
    private static final String SOURCE = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;
    private final ArrayList<ShaderPreparationOperation> operations = new ArrayList<>();
    private GraphicsAttachment context;
    private GraphicsDevice device;
    private long nativeContext;
    private ShaderPreparedResult ready;
    private final String mode = System.getProperty("libfdx.test.vulkanLoss", "worker");

    @Override
    public void create(Fdx fdx) {
        context = fdx.graphics().main().as(); device = context.device();
        nativeContext = (long) field(context, "context");
        // Let the backend finish its initial resize before the probe deliberately closes its context.
        new Handler(Looper.getMainLooper()).post(() -> validate(fdx));
    }

    private void validate(Fdx fdx) {
        try {
            if (mode.equals("held")) heldAndQueued();
            else if (mode.equals("submit") || mode.equals("present") || mode.equals("open")) activeFrame();
            else workerOrCache();
            require(VulkanLossFixture.calls() == 1, "Expected one injected return");
            for (var operation : operations) { operation.cancel(); await(operation::isDone); operation.dispose(); }
            if (ready != null) { ready.dispose(); ready = null; }
            context.dispose();
            await(() -> VulkanLossFixture.destructions() == 1);
            assertRetired();
            System.out.println("ANDROID_VULKAN_LOSS PASS mode=" + mode + " injections=1 destroyed=1 jobs=0");
        } finally {
            for (var operation : operations) { operation.cancel(); await(operation::isDone); operation.dispose(); }
            if (ready != null) ready.dispose();
            context.dispose(); fdx.app().requestExit();
        }
    }

    private void workerOrCache() {
        int site = switch (mode) {
            case "worker", "ordinary" -> 1;
            case "cache" -> 2;
            case "snapshot" -> 3;
            default -> throw new FdxException("Unknown Vulkan loss mode " + mode);
        };
        Object domain = device.resourceDomain();
        ShaderPreparationOperation unpublished = null;
        if (site == 1) { unpublished = prepare("unpublished"); await(unpublished::isDone); }
        VulkanLossFixture.arm(nativeContext, site, mode.equals("ordinary") ? -2 : -4);
        var operation = prepare("injected"); await(operation::isDone);
        if (mode.equals("ordinary")) {
            require(domain == device.resourceDomain(), "Ordinary error invalidated the device");
            expect(FdxException.class, operation::finish);
            require(!(field(operation, "failure") instanceof GraphicsContextLostException), "Ordinary error became device loss");
            var recovered = prepare("recovered"); await(recovered::isDone);
            ready = recovered.finish();
            require(context.beginFrame(), "Recovery frame did not start");
            var pass = draw(); pass.end();
            int width = context.currentFrame().width(), height = context.currentFrame().height();
            var pixels = context.currentFrame().frameBuffer().readPixelsRgba8();
            int center = (height / 2 * width + width / 2) * 4;
            require((pixels.get(center + 1) & 255) == 255 && (pixels.get(center) & 255) == 0
                    && (pixels.get(center + 2) & 255) == 0 && (pixels.get(1) & 255) == 0, "Recovery pixels incorrect");
            String capture = System.getProperty("libfdx.test.capture", "");
            if (!capture.isEmpty()) try { FramebufferCapture.writePpm(capture, width, height, pixels); }
            catch (Exception failure) { throw new FdxException("Recovery capture failed", failure); }
            System.out.println("ANDROID_VULKAN_LOSS RECOVERY_PIXELS_PASS");
        } else {
            require(domain != device.resourceDomain(), "Native loss left the domain usable");
            require(field(operation, "failure") instanceof GraphicsContextLostException, "JNI did not preserve typed device loss");
            expect(CancellationException.class, operation::finish);
            if (unpublished != null) expect(CancellationException.class, unpublished::finish);
            expect(GraphicsContextLostException.class, () -> prepare("after loss"));
            expect(GraphicsContextLostException.class, context::beginFrame);
        }
    }

    private void activeFrame() {
        var operation = prepare("ready"); await(operation::isDone); ready = operation.finish();
        require(context.beginFrame(), "Frame did not start");
        RenderPass pass = draw();
        if (mode.equals("open")) {
            VulkanLossFixture.arm(nativeContext, 1, -4);
            var failed = prepare("loss during recording"); await(failed::isDone);
            require(field(failed, "failure") instanceof GraphicsContextLostException, "Native worker lost the signal");
            expect(GraphicsContextLostException.class, () -> pass.draw(3, 1, 0, 0));
            pass.end();
            expect(GraphicsContextLostException.class, context::endFrame);
        } else {
            pass.end();
            if (mode.equals("present")) context.resize(400, 300);
            VulkanLossFixture.arm(nativeContext, mode.equals("submit") ? 5 : 6, -4);
            expect(GraphicsContextLostException.class, context::endFrame);
        }
        expect(GraphicsContextLostException.class, () -> prepare("after frame loss"));
    }

    private void heldAndQueued() {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger queued = new AtomicInteger();
        var service = new ShaderPreparation(context);
        ShaderProvider provider = new ShaderProvider() {
            @Override
            public GraphicsDevice preparationDevice() { return device; }
            @Override
            public boolean supports(ShaderRequest request) { return true; }
            @Override
            public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
                var source = ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                    if (request.variantKey().equals("held")) {
                        entered.countDown();
                        try { require(release.await(20, TimeUnit.SECONDS), "Held source timed out"); }
                        catch (InterruptedException error) { throw new FdxException("Source interrupted", error); }
                    } else if (request.variantKey().equals("queued")) queued.incrementAndGet();
                    return ShaderModuleDescriptor.wgsl(request.variantKey(), SOURCE);
                });
                return device.prepareRenderPipeline(new ShaderPipelineRequest(source,
                        new RenderPipelineDescriptor().colorFormat(context.surfaceFormat()), ShaderPassId.FORWARD, 0));
            }
        };
        try {
            var readyHandle = service.request(provider, request("ready"));
            await(() -> { service.update(); return readyHandle.state() == ShaderPreparationState.READY; });
            var pipeline = readyHandle.readyPass().pipeline();
            var held = service.request(provider, request("held"));
            var waiting = service.request(provider, request("queued"));
            service.update(); await(() -> entered.getCount() == 0);
            VulkanLossFixture.arm(nativeContext, 4, -4);
            expect(GraphicsContextLostException.class, context::beginFrame);
            service.update();
            require(readyHandle.state() == ShaderPreparationState.CANCELLED && held.state() == ShaderPreparationState.CANCELLED
                    && waiting.state() == ShaderPreparationState.CANCELLED, "Loss did not cancel all shader leases");
            require(pipeline.isDisposed() && readyHandle.readyPass() == null, "Ready pipeline survived loss");
            var drain = service.disposeAsync(); require(!drain.isDone(), "Held source released early");
            context.dispose(); require(VulkanLossFixture.destructions() == 0, "Device destroyed with source still active");
            release.countDown(); await(() -> { service.update(); return drain.isDone(); }); drain.get();
            require(queued.get() == 0, "Cancelled queued generator ran");
            readyHandle.dispose(); held.dispose(); waiting.dispose();
        } finally {
            release.countDown(); service.disposeAsync();
            await(() -> { service.update(); return !service.hasPendingWork(); });
        }
    }

    private RenderPass draw() {
        var frame = context.currentFrame();
        var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
        pass.setPipeline(ready.pass().pipeline()); pass.draw(3, 1, 0, 0); return pass;
    }
    private ShaderPreparationOperation prepare(String label) {
        var operation = device.prepareRenderPipeline(new ShaderPipelineRequest(ShaderModuleDescriptor.wgsl(label, SOURCE),
                new RenderPipelineDescriptor().colorFormat(context.surfaceFormat()), ShaderPassId.FORWARD, 0));
        operations.add(operation); return operation;
    }
    private ShaderRequest request(String variant) {
        return ShaderRequest.builder(ShaderPassId.FORWARD).variantKey(variant)
                .renderPass(RenderPassCompatibility.layout(RenderTargetLayout.color(context.surfaceFormat()))).build();
    }
    private void assertRetired() {
        Object preparation = field(device, "preparation"), executor = field(preparation, "executor");
        await(() -> ((ThreadPoolExecutor) field(executor, "executor")).isTerminated());
        require(((Set<?>) field(preparation, "jobs")).isEmpty(), "Preparation retained cancelled jobs");
    }
    private static void await(BooleanSupplier done) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            try { Thread.sleep(1); } catch (InterruptedException error) { throw new FdxException("Wait interrupted", error); }
        }
        require(done.getAsBoolean(), "Validation deadline exceeded");
    }
    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); } catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new FdxException("Expected " + type.getSimpleName(), error);
        }
        throw new FdxException("Missing " + type.getSimpleName());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new FdxException(message); }
    private static Object field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (ReflectiveOperationException error) { throw new FdxException("Probe could not inspect " + name, error); }
    }
}
