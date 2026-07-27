package io.github.libfdx.graphics.g3d;

import com.sun.management.ThreadMXBean;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class DefaultRenderQueue3DAllocationTest {
    private static final ProviderId PROVIDER_ID = ProviderId.of("allocation-test");

    @Test
    void warmedSortAndReadOnlyAccessAllocateNoPerCallObjects() {
        DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
        Mesh mesh = new Mesh(new FakeGraphicsContext(), "allocation-mesh", Mesh.POSITION_COLOR_LAYOUT,
                new float[] {
                        0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f,
                        1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f,
                        0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f
                }, 3, BoundingBox.empty());
        for (int i = 63; i >= 0; i--) {
            PbrMaterial material = new PbrMaterial(String.format("material-%02d", i));
            queue.add(new Renderable3D(new MeshPart(mesh, 0, 3), material, Matrix4.IDENTITY,
                    BoundingBox.empty()));
        }
        Camera camera = new Camera();
        queue.sort(camera);
        assertEquals("material-00", queue.get(0).material().id());
        assertEquals("material-63", queue.get(63).material().id());
        assertSame(queue.renderables(), queue.renderables());
        assertSame(Mesh.POSITION_COLOR_LAYOUT.attribute(0), Mesh.POSITION_COLOR_LAYOUT.attribute(0));

        for (int i = 0; i < 2_000; i++) {
            queue.sort(camera);
            queue.renderables();
            Mesh.POSITION_COLOR_LAYOUT.attribute(0);
        }

        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(threadId);
        int checksum = 0;
        for (int i = 0; i < 2_000; i++) {
            queue.sort(camera);
            checksum += queue.renderables().size();
            checksum += Mesh.POSITION_COLOR_LAYOUT.attribute(0).location();
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertEquals(128_000, checksum);
        assertTrue(allocated <= 512L, "Expected no post-warm-up queue/accessor churn, allocated " + allocated
                + " bytes");
        mesh.dispose();
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
            throw new UnsupportedOperationException();
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
}
