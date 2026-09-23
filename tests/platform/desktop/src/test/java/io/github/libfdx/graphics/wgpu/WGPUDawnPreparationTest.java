package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUFeatureName;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.tests.graphics.MultipleShadersTest;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "libfdx.test.nativeDawnPreparation", matches = "true")
@Timeout(30)
final class WGPUDawnPreparationTest {
    private static final String SOURCE = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;

    @Test
    void nativeDeviceEnablesSynchronizationBeforeWorkersCanUseIt() {
        new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("Dawn device synchronization")
                .size(128, 128).visible(false).vSync(false).graphics(provider()), new ApplicationAdapter() {
            @Override
            public void create(Fdx fdx) {
                WGPUContext context = fdx.graphics().main().as();
                synchronized (WGPUFeatureName.CUSTOM) {
                    int previous = WGPUFeatureName.CUSTOM.getValue();
                    try {
                        // Published Dawn header: WGPUFeatureName_ImplicitDeviceSynchronization.
                        assertTrue(context.nativeDevice().hasFeature(WGPUFeatureName.CUSTOM.setValue(0x00050004)),
                                "Dawn device must enable implicit synchronization before concurrent native calls");
                    } finally { WGPUFeatureName.CUSTOM.setValue(previous); }
                }
                fdx.app().requestExit();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void commonPreparationRenders128DistinctShaders(boolean invalid) {
        System.setProperty("libfdx.test.shaderAsync", "true");
        System.setProperty("libfdx.test.shaderInvalidIndex", invalid ? "17" : "-1");
        String capture = Path.of(System.getProperty("libfdx.test.wgpuAsyncOutput"),
                "grid-" + System.getProperty("libfdx.test.wgpuBridge") + "-" + invalid + ".ppm").toString();
        System.setProperty("libfdx.test.capture", capture);
        try {
            new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("Dawn shader preparation")
                    .size(960, 720).visible(false).vSync(false).graphics(provider()), new MultipleShadersTest(12));
        } finally {
            System.clearProperty("libfdx.test.shaderAsync");
            System.clearProperty("libfdx.test.shaderInvalidIndex");
            System.clearProperty("libfdx.test.capture");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateCallbacksDrainAfterCancellationAndContextShutdown(boolean shutdown) throws Exception {
        Pending scenario = new Pending(shutdown, 1);
        try {
            run(scenario);
            assertTrue(scenario.context.resourceDomain().isClosed());
            assertFalse(scenario.context.nativeDevice().isDisposed());
            scenario.resume.countDown();
            await(() -> scenario.jobs[0].isDone());
            assertThrows(CancellationException.class, scenario.jobs[0]::finish);
            scenario.jobs[0].dispose();
            await(() -> scenario.context.nativeDevice().isDisposed() && scenario.events.isTerminated());
            assertTrue(scenario.jobs[0].isDisposed());
        } finally { scenario.resume.countDown(); }
    }

    @Test
    void callbacksKeepTheQueueBoundedAfterCpuWorkersHaveReturned() throws Exception {
        Pending scenario = new Pending(true, 257);
        try {
            run(scenario);
            scenario.resume.countDown();
            for (ShaderPreparationOperation job : scenario.jobs) {
                await(job::isDone);
                assertThrows(CancellationException.class, job::finish);
                job.dispose();
            }
            await(() -> scenario.context.nativeDevice().isDisposed() && scenario.events.isTerminated());
        } finally { scenario.resume.countDown(); }
    }

    private static WGPUProvider provider() {
        WGPUProvider provider = new WGPUProvider().loaderBackend(WGPULoaderBackend.DAWN).backend(WGPUBackend.D3D12);
        provider.configuration().offscreenReadback(true).vSync(false).processEventsEachFrame(false)
                .preparationWorkerLimit(1);
        return provider;
    }

    private static void run(Pending scenario) {
        new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("Dawn callback lifetime")
                .size(128, 128).visible(false).vSync(false).graphics(provider()), scenario);
    }

    private static void await(BooleanSupplier ready) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) LockSupport.parkNanos(1_000_000);
        assertTrue(ready.getAsBoolean(), "Native work did not drain");
    }

    private static Object field(Object value, String name) throws ReflectiveOperationException {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(value);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class Pending extends ApplicationAdapter {
        final CountDownLatch resume = new CountDownLatch(1);
        final boolean shutdown;
        final ShaderPreparationOperation[] jobs;
        WGPUContext context;
        ScheduledThreadPoolExecutor events;

        Pending(boolean shutdown, int count) { this.shutdown = shutdown; jobs = new ShaderPreparationOperation[count]; }

        @Override
        public void create(Fdx fdx) {
            GraphicsContext graphics = fdx.graphics().main();
            context = graphics.as();
            try {
                Object dawn = field(context.preparation(), "dawn");
                events = (ScheduledThreadPoolExecutor) field(dawn, "events");
                List<?> pending = (List<?>) field(dawn, "pending");
                // Hold event delivery, not the compiler or owner lock. The actual async API has
                // accepted each request before cancellation. This is not a device-loss test.
                events.execute(() -> {
                    try { assertTrue(resume.await(20, TimeUnit.SECONDS)); }
                    catch (InterruptedException error) { throw new AssertionError(error); }
                });
                ShaderPipelineRequest request = new ShaderPipelineRequest(
                        ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("late Dawn", SOURCE)),
                        new RenderPipelineDescriptor().renderTargetLayout(RenderTargetLayout.color(graphics.surfaceFormat()))
                                .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
                for (int i = 0; i < jobs.length; i++) {
                    jobs[i] = graphics.device().prepareRenderPipeline(request);
                    int expected = i + 1;
                    await(() -> { synchronized (context.resourceDomain()) { return pending.size() == expected; } });
                }
                if (jobs.length == 257)
                    assertThrows(RejectedExecutionException.class, () -> graphics.device().prepareRenderPipeline(request));
                if (!shutdown) for (ShaderPreparationOperation job : jobs) job.cancel();
                for (ShaderPreparationOperation job : jobs) assertFalse(job.isDone());
                fdx.app().requestExit();
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
    }
}
