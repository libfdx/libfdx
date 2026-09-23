package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPU;
import com.github.xpenatan.webgpu.WGPUCallbackMode;
import com.github.xpenatan.webgpu.WGPUComputePipeline;
import com.github.xpenatan.webgpu.WGPUComputePipelineDescriptor;
import com.github.xpenatan.webgpu.WGPUCreateComputePipelineAsyncCallback;
import com.github.xpenatan.webgpu.WGPUCreatePipelineAsyncStatus;
import com.github.xpenatan.webgpu.WGPUCreateRenderPipelineAsyncCallback;
import com.github.xpenatan.webgpu.WGPURenderPipeline;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises published native async entry points; runs each loader/bridge in a separate JVM. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "libfdx.test.nativeWgpuAsync", matches = "true")
@Timeout(25)
final class WGPUNativeAsyncPipelineTest {
    private static final String SOURCE = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            @compute @workgroup_size(1) fn computeMain() {}
            """;

    @ParameterizedTest
    @MethodSource("backends")
    void callbacksRejectInvalidEntriesAndValidPipelinesStillRender(WGPUBackend backend) {
        WGPULoaderBackend loader = WGPULoaderBackend.valueOf(System.getProperty("libfdx.test.wgpuLoader", "DAWN"));
        Scenario scenario = new Scenario(loader, backend);
        WGPUProvider provider = new WGPUProvider();
        provider.configuration().loaderBackend(loader).backend(backend).offscreenReadback(true).vSync(false);
        new DesktopApplicationBackend().start(new DesktopApplicationConfig().title("Native async callbacks")
                .size(128, 128).visible(false).vSync(false).graphics(provider), scenario);
        assertTrue(scenario.verified, "Scenario exited without verifying pixels and callbacks");
    }

    private static Stream<WGPUBackend> backends() {
        // The published Windows Dawn package builds only D3D12; wgpu-native also includes Vulkan.
        return "DAWN".equals(System.getProperty("libfdx.test.wgpuLoader", "DAWN"))
                ? Stream.of(WGPUBackend.D3D12) : Stream.of(WGPUBackend.D3D12, WGPUBackend.VULKAN);
    }

    private static final class Scenario extends ApplicationAdapter {
        private final WGPULoaderBackend loader;
        private final WGPUBackend backend;
        private final RenderResult[] renders = new RenderResult[129];
        private final ComputeResult[] computes = new ComputeResult[2];
        private GraphicsContext graphics;
        private WGPUContext context;
        private Application application;
        private WGPUShaderModuleHandle module;
        private RenderPipeline ready;
        private long deadline;
        private int frames;
        private boolean verified;

        Scenario(WGPULoaderBackend loader, WGPUBackend backend) { this.loader = loader; this.backend = backend; }

        @Override
        public void create(Fdx fdx) {
            graphics = fdx.graphics().main(); context = graphics.as(); application = fdx.app();
            assertEquals(loader == WGPULoaderBackend.DAWN, WGPU.isDawnBackend(), "Wrong native binary loaded");
            module = (WGPUShaderModuleHandle) graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl("async callbacks", SOURCE));
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            // Invalid first, followed by 128 valid requests. Each descriptor stays alive until its callback drains.
            for (int i = 0; i < renders.length; i++) {
                RenderResult result = new RenderResult(i == 0); renders[i] = result;
                result.inputs = WGPUGraphicsDevice.createRenderPipelineInputs(context.nativeDevice(),
                        context.resourceDomain(), null, descriptor(), module);
                if (result.invalid) result.inputs.descriptor.getVertex().setEntryPoint("missingVertex");
                context.nativeDevice().createRenderPipelineAsync(result.inputs.descriptor,
                        WGPUCallbackMode.AllowProcessEvents, result);
                assertEquals(loader == WGPULoaderBackend.WGPU, result.calls == 1, "Unexpected inline callback");
            }
            for (int i = 0; i < computes.length; i++) {
                ComputeResult result = new ComputeResult(i == 0); computes[i] = result;
                result.descriptor = new WGPUComputePipelineDescriptor();
                result.descriptor.setLabel("native async compute");
                result.descriptor.getCompute().setModule(module.nativeModule());
                result.descriptor.getCompute().setEntryPoint(result.invalid ? "missingCompute" : "computeMain");
                context.nativeDevice().createComputePipelineAsync(result.descriptor,
                        WGPUCallbackMode.AllowProcessEvents, result);
                assertEquals(loader == WGPULoaderBackend.WGPU, result.calls == 1, "Unexpected inline callback");
            }
        }

        private RenderPipelineDescriptor descriptor() {
            return RenderPipelineDescriptor.shader(module, graphics.surfaceFormat()).label("native async render")
                    .depthTestEnabled(false).depthWriteEnabled(false);
        }

        @Override
        public void render() {
            assertTrue(System.nanoTime() < deadline, "Async callbacks did not complete");
            context.processEvents();
            boolean complete = true;
            for (RenderResult result : renders) complete &= result.calls != 0;
            for (ComputeResult result : computes) complete &= result.calls != 0;
            if (!complete) return;
            if (ready == null) {
                for (RenderResult result : renders) {
                    checkResult(result.calls, result.status, result.pipeline.isValid(), result.invalid, result.message);
                    if (loader == WGPULoaderBackend.DAWN && !result.invalid && ready == null) {
                        ready = result.inputs.takePipeline(result.pipeline).publish(); result.pipeline = null;
                    }
                }
                for (ComputeResult result : computes)
                    checkResult(result.calls, result.status, result.pipeline.isValid(), result.invalid, result.message);
                // Unsupported native async must leave the context usable for its synchronous worker route.
                if (ready == null) ready = graphics.device().createRenderPipeline(descriptor());
            }
            var frame = graphics.currentFrame();
            var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                    .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
            try { pass.setPipeline(ready); pass.draw(3, 1, 0, 0); }
            finally { pass.end(); }
            if (++frames == 3) {
                ByteBuffer pixels = context.readPixelsRgba8();
                int center = (frame.height() / 2 * frame.width() + frame.width() / 2) * 4;
                assertTrue((pixels.get(center + 1) & 255) > 240 && (pixels.get(center) & 255) < 10,
                        "Expected a green triangle");
                assertTrue((pixels.get(1) & 255) < 10, "Expected black corner");
                saveFrame(pixels, frame.width(), frame.height());
                context.processEvents();
                for (RenderResult result : renders) assertEquals(1, result.calls);
                for (ComputeResult result : computes) assertEquals(1, result.calls);
                verified = true;
                System.out.println("NATIVE_ASYNC_PASS loader=" + loader + " backend=" + backend
                        + " bridge=" + System.getProperty("libfdx.test.wgpuBridge")
                        + " renderCallbacks=129 computeCallbacks=2 nativeAsync=" + WGPU.isDawnBackend() + " pixels=PASS");
                application.requestExit();
            }
        }

        private void checkResult(int calls, WGPUCreatePipelineAsyncStatus status, boolean valid, boolean invalid, String message) {
            assertEquals(1, calls);
            WGPUCreatePipelineAsyncStatus expected = loader == WGPULoaderBackend.WGPU
                    ? WGPUCreatePipelineAsyncStatus.InternalError : invalid
                    ? WGPUCreatePipelineAsyncStatus.ValidationError : WGPUCreatePipelineAsyncStatus.Success;
            assertEquals(expected, status, message);
            assertEquals(expected == WGPUCreatePipelineAsyncStatus.Success, valid);
            if (expected != WGPUCreatePipelineAsyncStatus.Success) assertFalse(message.isBlank());
            if (loader == WGPULoaderBackend.WGPU) assertTrue(message.contains("unavailable in wgpu-native"), message);
        }

        private void saveFrame(ByteBuffer pixels, int width, int height) {
            try {
                Path output = Path.of(System.getProperty("libfdx.test.wgpuAsyncOutput"));
                Files.createDirectories(output);
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int i = (y * width + x) * 4;
                    image.setRGB(x, y, 0xff000000 | (pixels.get(i) & 255) << 16
                            | (pixels.get(i + 1) & 255) << 8 | (pixels.get(i + 2) & 255));
                }
                ImageIO.write(image, "png", output.resolve(loader + "-" + backend + "-"
                        + System.getProperty("libfdx.test.wgpuBridge") + ".png").toFile());
            } catch (Exception error) { throw new AssertionError("Could not capture native output", error); }
        }

        @Override
        public void dispose() {
            // Never destroy callbacks or their inputs while native work is outstanding, including on assertion failure.
            if (context != null) {
                long drainDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (pending() && System.nanoTime() < drainDeadline) context.processEvents();
                assertFalse(pending(), "Native callbacks did not drain before teardown");
            }
            if (ready != null) ready.dispose();
            for (RenderResult result : renders) if (result != null) {
                if (result.pipeline != null) { if (result.pipeline.isValid()) result.pipeline.release(); result.pipeline.dispose(); }
                result.dispose();
                if (result.inputs != null) result.inputs.close();
            }
            for (ComputeResult result : computes) if (result != null) {
                if (result.pipeline != null) { if (result.pipeline.isValid()) result.pipeline.release(); result.pipeline.dispose(); }
                result.dispose();
                if (result.descriptor != null) result.descriptor.dispose();
            }
            if (module != null) module.dispose();
        }

        private boolean pending() {
            for (RenderResult result : renders) if (result != null && result.calls == 0) return true;
            for (ComputeResult result : computes) if (result != null && result.calls == 0) return true;
            return false;
        }
    }

    private static final class RenderResult extends WGPUCreateRenderPipelineAsyncCallback {
        final boolean invalid;
        WGPUGraphicsDevice.RenderPipelineInputs inputs;
        WGPURenderPipeline pipeline;
        WGPUCreatePipelineAsyncStatus status;
        String message;
        int calls;
        RenderResult(boolean invalid) { this.invalid = invalid; }
        @Override
        protected void onCallback(WGPUCreatePipelineAsyncStatus status, WGPURenderPipeline pipeline, String message) {
            this.status = status; this.pipeline = pipeline; this.message = message; calls++;
        }
    }

    private static final class ComputeResult extends WGPUCreateComputePipelineAsyncCallback {
        final boolean invalid;
        WGPUComputePipelineDescriptor descriptor;
        WGPUComputePipeline pipeline;
        WGPUCreatePipelineAsyncStatus status;
        String message;
        int calls;
        ComputeResult(boolean invalid) { this.invalid = invalid; }
        @Override
        protected void onCallback(WGPUCreatePipelineAsyncStatus status, WGPUComputePipeline pipeline, String message) {
            this.status = status; this.pipeline = pipeline; this.message = message; calls++;
        }
    }
}
