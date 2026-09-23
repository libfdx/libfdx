package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g2d.*;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.*;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.math.Matrix4;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities.Execution.WORKERS;

/** Verifies ready-render behavior, capture, and first-draw hooks using provider doubles. */
final class ShaderReadyRenderingTest {
    private static final RenderTargetLayout TARGET = RenderTargetLayout.color(TextureFormat.RGBA8_UNORM);
    private static final int FRAMES = 100;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preparedSpriteFramesReusePipelinesAndCaptureDraws(boolean instanced) {
        Device device = new Device("gl", instanced);
        Context graphics = new Context(device);
        ShaderPreparation service = new ShaderPreparation(graphics);
        SpriteShaderPlan plan = new SpriteShaderPlan(graphics);
        SpriteBatch batch = new SpriteBatch(graphics, new SpriteBatchConfig().preparation(service).shaderPlan(plan));
        Pass pass = new Pass();
        Texture texture = new TestTexture(TextureDescriptor.rgba8("sprites", 16, 16));
        TextureRegion region = new TextureRegion(texture);
        float[] x = {1, 3, 5}, y = {2, 4, 6};
        var capture = service.captureRuntime("sprites");
        Runnable frame = () -> {
            service.update();
            batch.begin(pass);
            batch.color(1, 1, 1, 1).draw(texture, 0, 0, 4, 4);
            batch.color(1, 0, 0, 1).draw(texture, 4, 0, 4, 4);
            batch.draw(region, x, y, 3, 4, 4, 0, 0, 0);
            batch.end();
        };
        try {
            frame.run();
            device.complete(service);
            int initialDraws = pass.draws;
            frame.run();
            int drawsPerFrame = pass.draws - initialDraws;
            int requests = device.requests;
            int drawsBeforeFrames = pass.draws;
            frames(frame);
            assertEquals(requests, device.requests, "Ready frames requested more compilation");
            assertEquals(0, batch.skippedDrawsLastFrame().total());
            assertTrue(drawsPerFrame >= 2);
            assertEquals(drawsPerFrame * FRAMES, pass.draws - drawsBeforeFrames);
            assertFalse(service.hasPendingWork());
            assertTrue(device.writes > 0);
            assertDrawTimings(capture);
        } finally { batch.dispose(); device.close(service); texture.dispose(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"cpu-test", "gl"})
    void preparedModelFramesReusePipelinesAndCaptureDraws(String provider) {
        Device device = new Device(provider, false);
        Context graphics = new Context(device);
        ShaderPreparation service = new ShaderPreparation(graphics);
        ModelShaderPlan plan = new ModelShaderPlan(graphics);
        ModelBatch batch = new ModelBatch(graphics, new ModelBatchConfig().preparation(service).shaderPlan(plan));
        Mesh mesh = Mesh.positionColor3D(graphics, "models", new float[]{0, .4f, 0, -.4f, -.4f, 0, .4f, -.4f, 0},
                new float[]{1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1}, new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1},
                new float[]{.5f, 0, 0, 1, 1, 1}, new float[]{1, 0, .7f, 1, 0, .7f, 1, 0, .7f},
                new float[9], BoundingBox.empty());
        Renderable3D first = new Renderable3D(new MeshPart(mesh, 0, 3), new Material("first"), Matrix4.IDENTITY, mesh.bounds());
        Renderable3D second = new Renderable3D(new MeshPart(mesh, 0, 3), new Material("second"), Matrix4.IDENTITY, mesh.bounds());
        Camera camera = new Camera().projection(CameraProjection.PERSPECTIVE).viewport(64, 64)
                .nearFar(.1f, 48).position(0, 0, 3).direction(0, 0, -1);
        Pass pass = new Pass();
        var capture = service.captureRuntime("models");
        Runnable frame = () -> {
            service.update(); batch.begin(pass, camera, ShaderPassId.FORWARD);
            batch.render(first); batch.render(second); batch.end();
        };
        try {
            frame.run(); device.complete(service); frame.run();
            int requests = device.requests;
            int drawsBeforeFrames = pass.draws;
            frames(frame);
            assertEquals(requests, device.requests, "Ready frames requested more compilation");
            assertEquals(0, batch.skippedDrawsLastFrame().total());
            assertEquals(2 * FRAMES, pass.draws - drawsBeforeFrames);
            assertFalse(service.hasPendingWork());
            if (provider.equals("gl")) assertTrue(pass.parameterBinds > 0, "GPU PBR path was not exercised");
            else assertTrue(device.writes > FRAMES, "CPU projection path was not exercised");
            assertDrawTimings(capture);
            assertEquals(2, capture.snapshot().discoveries().getFirst().origins().size());
        } finally { batch.dispose(); device.close(service); plan.dispose(); mesh.dispose(); }
    }

