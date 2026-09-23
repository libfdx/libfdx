package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.reflection.*;
import io.github.libfdx.graphics.shader.runtime.*;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.math.ClipDepthRange;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities.Execution.WORKERS;
import static org.junit.jupiter.api.Assertions.*;

class SpritePreparationTest {
    @Test
    void capturedBuiltInSpriteRecipeReplaysWithoutCompilingUnusedGeometryPaths() {
        Fixture cold = new Fixture(false);
        var capture = cold.service.captureRuntime("sprites");
        SpriteBatch first = cold.batch();
        cold.draw(first, 3);
        var manifest = ShaderPreloadManifest.fromJson(capture.snapshot().manifest().toJson());
        assertEquals(-1, capture.snapshot().discoveries().getFirst().timings().firstDrawNanos());
        assertEquals(1, manifest.recipes().size());
        assertEquals("libfdx.sprite", manifest.recipes().getFirst().factory());
        Fixture replay = new Fixture(false);
        var scope = replay.service.createScope("sprites");
        assertFalse(scope.include(manifest, recipe -> replay.plan.resolve(recipe, replay.plan.targets())).hasUnresolvedEntries());
        replay.service.prepareAsync(scope.seal()); replay.service.update();
        replay.jobs.forEach(job -> job.done = true); replay.service.update();
        var observed = replay.service.captureRuntime("sprites");
        SpriteBatch second = replay.batch();
        replay.draw(second, 3); replay.service.update();
        assertEquals(0, second.skippedDrawsLastFrame().total());
        assertFalse(replay.service.hasPendingWork());
        assertEquals(ShaderPreloadDiscovery.Cause.NONE, observed.snapshot().discoveries().getFirst().cause());
        assertTrue(observed.snapshot().discoveries().getFirst().timings().firstDrawNanos() >= 0);
        assertEquals(1, replay.jobs.size());
        first.dispose(); cold.close(); second.dispose(); scope.dispose(); replay.close();
    }
    static final RenderTargetLayout TARGET = RenderTargetLayout.color(TextureFormat.RGBA8_UNORM);
    static final ShaderResourceLayout RESOURCES = ShaderResourceLayout.all(
            ShaderReflection.builder(ShaderProfile.PORTABLE_WEBGPU)
                    .entryPoints(ShaderEntryPoint.builder("vertexMain", ShaderStage.VERTEX).build(),
                            ShaderEntryPoint.builder("fragmentMain", ShaderStage.FRAGMENT).build())
                    .bindings(ShaderBinding.builder(0, 0, "texture", ShaderResourceKind.SAMPLED_TEXTURE)
                                    .visibility(ShaderStageVisibility.FRAGMENT).access(ShaderResourceAccess.READ)
                                    .texture(ShaderTextureDimension.D2, ShaderTextureSampleType.UNKNOWN_FILTERABLE).build(),
                            ShaderBinding.builder(0, 1, "sampler", ShaderResourceKind.SAMPLER)
                                    .visibility(ShaderStageVisibility.FRAGMENT).access(ShaderResourceAccess.NONE)
                                    .samplerKind(ShaderSamplerKind.UNKNOWN_FILTERING).build()).build());

    @Test
    void constructionAndPendingDrawingNeverCompileOrUploadSprites() {
        Fixture f = new Fixture(false);
        SpriteBatch batch = f.batch();
        assertEquals(0, f.jobs.size());
        int writes = f.writes;
        f.draw(batch, 3);
        assertEquals(3, batch.skippedDrawsLastFrame().pending());
        assertEquals(writes, f.writes);
        assertEquals(0, f.draws);
        f.service.update();
        assertEquals(1, f.jobs.size());
        f.jobs.forEach(job -> job.done = true);
        f.service.update();
        f.draw(batch, 3);
        assertEquals(0, batch.skippedDrawsLastFrame().total());
        assertEquals(1, f.draws);
        batch.dispose(); f.close();
    }

    @Test
    void preloadPinsExactEntriesAcrossBatchConstructionAndSizedPasses() {
        Fixture f = new Fixture(true);
        var scope = f.service.createScope("HUD");
        f.plan.include(scope, TARGET);
        f.service.prepareAsync(scope.seal());
        f.service.update();
        f.jobs.forEach(job -> job.done = true);
        f.service.update();
        assertEquals(2, scope.readyCount());
        SpriteBatch batch = f.batch();
        f.draw(batch, 4);
        f.service.update();
        assertEquals(2, f.jobs.size());
        assertEquals(0, batch.skippedDrawsLastFrame().total());
        batch.dispose();
        f.service.update();
        assertTrue(f.jobs.stream().noneMatch(job -> job.released));
        scope.dispose(); f.close();
        assertTrue(f.jobs.stream().allMatch(job -> job.released));
    }

    @Test
    void failedWhitePathDoesNotBlockColorAndDoesNotRetryEachFrame() {
        Fixture f = new Fixture(false);
        SpriteBatch batch = f.batch();
        f.draw(batch, 1);
        f.draw(batch.color(1, 0, 0, 1), 1);
        batch.color(1, 1, 1, 1);
        f.service.update();
        for (Job job : f.jobs) {
            job.done = true;
            job.failed = job.packet.passId().equals(SpriteShaderAbi.WHITE.passId());
        }
        f.service.update();
        batch.begin(f.pass);
        batch.color(1, 1, 1, 1).draw(f.texture, 0, 0, 1, 1);
        batch.color(1, 0, 0, 1).draw(f.texture, 0, 0, 1, 1);
        batch.end();
        assertEquals(1, batch.skippedDrawsLastFrame().failed());
        assertEquals(1, f.draws);
        for (int i = 0; i < 5; i++) { f.service.update(); f.draw(batch.color(1, 1, 1, 1), 2); }
        assertEquals(2, f.jobs.size());
        assertEquals(2, batch.skippedDrawsLastFrame().failed());
        batch.dispose(); f.close();
    }

