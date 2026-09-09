package io.github.libfdx.backend.desktop;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsConfig;
import io.github.libfdx.graphics.GraphicsContextLostException;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationState;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
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
import org.lwjgl.vulkan.VkDevice;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.*;

/** Injected VkResult at the LWJGL dispatch boundary. The real GPU remains healthy;
 * no invalid GPU command is submitted and no actual device-loss claim is made. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "libfdx.test.nativePreparationLifecycle", matches = "true")
@Timeout(30)
class DesktopVulkanDeviceLossTest {
    private static final FunctionDescriptor PIPELINE = FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    private static final FunctionDescriptor ACQUIRE = FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
    private static final String SOURCE = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;

    @Test void workerLossInvalidatesCompletedResultAndStopsNewPreparation() {
        run(new ApplicationAdapter() {
            @Override public void create(Fdx fdx) {
                GraphicsAttachment context = fdx.graphics().main().as();
                var device = context.device();
                Object originalDomain = device.resourceDomain();
                var completed = device.prepareRenderPipeline(request(context, "completed"));
                await(completed::isDone);
                assertNull(field(completed, "failure"));
                var nativeDevice = (VkDevice) field(context, "device");
                // All work happens before the first frame, so injected loss cannot
                // cause destruction of resources still used by the healthy GPU.
                assertEquals(VK_SUCCESS, vkDeviceWaitIdle(nativeDevice));
                ShaderPreparationOperation failed = null;
                try (NativeFailure injection = new NativeFailure(nativeDevice, "vkCreateGraphicsPipelines", PIPELINE, VK_ERROR_DEVICE_LOST)) {
                    failed = device.prepareRenderPipeline(request(context, "loss"));
                    await(failed::isDone);
                    assertEquals(1, injection.calls.get());
                    assertNotSame(originalDomain, device.resourceDomain(), "Native loss did not invalidate the shader domain");
                    assertThrows(CancellationException.class, completed::finish);
                    assertThrows(GraphicsContextLostException.class,
                            () -> device.prepareRenderPipeline(request(context, "after loss")));
                    assertThrows(GraphicsContextLostException.class, context::beginFrame);
                    assertThrows(GraphicsContextLostException.class, () -> context.resize(80, 80));
                    assertRetired(device);
                } finally {
                    completed.cancel(); await(completed::isDone); completed.dispose();
                    if (failed != null) { failed.cancel(); await(failed::isDone); failed.dispose(); }
                    fdx.app().requestExit();
                }
            }
        });
        renderFreshSession();
    }

    @Test void ordinaryPipelineFailureKeepsTheDeviceUsable() {
        run(new ApplicationAdapter() {
            @Override public void create(Fdx fdx) {
                GraphicsAttachment context = fdx.graphics().main().as();
                var device = context.device();
                Object originalDomain = device.resourceDomain();
                ShaderPreparationOperation failed;
                try (NativeFailure injection = new NativeFailure((VkDevice) field(context, "device"),
                        "vkCreateGraphicsPipelines", PIPELINE, VK_ERROR_OUT_OF_DEVICE_MEMORY)) {
                    failed = device.prepareRenderPipeline(request(context, "ordinary failure"));
                    await(failed::isDone);
                    assertEquals(1, injection.calls.get());
                    assertThrows(RuntimeException.class, failed::finish);
                    assertSame(originalDomain, device.resourceDomain());
                }
                failed.dispose();
                var recovered = device.prepareRenderPipeline(request(context, "valid after error"));
                await(recovered::isDone);
                recovered.finish().dispose(); recovered.dispose();
                fdx.app().requestExit();
            }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void optionalCacheLossMustNotContinueAsAnUncachedSuccess(boolean snapshot) throws Exception {
        Files.createDirectories(Path.of("build"));
        var store = new DesktopShaderCacheStore(Files.createTempDirectory(Path.of("build"), "vulkan-loss-cache-"), 32 * 1024 * 1024);
        var provider = new DesktopVulkanProvider();
        provider.configuration().preparationWorkerLimit(1).shaderCache(new ShaderArtifactCache(store));
        try {
            run(provider, new ApplicationAdapter() {
                @Override public void create(Fdx fdx) {
                    GraphicsAttachment context = fdx.graphics().main().as();
                    var device = context.device();
                    Object originalDomain = device.resourceDomain();
                    String function = snapshot ? "vkGetPipelineCacheData" : "vkCreatePipelineCache";
                    FunctionDescriptor signature = snapshot
                            ? FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                                    ValueLayout.ADDRESS, ValueLayout.ADDRESS)
                            : FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS, ValueLayout.ADDRESS);
                    try (NativeFailure injection = new NativeFailure((VkDevice) field(context, "device"), function,
                            signature, VK_ERROR_DEVICE_LOST)) {
                        var operation = device.prepareRenderPipeline(request(context, "cache loss"));
                        await(operation::isDone);
                        assertEquals(1, injection.calls.get(), "Lost cache operation was retried");
                        assertNotSame(originalDomain, device.resourceDomain());
                        assertThrows(CancellationException.class, operation::finish);
                        operation.dispose();
                        assertRetired(device);
                    } finally { fdx.app().requestExit(); }
                }
            });
            var flushed = store.flushAsync(); await(flushed::isDone); flushed.get();
        } finally { store.dispose(); }
    }

    @Test void acquireLossCancelsReadyHeldAndQueuedShaders() {
        run(new ApplicationAdapter() {
            @Override public void create(Fdx fdx) {
                GraphicsAttachment context = fdx.graphics().main().as();
                var device = context.device();
                var service = new ShaderPreparation(context);
                CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
                AtomicInteger queuedSources = new AtomicInteger();
                ShaderProvider provider = new ShaderProvider() {
                    @Override public GraphicsDevice preparationDevice() { return device; }
                    @Override public boolean supports(ShaderRequest request) { return true; }
                    @Override public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
                        var source = ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                            if (request.variantKey().equals("held")) {
                                entered.countDown();
                                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                                catch (InterruptedException failure) { throw new AssertionError(failure); }
                            } else if (request.variantKey().equals("queued")) queuedSources.incrementAndGet();
                            return ShaderModuleDescriptor.wgsl(request.variantKey(), SOURCE);
                        });
                        return device.prepareRenderPipeline(new ShaderPipelineRequest(source,
                                new RenderPipelineDescriptor().colorFormat(context.surfaceFormat()), ShaderPassId.FORWARD, 0));
                    }
                };
                try {
                    var ready = service.request(provider, shaderRequest(context, "ready"));
                    await(() -> { service.update(); return ready.state() == ShaderPreparationState.READY; });
                    var pipeline = ready.readyPass().pipeline();
                    var held = service.request(provider, shaderRequest(context, "held"));
                    var queued = service.request(provider, shaderRequest(context, "queued"));
                    service.update(); await(() -> entered.getCount() == 0);
                    try (NativeFailure injection = new NativeFailure((VkDevice) field(context, "device"),
                            "vkAcquireNextImageKHR", ACQUIRE, VK_ERROR_DEVICE_LOST)) {
                        assertThrows(GraphicsContextLostException.class, context::beginFrame);
                        assertEquals(1, injection.calls.get());
                    }
                    service.update();
                    assertTrue(service.isDisposed());
                    assertEquals(ShaderPreparationState.CANCELLED, ready.state());
                    assertEquals(ShaderPreparationState.CANCELLED, held.state());
                    assertEquals(ShaderPreparationState.CANCELLED, queued.state());
                    assertNull(ready.readyPass()); assertTrue(pipeline.isDisposed());
                    var drain = service.disposeAsync();
                    assertFalse(drain.isDone(), "Held source inputs were retired before the worker returned");
                    release.countDown();
                    await(() -> { service.update(); return drain.isDone(); }); drain.get();
                    assertEquals(0, queuedSources.get()); assertRetired(device);
                    ready.dispose(); held.dispose(); queued.dispose();
                } finally {
                    release.countDown(); service.disposeAsync();
                    await(() -> { service.update(); return !service.hasPendingWork(); });
                    fdx.app().requestExit();
                }
            }
        });
    }

    @Test void lossClosesPreparationForEverySharedContext() {
        var provider = new DesktopVulkanProvider(); provider.configuration().preparationWorkerLimit(1);
        run(provider, new ApplicationAdapter() {
            @Override public void create(Fdx fdx) {
                GraphicsAttachment main = fdx.graphics().main().as();
                var display = fdx.displays().create(new DisplayConfig().size(64, 64).visible(false).vSync(false));
                var secondary = fdx.graphics().create(GraphicsConfig.provider(provider).display(display));
                assertSame(main.device().resourceDomain(), secondary.device().resourceDomain());
                var first = main.device().prepareRenderPipeline(request(main, "main"));
                var second = secondary.device().prepareRenderPipeline(request(secondary, "secondary"));
                await(first::isDone); await(second::isDone);
                try (NativeFailure injection = new NativeFailure((VkDevice) field(main, "device"),
                        "vkAcquireNextImageKHR", ACQUIRE, VK_ERROR_DEVICE_LOST)) {
                    assertThrows(GraphicsContextLostException.class, main::beginFrame);
                    assertThrows(GraphicsContextLostException.class, secondary::beginFrame);
                    assertThrows(CancellationException.class, first::finish);
                    assertThrows(CancellationException.class, second::finish);
                    assertRetired(main.device()); assertRetired(secondary.device());
                } finally {
                    first.dispose(); second.dispose();
                    fdx.graphics().destroy(secondary); fdx.displays().destroy(display);
                    fdx.app().requestExit();
                }
            }
        });
    }

    @Test void presentationLossIsNotHiddenByAPendingResize() {
        run(new ApplicationAdapter() {
            @Override public void create(Fdx fdx) {
                GraphicsAttachment context = fdx.graphics().main().as();
                var nativeDevice = (VkDevice) field(context, "device");
                assertTrue(context.beginFrame());
                context.clear(0, 0, 0, 1); context.resize(80, 80);
                var signature = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
                // Drain the real submission before reporting fake loss, so teardown
                // never destroys objects in use by the still-healthy GPU.
                try (NativeFailure injection = new NativeFailure(nativeDevice, "vkQueuePresentKHR", signature,
                        VK_ERROR_DEVICE_LOST, () -> assertEquals(VK_SUCCESS, vkDeviceWaitIdle(nativeDevice)))) {
                    assertThrows(GraphicsContextLostException.class, context::endFrame);
                    assertEquals(1, injection.calls.get());
                    assertTrue(((DesktopVulkanProvider.VulkanResourceDomain) field(context, "resourceDomain")).isLost());
                } finally { fdx.app().requestExit(); }
            }
        });
    }

    @Test void lossDuringAnOpenPassRejectsDrawingAndReleasesRecordedResources() {
        AtomicReference<Object> allocation = new AtomicReference<>();
        run(new ApplicationAdapter() {
            @Override public void create(Fdx fdx) {
                GraphicsAttachment context = fdx.graphics().main().as();
                var operation = context.device().prepareRenderPipeline(request(context, "recorded"));
                await(operation::isDone);
                var ready = operation.finish(); operation.dispose();
                allocation.set(field(ready.pass().pipeline(), "allocation"));
                try {
                    assertTrue(context.beginFrame());
                    var frame = context.currentFrame();
                    var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                            .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
                    pass.setPipeline(ready.pass().pipeline()); pass.draw(3, 1, 0, 0);
                    var signature = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
                    try (NativeFailure injection = new NativeFailure((VkDevice) field(context, "device"),
                            "vkCreateBuffer", signature, VK_ERROR_DEVICE_LOST)) {
                        assertThrows(GraphicsContextLostException.class,
                                () -> context.device().createBuffer(BufferDescriptor.vertex("injected loss", 48)));
                        assertEquals(1, injection.calls.get());
                        assertThrows(GraphicsContextLostException.class, () -> pass.draw(3, 1, 0, 0));
                        assertDoesNotThrow(pass::end);
                        assertThrows(GraphicsContextLostException.class, context::endFrame);
                    }
                } finally { ready.dispose(); fdx.app().requestExit(); }
            }
        });
        try {
            Field released = allocation.get().getClass().getSuperclass().getDeclaredField("released");
            released.setAccessible(true); assertTrue(released.getBoolean(allocation.get()));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static ShaderRequest shaderRequest(GraphicsAttachment context, String variant) {
        return ShaderRequest.builder(ShaderPassId.FORWARD).variantKey(variant)
                .renderPass(RenderPassCompatibility.layout(RenderTargetLayout.color(context.surfaceFormat()))).build();
    }

    private static void assertRetired(GraphicsDevice device) {
        Object queue = field(device, "preparation");
        try { assertTrue(((ThreadPoolExecutor) field(queue, "executor")).awaitTermination(10, TimeUnit.SECONDS)); }
        catch (InterruptedException failure) { throw new AssertionError(failure); }
        assertTrue(((Set<?>) field(queue, "jobs")).isEmpty());
        assertEquals(0, field(field(queue, "domain"), "preparationReferences"));
    }

    private static void renderFreshSession() {
        run(new ApplicationAdapter() {
            @Override public void create(Fdx fdx) {
                GraphicsAttachment context = fdx.graphics().main().as();
                var operation = context.device().prepareRenderPipeline(request(context, "fresh session"));
                await(operation::isDone);
                var ready = operation.finish(); operation.dispose();
                try {
                    assertTrue(context.beginFrame());
                    var frame = context.currentFrame();
                    var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                            .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
                    try { pass.setPipeline(ready.pass().pipeline()); pass.draw(3, 1, 0, 0); }
                    finally { pass.end(); }
                    var pixels = frame.frameBuffer().readPixelsRgba8();
                    int width = frame.width(), height = frame.height(), center = (height / 2 * width + width / 2) * 4;
                    assertEquals(0, pixels.get(center) & 255); assertEquals(255, pixels.get(center + 1) & 255);
                    assertEquals(0, pixels.get(center + 2) & 255); assertEquals(0, pixels.get(1) & 255);
                    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                        int offset = (y * width + x) * 4;
                        image.setRGB(x, height - 1 - y, ((pixels.get(offset) & 255) << 16)
                                | ((pixels.get(offset + 1) & 255) << 8) | (pixels.get(offset + 2) & 255));
                    }
                    try {
                        Path output = Path.of("build", "desktop-vulkan-loss", "fresh-session.png");
                        Files.createDirectories(output.getParent()); ImageIO.write(image, "png", output.toFile());
                    } catch (Exception failure) { throw new AssertionError(failure); }
                } finally { ready.dispose(); fdx.app().requestExit(); }
            }
        });
    }

    private static ShaderPipelineRequest request(GraphicsAttachment context, String label) {
        return new ShaderPipelineRequest(ShaderModuleDescriptor.wgsl(label, SOURCE),
                new RenderPipelineDescriptor().colorFormat(context.surfaceFormat()), ShaderPassId.FORWARD, 0);
    }
    private static void run(ApplicationAdapter listener) {
        var provider = new DesktopVulkanProvider(); provider.configuration().preparationWorkerLimit(1);
        run(provider, listener);
    }
    private static void run(DesktopVulkanProvider provider, ApplicationAdapter listener) {
        new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("Vulkan injected loss")
                .size(64, 64).visible(false).vSync(false).graphics(provider), listener);
    }
    private static void await(BooleanSupplier done) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!done.getAsBoolean() && System.nanoTime() < deadline) LockSupport.parkNanos(1_000_000);
        assertTrue(done.getAsBoolean(), "Preparation did not settle");
    }
    private static Object field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static final class NativeFailure implements AutoCloseable {
        private final Arena arena = Arena.ofShared();
        private final Object capabilities;
        private final Field pointer;
        private final long original;
        private final int result;
        private final Runnable beforeFailure;
        private Throwable callbackFailure;
        final AtomicInteger calls = new AtomicInteger();
        NativeFailure(VkDevice device, String function, FunctionDescriptor signature, int result) {
            this(device, function, signature, result, () -> { });
        }
        NativeFailure(VkDevice device, String function, FunctionDescriptor signature, int result, Runnable beforeFailure) {
            this.beforeFailure = beforeFailure;
            this.result = result; capabilities = device.getCapabilities();
            try {
                pointer = capabilities.getClass().getField(function);
                pointer.setAccessible(true); original = pointer.getLong(capabilities);
                var callback = MethodHandles.lookup().findVirtual(NativeFailure.class, "fail",
                        MethodType.methodType(int.class)).bindTo(this);
                callback = MethodHandles.dropArguments(callback, 0, signature.toMethodType().parameterList());
                pointer.setLong(capabilities, Linker.nativeLinker().upcallStub(callback, signature, arena).address());
            } catch (ReflectiveOperationException failure) { arena.close(); throw new AssertionError(failure); }
        }
        @SuppressWarnings("unused")
        public int fail() {
            // Never unwind a Java exception through the native upcall boundary.
            try { beforeFailure.run(); } catch (Throwable failure) { callbackFailure = failure; }
            calls.incrementAndGet(); return result;
        }
        @Override public void close() {
            try { pointer.setLong(capabilities, original); }
            catch (IllegalAccessException failure) { throw new AssertionError(failure); }
            finally { arena.close(); }
            assertNull(callbackFailure, "Native failure callback threw");
        }
    }
}
