package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationState;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Actual process-local removal, never a GPU reset or an intentionally invalid GPU command. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "libfdx.test.nativePreparationLifecycle", matches = "true")
@Timeout(45)
final class D3D12DeviceLossTest {
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final String SIMPLE = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;

    @Test void removedDeviceCannotPublishAnAlreadyCompiledPipeline() {
        run(new ApplicationAdapter() {
            @Override public void create(Fdx fdx) {
                D3D12Context context = fdx.graphics().main().as();
                var operation = context.device().prepareRenderPipeline(packet(context, ShaderModuleSource.fixed(
                        ShaderModuleDescriptor.wgsl("ready before removal", SIMPLE))));
                try {
                    await(operation::isDone, "Pipeline did not complete before removal");
                    try {
                        assertNull(field(operation, "failure"), "Compilation failed before device removal");
                        assertNotNull(((AtomicReference<?>) field(operation, "output")).get(), "No native result awaiting publication");
                    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                    removeDevice(context);
                    ShaderPreparedResult[] unexpected = new ShaderPreparedResult[1];
                    try { assertThrows(RuntimeException.class, () -> unexpected[0] = operation.finish(),
                            "A pipeline must not publish after its native device was removed"); }
                    finally { if (unexpected[0] != null) unexpected[0].dispose(); }
                } finally { operation.cancel(); operation.dispose(); fdx.app().requestExit(); }
            }
        });
        renderFreshSession("unpublished");
    }

    @Test void removalInvalidatesReadyResultsAndCancelsQueuedAndRunningSource() {
        run(new ApplicationAdapter() {
            @Override public void create(Fdx fdx) {
                D3D12Context context = fdx.graphics().main().as();
                GraphicsDevice device = context.device();
                Object originalDomain = device.resourceDomain();
                ShaderPreparation service = new ShaderPreparation(context);
                CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
                AtomicInteger queuedSources = new AtomicInteger();
                ShaderProvider provider = new ShaderProvider() {
                    @Override public GraphicsDevice preparationDevice() { return device; }
                    @Override public boolean supports(ShaderRequest request) { return true; }
                    @Override public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
                        return device.prepareRenderPipeline(packet(context, ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                            if (request.variantKey().equals("held")) {
                                entered.countDown();
                                try { assertTrue(release.await(20, TimeUnit.SECONDS), "Source hold timed out"); }
                                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
                            } else if (request.variantKey().equals("queued")) queuedSources.incrementAndGet();
                            return ShaderModuleDescriptor.wgsl(request.variantKey(), SIMPLE);
                        })));
                    }
                };
                try {
                    var ready = service.request(provider, request(context, "ready"));
                    await(() -> { service.update(); return ready.state() == ShaderPreparationState.READY; }, "No ready result");
                    var pipeline = ready.readyPass().pipeline();
                    var held = service.request(provider, request(context, "held"));
                    var queued = service.request(provider, request(context, "queued"));
                    service.update();
                    await(() -> entered.getCount() == 0, "Source did not start");
                    removeDevice(context);
                    service.update();
                    assertTrue(service.isDisposed());
                    assertNotSame(originalDomain, device.resourceDomain());
                    assertSame(device.resourceDomain(), device.resourceDomain(), "Loss must invalidate the domain once");
                    assertEquals(ShaderPreparationState.CANCELLED, ready.state());
                    assertEquals(ShaderPreparationState.CANCELLED, held.state());
                    assertEquals(ShaderPreparationState.CANCELLED, queued.state());
                    assertNull(ready.readyPass()); assertTrue(pipeline.isDisposed());
                    assertThrows(FdxException.class, () -> device.prepareRenderPipeline(packet(context,
                            ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("late", SIMPLE)))));
                    assertThrows(FdxException.class, () -> service.request(provider, request(context, "late")));
                    var drain = service.disposeAsync();
                    AtomicInteger callbacks = new AtomicInteger();
                    drain.onSuccess(ignored -> callbacks.incrementAndGet());
                    assertFalse(drain.isDone(), "Source inputs were released before the held worker returned");
                    release.countDown();
                    await(() -> { service.update(); return drain.isDone(); }, "Removed-device work did not drain");
                    service.update();
                    assertEquals(1, callbacks.get()); assertEquals(0, queuedSources.get());
                    assertRetired(device);
                    ready.dispose(); held.dispose(); queued.dispose();
                    System.out.println("D3D12_LOSS_PASS ready=cancelled source=cancelled queued=not-run drain=complete");
                } finally {
                    release.countDown(); service.disposeAsync();
                    await(() -> { service.update(); return !service.hasPendingWork(); }, "Cleanup did not drain");
                    fdx.app().requestExit();
                }
            }
        });
        renderFreshSession("queued-source");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void removalDuringAnActiveFrameStillReleasesContextAndResources(boolean endPass) {
        run(new ApplicationAdapter() {
            @Override public void create(Fdx fdx) {
                D3D12Context context = fdx.graphics().main().as();
                var operation = context.device().prepareRenderPipeline(packet(context,
                        ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("active frame", SIMPLE))));
                await(operation::isDone, "Pipeline did not complete");
                var ready = operation.finish(); operation.dispose();
                var buffer = context.device().createBuffer(BufferDescriptor.staticVertex("lost buffer", 32));
                long nativeHandle = context.nativeHandle();
                try {
                    assertTrue(context.beginFrame());
                    var frame = context.currentFrame();
                    var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                            .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
                    pass.setPipeline(ready.pass().pipeline()); pass.draw(3, 1, 0, 0);
                    if (endPass) pass.end();
                    removeDevice(context);
                    context.dispose(); context.dispose();
                    assertTrue(context.isDisposed()); assertTrue(buffer.isDisposed());
                    assertTrue(ready.pass().pipeline().isDisposed());
                    assertThrows(FdxException.class, () -> D3D12Native.deviceRemovedReason(nativeHandle));
                    assertThrows(FdxException.class, context::beginFrame);
                    assertRetired(context.device());
                    System.out.println("D3D12_LOSS_PASS active-frame=context-and-resources-released endPass=" + endPass);
                } finally { ready.dispose(); buffer.dispose(); context.dispose(); fdx.app().requestExit(); }
            }
        });
        renderFreshSession(endPass ? "active-frame" : "active-pass");
    }

    @Test @Timeout(120) void repeatedRemovalDuringNativePipelineCreationDrainsRetainedDeviceJobs() {
        int proven = 0;
        for (int attempt = 0; attempt < 6 && proven < 3; attempt++) {
            NativeRemoval scenario = new NativeRemoval(512 + attempt * 128);
            try {
                run(scenario);
                await(scenario.operation::isDone, "Removed-device native call did not drain");
                assertThrows(CancellationException.class, scenario.operation::finish);
                scenario.operation.dispose(); scenario.operation.dispose();
                assertRetired(scenario.device);
                if (scenario.before.length > 0 && scenario.after.length > 0) {
                    proven++;
                    System.out.println("D3D12_NATIVE_LOSS_PASS overlap=" + proven + " attempt=" + attempt
                            + " before=" + Arrays.toString(scenario.before) + " after=" + Arrays.toString(scenario.after));
                } else System.out.println("D3D12_NATIVE_LOSS_UNPROVEN attempt=" + attempt);
            } finally { if (scenario.operation != null) scenario.operation.cancel(); }
        }
        assertEquals(3, proven, "Need three actual native calls still active across removal");
        renderFreshSession("native-pipeline");
    }

    private static final class NativeRemoval extends ApplicationAdapter {
        final int steps;
        volatile Thread worker;
        GraphicsDevice device;
        ShaderPreparationOperation operation;
        StackTraceElement[] before = {}, after = {};

        NativeRemoval(int steps) { this.steps = steps; }
        @Override public void create(Fdx fdx) {
            D3D12Context context = fdx.graphics().main().as();
            device = context.device();
            Object domain = device.resourceDomain();
            String source = complexSource(steps);
            operation = device.prepareRenderPipeline(packet(context, ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                worker = Thread.currentThread();
                return ShaderModuleDescriptor.wgsl("remove during native creation", source);
            })));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!operation.isDone() && System.nanoTime() < deadline) {
                if (operation.phase() == ShaderPreparationPhase.PIPELINE) {
                    before = nativePipelineStack(worker);
                    if (before.length > 0) break;
                }
                LockSupport.parkNanos(100_000);
            }
            if (before.length > 0) {
                removeDevice(context);
                after = nativePipelineStack(worker);
                assertNotSame(domain, device.resourceDomain());
                assertThrows(FdxException.class, () -> device.prepareRenderPipeline(packet(context,
                        ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("late native request", SIMPLE)))));
            } else operation.cancel();
            fdx.app().requestExit();
        }
    }

    private static StackTraceElement[] nativePipelineStack(Thread worker) {
        if (worker == null) return new StackTraceElement[0];
        ThreadInfo info = THREADS.getThreadInfo(worker.threadId(), 40);
        if (info == null || !info.isInNative()) return new StackTraceElement[0];
        StackTraceElement[] frames = info.getStackTrace();
        boolean pipeline = false, downcall = false;
        for (StackTraceElement frame : frames) {
            pipeline |= frame.getClassName().equals(D3D12PipelineCache.class.getName()) && frame.getMethodName().equals("create");
            downcall |= frame.getClassName().equals(D3D12Ffm.class.getName()) && frame.getMethodName().equals("comIntAAAA");
        }
        return pipeline && downcall ? frames : new StackTraceElement[0];
    }

    private static String complexSource(int steps) {
        StringBuilder source = new StringBuilder(SIMPLE.substring(0, SIMPLE.indexOf("@fragment")))
                .append("@fragment fn fragmentMain(@builtin(position) p: vec4f) -> @location(0) vec4f { var v = p * ")
                .append(0.001 + (System.nanoTime() & 0xffff) * 0.00000001).append(";\n");
        for (int i = 0; i < steps; i++) source.append("v = sin(v.yzwx * 1.00001 + vec4f(")
                .append(0.001 + i * 0.00001).append("));\n");
        return source.append("return abs(v); }\n").toString();
    }

    private static ShaderRequest request(D3D12Context context, String variant) {
        return ShaderRequest.builder(ShaderPassId.FORWARD).variantKey(variant)
                .renderPass(RenderPassCompatibility.layout(RenderTargetLayout.color(context.surfaceFormat()))).build();
    }

    private static void assertRetired(GraphicsDevice device) {
        try {
            Object queue = field(device, "preparation");
            ThreadPoolExecutor executor = (ThreadPoolExecutor) field(queue, "executor");
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Compiler worker did not retire");
            assertTrue(((Set<?>) field(queue, "jobs")).isEmpty(), "Queue retained removed-device jobs");
        } catch (ReflectiveOperationException | InterruptedException error) { throw new AssertionError(error); }
    }
    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }

    private static void renderFreshSession(String name) {
        run(new ApplicationAdapter() {
            Fdx fdx;
            ShaderPreparationOperation operation;
            ShaderPreparedResult ready;
            long deadline;
            @Override public void create(Fdx fdx) {
                this.fdx = fdx;
                D3D12Context context = fdx.graphics().main().as();
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                operation = context.device().prepareRenderPipeline(packet(context,
                        ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("recovery", SIMPLE))));
            }
            @Override public void render() {
                assertTrue(System.nanoTime() < deadline, "Fresh session did not prepare");
                if (!operation.isDone()) return;
                ready = operation.finish(); operation.dispose();
                var frame = fdx.graphics().main().currentFrame();
                int width = frame.width(), height = frame.height();
                var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                        .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
                try { pass.setPipeline(ready.pass().pipeline()); pass.draw(3, 1, 0, 0); }
                finally { pass.end(); }
                var pixels = frame.frameBuffer().readPixelsRgba8();
                int center = (height / 2 * width + width / 2) * 4;
                assertEquals(0, pixels.get(center) & 255); assertEquals(255, pixels.get(center + 1) & 255);
                assertEquals(0, pixels.get(center + 2) & 255); assertEquals(0, pixels.get(1) & 255);
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int offset = (y * width + x) * 4;
                    image.setRGB(x, height - 1 - y, ((pixels.get(offset) & 255) << 16)
                            | ((pixels.get(offset + 1) & 255) << 8) | (pixels.get(offset + 2) & 255));
                }
                try {
                    Path output = Path.of("build", "shader-device-loss", name + ".png");
                    Files.createDirectories(output.getParent()); ImageIO.write(image, "png", output.toFile());
                } catch (Exception error) { throw new AssertionError(error); }
                System.out.println("D3D12_RECOVERY_PASS after=" + name);
                fdx.app().requestExit();
            }
            @Override public void dispose() { if (ready != null) ready.dispose(); else if (operation != null) operation.cancel(); }
        });
    }

    private static void removeDevice(D3D12Context context) {
        MemorySegment device = D3D12Native.retainPreparationDevice(context.nativeHandle());
        try (Arena arena = Arena.ofConfined()) {
            // ID3D12Device5 GUID and Windows vtable slots verified against Microsoft's d3d12.h.
            MemorySegment iid = arena.allocate(16, 4);
            iid.set(ValueLayout.JAVA_INT, 0, 0x8b4f173b);
            iid.set(ValueLayout.JAVA_SHORT, 4, (short) 0x2fea);
            iid.set(ValueLayout.JAVA_SHORT, 6, (short) 0x4b80);
            byte[] tail = {(byte) 0x8f, 0x58, 0x43, 0x07, 0x19, 0x1a, (byte) 0xb9, 0x5d};
            for (int i = 0; i < tail.length; i++) iid.set(ValueLayout.JAVA_BYTE, 8 + i, tail[i]);
            MemorySegment output = arena.allocate(ValueLayout.ADDRESS);
            D3D12Ffm.check(D3D12Ffm.comIntAAA(device, 0, iid, output), "ID3D12Device5 unavailable");
            MemorySegment device5 = output.get(ValueLayout.ADDRESS, 0);
            try {
                assertEquals(0, D3D12Ffm.comIntA(device, 37), "Device was already removed");
                D3D12Ffm.comVoidA(device5, 58); // ID3D12Device5.RemoveDevice, test only.
                int reason = D3D12Ffm.comIntA(device, 37);
                assertTrue(reason < 0, "RemoveDevice did not report native device loss");
                System.out.println("D3D12_REMOVED reason=0x" + Integer.toHexString(reason));
            } finally { D3D12Ffm.release(device5); }
        } finally { D3D12Ffm.release(device); }
    }

    private static ShaderPipelineRequest packet(D3D12Context context, ShaderModuleSource source) {
        return new ShaderPipelineRequest(source, new RenderPipelineDescriptor()
                .renderTargetLayout(RenderTargetLayout.color(context.surfaceFormat()))
                .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
    }
    private static void run(ApplicationAdapter listener) {
        D3D12Provider provider = new D3D12Provider();
        provider.configuration().shaderPreparationWorkers(1).optimizeShaders(false);
        new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("D3D12 device loss")
                .size(96, 96).visible(false).vSync(false).graphics(provider), listener);
    }
    private static void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) LockSupport.parkNanos(100_000);
        assertTrue(condition.getAsBoolean(), message);
    }
}
