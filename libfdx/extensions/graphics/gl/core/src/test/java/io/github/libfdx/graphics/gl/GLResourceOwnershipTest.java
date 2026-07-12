package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderBinding;
import io.github.libfdx.graphics.ShaderBindingType;
import io.github.libfdx.graphics.ShaderAttribute;
import io.github.libfdx.graphics.ShaderReflection;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.VertexLayout;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GLResourceOwnershipTest {
    private static final ProviderId PROVIDER_ID = ProviderId.of("gl-test");

    @Test
    void renderPassPoolReusesHighWaterSlotsAndRejectsOverlap() {
        GLGraphicsAttachment attachment = attachment(new FakeGL(), new FakeSurface());
        assertTrue(attachment.beginFrame());
        RenderPassDescriptor descriptor = RenderPassDescriptor.color(
                attachment.currentFrame().colorAttachment(), LoadOp.load(), StoreOp.store());
        RenderPass first = attachment.currentFrame().commandEncoder().beginRenderPass(descriptor);
        assertThrows(FdxException.class,
                () -> attachment.currentFrame().commandEncoder().beginRenderPass(descriptor));
        assertThrows(FdxException.class, attachment::endFrame);
        first.end();
        RenderPass second = attachment.currentFrame().commandEncoder().beginRenderPass(descriptor);
        assertNotSame(first, second);
        second.end();
        attachment.endFrame();

        assertTrue(attachment.beginFrame());
        RenderPass reusedFirst = attachment.currentFrame().commandEncoder().beginRenderPass(descriptor);
        reusedFirst.end();
        RenderPass reusedSecond = attachment.currentFrame().commandEncoder().beginRenderPass(descriptor);
        reusedSecond.end();
        attachment.endFrame();

        assertSame(first, reusedFirst);
        assertSame(second, reusedSecond);
        attachment.dispose();
    }

    @Test
    void independentDeviceRejectsForeignBufferBeforeCallingGl() {
        FakeGL firstGl = new FakeGL();
        FakeGL secondGl = new FakeGL();
        GLGraphicsAttachment first = attachment(firstGl, new FakeSurface());
        GLGraphicsAttachment second = attachment(secondGl, new FakeSurface());
        Buffer buffer = first.device().createBuffer(BufferDescriptor.vertex("first", 16));
        secondGl.resetCalls();

        assertThrows(FdxException.class,
                () -> second.device().writeBuffer(buffer, ByteBuffer.allocateDirect(16)));
        assertEquals(0, secondGl.calls());

        buffer.dispose();
        first.dispose();
        second.dispose();
    }

    @Test
    void deviceRejectsDisposedBufferBeforeCallingGl() {
        FakeGL fakeGl = new FakeGL();
        GLGraphicsAttachment attachment = attachment(fakeGl, new FakeSurface());
        GraphicsDevice device = attachment.device();
        Buffer buffer = device.createBuffer(BufferDescriptor.vertex("disposed", 16));
        buffer.dispose();
        fakeGl.resetCalls();

        assertThrows(FdxException.class,
                () -> device.writeBuffer(buffer, ByteBuffer.allocateDirect(16)));
        assertEquals(0, fakeGl.calls());

        attachment.dispose();
    }

    @Test
    void explicitlySharedDeviceAcceptsBufferFromItsResourceDomain() {
        FakeGL firstGl = new FakeGL();
        FakeGL secondGl = new FakeGL();
        GLGraphicsAttachment first = attachment(firstGl, new FakeSurface());
        GLGraphicsAttachment second = sharedAttachment(secondGl, new FakeSurface(), first);
        Buffer buffer = first.device().createBuffer(BufferDescriptor.vertex("shared", 16));
        secondGl.resetCalls();

        second.device().writeBuffer(buffer, ByteBuffer.allocateDirect(16));
        assertTrue(secondGl.calls() > 0);

        buffer.dispose();
        first.dispose();
        second.dispose();
    }

    @Test
    void beginFrameMakesItsSurfaceCurrentBeforeGlCalls() {
        FakeGL fakeGl = new FakeGL();
        FakeSurface surface = new FakeSurface();
        GLGraphicsAttachment attachment = attachment(fakeGl, surface);
        GLGraphicsAttachment other = attachment(new FakeGL(), new FakeSurface());
        surface.reset();
        fakeGl.resetCalls();

        assertTrue(attachment.beginFrame());
        assertEquals(1, surface.makeCurrentCalls());
        assertTrue(fakeGl.calls() > 0);

        attachment.endFrame();
        attachment.dispose();
        other.dispose();
    }

    @Test
    void deviceRejectsDisposedAndForeignTexturesBeforeCallingGl() {
        FakeGL firstGl = new FakeGL();
        FakeGL secondGl = new FakeGL();
        GLGraphicsAttachment first = attachment(firstGl, new FakeSurface());
        GLGraphicsAttachment second = attachment(secondGl, new FakeSurface());
        Texture foreign = first.device().createTexture(TextureDescriptor.rgba8("foreign", 2, 2));
        secondGl.resetCalls();

        assertThrows(FdxException.class,
                () -> second.device().writeTexture(foreign, ByteBuffer.allocateDirect(16)));
        assertEquals(0, secondGl.calls());

        Texture disposed = second.device().createTexture(TextureDescriptor.rgba8("disposed", 2, 2));
        disposed.dispose();
        secondGl.resetCalls();
        assertThrows(FdxException.class,
                () -> second.device().writeTexture(disposed, ByteBuffer.allocateDirect(16)));
        assertEquals(0, secondGl.calls());

        foreign.dispose();
        first.dispose();
        second.dispose();
    }

    @Test
    void pipelineCreationRejectsDisposedAndForeignShadersBeforeCallingGl() {
        FakeGL firstGl = new FakeGL();
        FakeGL secondGl = new FakeGL();
        GLGraphicsAttachment first = attachment(firstGl, new FakeSurface());
        GLGraphicsAttachment second = attachment(secondGl, new FakeSurface());
        GLShaderModuleHandle foreign = shader(first, firstGl, 101);
        secondGl.resetCalls();

        assertThrows(FdxException.class, () -> second.device().createRenderPipeline(
                RenderPipelineDescriptor.shader(foreign, TextureFormat.RGBA8_UNORM)));
        assertEquals(0, secondGl.calls());

        GLShaderModuleHandle disposed = shader(second, secondGl, 102);
        disposed.dispose();
        secondGl.resetCalls();
        assertThrows(FdxException.class, () -> second.device().createRenderPipeline(
                RenderPipelineDescriptor.shader(disposed, TextureFormat.RGBA8_UNORM)));
        assertEquals(0, secondGl.calls());

        foreign.dispose();
        first.dispose();
        second.dispose();
    }

    @Test
    void renderPassRejectsDisposedAndForeignBindingsBeforeCallingGl() {
        FakeGL firstGl = new FakeGL();
        FakeGL secondGl = new FakeGL();
        GLGraphicsAttachment first = attachment(firstGl, new FakeSurface());
        GLGraphicsAttachment second = attachment(secondGl, new FakeSurface());
        RenderPass pass = beginSurfacePass(first);

        Buffer foreignBuffer = second.device().createBuffer(BufferDescriptor.vertex("foreign", 16));
        firstGl.resetCalls();
        assertThrows(FdxException.class, () -> pass.setVertexBuffer(foreignBuffer));
        assertEquals(0, firstGl.calls());

        Buffer disposedBuffer = first.device().createBuffer(BufferDescriptor.vertex("disposed", 16));
        disposedBuffer.dispose();
        firstGl.resetCalls();
        assertThrows(FdxException.class, () -> pass.setVertexBuffer(disposedBuffer));
        assertEquals(0, firstGl.calls());

        GLShaderModuleHandle textureShader = shader(first, firstGl, 200);
        GLRenderPipelineHandle texturePipeline = pipeline(first, firstGl, textureShader, 1);
        pass.setPipeline(texturePipeline);
        Texture foreignTexture = second.device().createTexture(TextureDescriptor.rgba8("foreign", 2, 2));
        firstGl.resetCalls();
        assertThrows(FdxException.class, () -> pass.setTexture(0, foreignTexture));
        assertEquals(0, firstGl.calls());

        Texture disposedTexture = first.device().createTexture(TextureDescriptor.rgba8("disposed", 2, 2));
        disposedTexture.dispose();
        firstGl.resetCalls();
        assertThrows(FdxException.class, () -> pass.setTexture(0, disposedTexture));
        assertEquals(0, firstGl.calls());

        GLShaderModuleHandle foreignShader = shader(second, secondGl, 201);
        GLRenderPipelineHandle foreignPipeline = pipeline(second, secondGl, foreignShader);
        firstGl.resetCalls();
        assertThrows(FdxException.class, () -> pass.setPipeline(foreignPipeline));
        assertEquals(0, firstGl.calls());

        GLShaderModuleHandle localShader = shader(first, firstGl, 202);
        GLRenderPipelineHandle disposedPipeline = pipeline(first, firstGl, localShader);
        disposedPipeline.dispose();
        firstGl.resetCalls();
        assertThrows(FdxException.class, () -> pass.setPipeline(disposedPipeline));
        assertEquals(0, firstGl.calls());

        pass.end();
        first.endFrame();
        foreignBuffer.dispose();
        foreignTexture.dispose();
        foreignPipeline.dispose();
        foreignShader.dispose();
        texturePipeline.dispose();
        textureShader.dispose();
        localShader.dispose();
        first.dispose();
        second.dispose();
    }

    @Test
    void drawRejectsTextureDisposedAfterBindingBeforeCallingGl() {
        FakeGL fakeGl = new FakeGL();
        GLGraphicsAttachment attachment = attachment(fakeGl, new FakeSurface());
        GLShaderModuleHandle shader = shader(attachment, fakeGl, 250);
        GLRenderPipelineHandle pipeline = pipeline(attachment, fakeGl, shader, 1);
        Texture texture = attachment.device().createTexture(TextureDescriptor.rgba8("sampled", 2, 2));
        RenderPass pass = beginSurfacePass(attachment);
        pass.setPipeline(pipeline);

        fakeGl.resetCalls();
        assertThrows(FdxException.class, () -> pass.setTexture(-1, texture));
        assertThrows(FdxException.class, () -> pass.setTexture(1, texture));
        assertThrows(FdxException.class, () -> pass.draw(3, 1, 0, 0));
        assertEquals(0, fakeGl.calls());

        pass.setTexture(0, texture);
        texture.dispose();
        fakeGl.resetCalls();
        assertThrows(FdxException.class, () -> pass.draw(3, 1, 0, 0));
        assertEquals(0, fakeGl.calls());

        pass.end();
        attachment.endFrame();
        pipeline.dispose();
        shader.dispose();
        attachment.dispose();
    }

    @Test
    void passRejectsRenderTargetDisposedAfterBeginBeforeCallingGl() {
        FakeGL fakeGl = new FakeGL();
        GLGraphicsAttachment attachment = attachment(fakeGl, new FakeSurface());
        Texture target = attachment.device().createTexture(TextureDescriptor.rgba8RenderTarget("target", 4, 4));
        assertTrue(attachment.beginFrame());
        RenderPass pass = attachment.currentFrame().commandEncoder().beginRenderPass(RenderPassDescriptor.color(
                target.view(), LoadOp.load(), StoreOp.store()));
        target.dispose();
        fakeGl.resetCalls();

        assertThrows(FdxException.class, () -> pass.setViewport(0, 0, 4, 4));
        assertEquals(0, fakeGl.calls());

        pass.end();
        attachment.endFrame();
        attachment.dispose();
    }

    @Test
    void commandEncoderRejectsAnotherAttachmentsFrameViewBeforeCallingGl() {
        FakeGL firstGl = new FakeGL();
        GLGraphicsAttachment first = attachment(firstGl, new FakeSurface());
        GLGraphicsAttachment second = attachment(new FakeGL(), new FakeSurface());
        assertTrue(first.beginFrame());
        assertTrue(second.beginFrame());
        firstGl.resetCalls();

        RenderPassDescriptor descriptor = RenderPassDescriptor.color(second.currentFrame().colorAttachment(),
                LoadOp.load(), StoreOp.store());
        assertThrows(FdxException.class,
                () -> first.currentFrame().commandEncoder().beginRenderPass(descriptor));
        assertEquals(0, firstGl.calls());

        first.endFrame();
        second.endFrame();
        first.dispose();
        second.dispose();
    }

    @Test
    void sharedRenderTargetUsesOneContextLocalFramebufferPerAttachment() {
        FakeGL firstGl = new FakeGL();
        FakeGL secondGl = new FakeGL();
        GLGraphicsAttachment first = attachment(firstGl, new FakeSurface());
        GLGraphicsAttachment second = sharedAttachment(secondGl, new FakeSurface(), first);
        Texture target = first.device().createTexture(TextureDescriptor.rgba8RenderTarget("shared-target", 4, 4));

        renderTo(first, target);
        renderTo(second, target);
        renderTo(first, target);
        assertEquals(1, firstGl.calls("genFramebuffer"));
        assertEquals(1, secondGl.calls("genFramebuffer"));

        target.dispose();
        assertEquals(1, firstGl.calls("deleteFramebuffer"));
        assertEquals(0, secondGl.calls("deleteFramebuffer"));
        assertTrue(second.beginFrame());
        second.endFrame();
        assertEquals(1, secondGl.calls("deleteFramebuffer"));

        first.dispose();
        second.dispose();
    }

    @Test
    void shaderProgramLivesUntilItsLastPipelineIsDisposed() {
        FakeGL fakeGl = new FakeGL();
        GLGraphicsAttachment attachment = attachment(fakeGl, new FakeSurface());
        GLShaderModuleHandle shader = shader(attachment, fakeGl, 301);
        GLRenderPipelineHandle pipeline = pipeline(attachment, fakeGl, shader);
        fakeGl.resetCalls();

        shader.dispose();
        assertEquals(0, fakeGl.calls("deleteProgram"));
        pipeline.dispose();
        assertEquals(1, fakeGl.calls("deleteProgram"));

        attachment.dispose();
    }

    @Test
    void failedBufferConstructionDeletesGeneratedBuffer() {
        FakeGL fakeGl = new FakeGL();
        GLGraphicsAttachment attachment = attachment(fakeGl, new FakeSurface());
        fakeGl.resetCalls();
        fakeGl.failOn("bufferData", 1);

        assertThrows(IllegalStateException.class,
                () -> attachment.device().createBuffer(BufferDescriptor.vertex("failure", 16)));
        assertEquals(1, fakeGl.calls("genBuffer"));
        assertEquals(1, fakeGl.calls("deleteBuffer"));

        attachment.dispose();
    }

    @Test
    void failedTextureConstructionDeletesGeneratedTexture() {
        FakeGL fakeGl = new FakeGL();
        GLGraphicsAttachment attachment = attachment(fakeGl, new FakeSurface());
        fakeGl.resetCalls();
        fakeGl.failOn("texImage2D", 1);

        assertThrows(IllegalStateException.class,
                () -> attachment.device().createTexture(TextureDescriptor.rgba8("failure", 2, 2)));
        assertEquals(1, fakeGl.calls("genTexture"));
        assertEquals(1, fakeGl.calls("deleteTexture"));

        attachment.dispose();
    }

    @Test
    void failedShaderCompilationDeletesGeneratedShader() {
        FakeGL fakeGl = new FakeGL();
        GLGraphicsAttachment attachment = attachment(fakeGl, new FakeSurface());
        fakeGl.resetCalls();
        fakeGl.failOn("shaderSource", 1);

        assertThrows(IllegalStateException.class, () -> ((GLGraphicsDevice)attachment.device())
                .compileShader(GLShaderType.VERTEX, "void main() {}", "failure"));
        assertEquals(1, fakeGl.calls("createShader"));
        assertEquals(1, fakeGl.calls("deleteShader"));

        attachment.dispose();
    }

    @Test
    void failedProgramLinkDeletesGeneratedProgram() {
        FakeGL fakeGl = new FakeGL();
        GLGraphicsAttachment attachment = attachment(fakeGl, new FakeSurface());
        fakeGl.resetCalls();
        fakeGl.failOn("linkProgram", 1);

        assertThrows(IllegalStateException.class,
                () -> ((GLGraphicsDevice)attachment.device()).linkProgram(11, 12, "failure"));
        assertEquals(1, fakeGl.calls("createProgram"));
        assertEquals(1, fakeGl.calls("deleteProgram"));

        attachment.dispose();
    }

    @Test
    void failedUniformBufferConstructionDeletesGeneratedBuffer() {
        FakeGL fakeGl = new FakeGL();
        GLGraphicsAttachment attachment = attachment(fakeGl, new FakeSurface());
        GLShaderModuleHandle shader = shader(attachment, fakeGl, 401);
        RenderPipelineDescriptor descriptor = RenderPipelineDescriptor
                .shader(shader, TextureFormat.RGBA8_UNORM)
                .shaderReflection(ShaderReflection.of(new ShaderBinding[] {
                        ShaderBinding.of(0, 0, "uniforms", ShaderBindingType.UNIFORM_BUFFER)
                }, new ShaderAttribute[0]));
        fakeGl.resetCalls();
        fakeGl.failOn("uniformBufferData", 1);

        assertThrows(IllegalStateException.class,
                () -> attachment.device().createRenderPipeline(descriptor));
        assertEquals(1, fakeGl.calls("genBuffer"));
        assertEquals(1, fakeGl.calls("deleteBuffer"));

        shader.dispose();
        attachment.dispose();
    }

    private static GLGraphicsAttachment attachment(FakeGL fakeGl, FakeSurface surface) {
        return new GLGraphicsAttachment(PROVIDER_ID, fakeGl.api(), surface, 64, 64,
                TextureFormat.RGBA8_UNORM);
    }

    private static GLGraphicsAttachment sharedAttachment(FakeGL fakeGl, FakeSurface surface,
            GLGraphicsAttachment sharedAttachment) {
        return new GLGraphicsAttachment(PROVIDER_ID, fakeGl.api(), surface, 64, 64,
                TextureFormat.RGBA8_UNORM, sharedAttachment);
    }

    private static RenderPass beginSurfacePass(GLGraphicsAttachment attachment) {
        assertTrue(attachment.beginFrame());
        return attachment.currentFrame().commandEncoder().beginRenderPass(RenderPassDescriptor.color(
                attachment.currentFrame().colorAttachment(), LoadOp.load(), StoreOp.store()));
    }

    private static void renderTo(GLGraphicsAttachment attachment, Texture texture) {
        assertTrue(attachment.beginFrame());
        RenderPass pass = attachment.currentFrame().commandEncoder().beginRenderPass(RenderPassDescriptor.color(
                texture.view(), LoadOp.load(), StoreOp.store()));
        pass.end();
        attachment.endFrame();
    }

    private static GLShaderModuleHandle shader(GLGraphicsAttachment attachment, FakeGL fakeGl, int program) {
        return new GLShaderModuleHandle(PROVIDER_ID, fakeGl.api(), attachment.resourceDomain(), program);
    }

    private static GLRenderPipelineHandle pipeline(GLGraphicsAttachment attachment, FakeGL fakeGl,
            GLShaderModuleHandle shader) {
        return pipeline(attachment, fakeGl, shader, 0);
    }

    private static GLRenderPipelineHandle pipeline(GLGraphicsAttachment attachment, FakeGL fakeGl,
            GLShaderModuleHandle shader, int sampledTextureCount) {
        return new GLRenderPipelineHandle(PROVIDER_ID, fakeGl.api(), attachment.resourceDomain(), shader,
                PrimitiveTopology.TRIANGLE_LIST, new VertexLayout[0], sampledTextureCount, false, true, 0);
    }

    private static final class FakeSurface implements GLSurface {
        private int makeCurrentCalls;

        @Override
        public void makeCurrent() {
            makeCurrentCalls++;
        }

        @Override
        public void swapBuffers() {
        }

        @Override
        public void releaseCurrent() {
        }

        int makeCurrentCalls() {
            return makeCurrentCalls;
        }

        void reset() {
            makeCurrentCalls = 0;
        }
    }

    private static final class FakeGL implements InvocationHandler {
        private final GLApi api = (GLApi) Proxy.newProxyInstance(
                GLApi.class.getClassLoader(), new Class<?>[] { GLApi.class }, this);
        private int nextHandle = 1;
        private int calls;
        private final Map<String, Integer> callsByMethod = new HashMap<>();
        private String failingMethod;
        private int failingCall;

        GLApi api() {
            return api;
        }

        int calls() {
            return calls;
        }

        int calls(String methodName) {
            return callsByMethod.getOrDefault(methodName, 0);
        }

        void resetCalls() {
            calls = 0;
            callsByMethod.clear();
        }

        void failOn(String methodName, int call) {
            failingMethod = methodName;
            failingCall = call;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            calls++;
            callsByMethod.merge(method.getName(), 1, Integer::sum);
            if (method.getName().equals(failingMethod) && calls(method.getName()) == failingCall) {
                throw new IllegalStateException("Injected GL failure at " + method.getName());
            }
            Class<?> type = method.getReturnType();
            if (type == Integer.TYPE) {
                return nextHandle++;
            }
            if (type == Boolean.TYPE) {
                return true;
            }
            if (type == Long.TYPE) {
                return 0L;
            }
            if (type == Float.TYPE) {
                return 0.0f;
            }
            if (type == Double.TYPE) {
                return 0.0d;
            }
            if (type == Byte.TYPE) {
                return (byte) 0;
            }
            if (type == Short.TYPE) {
                return (short) 0;
            }
            if (type == Character.TYPE) {
                return (char) 0;
            }
            return null;
        }
    }
}
