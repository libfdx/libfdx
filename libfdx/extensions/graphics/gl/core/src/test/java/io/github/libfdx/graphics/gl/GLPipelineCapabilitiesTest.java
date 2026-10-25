package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsContextLostException;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.TextureFormat;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GLPipelineCapabilitiesTest {
    private static final ProviderId PROVIDER = ProviderId.of("gl");

    @Test
    void repeatedPipelineBindsAndFramesReuseInitializationProbesAndKeepStateCalls() {
        FakeGL gl = new FakeGL(true, true);
        GLGraphicsAttachment attachment = attachment(gl, null);
        assertEquals(List.of("supportsDepthTextures", "supportsMipTextures", "supportsRgba16FloatTextures",
                "supportsCompletePipelineState", "supportsMultipleTargets", "supportsCompute"), gl.probes);
        GLShaderModuleHandle shader = shader(attachment, gl);
        RenderPipeline first = pipeline(attachment, shader);
        RenderPipeline second = pipeline(attachment, shader);
        for (int frame = 0; frame < 2; frame++) {
            RenderPass pass = begin(attachment);
            gl.stateCalls.clear();
            pass.setPipeline(first);
            assertEquals(List.of("useProgram", "enableDepthTest", "depthMask", "enableAlphaBlending",
                    "applyPipelineState", "applyColorTargets"), gl.stateCalls);
            pass.setPipeline(first);
            pass.setPipeline(second);
            pass.setPipeline(first);
            pass.draw(3, 1, 0, 0);
            pass.end();
            attachment.endFrame();
        }
        assertEquals(1, gl.calls("supportsCompletePipelineState"));
        assertEquals(1, gl.calls("supportsMultipleTargets"));
        assertEquals(6, gl.calls("applyPipelineState"));
        assertEquals(6, gl.calls("applyColorTargets"));
        first.dispose();
        second.dispose();
        shader.dispose();
        attachment.dispose();
    }

    @Test
    void falseCapabilityResultsAreRetainedWithoutEnablingUnsupportedState() {
        for (boolean complete : new boolean[] {false, true}) {
            FakeGL gl = new FakeGL(complete, false);
            GLGraphicsAttachment attachment = attachment(gl, null);
            GLShaderModuleHandle shader = shader(attachment, gl);
            RenderPipeline pipeline = pipeline(attachment, shader);
            for (int frame = 0; frame < 2; frame++) {
                RenderPass pass = begin(attachment);
                pass.setPipeline(pipeline);
                pass.draw(3, 1, 0, 0);
                pass.end();
                attachment.endFrame();
            }
            assertEquals(1, gl.calls("supportsCompletePipelineState"));
            assertEquals(1, gl.calls("supportsMultipleTargets"));
            assertEquals(complete ? 2 : 0, gl.calls("applyPipelineState"));
            assertEquals(0, gl.calls("applyColorTargets"));
            pipeline.dispose();
            shader.dispose();
            attachment.dispose();
        }
    }

    @Test
    void sharedPipelineUsesTheDrawingAttachmentsCapabilities() {
        for (boolean complete : new boolean[] {false, true}) {
            for (boolean multiple : new boolean[] {false, true}) {
                FakeGL creatorGl = new FakeGL(!complete, !multiple);
                GLGraphicsAttachment creator = attachment(creatorGl, null);
                GLShaderModuleHandle shader = shader(creator, creatorGl);
                RenderPipeline pipeline = pipeline(creator, shader);
                FakeGL drawingGl = new FakeGL(complete, multiple);
                GLGraphicsAttachment drawing = attachment(drawingGl, creator);
                RenderPass pass = begin(drawing);
                pass.setPipeline(pipeline);
                pass.draw(3, 1, 0, 0);
                assertEquals(complete ? 1 : 0, creatorGl.calls("applyPipelineState"));
                assertEquals(complete && multiple ? 1 : 0, creatorGl.calls("applyColorTargets"));
                if (complete) assertSame(drawingGl, creatorGl.stateContext);
                assertEquals(1, creatorGl.calls("supportsCompletePipelineState"));
                assertEquals(1, creatorGl.calls("supportsMultipleTargets"));
                assertEquals(1, drawingGl.calls("supportsCompletePipelineState"));
                assertEquals(1, drawingGl.calls("supportsMultipleTargets"));
                pass.end();
                drawing.endFrame();
                pipeline.dispose();
                shader.dispose();
                drawing.dispose();
                creator.dispose();
            }
        }
    }

    @Test
    void disposedBoundPipelineIsRejectedBeforeAnyStateCall() {
        FakeGL gl = new FakeGL(true, true);
        GLGraphicsAttachment attachment = attachment(gl, null);
        GLShaderModuleHandle shader = shader(attachment, gl);
        RenderPipeline pipeline = pipeline(attachment, shader);
        RenderPass pass = begin(attachment);
        pass.setPipeline(pipeline);
        pipeline.dispose();
        int calls = gl.totalCalls;
        assertThrows(FdxException.class, () -> pass.setPipeline(pipeline));
        assertThrows(FdxException.class, () -> pass.draw(3, 1, 0, 0));
        assertEquals(calls, gl.totalCalls);
        pass.end();
        attachment.endFrame();
        shader.dispose();
        attachment.dispose();
    }

    @Test
    void replacementAfterContextLossProbesAgainAndRejectsOldResources() {
        FakeGL gl = new FakeGL(true, true);
        GLGraphicsAttachment old = attachment(gl, null);
        GLShaderModuleHandle shader = shader(old, gl);
        RenderPipeline oldPipeline = pipeline(old, shader);
        RenderPass oldPass = begin(old);
        oldPass.setPipeline(oldPipeline);
        gl.lost = true;
        assertTrue(old.detectContextLoss());
        int calls = gl.totalCalls;
        assertThrows(GraphicsContextLostException.class, () -> oldPass.setPipeline(oldPipeline));
        assertThrows(GraphicsContextLostException.class, () -> oldPass.draw(3, 1, 0, 0));
        assertEquals(calls, gl.totalCalls);
        old.dispose();

        gl.lost = false;
        gl.complete = false;
        gl.multiple = false;
        GLGraphicsAttachment replacement = attachment(gl, null);
        GLShaderModuleHandle replacementShader = shader(replacement, gl);
        RenderPipeline replacementPipeline = pipeline(replacement, replacementShader);
        RenderPass pass = begin(replacement);
        calls = gl.totalCalls;
        assertThrows(FdxException.class, () -> pass.setPipeline(oldPipeline));
        assertEquals(calls, gl.totalCalls);
        int applied = gl.calls("applyPipelineState");
        pass.setPipeline(replacementPipeline);
        assertEquals(applied, gl.calls("applyPipelineState"));
        assertEquals(2, gl.calls("supportsCompletePipelineState"));
        assertEquals(2, gl.calls("supportsMultipleTargets"));
        pass.end();
        replacement.endFrame();
        replacementPipeline.dispose();
        replacementShader.dispose();
        replacement.dispose();
        oldPipeline.dispose();
        shader.dispose();
    }

    @Test
    void disposedAttachmentCannotStartAnotherFrameOrReuseEndedPass() {
        FakeGL gl = new FakeGL(true, true);
        GLGraphicsAttachment attachment = attachment(gl, null);
        GLShaderModuleHandle shader = shader(attachment, gl);
        RenderPipeline pipeline = pipeline(attachment, shader);
        RenderPass pass = begin(attachment);
        pass.setPipeline(pipeline);
        pass.end();
        attachment.endFrame();
        attachment.dispose();
        int calls = gl.totalCalls;
        assertFalse(attachment.beginFrame());
        assertThrows(FdxException.class, () -> pass.setPipeline(pipeline));
        assertEquals(calls, gl.totalCalls);
        pipeline.dispose();
        shader.dispose();
    }

    private static GLGraphicsAttachment attachment(FakeGL gl, GLGraphicsAttachment shared) {
        return new GLGraphicsAttachment(PROVIDER, gl.api, new GLSurface() {
            @Override public void makeCurrent() { FakeGL.current = gl; }
            @Override public void swapBuffers() { }
            @Override public void releaseCurrent() { FakeGL.current = null; }
        }, 64, 64, TextureFormat.RGBA8_UNORM, shared);
    }

    private static GLShaderModuleHandle shader(GLGraphicsAttachment attachment, FakeGL gl) {
        return new GLShaderModuleHandle(PROVIDER, gl.api, attachment.resourceDomain(), 101);
    }

    private static RenderPipeline pipeline(GLGraphicsAttachment attachment, GLShaderModuleHandle shader) {
        return attachment.device().createRenderPipeline(
                RenderPipelineDescriptor.shader(shader, TextureFormat.RGBA8_UNORM));
    }

    private static RenderPass begin(GLGraphicsAttachment attachment) {
        assertTrue(attachment.beginFrame());
        return attachment.currentFrame().commandEncoder().beginRenderPass(RenderPassDescriptor.color(
                attachment.currentFrame().colorAttachment(), LoadOp.load(), StoreOp.store()));
    }

    private static final class FakeGL implements InvocationHandler {
        private static FakeGL current;
        private final GLApi api = (GLApi) Proxy.newProxyInstance(
                GLApi.class.getClassLoader(), new Class<?>[] {GLApi.class}, this);
        private final Map<String, Integer> counts = new HashMap<>();
        private final List<String> probes = new ArrayList<>();
        private final List<String> stateCalls = new ArrayList<>();
        private boolean complete, multiple, lost;
        private int nextHandle = 1, totalCalls;
        private FakeGL stateContext;

        private FakeGL(boolean complete, boolean multiple) {
            this.complete = complete;
            this.multiple = multiple;
        }

        private int calls(String name) { return counts.getOrDefault(name, 0); }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            totalCalls++;
            counts.merge(name, 1, Integer::sum);
            if (name.startsWith("supports")) {
                assertSame(this, current, "Feature probes must use the attachment's current context");
                probes.add(name);
            }
            if (name.equals("useProgram") || name.equals("enableDepthTest") || name.equals("depthMask")
                    || name.equals("enableAlphaBlending") || name.equals("applyPipelineState")
                    || name.equals("applyColorTargets")) stateCalls.add(name);
            if (name.equals("applyPipelineState")) stateContext = current;
            return switch (name) {
                case "supportsCompletePipelineState" -> complete;
                case "supportsMultipleTargets" -> multiple;
                case "isContextLost" -> lost;
                default -> method.getReturnType() == int.class ? nextHandle++
                        : method.getReturnType() == boolean.class ? false
                        : method.getReturnType() == long.class ? 0L : null;
            };
        }
    }
}
