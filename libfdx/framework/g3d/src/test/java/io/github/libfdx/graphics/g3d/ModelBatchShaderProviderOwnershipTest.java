package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelBatchShaderProviderOwnershipTest {
    private static final ProviderId PROVIDER_ID = ProviderId.of("model-batch-ownership-test");

    @Test
    void disposesOwnedDefaultAfterReplacementWithoutDisposingBorrowedReplacement() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        ModelBatch batch = new ModelBatch(graphics);
        CountingShaderProvider replacement = new CountingShaderProvider();

        assertEquals(1, graphics.device.shaderModuleCount);
        assertFalse(graphics.device.shaderModule.isDisposed());

        batch.shaderProvider(replacement);
        assertFalse(graphics.device.shaderModule.isDisposed());
        assertEquals(0, replacement.disposeCount);

        batch.dispose();
        batch.dispose();

        assertTrue(graphics.device.shaderModule.isDisposed());
        assertEquals(1, graphics.device.shaderModule.disposeCount);
        assertEquals(0, replacement.disposeCount);
    }

    @Test
    void neverDisposesConfiguredOrReplacementBorrowedProviders() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        CountingShaderProvider configured = new CountingShaderProvider();
        CountingShaderProvider replacement = new CountingShaderProvider();
        ModelBatch batch = new ModelBatch(graphics,
                new ModelBatchConfig().shaderProvider(configured));

        assertEquals(0, graphics.device.shaderModuleCount);
        batch.shaderProvider(replacement);
        assertEquals(0, configured.disposeCount);
        assertEquals(0, replacement.disposeCount);

        batch.dispose();

        assertEquals(0, configured.disposeCount);
        assertEquals(0, replacement.disposeCount);
    }

    @Test
    void rejectsReplacementWhileDrawingAndKeepsPendingWorkOnTheOriginalProvider() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        CountingShaderProvider original = new CountingShaderProvider();
        CountingShaderProvider replacement = new CountingShaderProvider();
        ModelBatch batch = new ModelBatch(graphics,
                new ModelBatchConfig().shaderProvider(original));
        Mesh mesh = Mesh.coloredTriangle(graphics, "provider replacement");
        Renderable3D renderable = new Renderable3D(
                new MeshPart(mesh, 0, 3),
                new Material("provider replacement"),
                Matrix4.IDENTITY,
                BoundingBox.empty());
        FakeRenderPass pass = new FakeRenderPass();
        Camera camera = new Camera();

        batch.begin(pass, camera);
        batch.render(renderable);

        assertThrows(FdxException.class, () -> batch.shaderProvider(replacement));

        batch.end();
        assertEquals(1, original.shaderRequestCount);
        assertEquals(1, original.shader.renderCount);
        assertEquals(0, replacement.shaderRequestCount);

        batch.shaderProvider(replacement);
        batch.begin(pass, camera);
        batch.render(renderable);
        batch.end();

        assertEquals(1, replacement.shaderRequestCount);
        assertEquals(1, replacement.shader.renderCount);

        batch.dispose();
        mesh.dispose();
        assertEquals(0, original.disposeCount);
        assertEquals(0, replacement.disposeCount);
    }

    private static final class CountingShaderProvider implements ShaderProvider3D, Disposable {
        private final CountingShader shader = new CountingShader();
        private int shaderRequestCount;
        private int disposeCount;

        @Override
        public Shader3D shader(Renderable3D renderable, RenderContext3D context) {
            shaderRequestCount++;
            return shader;
        }

        @Override
        public void dispose() {
            disposeCount++;
        }

        @Override
        public boolean isDisposed() {
            return disposeCount > 0;
        }
    }

    private static final class CountingShader implements Shader3D {
        private int renderCount;
        private boolean disposed;

        @Override
        public boolean canRender(Renderable3D renderable) {
            return true;
        }

        @Override
        public void begin(RenderContext3D context) {
        }

        @Override
        public void render(Renderable3D renderable) {
            renderCount++;
        }

        @Override
        public void end() {
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class FakeGraphicsContext implements GraphicsContext {
        private final FakeGraphicsDevice device = new FakeGraphicsDevice();

        @Override
        public GraphicsDevice device() {
            return device;
        }

        @Override
        public TextureFormat surfaceFormat() {
            return TextureFormat.RGBA8_UNORM;
        }

        @Override
        public GraphicsFrame currentFrame() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear(float red, float green, float blue, float alpha) {
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T)this;
        }
    }

    private static final class FakeGraphicsDevice implements GraphicsDevice {
        private FakeShaderModule shaderModule;
        private int shaderModuleCount;

        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            return new FakeBuffer(descriptor.size(), descriptor.usage());
        }

        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
        }

        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTexture(Texture texture, ByteBuffer data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            shaderModule = new FakeShaderModule();
            shaderModuleCount++;
            return shaderModule;
        }

        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T)this;
        }
    }

    private static final class FakeShaderModule implements ShaderModule {
        private int disposeCount;

        @Override
        public ShaderLanguage language() {
            return ShaderLanguage.WGSL;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T)this;
        }

        @Override
        public void dispose() {
            disposeCount++;
        }

        @Override
        public boolean isDisposed() {
            return disposeCount > 0;
        }
    }

    private static final class FakeBuffer implements Buffer {
        private final int size;
        private final BufferUsage usage;
        private boolean disposed;

        FakeBuffer(int size, BufferUsage usage) {
            this.size = size;
            this.usage = usage;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public BufferUsage usage() {
            return usage;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T)this;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class FakeRenderPass implements RenderPass {
        @Override
        public void setPipeline(RenderPipeline pipeline) {
        }

        @Override
        public void setVertexBuffer(Buffer buffer) {
        }

        @Override
        public void setTexture(int slot, Texture texture) {
        }

        @Override
        public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        }

        @Override
        public void end() {
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T)this;
        }
    }
}