    @Test
    void compactPendingRangeCountsLogicalSpritesAndAccumulatesAcrossBegins() {
        Fixture f = new Fixture(true);
        SpriteBatch batch = f.batch();
        TextureRegion region = new TextureRegion(f.texture);
        batch.begin(f.pass);
        batch.draw(region, new float[] {0, 1, 2}, new float[] {0, 1, 2}, 3, 1, 1, 0, 0, 0);
        batch.end();
        f.draw(batch, 2);
        assertEquals(5, batch.skippedDrawsLastFrame().pending());
        assertEquals(0, f.draws);
        batch.dispose(); f.close();
    }

    static final class Fixture {
        final Object domain = new Object();
        final List<Job> jobs = new ArrayList<>();
        final GraphicsDevice device;
        final GraphicsContext graphics;
        final ShaderPreparation service;
        final SpriteShaderPlan plan;
        final RenderPass pass;
        final Texture texture;
        int writes, draws;
        Fixture(boolean instanced) {
            var caps = GraphicsCapabilities.builder().profile(ShaderProfile.PORTABLE_WEBGPU)
                    .clipDepthRange(ClipDepthRange.ZERO_TO_ONE).colorFormats(TextureFormat.RGBA8_UNORM);
            if (instanced) caps.feature(GraphicsFeature.INSTANCED_DRAW).feature(GraphicsFeature.INDEXED_DRAW);
            GraphicsCapabilities capabilities = caps.build();
            device = proxy(GraphicsDevice.class, (p, m, a) -> switch (m.getName()) {
                case "resourceDomain" -> domain;
                case "providerId" -> ProviderId.of("test");
                case "capabilities" -> capabilities;
                case "shaderPreparationCapabilities" -> new ShaderPreparationCapabilities(WORKERS, WORKERS, true, 4, false, false);
                case "prepareRenderPipeline" -> { Job job = new Job((ShaderPipelineRequest) a[0]); jobs.add(job); yield job; }
                case "createBuffer" -> {
                    int size = ((BufferDescriptor) a[0]).size();
                    yield proxy(Buffer.class, (b, bm, ba) -> switch (bm.getName()) {
                        case "size" -> size;
                        case "dispose" -> null;
                        case "isDisposed" -> false;
                        default -> throw new AssertionError(bm);
                    });
                }
                case "writeBuffer" -> { writes++; yield null; }
                default -> throw new AssertionError("Unexpected device call, synchronous preparation forbidden: " + m);
            });
            graphics = proxy(GraphicsContext.class, (p, m, a) -> switch (m.getName()) {
                case "device" -> device;
                case "surfaceFormat" -> TextureFormat.RGBA8_UNORM;
                case "providerId" -> ProviderId.of("test");
                default -> throw new AssertionError(m);
            });
            pass = proxy(RenderPass.class, (p, m, a) -> switch (m.getName()) {
                case "compatibility" -> RenderPassCompatibility.of(TARGET, 800, 600);
                case "draw", "drawIndexed" -> { draws++; yield null; }
                case "setPipeline" -> { assertNotNull(a[0]); yield null; }
                case "setTexture", "setVertexBuffer", "setIndexBuffer", "end" -> null;
                default -> throw new AssertionError(m);
            });
            texture = proxy(Texture.class, (p, m, a) -> switch (m.getName()) {
                case "width", "height" -> 16;
                default -> throw new AssertionError(m);
            });
            service = new ShaderPreparation(device, new ShaderPreparationOptions(8, 32, 0));
            plan = new SpriteShaderPlan(graphics);
        }
        SpriteBatch batch() { return new SpriteBatch(graphics, new SpriteBatchConfig().preparation(service).shaderPlan(plan)); }
        void draw(SpriteBatch batch, int count) {
            batch.begin(pass);
            for (int i = 0; i < count; i++) batch.draw(texture, i, i, 1, 1);
            batch.end();
        }
        void close() {
            service.disposeAsync();
            jobs.forEach(job -> job.done = true);
            service.update();
            assertFalse(service.hasPendingWork());
        }
    }

    static final class Job implements ShaderPreparationOperation {
        final ShaderPipelineRequest packet;
        boolean done, failed, disposed, released;
        Job(ShaderPipelineRequest packet) { this.packet = packet; }
        @Override
        public boolean isDone() { return done; }
        @Override
        public ShaderPreparationPhase phase() { return ShaderPreparationPhase.COMPILATION; }
        @Override
        public ShaderPreparedResult finish() {
            assertTrue(done);
            if (failed) throw new IllegalStateException("invalid source");
            RenderPipeline pipeline = proxy(RenderPipeline.class, (p, m, a) -> switch (m.getName()) {
                case "targetLayout" -> packet.targetLayout();
                case "dispose" -> { released = true; yield null; }
                case "isDisposed" -> released;
                default -> throw new AssertionError(m);
            });
            return new ShaderPreparedResult(ResolvedShaderPass.of(packet.passId(), pipeline, RESOURCES, packet.providerRevision()), pipeline);
        }
        @Override
        public void cancel() { }
        @Override
        public void dispose() { assertTrue(done); disposed = true; }
        @Override
        public boolean isDisposed() { return disposed; }
    }

    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
