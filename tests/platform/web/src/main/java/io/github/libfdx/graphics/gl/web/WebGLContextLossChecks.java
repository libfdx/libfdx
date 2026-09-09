package io.github.libfdx.graphics.gl.web;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContextLostException;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.gl.GLGraphicsAttachment;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.runtime.PreparedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOptions;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationScope;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationState;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.webgl.WebGLRenderingContext;

/** Real browser loss/restore fixtures, isolated from the launcher's rendering context. */
public final class WebGLContextLossChecks {
    private static final String SOURCE = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                var p = array<vec2f,3>(vec2f(-0.8,-0.8), vec2f(0.8,-0.8), vec2f(0.0,0.8));
                return vec4f(p[i],0.0,1.0);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0.1,0.9,0.2,1.0); }
            """;
    private static final String[] CASES = {"unpublished", "submitted-link", "queued-source",
            "ready-and-queued", "restored-before-poll", "open-render-pass"};
    private int index, phase, generated, beforeLossDeletes, drainCallbacks;
    private long deadline;
    private HTMLCanvasElement canvas;
    private JSObject probe;
    private WebGLRenderingContext nativeContext;
    private WebGLApi api;
    private GLGraphicsAttachment attachment;
    private Object originalDomain;
    private ShaderPreparationOperation operation;
    private ShaderPreparedResult result;
    private ShaderPreparation service;
    private ShaderPreparationScope scope, queuedScope;
    private PreparedShaderPass ready, queued;
    private RenderPipeline oldPipeline;
    private boolean complete;

    public WebGLContextLossChecks() { next(); }

    private void next() {
        deadline = System.currentTimeMillis() + 20_000;
        phase = generated = drainCallbacks = 0;
        canvas = createCanvas(CASES[index]);
        nativeContext = context(canvas);
        check(nativeContext != null, "WebGL2 unavailable");
        probe = instrument(nativeContext);
        check(probe != null, "WEBGL_lose_context unavailable");
        attach();
        originalDomain = attachment.device().resourceDomain();
        if (index == 3) {
            service = new ShaderPreparation(attachment.device(), ShaderPreparationOptions.DEFAULT);
            scope = service.createScope("ready before loss");
            ready = scope.include(provider(), request("ready"));
            service.prepareAsync(scope.seal());
        } else operation = attachment.device().prepareRenderPipeline(packet());
    }

    private void attach() {
        api = new WebGLApi(nativeContext);
        attachment = new GLGraphicsAttachment(WebGLProvider.ID, api, new WebGLSurface(), 240, 180,
                TextureFormat.RGBA8_UNORM);
    }

    private ShaderProvider provider() {
        GraphicsDevice device = attachment.device();
        return new ShaderProvider() {
            @Override public GraphicsDevice preparationDevice() { return device; }
            @Override public boolean supports(ShaderRequest request) { return true; }
            @Override public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
                return device.prepareRenderPipeline(packet());
            }
        };
    }

    private static ShaderRequest request(String name) {
        return ShaderRequest.builder(ShaderPassId.FORWARD).variantKey(name)
                .renderPass(RenderPassCompatibility.layout(RenderTargetLayout.color(TextureFormat.RGBA8_UNORM))).build();
    }

    private ShaderPipelineRequest packet() {
        return new ShaderPipelineRequest(ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
            generated++;
            return ShaderModuleDescriptor.wgsl("WebGL loss triangle", SOURCE);
        }), new RenderPipelineDescriptor().colorFormat(TextureFormat.RGBA8_UNORM)
                .depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0);
    }

    public void render() {
        if (complete) return;
        check(System.currentTimeMillis() < deadline, "Timed out in " + CASES[index] + " phase " + phase);
        if (phase == 0) {
            if (index == 3) {
                service.updateLoading();
                if (ready.state() != ShaderPreparationState.READY) return;
                oldPipeline = ready.readyPass().pipeline();
                queuedScope = service.createScope("queued at loss");
                queued = queuedScope.include(provider(), request("queued"));
                service.prepareAsync(queuedScope.seal());
                service.update(); // Loading-only source must not run in an ordinary update.
                check(generated == 1, "Queued source started outside loading");
            } else if (index != 2) {
                operation.advanceLoading();
                if (index == 1) {
                    check(api.supportsParallelShaderCompilation(), "Submitted-link check requires driver polling");
                    check(operation.phase() == ShaderPreparationPhase.PIPELINE, "Link was not submitted");
                } else if (!operation.isDone()) return;
                if (index == 5) {
                    result = operation.finish(); operation.dispose(); operation = null;
                    attachment.beginFrame();
                    var frame = attachment.currentFrame();
                    var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                            .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
                    pass.setPipeline(result.pass().pipeline()); pass.draw(3, 1, 0, 0);
                    // Deliberately leave this pass open across loss.
                }
            }
            beforeLossDeletes = deletes(probe);
            lose(probe);
            phase = 1;
            return;
        }
        if (phase == 1) {
            if (!lossDelivered(probe)) return;
            check(rawLost(nativeContext), "Browser did not report context loss");
            if (index == 4) { restore(probe); phase = 2; return; }
            verifyLoss();
        } else if (phase == 2) {
            if (!restoreDelivered(probe)) return;
            check(!rawLost(nativeContext), "Browser did not restore context");
            verifyLoss(); // No provider/domain/preparation calls occurred between loss and restoration.
        } else if (phase == 3) {
            operation.advanceLoading();
            if (!operation.isDone()) return;
            result = operation.finish(); operation.dispose(); operation = null;
            renderRecovery();
            result.dispose(); result = null;
            attachment.dispose();
            passed(canvas, CASES[index]);
            System.out.println("WEBGL_LOSS_PASS case=" + CASES[index] + " recovery_pixels=true");
            if (++index == CASES.length) {
                complete = true;
                finished();
                System.out.println("WEBGL_LOSS_ALL_PASS cases=6 recoveries=6");
            } else next();
        }
    }

    private void verifyLoss() {
        if (index == 3) {
            service.update();
            check(service.isDisposed(), "Loss did not invalidate common preparation");
            check(ready.state() == ShaderPreparationState.CANCELLED && ready.readyPass() == null,
                    "Ready shader survived loss");
            check(queued.state() == ShaderPreparationState.CANCELLED && generated == 1,
                    "Queued source ran after loss");
            check(oldPipeline.isDisposed(), "Ready pipeline not retired");
            var drain = service.disposeAsync();
            drain.onSuccess(ignored -> drainCallbacks++);
            service.update(); service.update();
            check(drain.isDone() && drainCallbacks == 1, "Drain did not complete exactly once");
            scope.dispose(); queuedScope.dispose();
            service = null; scope = queuedScope = null; ready = queued = null; oldPipeline = null;
        } else if (index == 5) {
            try { attachment.endFrame(); throw new AssertionError("Lost frame was submitted"); }
            catch (GraphicsContextLostException expected) { }
            attachment.endFrame(); // Frame was abandoned even though endFrame threw.
            result.dispose(); result = null;
        } else {
            if (index == 2) operation.advanceLoading();
            check(operation.isDone(), "Lost operation did not settle");
            try { operation.finish(); throw new AssertionError("Lost pipeline was published"); }
            catch (CancellationException expected) { }
            operation.dispose(); operation = null;
            if (index == 2) check(generated == 0, "Lost queued source was generated");
        }
        Object invalidated = attachment.device().resourceDomain();
        check(invalidated != originalDomain && invalidated == attachment.device().resourceDomain(),
                "Resource domain did not invalidate once");
        try { attachment.device().prepareRenderPipeline(packet()); throw new AssertionError("Lost device accepted work"); }
        catch (FdxException expected) { }
        attachment.dispose(); attachment.dispose();
        check(deletes(probe) == beforeLossDeletes, "Old native names were deleted after loss/restoration");
        if (index != 4) {
            remove(canvas);
            canvas = createCanvas(CASES[index]);
            nativeContext = context(canvas);
        }
        attach(); // A fresh resource domain, including on the already restored canvas in case 4.
        check(attachment.device().resourceDomain() != originalDomain, "Recovery reused the old domain");
        operation = attachment.device().prepareRenderPipeline(packet());
        phase = 3;
    }

    private void renderRecovery() {
        check(attachment.beginFrame(), "No recovery frame");
        var frame = attachment.currentFrame();
        var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
        pass.setPipeline(result.pass().pipeline()); pass.draw(3, 1, 0, 0); pass.end();
        ByteBuffer pixels = attachment.readPixelsRgba8();
        int center = (90 * 240 + 120) * 4;
        check((pixels.get(center + 1) & 255) > 180 && (pixels.get(center) & 255) < 60,
                "Recovery triangle center is not green");
        check((pixels.get(0) & 255) < 10 && (pixels.get(1) & 255) < 10, "Recovery background is not black");
        attachment.endFrame();
    }

    public void dispose() {
        check(complete, "WebGL loss checks stopped before completion");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    @JSBody(params = "name", script = """
            let root=document.getElementById('loss-results');
            if(!root){root=document.createElement('div');root.id='loss-results';
              root.style='position:fixed;inset:0;background:#17202c;color:white;padding:24px;z-index:10000;display:flex;flex-wrap:wrap;align-content:flex-start;gap:16px;overflow:auto;font:16px system-ui';
              let h=document.createElement('h2');h.id='loss-status';h.style='width:100%';h.textContent='WebGL shader context-loss validation';root.append(h);document.body.append(root);}
            let box=document.createElement('div');let p=document.createElement('p');p.textContent=name+' — running';box.append(p);
            let canvas=document.createElement('canvas');canvas.width=240;canvas.height=180;canvas.style='width:240px;height:180px';box.append(canvas);root.append(box);return canvas;
            """)
    private static native HTMLCanvasElement createCanvas(String name);
    @JSBody(params = "canvas", script = "return canvas.getContext('webgl2',{antialias:false,preserveDrawingBuffer:true});")
    private static native WebGLRenderingContext context(HTMLCanvasElement canvas);
    @JSBody(params = "gl", script = """
            var ext=gl.getExtension('WEBGL_lose_context');if(!ext)return null;
            var p={gl:gl,ext:ext,lost:false,restored:false,deletes:0};
            gl.canvas.addEventListener('webglcontextlost',function(e){e.preventDefault();p.lost=true;},{once:true});
            gl.canvas.addEventListener('webglcontextrestored',function(){p.restored=true;},{once:true});
            ['deleteProgram','deleteShader'].forEach(function(name){var original=gl[name];
              gl[name]=function(value){p.deletes++;return original.call(gl,value);};});
            return p;
            """)
    private static native JSObject instrument(WebGLRenderingContext gl);
    @JSBody(params = "p", script = "p.ext.loseContext();") private static native void lose(JSObject p);
    @JSBody(params = "p", script = "p.ext.restoreContext();") private static native void restore(JSObject p);
    @JSBody(params = "p", script = "return p.lost;") private static native boolean lossDelivered(JSObject p);
    @JSBody(params = "p", script = "return p.restored;") private static native boolean restoreDelivered(JSObject p);
    @JSBody(params = "p", script = "return p.deletes;") private static native int deletes(JSObject p);
    @JSBody(params = "gl", script = "return gl.isContextLost();") private static native boolean rawLost(WebGLRenderingContext gl);
    @JSBody(params = "canvas", script = "canvas.parentElement.remove();") private static native void remove(HTMLCanvasElement canvas);
    @JSBody(params = {"canvas", "name"}, script = "canvas.previousElementSibling.textContent=name+' — PASS';")
    private static native void passed(HTMLCanvasElement canvas, String name);
    @JSBody(script = "document.getElementById('loss-status').textContent='PASS: 6 losses · 6 recovery renders';")
    private static native void finished();
}
