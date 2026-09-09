package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUCommandEncoder;
import com.github.xpenatan.webgpu.WGPUCommandEncoderDescriptor;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
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
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "libfdx.test.nativeWgpuAsync", matches = "true")
@Timeout(25)
final class WGPUDeviceLossTest {
    private static final String SOURCE = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;

    @ParameterizedTest @ValueSource(strings = {"source", "native", "unpublished", "published", "frame"})
    void nativeLossInvalidatesPreparationAndDrainsSafely(String stage) throws Exception {
        WGPUContext[] retained = {null};
        ShaderPreparationOperation[] operation = {null};
        ShaderPreparedResult[] result = {null};
        RenderPass[] pass = {null};
        CountDownLatch resume = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        WGPUProvider provider = new WGPUProvider().loaderBackend(WGPULoaderBackend.DAWN).backend(WGPUBackend.D3D12);
        provider.configuration().offscreenReadback(true).processEventsEachFrame(false).preparationWorkerLimit(1);
        try {
            new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("WGPU device loss")
                    .size(128, 128).visible(false).vSync(false).graphics(provider), new ApplicationAdapter() {
                @Override public void create(Fdx fdx) {
                    GraphicsContext graphics = fdx.graphics().main();
                    WGPUContext context = graphics.as();
                    retained[0] = context;
                    Object identity = graphics.device().resourceDomain();
                    try {
                        Object dawn = field(context.preparation(), "dawn");
                        List<?> pending = (List<?>) field(dawn, "pending");
                        if (stage.equals("native")) {
                            ScheduledThreadPoolExecutor events = (ScheduledThreadPoolExecutor) field(dawn, "events");
                            events.execute(() -> waitLatch(resume));
                        }
                        ShaderModuleSource source = stage.equals("source")
                                ? ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                                    entered.countDown(); waitLatch(resume);
                                    return ShaderModuleDescriptor.wgsl("loss", SOURCE);
                                }) : ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("loss", SOURCE));
                        ShaderPipelineRequest request = new ShaderPipelineRequest(source,
                                new RenderPipelineDescriptor().renderTargetLayout(RenderTargetLayout.color(graphics.surfaceFormat()))
                                        .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
                        operation[0] = graphics.device().prepareRenderPipeline(request);
                        if (stage.equals("source")) waitLatch(entered);
                        else if (stage.equals("native"))
                            await(() -> { synchronized (context.resourceDomain()) { return !pending.isEmpty(); } });
                        else await(operation[0]::isDone);
                        if (stage.equals("published") || stage.equals("frame")) result[0] = operation[0].finish();
                        if (stage.equals("frame")) {
                            assertTrue(context.beginFrame());
                            var frame = context.currentFrame();
                            pass[0] = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                                    .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
                            pass[0].setPipeline(result[0].pass().pipeline());
                        }

                        // A real Dawn Destroyed notification, without invalid GPU commands or a host reset.
                        context.nativeDevice().destroy();
                        await(() -> {
                            synchronized (context.resourceDomain()) { context.nativeInstance().processEvents(); }
                            return context.resourceDomain().deviceLoss() != null;
                        });
                        assertFalse(context.isReady());
                        assertNotSame(identity, graphics.device().resourceDomain());
                        assertThrows(GraphicsContextLostException.class, context::processEvents);
                        assertThrows(GraphicsContextLostException.class, context::beginFrame);
                        assertThrows(GraphicsContextLostException.class, () -> graphics.device().prepareRenderPipeline(request));
                        if (pass[0] != null) {
                            assertThrows(GraphicsContextLostException.class, () -> pass[0].draw(3, 1, 0, 0));
                            assertThrows(GraphicsContextLostException.class, context::endFrame);
                        }
                        if (result[0] != null) {
                            assertThrows(FdxException.class, () -> WGPUResources.requirePipeline(
                                    result[0].pass().pipeline(), context.resourceDomain(), "Lost pipeline"));
                            result[0].dispose();
                        }
                        if (stage.equals("source") || stage.equals("native"))
                            assertFalse(context.nativeDevice().isDisposed(), "In-flight work still owns the device");
                        fdx.app().requestExit();
                    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                }
            });
            resume.countDown();
            await(operation[0]::isDone);
            if (!stage.equals("published") && !stage.equals("frame"))
                assertThrows(CancellationException.class, operation[0]::finish);
            operation[0].dispose();
            await(() -> retained[0].nativeDevice().isDisposed());
            assertNull(field(retained[0], "deviceLostCallback"), "Native callback must retire after instance release");
            if (stage.equals("frame")) assertFreshSessionRenders();
        } finally {
            resume.countDown();
            if (result[0] != null) result[0].dispose();
        }
    }

    @ParameterizedTest @EnumSource(value = WGPUBackend.class, names = {"VULKAN", "D3D12"})
    void wgpuNativeReportedLossRejectsResultsAndRetainsPendingInputs(WGPUBackend backend) throws Exception {
        WGPUContext[] retained = {null};
        ShaderPreparationOperation[] completed = {null}, held = {null};
        CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        WGPUProvider provider = new WGPUProvider().loaderBackend(WGPULoaderBackend.WGPU).backend(backend);
        provider.configuration().preparationWorkerLimit(1);
        try {
            new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("Native wgpu loss")
                    .size(128, 128).visible(false).graphics(provider), new ApplicationAdapter() {
                @Override public void create(Fdx fdx) {
                    WGPUContext context = fdx.graphics().main().as();
                    retained[0] = context;
                    Object identity = context.device().resourceDomain();
                    var pipeline = new RenderPipelineDescriptor()
                            .renderTargetLayout(RenderTargetLayout.color(context.surfaceFormat()));
                    var request = new ShaderPipelineRequest(ShaderModuleSource.fixed(
                            ShaderModuleDescriptor.wgsl("completed before loss", SOURCE)), pipeline, ShaderPassId.FORWARD, 0);
                    completed[0] = context.device().prepareRenderPipeline(request);
                    await(completed[0]::isDone);
                    // Verify successful creation without consuming the unpublished result.
                    assertEquals(ShaderPreparationPhase.PUBLICATION_WAIT, completed[0].phase());
                    held[0] = context.device().prepareRenderPipeline(new ShaderPipelineRequest(
                            ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
                                entered.countDown(); waitLatch(resume);
                                return ShaderModuleDescriptor.wgsl("pending at loss", SOURCE);
                            }), pipeline, ShaderPassId.FORWARD, 1));
                    waitLatch(entered);
                    context.nativeDevice().destroy();
                    assertNull(context.resourceDomain().deviceLoss(), "Pinned wgpu destroy does not notify alone");
                    // Audited CPU-side call returns DeviceError::Lost and invokes the real upstream
                    // callback. No encoder is used or submitted; no hardware reset is induced.
                    WGPUCommandEncoderDescriptor descriptor = new WGPUCommandEncoderDescriptor();
                    WGPUCommandEncoder encoder = new WGPUCommandEncoder();
                    try { context.nativeDevice().createCommandEncoder(descriptor, encoder); }
                    finally { if (encoder.isValid()) encoder.release(); encoder.dispose(); descriptor.dispose(); }
                    assertNotNull(context.resourceDomain().deviceLoss());
                    assertFalse(context.isReady());
                    assertNotSame(identity, context.device().resourceDomain());
                    assertThrows(GraphicsContextLostException.class, context::processEvents);
                    assertThrows(GraphicsContextLostException.class, context::beginFrame);
                    assertThrows(CancellationException.class, completed[0]::finish);
                    assertThrows(GraphicsContextLostException.class, () -> context.device().prepareRenderPipeline(request));
                    assertFalse(context.nativeDevice().isDisposed(), "Held source retains native device");
                    fdx.app().requestExit();
                }
            });
            assertFalse(retained[0].nativeDevice().isDisposed());
            assertNotNull(field(retained[0], "deviceLostCallback"));
            resume.countDown();
            await(held[0]::isDone);
            assertThrows(CancellationException.class, held[0]::finish);
            held[0].dispose(); completed[0].dispose();
            await(() -> retained[0].nativeDevice().isDisposed());
            await(() -> {
                try { return field(retained[0], "deviceLostCallback") == null; }
                catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
        } finally { resume.countDown(); }
    }

    private static void assertFreshSessionRenders() {
        WGPUProvider provider = new WGPUProvider().loaderBackend(WGPULoaderBackend.DAWN).backend(WGPUBackend.D3D12);
        provider.configuration().offscreenReadback(true).preparationWorkerLimit(1);
        new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("WGPU loss recovery")
                .size(128, 128).visible(false).vSync(false).graphics(provider), new ApplicationAdapter() {
            @Override public void create(Fdx fdx) {
                WGPUContext context = fdx.graphics().main().as();
                var request = new ShaderPipelineRequest(ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("recovery", SOURCE)),
                        new RenderPipelineDescriptor().renderTargetLayout(RenderTargetLayout.color(context.surfaceFormat()))
                                .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
                var operation = context.device().prepareRenderPipeline(request);
                await(operation::isDone);
                var result = operation.finish();
                try {
                    assertTrue(context.beginFrame());
                    var frame = context.currentFrame();
                    var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                            .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
                    pass.setPipeline(result.pass().pipeline());
                    pass.draw(3, 1, 0, 0);
                    pass.end();
                    ByteBuffer pixels = context.readPixelsRgba8();
                    int center = (context.height() / 2 * context.width() + context.width() / 2) * 4;
                    assertEquals(0, pixels.get(center) & 255);
                    assertEquals(255, pixels.get(center + 1) & 255);
                    assertEquals(0, pixels.get(center + 2) & 255);
                    BufferedImage image = new BufferedImage(context.width(), context.height(), BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < context.height(); y++) for (int x = 0; x < context.width(); x++) {
                        int p = (y * context.width() + x) * 4;
                        image.setRGB(x, y, 0xff000000 | (pixels.get(p) & 255) << 16
                                | (pixels.get(p + 1) & 255) << 8 | (pixels.get(p + 2) & 255));
                    }
                    Path output = Path.of(System.getProperty("libfdx.test.wgpuAsyncOutput"),
                            "loss-recovery-" + System.getProperty("libfdx.test.wgpuBridge") + ".png");
                    Files.createDirectories(output.getParent());
                    ImageIO.write(image, "png", output.toFile());
                } catch (Exception error) { throw new AssertionError(error); }
                finally { result.dispose(); operation.dispose(); }
                fdx.app().requestExit();
            }
        });
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void waitLatch(CountDownLatch latch) {
        try { assertTrue(latch.await(15, TimeUnit.SECONDS), "Probe gate timed out"); }
        catch (InterruptedException error) { throw new AssertionError(error); }
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) LockSupport.parkNanos(1_000_000);
        assertTrue(condition.getAsBoolean(), "Native work did not settle");
    }
}
