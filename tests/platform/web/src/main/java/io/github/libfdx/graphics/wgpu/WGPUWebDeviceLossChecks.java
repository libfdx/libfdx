package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.NativeWindow;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.target.ShaderCompilerId;
import io.github.libfdx.graphics.shader.target.ShaderStageArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetVerification;
import io.github.libfdx.graphics.shader.target.ShaderTranslatedInterface;
import java.util.concurrent.CancellationException;
import org.teavm.jso.JSBody;

/** Real browser device destruction, late callback retirement, and fresh-device rendering. */
public final class WGPUWebDeviceLossChecks {
    private static final String SOURCE = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;
    private WGPUWebGraphicsAttachment attachment;
    private WGPUContext context;
    private GraphicsDevice device;
    private Object identity;
    private ShaderPipelineRequest request;
    private ShaderPreparationOperation job;
    private ShaderPreparedResult ready;
    private RenderPass lossPass;
    private int scenario, phase;
    private long deadline;
    private boolean stopped;
    private final StringBuilder report = new StringBuilder();

    public WGPUWebDeviceLossChecks() { createPanel(); }

    public void render() {
        if (stopped) return;
        try { advance(); }
        catch (Throwable failure) {
            stopped = true;
            status("FAIL", report + "\n" + failure);
            System.out.println("WEBGPU_DEVICE_LOSS_FAIL " + failure);
            dispose();
            throw failure;
        }
    }

    private void advance() {
        if (attachment == null) {
            attachment = new WGPUWebGraphicsAttachment(NativeWindow.web("loss-canvas"),
                    new WGPUConfiguration().backend(WGPUBackend.WEBGPU), 256, 256);
            deadline = System.currentTimeMillis() + 20000;
            phase = 0;
        }
        check(System.currentTimeMillis() < deadline, "Lifecycle timed out: " + scenario + "/" + phase);
        if (phase == 0) {
            attachment.processEvents();
            if (!attachment.isReady()) return;
            context = attachment.as();
            device = attachment.device();
            identity = device.resourceDomain();
            request = new ShaderPipelineRequest(ShaderModuleSource.fixed(source()),
                    new RenderPipelineDescriptor().renderTargetLayout(RenderTargetLayout.color(attachment.surfaceFormat()))
                            .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
            job = device.prepareRenderPipeline(request);
            if (scenario != 0) job.advanceLoading();
            check(!job.isDone(), "Native completion must yield to the browser");
            phase = 1;
        }
        if (phase == 1) {
            if (scenario >= 2) {
                if (!job.isDone()) return;
                if (scenario >= 3) ready = job.finish();
            }
            if (scenario == 6) {
                draw();
                report.append("PASS fresh device prepares and draws the green triangle\n");
                status("PASS 7/7", report.toString());
                System.out.println("WEBGPU_DEVICE_LOSS_PASS cases=7");
                stopped = true; // Keep the final rendered canvas and owned resources until disposal.
                return;
            }
            if (scenario == 4 || scenario == 5) {
                lossPass = beginDraw();
                if (scenario == 5) lossPass.end();
            }
            context.nativeDevice().destroy();
            phase = 2;
            return;
        }
        if (phase == 2) {
            if (context.resourceDomain().deviceLoss() == null) return;
            check(!context.isReady(), "Lost context stayed ready");
            check(identity != device.resourceDomain(), "Lost domain identity stayed current");
            rejected(context::processEvents);
            rejected(context::beginFrame);
            rejected(() -> device.prepareRenderPipeline(request));
            if (lossPass != null) {
                if (scenario == 4) rejected(() -> lossPass.draw(3, 1, 0, 0));
                rejected(context::endFrame);
            }
            if (ready != null) {
                rejected(() -> WGPUResources.requirePipeline(ready.pass().pipeline(), context.resourceDomain(), "Lost pipeline"));
                ready.dispose();
                ready = null;
            }
            attachment.dispose();
            phase = 3;
        }
        if (phase == 3) {
            if (!job.isDone() || !context.nativeDevice().isDisposed()) return;
            if (scenario < 3) {
                boolean cancelled = false;
                try { job.finish(); } catch (CancellationException expected) { cancelled = true; }
                check(cancelled, "Lost result was published");
            }
            job.dispose();
            check(context.resourceDomain().contextReferences() == 0, "Lost context reference leaked");
            report.append("PASS ").append(new String[] {"queued source", "pending native callback",
                    "completed unpublished result", "published pipeline", "acquired frame with open pass",
                    "acquired frame with ended pass"}[scenario]).append(" invalidated and drained\n");
            status("RUNNING", report.toString());
            scenario++;
            attachment = null;
            context = null;
            job = null;
            lossPass = null;
        }
    }

    private void draw() {
        RenderPass pass = beginDraw();
        pass.end();
        attachment.endFrame();
    }

    private RenderPass beginDraw() {
        check(attachment.beginFrame(), "Fresh context did not start its frame");
        // Browser surface acquisition is lazy; force it before destroying the device.
        context.ensureFrameTexture();
        var frame = attachment.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(.03f, .06f, .1f, 1)));
        pass.setPipeline(ready.pass().pipeline());
        pass.draw(3, 1, 0, 0);
        return pass;
    }

    public void dispose() {
        if (ready != null) { ready.dispose(); ready = null; }
        if (job != null) job.dispose();
        if (attachment != null) attachment.dispose();
    }

    private static void rejected(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (FdxException expected) { rejected = true; }
        check(rejected, "Lost device accepted an operation");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ShaderModuleDescriptor source() {
        ShaderTarget target = ShaderTarget.WGPU_WGSL;
        ShaderReflection reflection = ShaderReflection.builder(ShaderProfile.PORTABLE_WEBGPU).complete(true)
                .entryPoints(ShaderEntryPoint.builder("vertexMain", ShaderStage.VERTEX).build(),
                        ShaderEntryPoint.builder("fragmentMain", ShaderStage.FRAGMENT).build()).build();
        ShaderTargetArtifact artifact = ShaderTargetArtifact.compiled(target.id(), target.format(), target.environment(),
                new ShaderStageArtifact[] {ShaderStageArtifact.text(ShaderArtifactStage.MODULE, "", target.format(), SOURCE)},
                ShaderTranslatedInterface.identity(reflection, null), ShaderCompilerId.of("test.webgpu-loss"), "1", "");
        artifact = artifact.withVerification(ShaderTargetVerification.providerPipeline(target.environment(), null, artifact.compileCacheKey()));
        return ShaderModuleDescriptors.descriptor(artifact, "browser device loss", SOURCE);
    }

    @JSBody(script = "const panel=document.createElement('section'); panel.style='position:fixed;inset:12px;z-index:100;background:#172332;color:white;padding:12px;font:13px monospace;overflow:auto'; panel.innerHTML='<h2 style=\"margin:0 0 8px\">WebGPU device-loss lifecycle</h2><strong id=\"loss-status\">RUNNING</strong><pre id=\"loss-report\" style=\"white-space:pre-wrap;margin:8px 0\"></pre><canvas id=\"loss-canvas\" width=\"256\" height=\"256\" style=\"width:128px;height:128px\"></canvas>'; document.body.appendChild(panel);")
    private static native void createPanel();
    @JSBody(params = {"status", "report"}, script = "document.getElementById('loss-status').textContent=status; document.getElementById('loss-report').textContent=report;")
    private static native void status(String status, String report);
}
