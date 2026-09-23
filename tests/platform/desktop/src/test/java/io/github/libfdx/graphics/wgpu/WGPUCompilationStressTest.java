package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.ServiceLoader;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real-driver stress. Stack samples must bracket the lifecycle action inside the same
 * native pipeline call. Merely seeing PIPELINE, or cancelling source/translation, is not a pass. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "libfdx.test.nativeWgpuPreparation", matches = "true")
@Timeout(90)
final class WGPUCompilationStressTest {
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final String VERTEX = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            """;
    private static final String SIMPLE = VERTEX
            + "@fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }";

    @ParameterizedTest
    @ValueSource(strings = {"Vulkan", "D3D12"})
    void cancelDuringNativePipelineCall(String backend) throws Exception { nativeOverlap(backend, false); }

    @ParameterizedTest
    @ValueSource(strings = {"Vulkan", "D3D12"})
    void shutdownDuringNativePipelineCall(String backend) throws Exception { nativeOverlap(backend, true); }

    private void nativeOverlap(String backend, boolean shutdown) throws Exception {
        int proven = 0;
        // Missed sampling windows are recorded and retried with a fresh shader, never counted as overlap.
        for (int attempt = 0; attempt < 6 && proven < 3; attempt++) {
            NativeScenario scenario = new NativeScenario(backend, shutdown, attempt);
            try {
                run(backend, scenario);
                if (shutdown) scenario.after = nativeStack(scenario.worker, backend);
                if (shutdown && scenario.after.length != 0)
                    assertFalse(scenario.context.nativeDevice().isDisposed(), "Device released inside native call");
                assertThrows(FdxException.class, () -> scenario.device.prepareRenderPipeline(scenario.request));
                boolean overlap = scenario.before.length != 0 && scenario.after.length != 0;
                await(scenario.operation::isDone, "Native operation did not drain");
                assertThrows(CancellationException.class, scenario.operation::finish);
                scenario.operation.dispose(); scenario.operation.dispose();
                assertTrue(scenario.operation.isDisposed());
                assertRetired(scenario.context);
                if (overlap) {
                    assertNull(field(scenario.operation, "failure"), "Native completion/cleanup failed during cancellation");
                    proven++;
                    System.out.println("NATIVE_OVERLAP_PASS bridge=" + bridge() + " backend=" + backend + " action="
                            + (shutdown ? "shutdown" : "cancel") + " attempt=" + attempt);
                    System.out.println("BEFORE " + Arrays.toString(scenario.before));
                    System.out.println("AFTER " + Arrays.toString(scenario.after));
                } else System.out.println("NATIVE_OVERLAP_MISSED backend=" + backend + " attempt=" + attempt);
            } finally { if (scenario.operation != null) scenario.operation.cancel(); }
        }
        assertEquals(3, proven, "Could not prove three native-call overlaps on this driver");
        renderFreshSession(backend, shutdown ? "shutdown" : "cancel");
    }

    private static final class NativeScenario extends ApplicationAdapter {
        final String backend;
        final boolean shutdown;
        final int steps;
        volatile Thread worker;
        GraphicsDevice device;
        WGPUContext context;
        ShaderPipelineRequest request;
        ShaderPreparationOperation operation;
        StackTraceElement[] before = {}, after = {};

        NativeScenario(String backend, boolean shutdown, int attempt) {
            this.backend = backend; this.shutdown = shutdown; this.steps = 512 + attempt * 128;
        }
        @Override
        public void create(Fdx fdx) {
            device = fdx.graphics().main().device(); context = fdx.graphics().main().as();
            String source = complexSource(steps);
            request = packet(fdx, ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                worker = Thread.currentThread();
                return ShaderModuleDescriptor.wgsl("native overlap", source);
            }));
            operation = device.prepareRenderPipeline(request);
            long expires = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            StackTraceElement[] observed = {};
            while (!operation.isDone() && System.nanoTime() < expires) {
                if (operation.phase() == ShaderPreparationPhase.PIPELINE) {
                    if (observed.length == 0) observed = worker.getStackTrace();
                    before = nativeStack(worker, backend);
                    if (before.length != 0) break;
                }
                LockSupport.parkNanos(100_000);
            }
            if (before.length == 0) {
                System.out.println("UNPROVEN phase=" + operation.phase() + " done=" + operation.isDone()
                        + " pipelineSample=" + Arrays.toString(observed));
                if (operation.isDone()) {
                    // Expose preparation errors instead of hiding them behind subsequent cancellation.
                    try { assertNull(field(operation, "failure"), "Preparation failed before native sampling"); }
                    catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                }
            }
            if (!shutdown || before.length == 0) {
                operation.cancel(); operation.cancel();
                after = nativeStack(worker, backend);
            }
            fdx.app().requestExit(); // Backend retirement runs before start() returns; no render/GPU work to wait for.
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Vulkan", "D3D12"})
    void queueOverflowAndRepeatedCancellationDrainEveryAcceptedJob(String backend) throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ArrayList<ShaderPreparationOperation> accepted = new ArrayList<>();
        AtomicInteger queuedSources = new AtomicInteger();
        WGPUContext[] capturedContext = new WGPUContext[1];
        try {
            run(backend, new ApplicationAdapter() {
                @Override
                public void create(Fdx fdx) {
                    GraphicsDevice device = fdx.graphics().main().device(); capturedContext[0] = fdx.graphics().main().as();
                    accepted.add(device.prepareRenderPipeline(packet(fdx,
                            ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                                entered.countDown();
                                try { assertTrue(release.await(15, TimeUnit.SECONDS), "Source release timed out"); }
                                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
                                return ShaderModuleDescriptor.wgsl("held", SIMPLE);
                            }))));
                    await(() -> entered.getCount() == 0, "Worker did not start");
                    ShaderPipelineRequest queued = packet(fdx, ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                        queuedSources.incrementAndGet(); return ShaderModuleDescriptor.wgsl("queued", SIMPLE);
                    }));
                    for (int i = 0; i < 256; i++) accepted.add(device.prepareRenderPipeline(queued));
                    ShaderPreparationOperation rejected = device.prepareRenderPipeline(queued);
                    assertTrue(rejected.isDone());
                    assertThrows(RejectedExecutionException.class, rejected::finish);
                    rejected.dispose();
                    for (ShaderPreparationOperation job : accepted) { job.cancel(); job.cancel(); }
                    assertFalse(accepted.getFirst().isDone(), "Cancellation released active inputs early");
                    fdx.app().requestExit();
                }
            });
        } finally { release.countDown(); }
        for (ShaderPreparationOperation job : accepted) {
            await(job::isDone, "Accepted queue entry did not drain");
            assertThrows(CancellationException.class, job::finish);
            job.dispose(); job.dispose();
        }
        assertEquals(257, accepted.size());
        assertEquals(0, queuedSources.get());
        assertRetired(capturedContext[0]);
        System.out.println("QUEUE_PRESSURE_PASS bridge=" + bridge() + " backend=" + backend + " accepted=257 rejected=1 queuedSources=0");
        renderFreshSession(backend, "queue");
    }

    private static StackTraceElement[] nativeStack(Thread worker, String backend) {
        if (worker == null) return new StackTraceElement[0];
        ThreadInfo info = THREADS.getThreadInfo(worker.threadId(), 40);
        if (info == null || !info.isInNative()) return new StackTraceElement[0];
        StackTraceElement[] stack = info.getStackTrace();
        boolean pipeline = false, downcall = false;
        for (StackTraceElement frame : stack) {
            pipeline |= frame.getClassName().equals("com.github.xpenatan.webgpu.WGPUDevice")
                    && frame.getMethodName().equals("createRenderPipeline");
            // FFM hides adapter frames; the native thread state plus this exact downcall
            // distinguishes pipeline creation from translation, shader modules and Java setup.
            downcall |= frame.getClassName().equals("com.github.xpenatan.webgpu.WGPUDevice")
                    && frame.getMethodName().equals("internal_native_CreateRenderPipeline");
        }
        return pipeline && downcall ? stack : new StackTraceElement[0];
    }

    private static String complexSource(int steps) {
        StringBuilder source = new StringBuilder(VERTEX).append("\n@fragment fn fragmentMain(@builtin(position) p: vec4f) -> @location(0) vec4f {\nvar v = p * ")
                .append(0.001 + (System.nanoTime() & 0xffff) * 0.00000001).append(";\n");
        for (int i = 0; i < steps; i++) source.append("v = sin(v.yzwx * 1.00001 + vec4f(")
                .append(0.001 + i * 0.00001).append("));\n");
        return source.append("return abs(v);\n}\n").toString();
    }

    private static ShaderPipelineRequest packet(Fdx fdx, ShaderModuleSource source) {
        return new ShaderPipelineRequest(source, new RenderPipelineDescriptor()
                .renderTargetLayout(RenderTargetLayout.color(fdx.graphics().main().surfaceFormat()))
                .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
    }

    private static GraphicsAttachmentProvider provider(String backend) {
        WGPUProvider provider = new WGPUProvider();
        provider.configuration().backend(backend.equals("Vulkan") ? WGPUBackend.VULKAN : WGPUBackend.D3D12)
                .offscreenReadback(true).vSync(false).preparationWorkerLimit(1);
        return provider;
    }
    private static String bridge() { return System.getProperty("libfdx.test.wgpuBridge", "ffm"); }

    private static void run(String backend, ApplicationAdapter listener) {
        assertEquals(bridge().equals("jni") ? "WGPUJniPreparation" : "WGPUFfmPreparation",
                ServiceLoader.load(WGPUPreparation.class).findFirst().orElseThrow().getClass().getSimpleName());
        new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("Native preparation stress " + backend)
                .size(96, 96).visible(false).vSync(false).graphics(provider(backend)), listener);
    }

    private static void assertRetired(WGPUContext context) throws Exception {
        Object queue = context.preparation();
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field(queue, "executor");
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Compiler worker failed to retire");
        assertTrue(((Set<?>) field(queue, "jobs")).isEmpty(), "Queue retained cancelled jobs");
        assertTrue(context.resourceDomain().isClosed());
        assertEquals(0, field(context.resourceDomain(), "preparationReferences"));
        assertEquals(0, context.resourceDomain().contextReferences());
        assertNull(field(context.resourceDomain(), "nativeRelease"));
        assertTrue(context.nativeDevice().isDisposed(), "Retained native device did not drain");
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) LockSupport.parkNanos(100_000);
        assertTrue(condition.getAsBoolean(), message);
    }

    private static void renderFreshSession(String backend, String name) {
        run(backend, new ApplicationAdapter() {
            Fdx fdx;
            ShaderPreparationOperation job;
            ShaderPreparedResult ready;
            long deadline;
            @Override
            public void create(Fdx fdx) {
                this.fdx = fdx; deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                job = fdx.graphics().main().device().prepareRenderPipeline(packet(fdx,
                        ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("fresh after stress", SIMPLE))));
            }
            @Override
            public void render() {
                assertTrue(System.nanoTime() < deadline, "Fresh session did not prepare");
                if (!job.isDone()) return;
                ready = job.finish(); job.dispose();
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
                    Path output = Path.of("build", "wgpu-native-compilation-stress", bridge() + "-" + backend + "-" + name + ".png");
                    Files.createDirectories(output.getParent()); ImageIO.write(image, "png", output.toFile());
                } catch (Exception error) { throw new AssertionError(error); }
                System.out.println("FRESH_RENDER_PASS backend=" + backend + " after=" + name);
                fdx.app().requestExit();
            }
            @Override
            public void dispose() { if (ready != null) ready.dispose(); else if (job != null) job.cancel(); }
        });
    }
}
