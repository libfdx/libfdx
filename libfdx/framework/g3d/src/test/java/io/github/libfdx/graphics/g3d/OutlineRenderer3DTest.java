package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OutlineRenderer3DTest {
    private static final ProviderId PROVIDER_ID =
            ProviderId.of("model-builder-outline-test");

    @Test
    void outlinesStaticNormalLayouts() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        float[] positions = {
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f
        };
        float[] colors = {
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f
        };
        float[] normals = {
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f
        };
        Mesh positionNormal = Mesh.positionNormal3D(graphics,
                "outline position normal", positions, colors, normals,
                BoundingBox.empty());
        Mesh positionNormalColor = Mesh.positionNormalColor3D(graphics,
                "outline position normal color", positions, colors, normals,
                BoundingBox.empty());
        VertexLayout equivalentPositionNormalLayout = VertexLayout.of(
                Mesh.POSITION_NORMAL_BYTES_PER_VERTEX,
                VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
                VertexAttribute.of(1, VertexFormat.FLOAT32X3, 12));
        assertEquals(Mesh.POSITION_NORMAL_LAYOUT,
                equivalentPositionNormalLayout);
        assertNotSame(Mesh.POSITION_NORMAL_LAYOUT,
                equivalentPositionNormalLayout);
        Mesh equivalentPositionNormal = new Mesh(graphics,
                "outline equivalent position normal",
                equivalentPositionNormalLayout, new float[] {
                        0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f,
                        1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f,
                        0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f
                }, 3, BoundingBox.empty());
        Mesh pbr = Mesh.positionColor3D(graphics, "outline pbr", positions,
                colors, normals,
                new float[] {0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f},
                new float[] {
                        1.0f, 0.0f, 1.0f,
                        1.0f, 0.0f, 1.0f,
                        1.0f, 0.0f, 1.0f
                },
                new float[] {
                        0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f
                }, BoundingBox.empty());
        Material material = new Material("outline material");
        OutlineRenderer3D renderer = new OutlineRenderer3D(graphics);
        try {
            render(renderer, graphics, positionNormal, material);
            render(renderer, graphics, equivalentPositionNormal, material);
            render(renderer, graphics, positionNormalColor, material);
            render(renderer, graphics, pbr, material);
            assertEquals(3, graphics.device.pipelineCount);
        }
        finally {
            renderer.dispose();
            positionNormal.dispose();
            equivalentPositionNormal.dispose();
            positionNormalColor.dispose();
            pbr.dispose();
        }
    }

    @Test
    void constructorDisposesCreatedShadersWhenLaterVariantFails() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        graphics.device.failShaderCreationAt = 3;

        FdxException failure = assertThrows(FdxException.class,
                () -> new OutlineRenderer3D(graphics));

        assertEquals("synthetic shader creation failure", failure.getMessage());
        assertEquals(3, graphics.device.shaderCreationCount);
        assertEquals(2, graphics.device.shaderModules.size());
        assertTrue(graphics.device.shaderModules.get(0).isDisposed());
        assertTrue(graphics.device.shaderModules.get(1).isDisposed());
    }

    @Test
    void endClosesOwnedPassAndResetsStateWhenFlushFails() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        OutlineRenderer3D renderer = new OutlineRenderer3D(graphics);
        Mesh mesh = new Mesh(graphics, "unsupported outline mesh",
                Mesh.POSITION_COLOR_LAYOUT, new float[] {
                        0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f,
                        1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f,
                        0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f
                }, 3, BoundingBox.empty());
        Material material = new Material("unsupported outline material");
        try {
            renderer.begin(new Camera());
            renderer.render(new Renderable3D(new MeshPart(mesh, 0, 3),
                    material, Matrix4.IDENTITY, BoundingBox.empty()));
            FdxException failure = assertThrows(FdxException.class,
                    renderer::end);
            assertEquals("OutlineRenderer3D requires a static mesh layout with normals",
                    failure.getMessage());
            FakeRenderPass failedPass = graphics.frame.encoder.lastPass;
            assertTrue(failedPass.ended);

            renderer.begin(new Camera());
            FakeRenderPass recoveryPass = graphics.frame.encoder.lastPass;
            renderer.end();
            assertTrue(recoveryPass.ended);
        }
        finally {
            renderer.dispose();
            mesh.dispose();
        }
    }

    private static void render(OutlineRenderer3D renderer,
            FakeGraphicsContext graphics, Mesh mesh, Material material) {
        renderer.begin(new Camera());
        renderer.render(new Renderable3D(new MeshPart(mesh, 0, 3),
                material, Matrix4.IDENTITY, BoundingBox.empty()));
        renderer.end();
        assertTrue(graphics.frame.encoder.lastPass.ended);
    }

    private static final class FakeGraphicsContext implements GraphicsContext {
        private final FakeGraphicsDevice device = new FakeGraphicsDevice();
        private final FakeGraphicsFrame frame = new FakeGraphicsFrame();

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
            return frame;
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
        private int pipelineCount;
        private int shaderCreationCount;
        private int failShaderCreationAt = -1;
        private final List<FakeShaderModule> shaderModules =
                new ArrayList<FakeShaderModule>();

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
        public ShaderModule createShaderModule(
                ShaderModuleDescriptor descriptor) {
            shaderCreationCount++;
            if (shaderCreationCount == failShaderCreationAt) {
                throw new FdxException("synthetic shader creation failure");
            }
            FakeShaderModule shader = new FakeShaderModule();
            shaderModules.add(shader);
            return shader;
        }

        @Override
        public RenderPipeline createRenderPipeline(
                RenderPipelineDescriptor descriptor) {
            pipelineCount++;
            return new FakeRenderPipeline();
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

    private static final class FakeGraphicsFrame implements GraphicsFrame {
        private final FakeCommandEncoder encoder = new FakeCommandEncoder();
        private final TextureView colorAttachment = new FakeTextureView();

        @Override
        public CommandEncoder commandEncoder() {
            return encoder;
        }

        @Override
        public FrameBuffer frameBuffer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TextureView colorAttachment() {
            return colorAttachment;
        }

        @Override
        public int width() {
            return 16;
        }

        @Override
        public int height() {
            return 16;
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

    private static final class FakeCommandEncoder implements CommandEncoder {
        private FakeRenderPass lastPass;

        @Override
        public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
            lastPass = new FakeRenderPass();
            return lastPass;
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

    private static final class FakeRenderPass implements RenderPass {
        private boolean ended;

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
        public void setParameterBlock(int group, int binding,
                ShaderParameterBlock block) {
        }

        @Override
        public void draw(int vertexCount, int instanceCount,
                int firstVertex, int firstInstance) {
        }

        @Override
        public void end() {
            ended = true;
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

    private static final class FakeTextureView implements TextureView {
        @Override
        public TextureFormat format() {
            return TextureFormat.RGBA8_UNORM;
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
        private boolean disposed;

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
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class FakeRenderPipeline implements RenderPipeline {
        private boolean disposed;

        @Override
        public io.github.libfdx.graphics.RenderTargetLayout targetLayout() {
            return null;
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
