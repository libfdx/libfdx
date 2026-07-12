package io.github.libfdx.graphics.g3d;

import com.sun.management.ThreadMXBean;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderLanguage;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureUsage;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class CascadedShadowMap3DTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void cpuProjectionAllocatesNoPerDrawObjectsAfterWarmup() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("cpu-test"));
        Renderable3D renderable = renderable(graphics);
        Camera camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(64.0f, 64.0f)
                .nearFar(0.1f, 48.0f)
                .position(0.0f, 0.0f, 3.0f)
                .direction(0.0f, 0.0f, -1.0f);
        Environment3D environment = new Environment3D()
                .add(new DirectionalLight().direction(-0.5f, -1.0f, -0.25f));
        FakeRenderPass pass = new FakeRenderPass();
        RenderContext3D context = new RenderContext3D(graphics, camera, environment, null, pass);
        PbrShaderProvider provider = new PbrShaderProvider(graphics, new PbrShaderConfig());
        Shader3D shader = provider.shader(renderable, context);

        for (int i = 0; i < 2_000; i++) {
            shader.begin(context);
            shader.render(renderable);
            shader.end();
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
        int initialDrawCalls = pass.drawCalls;
        for (int i = 0; i < 2_000; i++) {
            shader.begin(context);
            shader.render(renderable);
            shader.end();
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertEquals(initialDrawCalls + 2_000, pass.drawCalls);
        assertTrue(allocated <= 1_024L, "Expected no post-warm-up CPU projection churn, allocated " + allocated
                + " bytes");
        provider.dispose();
        renderable.meshPart().mesh().dispose();
    }

    @Test
    void updateComputesUniformPerspectiveSplitsAndBounds() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        CascadedShadowMap3D cascades = new CascadedShadowMap3D(graphics, 2, 16, 16)
                .splitLambda(0.0f)
                .padding(1.0f)
                .maxDistance(20.0f);
        Camera camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(10.0f, 5.0f)
                .fieldOfView(90.0f)
                .nearFar(1.0f, 21.0f)
                .position(0.0f, 0.0f, 10.0f)
                .direction(0.0f, 0.0f, -1.0f);

        cascades.update(camera);

        assertEquals(2, cascades.cascadeCount());
        assertSame(cascades.cascade(0), cascades.activeShadowMap());
        assertEquals(10.5f, cascades.splitDistance(0), EPSILON);
        assertEquals(20.0f, cascades.splitDistance(1), EPSILON);
        assertEquals(4.25f, cascades.cascadeCenterZ(0), EPSILON);
        assertEquals(-5.25f, cascades.cascadeCenterZ(1), EPSILON);
        assertTrue(cascades.cascadeHalfSize(0) > 0.0f);
        assertTrue(cascades.cascadeHalfSize(1) > cascades.cascadeHalfSize(0));

        cascades.dispose();
    }

    @Test
    void environmentStoresCascadedShadowMapReference() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        CascadedShadowMap3D cascades = new CascadedShadowMap3D(graphics, 1, 8, 8);
        Environment3D environment = new Environment3D().cascadedShadowMap(cascades);

        assertSame(cascades, environment.cascadedShadowMap());

        environment.clearCascadedShadowMap();

        assertNull(environment.cascadedShadowMap());
        cascades.dispose();
    }

    @Test
    void pbrShaderBindsCascadesOverSingleDirectionalMap() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        CascadedShadowMap3D cascades = new CascadedShadowMap3D(graphics, 2, 16, 16)
                .bias(0.02f)
                .strength(0.5f);
        DirectionalShadowMap3D singleShadow = new DirectionalShadowMap3D(graphics, 16, 16)
                .bias(0.2f)
                .strength(0.1f);
        Camera shadowCamera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(64.0f, 64.0f)
                .nearFar(1.0f, 24.0f)
                .position(0.0f, 0.0f, 6.0f)
                .direction(0.0f, 0.0f, -1.0f);
        Camera renderCamera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(64.0f, 64.0f)
                .nearFar(0.1f, 48.0f)
                .position(9.0f, 4.0f, 2.0f)
                .direction(-1.0f, -0.2f, -0.4f);
        cascades.update(shadowCamera);
        Environment3D environment = new Environment3D()
                .directionalShadowMap(singleShadow)
                .cascadedShadowMap(cascades);
        Renderable3D renderable = renderable(graphics);
        FakeRenderPass pass = new FakeRenderPass();
        RenderContext3D context = new RenderContext3D(graphics, renderCamera, environment, null, pass);
        PbrShaderProvider provider = new PbrShaderProvider(graphics, new PbrShaderConfig());
        Shader3D shader = provider.shader(renderable, context);

        shader.begin(context);
        shader.render(renderable);
        shader.end();

        assertSame(cascades.cascade(0).texture(), pass.textureSlots[5]);
        assertSame(cascades.cascade(1).texture(), pass.textureSlots[6]);
        assertNotNull(pass.textureSlots[7]);
        assertNotNull(pass.textureSlots[8]);
        assertNotNull(pass.shadowParams);
        assertEquals(2.0f, pass.shadowParams[0], EPSILON);
        assertEquals(cascades.cascadeBias(0), pass.shadowParams[1], EPSILON);
        assertEquals(0.5f, pass.shadowParams[2], EPSILON);
        assertNotNull(pass.shadowCascadeSplits);
        assertEquals(cascades.splitDistance(0), pass.shadowCascadeSplits[0], EPSILON);
        assertEquals(cascades.splitDistance(1), pass.shadowCascadeSplits[1], EPSILON);
        assertEquals(0.0f, pass.shadowCascadeSplits[2], EPSILON);
        assertEquals(0.0f, pass.shadowCascadeSplits[3], EPSILON);
        assertNotNull(pass.shadowBiases);
        assertEquals(cascades.cascadeBias(0), pass.shadowBiases[0], EPSILON);
        assertEquals(cascades.cascadeBias(1), pass.shadowBiases[1], EPSILON);
        assertEquals(0.0f, pass.shadowBiases[2], EPSILON);
        assertEquals(0.0f, pass.shadowBiases[3], EPSILON);
        assertNotNull(pass.shadowCameraPosition);
        assertEquals(shadowCamera.position().x(), pass.shadowCameraPosition[0], EPSILON);
        assertEquals(shadowCamera.position().y(), pass.shadowCameraPosition[1], EPSILON);
        assertEquals(shadowCamera.position().z(), pass.shadowCameraPosition[2], EPSILON);
        assertNotNull(pass.shadowCameraDirection);
        assertEquals(shadowCamera.direction().x(), pass.shadowCameraDirection[0], EPSILON);
        assertEquals(shadowCamera.direction().y(), pass.shadowCameraDirection[1], EPSILON);
        assertEquals(shadowCamera.direction().z(), pass.shadowCameraDirection[2], EPSILON);
        assertNotEquals(renderCamera.position().x(), pass.shadowCameraPosition[0], EPSILON);
        assertTrue(pass.shadowMatrices[0]);
        assertTrue(pass.shadowMatrices[1]);
        assertTrue(pass.shadowMatrices[2]);
        assertTrue(pass.shadowMatrices[3]);
        assertEquals(1, pass.drawCalls);

        provider.dispose();
        singleShadow.dispose();
        cascades.dispose();
    }

    @Test
    void pbrShaderBindsSingleDirectionalShadowWhenNoCascades() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        DirectionalShadowMap3D singleShadow = new DirectionalShadowMap3D(graphics, 16, 16)
                .bias(0.03f)
                .strength(0.65f);
        Camera camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(64.0f, 64.0f)
                .nearFar(1.0f, 24.0f)
                .position(0.0f, 0.0f, 6.0f)
                .direction(0.0f, 0.0f, -1.0f);
        Environment3D environment = new Environment3D().directionalShadowMap(singleShadow);
        Renderable3D renderable = renderable(graphics);
        FakeRenderPass pass = new FakeRenderPass();
        RenderContext3D context = new RenderContext3D(graphics, camera, environment, null, pass);
        PbrShaderProvider provider = new PbrShaderProvider(graphics, new PbrShaderConfig());
        Shader3D shader = provider.shader(renderable, context);

        shader.begin(context);
        shader.render(renderable);
        shader.end();

        assertSame(singleShadow.texture(), pass.textureSlots[5]);
        assertNotNull(pass.textureSlots[6]);
        assertNotNull(pass.textureSlots[7]);
        assertNotNull(pass.textureSlots[8]);
        assertNotNull(pass.shadowParams);
        assertEquals(1.0f, pass.shadowParams[0], EPSILON);
        assertEquals(0.03f, pass.shadowParams[1], EPSILON);
        assertEquals(0.65f, pass.shadowParams[2], EPSILON);
        assertNotNull(pass.shadowCascadeSplits);
        assertEquals(0.0f, pass.shadowCascadeSplits[0], EPSILON);
        assertEquals(0.0f, pass.shadowCascadeSplits[1], EPSILON);
        assertEquals(0.0f, pass.shadowCascadeSplits[2], EPSILON);
        assertEquals(0.0f, pass.shadowCascadeSplits[3], EPSILON);
        assertNotNull(pass.shadowBiases);
        assertEquals(0.03f, pass.shadowBiases[0], EPSILON);
        assertEquals(0.0f, pass.shadowBiases[1], EPSILON);
        assertEquals(0.0f, pass.shadowBiases[2], EPSILON);
        assertEquals(0.0f, pass.shadowBiases[3], EPSILON);
        assertTrue(pass.shadowMatrices[0]);
        assertTrue(pass.shadowMatrices[1]);
        assertTrue(pass.shadowMatrices[2]);
        assertTrue(pass.shadowMatrices[3]);
        assertEquals(1, pass.drawCalls);

        provider.dispose();
        singleShadow.dispose();
    }

    private static Renderable3D renderable(FakeGraphicsContext graphics) {
        Mesh mesh = Mesh.positionColor3D(graphics, "cascade-pbr",
                new float[] {
                        0.0f, 0.4f, 0.0f,
                        -0.4f, -0.4f, 0.0f,
                        0.4f, -0.4f, 0.0f
                },
                new float[] {
                        1.0f, 0.2f, 0.2f, 1.0f,
                        0.2f, 1.0f, 0.2f, 1.0f,
                        0.2f, 0.2f, 1.0f, 1.0f
                },
                new float[] {
                        0.0f, 0.0f, 1.0f,
                        0.0f, 0.0f, 1.0f,
                        0.0f, 0.0f, 1.0f
                },
                new float[] {
                        0.5f, 0.0f,
                        0.0f, 1.0f,
                        1.0f, 1.0f
                },
                new float[] {
                        1.0f, 0.0f, 0.7f,
                        1.0f, 0.0f, 0.7f,
                        1.0f, 0.0f, 0.7f
                },
                new float[] {
                        0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f
                },
                BoundingBox.empty());
        return new Renderable3D(new MeshPart("cascade-pbr", mesh, null, 0, mesh.vertexCount()),
                new PbrMaterial("cascade-material"), Matrix4.IDENTITY, mesh.bounds());
    }

    private static final class FakeGraphicsContext implements GraphicsContext {
        private final ProviderId providerId;
        private final FakeGraphicsDevice device = new FakeGraphicsDevice();

        FakeGraphicsContext(ProviderId providerId) {
            this.providerId = providerId;
        }

        @Override
        public FakeGraphicsDevice device() {
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
            return providerId;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeGraphicsDevice implements GraphicsDevice {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-device");

        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            return new FakeBuffer(descriptor.size(), descriptor.usage());
        }

        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
        }

        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            return new FakeTexture(descriptor.width(), descriptor.height(), descriptor.format(), descriptor.usage());
        }

        @Override
        public void writeTexture(Texture texture, ByteBuffer data) {
        }

        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            return new FakeShaderModule(descriptor.language());
        }

        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            return new FakeRenderPipeline(descriptor);
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeBuffer implements Buffer {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-buffer");
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
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeTexture implements Texture {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-texture");
        private final int width;
        private final int height;
        private final TextureFormat format;
        private final TextureUsage usage;
        private final FakeTextureView view;
        private boolean disposed;

        FakeTexture(int width, int height, TextureFormat format, TextureUsage usage) {
            this.width = width;
            this.height = height;
            this.format = format;
            this.usage = usage;
            view = new FakeTextureView(format);
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public TextureFormat format() {
            return format;
        }

        @Override
        public TextureUsage usage() {
            return usage;
        }

        @Override
        public TextureView view() {
            return view;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeTextureView implements TextureView {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-texture-view");
        private final TextureFormat format;

        FakeTextureView(TextureFormat format) {
            this.format = format;
        }

        @Override
        public TextureFormat format() {
            return format;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeShaderModule implements ShaderModule {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-shader-module");
        private final ShaderLanguage language;
        private boolean disposed;

        FakeShaderModule(ShaderLanguage language) {
            this.language = language;
        }

        @Override
        public ShaderLanguage language() {
            return language;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeRenderPipeline implements RenderPipeline {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-pipeline");
        private final RenderPipelineDescriptor descriptor;
        private boolean disposed;

        FakeRenderPipeline(RenderPipelineDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        RenderPipelineDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
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
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-pass");
        private final Texture[] textureSlots = new Texture[9];
        private final boolean[] shadowMatrices = new boolean[4];
        private float[] shadowParams;
        private float[] shadowCascadeSplits;
        private float[] shadowBiases;
        private float[] shadowCameraPosition;
        private float[] shadowCameraDirection;
        private int drawCalls;

        @Override
        public void setPipeline(RenderPipeline pipeline) {
            FakeRenderPipeline fake = pipeline.as();
            assertNotNull(fake.descriptor());
        }

        @Override
        public void setVertexBuffer(Buffer buffer) {
        }

        @Override
        public void setIndexBuffer(Buffer buffer) {
        }

        @Override
        public void setTexture(int slot, Texture texture) {
            if (slot >= 0 && slot < textureSlots.length) {
                textureSlots[slot] = texture;
            }
        }

        @Override
        public void setUniform1i(String name, int value) {
        }

        @Override
        public void setUniform1f(String name, float value) {
        }

        @Override
        public void setUniform3f(String name, float x, float y, float z) {
        }

        @Override
        public void setUniform4f(String name, float x, float y, float z, float w) {
            if ("u_shadowParams".equals(name)) {
                shadowParams = new float[] { x, y, z, w };
            }
            else if ("u_shadowCascadeSplits".equals(name)) {
                shadowCascadeSplits = new float[] { x, y, z, w };
            }
            else if ("u_shadowBiases".equals(name)) {
                shadowBiases = new float[] { x, y, z, w };
            }
            else if ("u_shadowCameraPosition".equals(name)) {
                shadowCameraPosition = new float[] { x, y, z, w };
            }
            else if ("u_shadowCameraDirection".equals(name)) {
                shadowCameraDirection = new float[] { x, y, z, w };
            }
        }

        @Override
        public void setUniformMatrix4(String name, float[] values) {
            if ("u_shadowViewProjection".equals(name) || "u_shadowViewProjection0".equals(name)) {
                shadowMatrices[0] = true;
            }
            else if ("u_shadowViewProjection1".equals(name)) {
                shadowMatrices[1] = true;
            }
            else if ("u_shadowViewProjection2".equals(name)) {
                shadowMatrices[2] = true;
            }
            else if ("u_shadowViewProjection3".equals(name)) {
                shadowMatrices[3] = true;
            }
        }

        @Override
        public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
            drawCalls++;
        }

        @Override
        public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int baseVertex,
                int firstInstance) {
            drawCalls++;
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