    @Test
    void equalTargetLayoutsRemainInterchangeableAsPipelineCacheKeys() {
        var other = RenderTargetLayout.of(new TextureFormat[]{TextureFormat.RGBA8_UNORM}, TextureFormat.UNKNOWN, 1);
        assertEquals(TARGET, other);
        assertEquals(TARGET.hashCode(), other.hashCode());
        var map = new HashMap<RenderTargetLayout, Object>();
        Object pipeline = new Object(); map.put(TARGET, pipeline);
        assertSame(pipeline, map.get(other));
        assertNotEquals(TARGET, RenderTargetLayout.of(new TextureFormat[]{TextureFormat.RGBA8_UNORM}, TextureFormat.DEPTH32_FLOAT, 1));
        assertNotEquals(TARGET, RenderTargetLayout.of(new TextureFormat[]{TextureFormat.RGBA8_UNORM}, TextureFormat.UNKNOWN, 4));
    }

    private static void assertDrawTimings(ShaderPreloadCapture capture) {
        var discoveries = capture.snapshot().discoveries();
        assertFalse(discoveries.isEmpty());
        for (var discovery : discoveries) assertTrue(discovery.timings().firstDrawNanos() >= 0, "Captured path did not draw");
    }

    private static void frames(Runnable frame) { for (int i = 0; i < FRAMES; i++) frame.run(); }

