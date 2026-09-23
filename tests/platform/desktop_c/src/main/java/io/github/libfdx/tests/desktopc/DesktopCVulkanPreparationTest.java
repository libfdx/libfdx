package io.github.libfdx.tests.desktopc;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

/** Runs inside the generated TeaVM C executable, including post-backend shutdown assertions. */
public final class DesktopCVulkanPreparationTest extends GraphicsParityTest {
    private static final String SHADER = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;
    private final RenderPassDescriptor pass = new RenderPassDescriptor().colorLoadOp(LoadOp.clear(0, 0, 0, 1));
    private ShaderPreparationOperation display, cancelled, pendingAtShutdown, unpublishedAtShutdown;
    private ShaderPreparedResult ready;
    private int polls, generated;
    private boolean disposed;

    public DesktopCVulkanPreparationTest(long frames) { super(frames > 12 ? frames : 40); }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "DesktopCVulkanPreparationTest");
        var capabilities = graphics.device().shaderPreparationCapabilities();
        require(capabilities.cpuExecution() == ShaderPreparationCapabilities.Execution.OWNER_THREAD
                && capabilities.nativeExecution() == ShaderPreparationCapabilities.Execution.OWNER_THREAD
                && !capabilities.runtimeNonblocking() && capabilities.workerLimit() == 0,
                "Incorrect TeaVM C execution capabilities");
        logger.info("DESKTOP_C_CAPABILITIES " + capabilities);
        display = prepare(() -> { generated++; return ShaderModuleDescriptor.wgsl("display", SHADER); });
        cancelled = prepare(() -> { throw new AssertionError("Cancelled source ran"); });
        pendingAtShutdown = prepare(() -> { throw new AssertionError("Shutdown source ran"); });
        markCreated();
    }

    private ShaderPreparationOperation prepare(Supplier<ShaderModuleDescriptor> source) {
        return graphics.device().prepareRenderPipeline(new ShaderPipelineRequest(
                ShaderModuleSource.deferred("vertexMain", "fragmentMain", source),
                new RenderPipelineDescriptor().renderTargetLayout(RenderTargetLayout.color(graphics.surfaceFormat()))
                        .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0));
    }

    @Override
    public void render() {
        if (polls < 12) {
            require(!display.isDone() && generated == 0, "Ordinary polling compiled a shader");
            if (++polls == 12) {
                cancelled.cancel(); cancelled.advanceLoading(); expectCancelled(cancelled); cancelled.dispose();
                ShaderPreparationOperation invalid = prepare(() -> ShaderModuleDescriptor.wgsl("invalid", "invalid WGSL"));
                invalid.advanceLoading();
                require(invalid.isDone(), "Invalid request did not finish");
                try { invalid.finish(); throw new AssertionError("Invalid shader succeeded"); }
                catch (RuntimeException expected) { logger.info("DESKTOP_C_EXPECTED_FAILURE " + expected.getMessage()); }
                invalid.dispose();
                verifyCompilationException();
                display.advanceLoading();
                require(display.isDone() && generated == 1, "Loading did not prepare exactly once");
                ready = display.finish(); display.dispose();
                require(!ready.isDisposed() && !ready.pass().pipeline().isDisposed(), "Publication lost pipeline ownership");
                unpublishedAtShutdown = prepare(() -> ShaderModuleDescriptor.wgsl("unpublished", SHADER));
                unpublishedAtShutdown.advanceLoading();
                require(unpublishedAtShutdown.isDone(), "Unpublished result was not created");
                logger.info("DESKTOP_C_LOADING_PASS polls=12 cancelled=1 rejected=1 ready=1");
            }
        }
        var frame = graphics.currentFrame();
        var render = frame.commandEncoder().beginRenderPass(pass.colorAttachment(frame.colorAttachment()));
        try { if (ready != null) { render.setPipeline(ready.pass().pipeline()); render.draw(3, 1, 0, 0); } }
        finally { render.end(); }
        finishFrame();
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (ready != null) ready.dispose();
        require(generated == 1 && ready != null, "Preparation did not render");
        verifyDisposed();
        // Pending and completed-but-unpublished operations deliberately remain for context teardown.
    }

    public void verifyShutdown() {
        require(disposed && pendingAtShutdown.isDisposed() && unpublishedAtShutdown.isDisposed(),
                "Context did not drain its preparation operations");
        expectCancelled(pendingAtShutdown); expectCancelled(unpublishedAtShutdown);
        logger.info("DESKTOP_C_SHUTDOWN_PASS queued=1 unpublished=1");
    }

    private static void expectCancelled(ShaderPreparationOperation operation) {
        require(operation.isDone(), "Cancellation did not settle");
        try { operation.finish(); throw new AssertionError("Cancelled operation published"); }
        catch (CancellationException expected) { }
    }

    private void verifyCompilationException() {
        String label = "legacy-invalid";
        try {
            ShaderModuleDescriptors.requireTarget(ShaderModuleDescriptor.wgsl(label, "invalid WGSL"),
                    ShaderTarget.VULKAN_SPIRV, "desktop C Vulkan");
            throw new AssertionError("Invalid shader succeeded through convenience overload");
        } catch (FdxException expected) {
            require(expected.getMessage().contains(label), "Compilation error lost its shader label");
            logger.info("DESKTOP_C_EXCEPTION_PASS " + expected.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