    private static class Handle {
        private static final ProviderId ID = ProviderId.of("rendering-test");
        private boolean disposed;
        public ProviderId providerId() { return ID; }
        @SuppressWarnings("unchecked")
        public <T> T as() { return (T) this; }
        public void dispose() { disposed = true; }
        public boolean isDisposed() { return disposed; }
    }
    private static final class Context extends Handle implements GraphicsContext {
        final Device device;
        Context(Device device) { this.device = device; }
        @Override
        public GraphicsDevice device() { return device; }
        @Override
        public TextureFormat surfaceFormat() { return TextureFormat.RGBA8_UNORM; }
        @Override
        public GraphicsFrame currentFrame() { throw new AssertionError("Use the borrowed pass"); }
        @Override
        public void clear(float r, float g, float b, float a) { throw new AssertionError(); }
        @Override
        public ProviderId providerId() { return device.id; }
    }
    private static final class Device extends Handle implements GraphicsDevice {
        final ProviderId id;
        final GraphicsCapabilities capabilities;
        final List<Job> jobs = new ArrayList<>();
        int requests, writes;
        Device(String provider, boolean instanced) {
            id = ProviderId.of(provider);
            var builder = GraphicsCapabilities.builder().profile(ShaderProfile.PORTABLE_WEBGPU)
                    .clipDepthRange(ClipDepthRange.ZERO_TO_ONE).colorFormats(TextureFormat.RGBA8_UNORM);
            if (instanced) builder.feature(GraphicsFeature.INSTANCED_DRAW).feature(GraphicsFeature.INDEXED_DRAW);
            capabilities = builder.build();
        }
        @Override
        public GraphicsCapabilities capabilities() { return capabilities; }
        @Override
        public ProviderId providerId() { return id; }
        @Override
        public ShaderPreparationCapabilities shaderPreparationCapabilities() {
            return new ShaderPreparationCapabilities(WORKERS, WORKERS, true, 4, false, false);
        }
        @Override
        public ShaderPreparationOperation prepareRenderPipeline(ShaderPipelineRequest request) {
            requests++;
            Job job = new Job(request); jobs.add(job); return job;
        }
        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) { return new TestBuffer(descriptor); }
        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) { writes++; }
        @Override
        public Texture createTexture(TextureDescriptor descriptor) { return new TestTexture(descriptor); }
        @Override
        public void writeTexture(Texture texture, ByteBuffer data) { }
        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) { throw new AssertionError("Synchronous compilation"); }
        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) { throw new AssertionError("Synchronous compilation"); }
        void complete(ShaderPreparation service) {
            service.update();
            for (Job job : jobs) job.done = true;
            service.update();
            assertFalse(service.hasPendingWork());
        }
        void close(ShaderPreparation service) { service.disposeAsync(); complete(service); }
    }
    private static final class TestBuffer extends Handle implements Buffer {
        final BufferDescriptor descriptor;
        TestBuffer(BufferDescriptor descriptor) { this.descriptor = descriptor; }
        @Override
        public int size() { return descriptor.size(); }
        @Override
        public BufferUsage usage() { return descriptor.usage(); }
    }
    private static final class TestTexture extends Handle implements Texture {
        final TextureDescriptor descriptor;
        TestTexture(TextureDescriptor descriptor) { this.descriptor = descriptor; }
        @Override
        public int width() { return descriptor.width(); }
        @Override
        public int height() { return descriptor.height(); }
        @Override
        public TextureFormat format() { return descriptor.format(); }
        @Override
        public TextureUsage usage() { return descriptor.usage(); }
    }
    private static final class Pipeline extends Handle implements RenderPipeline {
        final RenderTargetLayout target;
        Pipeline(RenderTargetLayout target) { this.target = target; }
        @Override
        public RenderTargetLayout targetLayout() { return target; }
    }
    private static final class Job extends Handle implements ShaderPreparationOperation {
        final ShaderPipelineRequest request;
        boolean done;
        Job(ShaderPipelineRequest request) { this.request = request; }
        @Override
        public boolean isDone() { return done; }
        @Override
        public ShaderPreparationPhase phase() { return ShaderPreparationPhase.PUBLICATION_WAIT; }
        @Override
        public ShaderPreparedResult finish() {
            assertTrue(done);
            ShaderReflection reflection = request.sourceDescriptor().reflection();
            if (!reflection.complete()) reflection = ShaderReflection.builder(ShaderProfile.PORTABLE_WEBGPU)
                    .entryPoints(ShaderEntryPoint.builder("vertexMain", ShaderStage.VERTEX).build(),
                            ShaderEntryPoint.builder("fragmentMain", ShaderStage.FRAGMENT).build())
                    .bindings(ShaderBinding.builder(0, 0, "texture", ShaderResourceKind.SAMPLED_TEXTURE)
                                    .visibility(ShaderStageVisibility.FRAGMENT).access(ShaderResourceAccess.READ)
                                    .texture(ShaderTextureDimension.D2, ShaderTextureSampleType.UNKNOWN_FILTERABLE).build(),
                            ShaderBinding.builder(0, 1, "sampler", ShaderResourceKind.SAMPLER)
                                    .visibility(ShaderStageVisibility.FRAGMENT).access(ShaderResourceAccess.NONE)
                                    .samplerKind(ShaderSamplerKind.UNKNOWN_FILTERING).build()).build();
            Pipeline pipeline = new Pipeline(request.targetLayout());
            return new ShaderPreparedResult(ResolvedShaderPass.of(request.passId(), pipeline,
                    ShaderResourceLayout.all(reflection), request.providerRevision()), pipeline);
        }
        @Override
        public void cancel() { done = true; }
    }
    private static final class Pass extends Handle implements RenderPass {
        final RenderPassCompatibility compatibility = RenderPassCompatibility.of(TARGET, 64, 64);
        int draws, parameterBinds;
        @Override
        public RenderPassCompatibility compatibility() { return compatibility; }
        @Override
        public void setPipeline(RenderPipeline pipeline) { assertNotNull(pipeline); }
        @Override
        public void setVertexBuffer(Buffer buffer) { }
        @Override
        public void setVertexBuffer(int slot, Buffer buffer) { }
        @Override
        public void setIndexBuffer(Buffer buffer) { }
        @Override
        public void setTexture(int slot, Texture texture) { }
        @Override
        public void setTextureBinding(int group, int binding, Texture texture) { }
        @Override
        public void setTextureSamplerBinding(int group, int binding, Texture texture) { }
        @Override
        public void setParameterBlock(int group, int binding, ShaderParameterBlock block) { parameterBinds++; }
        @Override
        public void draw(int vertices, int instances, int firstVertex, int firstInstance) { draws++; }
        @Override
        public void drawIndexed(int indices, int instances, int firstIndex, int baseVertex, int firstInstance) { draws++; }
        @Override
        public void end() { }
    }
}
