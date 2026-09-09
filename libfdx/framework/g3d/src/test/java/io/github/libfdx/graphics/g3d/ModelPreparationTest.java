package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shader.runtime.*;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities.Execution.WORKERS;
import static org.junit.jupiter.api.Assertions.*;

class ModelPreparationTest {
    @Test void opaqueMaterialIsUnsupportedInBothPreloadAndRuntimeCaptureWithoutCallingIt() {
        Fixture f = new Fixture();
        f.renderable.material().shaderProvider((renderable, context) -> { throw new AssertionError("Opaque shader callback invoked"); });
        var scope = f.service.createScope("opaque");
        assertEquals(ShaderPreparationState.UNSUPPORTED, f.plan.include(scope, f.renderable, ShaderPassId.FORWARD, TARGET).state());
        var report = f.service.prepareAsync(scope.seal()); f.service.update();
        assertEquals(1, report.get().unsupportedCount());
        var capture = f.service.captureRuntime("opaque");
        ModelBatch batch = f.batch(null);
        for (int i = 0; i < 4; i++) { f.service.update(); f.draw(batch, ShaderPassId.FORWARD); }
        assertEquals(1, batch.skippedDrawsLastFrame().unsupported());
        assertEquals(0, f.draws); assertTrue(f.jobs.isEmpty());
        var export = capture.snapshot();
        assertEquals(1, export.discoveries().size());
        assertEquals(ShaderPreloadDiscovery.Cause.UNSUPPORTED, export.discoveries().getFirst().cause());
        assertEquals(4, export.discoveries().getFirst().skippedDraws());
        assertEquals("libfdx.requires-input", export.manifest().recipes().getFirst().factory());
        assertTrue(export.markdown().contains("REQUIRES_INPUT"));
        batch.dispose(); scope.dispose(); f.close();
    }

    @Test void opaqueBatchRendererProducesAnUnsupportedCaptureInsteadOfCompilingTheDefault() {
        Fixture f = new Fixture();
        var capture = f.service.captureRuntime("opaque batch");
        ModelBatch batch = new ModelBatch(f.graphics, new ModelBatchConfig().preparation(f.service)
                .shaderProvider((renderable, context) -> { throw new AssertionError("Opaque renderer invoked"); }));
        f.draw(batch, ShaderPassId.FORWARD); f.service.update();
        assertEquals(1, batch.skippedDrawsLastFrame().unsupported());
        assertEquals(ShaderPreparationState.UNSUPPORTED, capture.snapshot().discoveries().getFirst().state());
        assertEquals("libfdx.requires-input", capture.snapshot().manifest().recipes().getFirst().factory());
        assertTrue(f.jobs.isEmpty());
        batch.dispose(); f.close();
    }

    @Test void opaqueMaterialTransitionHidesEveryRequiredPassAndRestoringDefinitionsReusesReadyWork() {
        Fixture f = new Fixture();
        var group = new ModelShaderGroup(f.service, f.plan).include(f.renderable,
                new ShaderPassId[] {ShaderPassId.FORWARD, ShaderPassId.SHADOW}, new RenderTargetLayout[] {TARGET, TARGET});
        ModelBatch batch = f.batch(group);
        group.beginFrame(); f.service.update(); f.jobs.forEach(job -> job.done = true);
        f.service.update(); group.beginFrame(); assertTrue(group.isReady(f.renderable));
        f.renderable.material().shaderProvider((renderable, context) -> { throw new AssertionError("Opaque renderer invoked"); });
        var capture = f.service.captureRuntime("opaque transition");
        f.service.update(); group.beginFrame();
        assertFalse(group.isReady(f.renderable));
        assertEquals(ShaderPreparationState.UNSUPPORTED, group.state(f.renderable));
        f.draw(batch, ShaderPassId.FORWARD); f.draw(batch, ShaderPassId.SHADOW);
        assertEquals(0, f.draws); assertEquals(2, batch.skippedDrawsLastFrame().unsupported());
        assertEquals(2, capture.snapshot().discoveries().size());
        assertTrue(capture.snapshot().manifest().recipes().stream().allMatch(recipe -> recipe.factory().equals("libfdx.requires-input")));
        f.renderable.material().shaderProvider(null);
        f.service.update(); group.beginFrame(); assertTrue(group.isReady(f.renderable));
        f.draw(batch, ShaderPassId.FORWARD); f.draw(batch, ShaderPassId.SHADOW);
        assertEquals(2, f.draws); assertEquals(2, f.jobs.size());
        batch.dispose(); group.dispose(); f.close();
    }

    @Test void explicitPbrRendererExposesItsSharedPlanAndConsumesItsPreloadedMaterialPass() {
        Fixture f = new Fixture();
        PbrShaderProvider renderer = new PbrShaderProvider(f.graphics, new PbrShaderConfig().shaderPlan(f.plan));
        assertSame(f.plan, renderer.preparationPlan());
        f.renderable.material().shaderProvider(renderer);
        var scope = f.service.createScope("explicit PBR");
        f.plan.include(scope, f.renderable, ShaderPassId.FORWARD, TARGET);
        f.service.prepareAsync(scope.seal()); f.service.update(); f.jobs.getFirst().done = true; f.service.update();
        ModelBatch batch = new ModelBatch(f.graphics, new ModelBatchConfig().preparation(f.service).shaderProvider(renderer));
        f.draw(batch, ShaderPassId.FORWARD);
        assertEquals(1, f.draws); assertEquals(1, f.jobs.size());
        batch.dispose(); renderer.dispose(); scope.dispose(); f.close();
    }

    @Test void independentShadowProviderMustBeReadyBeforeEitherPassCanDraw() {
        Fixture f = new Fixture();
        MutableProvider shadowProvider = new MutableProvider(f.device);
        ModelShaderPlan shadowPlan = new ModelShaderPlan(f.graphics, shadowProvider);
        ModelShaderGroup group = new ModelShaderGroup(f.service, f.plan)
                .usePlan(ShaderPassId.SHADOW, shadowPlan).include(f.renderable,
                        new ShaderPassId[]{ShaderPassId.FORWARD, ShaderPassId.SHADOW},
                        new RenderTargetLayout[]{TARGET, TARGET});
        group.beginFrame(); f.service.update();
        assertEquals(2, f.jobs.size());
        f.jobs.get(0).done = true;
        f.service.update(); group.beginFrame();
        assertNull(group.resolve(f.renderable, ShaderPassId.FORWARD, TARGET, f.plan));
        assertNull(group.resolve(f.renderable, ShaderPassId.SHADOW, TARGET, shadowPlan));
        f.jobs.get(1).done = true;
        f.service.update(); group.beginFrame();
        assertNotNull(group.resolve(f.renderable, ShaderPassId.FORWARD, TARGET, f.plan));
        assertNotNull(group.resolve(f.renderable, ShaderPassId.SHADOW, TARGET, shadowPlan));
        assertThrows(RuntimeException.class, () -> group.resolve(f.renderable, ShaderPassId.SHADOW, TARGET, f.plan));
        group.dispose(); shadowPlan.dispose(); f.close();
    }

    @Test void shadowRevisionChangeDefersBothPassesUntilItsReplacementIsPublished() {
        Fixture f = new Fixture();
        MutableProvider shadowProvider = new MutableProvider(f.device);
        ModelShaderPlan shadowPlan = new ModelShaderPlan(f.graphics, shadowProvider);
        ModelShaderGroup group = new ModelShaderGroup(f.service, f.plan)
                .usePlan(ShaderPassId.SHADOW, shadowPlan).include(f.renderable,
                        new ShaderPassId[]{ShaderPassId.FORWARD, ShaderPassId.SHADOW},
                        new RenderTargetLayout[]{TARGET, TARGET});
        group.beginFrame(); f.service.update(); f.jobs.forEach(job -> job.done = true);
        f.service.update(); group.beginFrame();
        var forward = group.resolve(f.renderable, ShaderPassId.FORWARD, TARGET, f.plan);
        assertNotNull(forward);
        shadowProvider.revision++;
        f.service.update(); group.beginFrame();
        assertNull(group.resolve(f.renderable, ShaderPassId.FORWARD, TARGET, f.plan));
        assertNull(group.resolve(f.renderable, ShaderPassId.SHADOW, TARGET, shadowPlan));
        f.service.update(); group.beginFrame();
        assertEquals(3, f.jobs.size(), "The unchanged forward requirement is reused");
        f.jobs.getLast().done = true;
        assertNull(group.resolve(f.renderable, ShaderPassId.FORWARD, TARGET, f.plan));
        f.service.update(); group.beginFrame();
        assertSame(forward, group.resolve(f.renderable, ShaderPassId.FORWARD, TARGET, f.plan));
        assertEquals(shadowProvider.revision,
                group.resolve(f.renderable, ShaderPassId.SHADOW, TARGET, shadowPlan).providerRevision());
        group.dispose(); shadowPlan.dispose(); f.close();
    }

    @Test void unknownRevisionCompatibilitySkipsUntilReplacementIsReady() {
        Fixture f = new Fixture();
        MutableProvider provider = new MutableProvider(f.device);
        ModelShaderPlan plan = new ModelShaderPlan(f.graphics, provider);
        ModelPreparedShaders cache = new ModelPreparedShaders(f.service, plan);
        assertNull(cache.resolve(f.renderable, ShaderPassId.FORWARD, TARGET));
        f.service.update(); f.jobs.getFirst().done = true; f.service.update();
        assertNotNull(cache.resolve(f.renderable, ShaderPassId.FORWARD, TARGET));
        provider.revision++;
        assertNull(cache.resolve(f.renderable, ShaderPassId.FORWARD, TARGET));
        f.service.update(); f.jobs.getLast().done = true; f.service.update();
        assertEquals(provider.revision, cache.resolve(f.renderable, ShaderPassId.FORWARD, TARGET).providerRevision());
        assertEquals(2, f.jobs.size());
        cache.dispose(); plan.dispose(); f.close();
    }

    @Test void explicitlyCompatibleRevisionKeepsReadyRenderingWhenReplacementFails() {
        Fixture f = new Fixture();
        MutableProvider provider = new MutableProvider(f.device); provider.compatible = true;
        ModelShaderPlan plan = new ModelShaderPlan(f.graphics, provider);
        ModelPreparedShaders cache = new ModelPreparedShaders(f.service, plan);
        cache.resolve(f.renderable, ShaderPassId.FORWARD, TARGET);
        f.service.update(); f.jobs.getFirst().done = true; f.service.update();
        var previous = cache.resolve(f.renderable, ShaderPassId.FORWARD, TARGET);
        provider.revision++;
        assertSame(previous, cache.resolve(f.renderable, ShaderPassId.FORWARD, TARGET));
        f.service.update(); f.jobs.getLast().failed = true; f.jobs.getLast().done = true; f.service.update();
        assertSame(previous, cache.resolve(f.renderable, ShaderPassId.FORWARD, TARGET));
        assertEquals(2, f.jobs.size());
        cache.dispose(); plan.dispose(); f.close();
    }

    private static final class MutableProvider implements ShaderProvider {
        final GraphicsDevice device;
        long revision;
        boolean compatible;
        ShaderPassId incompatiblePass;
        MutableProvider(GraphicsDevice device) { this.device = device; }
        @Override public GraphicsDevice preparationDevice() { return device; }
        @Override public boolean supportsPassResolution() { return true; }
        @Override public boolean supports(ShaderRequest request) { return true; }
        @Override public long revision() { return revision; }
        @Override public boolean canRenderPreparedRevision(ShaderRequest request, ResolvedShaderPass previous) {
            return (compatible && !request.passId().equals(incompatiblePass))
                    || ShaderProvider.super.canRenderPreparedRevision(request, previous);
        }
        @Override public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
            return device.prepareRenderPipeline(new ShaderPipelineRequest(ShaderModuleDescriptor.wgsl("fake", "fake"),
                    new RenderPipelineDescriptor().vertexLayouts(request.vertexLayouts()).primitiveTopology(request.topology())
                            .renderTargetLayout(request.renderPass().targetLayout()), request.passId(), revision));
        }
    }

    @Test void oneIncompatiblePassDiscardsTheWholeOldRevisionGroup() {
        Fixture f = new Fixture();
        MutableProvider provider = new MutableProvider(f.device); provider.compatible = true;
        ModelShaderPlan plan = new ModelShaderPlan(f.graphics, provider);
        ModelShaderGroup group = new ModelShaderGroup(f.service, plan).include(f.renderable,
                new ShaderPassId[] {ShaderPassId.SHADOW, ShaderPassId.FORWARD}, new RenderTargetLayout[] {TARGET, TARGET});
        group.beginFrame(); f.service.update(); f.jobs.forEach(job -> job.done = true);
        f.service.update(); group.beginFrame();
        assertTrue(group.isReady(f.renderable));
        provider.revision++; provider.incompatiblePass = ShaderPassId.SHADOW;
        f.service.update(); group.beginFrame();
        assertFalse(group.isReady(f.renderable));
        assertNull(group.resolve(f.renderable, ShaderPassId.SHADOW, TARGET));
        assertNull(group.resolve(f.renderable, ShaderPassId.FORWARD, TARGET));
        group.dispose(); plan.dispose(); f.close();
    }

    @Test void surfacePreloadIncludesTheDepthLayoutUsedByModelBatch() {
        Fixture f = new Fixture();
        TextureView color = proxy(TextureView.class, (p, m, a) -> switch (m.getName()) {
            case "format" -> TextureFormat.RGBA8_UNORM;
            case "width" -> 800;
            case "height" -> 600;
            case "sampleCount" -> 1;
            default -> throw new AssertionError(m);
        });
        GraphicsFrame frame = proxy(GraphicsFrame.class, (p, m, a) -> switch (m.getName()) {
            case "colorAttachment" -> color;
            default -> throw new AssertionError("Collection must not begin a pass or use color-only frame compatibility: " + m);
        });
        var layout = f.plan.surfaceTarget(frame);
        assertEquals(TextureFormat.DEPTH32_FLOAT, layout.depthStencilFormat());
        assertEquals(TextureFormat.RGBA8_UNORM, layout.colorFormat(0));
        assertEquals("surface", f.plan.targets().role(layout));
        assertTrue(f.jobs.isEmpty());
        f.close();
    }
    @Test void modelRecipeFromActualDrawReplaysAgainstAFreshPlan() {
        Fixture cold = new Fixture();
        var capture = cold.service.captureRuntime("models");
        ModelBatch first = cold.batch(null); cold.draw(first, ShaderPassId.FORWARD);
        var manifest = ShaderPreloadManifest.fromJson(capture.snapshot().manifest().toJson());
        assertEquals(-1, capture.snapshot().discoveries().getFirst().timings().firstDrawNanos());
        assertEquals("libfdx.model", manifest.recipes().getFirst().factory());
        Fixture replay = new Fixture();
        var scope = replay.service.createScope("models");
        assertFalse(scope.include(manifest, recipe -> replay.plan.resolve(recipe, replay.plan.targets())).hasUnresolvedEntries());
        replay.service.prepareAsync(scope.seal()); replay.service.update();
        replay.jobs.getFirst().done = true; replay.service.update();
        var observations = replay.service.captureRuntime("models");
        ModelBatch second = replay.batch(null); replay.draw(second, ShaderPassId.FORWARD);
        assertEquals(0, second.skippedDrawsLastFrame().total());
        assertEquals(ShaderPreloadDiscovery.Cause.NONE, observations.snapshot().discoveries().getFirst().cause());
        assertTrue(observations.snapshot().discoveries().getFirst().timings().firstDrawNanos() >= 0);
        assertEquals(1, replay.jobs.size());
        first.dispose(); cold.close(); second.dispose(); scope.dispose(); replay.close();
    }
    private static final RenderTargetLayout TARGET = RenderTargetLayout.color(TextureFormat.RGBA8_UNORM);
    private static final ShaderResourceLayout RESOURCES = ShaderResourceLayout.all(
            ShaderReflection.builder(ShaderProfile.PORTABLE_WEBGPU)
                    .entryPoints(ShaderEntryPoint.builder("vertexMain", ShaderStage.VERTEX).build(),
                            ShaderEntryPoint.builder("fragmentMain", ShaderStage.FRAGMENT).build()).build());

    @Test void asyncBatchQueuesOnceSkipsPendingThenDrawsWithoutAnotherRequest() {
        Fixture f = new Fixture();
        ModelBatch batch = f.batch(null);
        assertTrue(f.jobs.isEmpty());
        f.draw(batch, ShaderPassId.FORWARD);
        assertEquals(1, batch.skippedDrawsLastFrame().pending());
        assertEquals(0, f.draws);
        f.service.update();
        assertEquals(1, f.jobs.size());
        f.jobs.getFirst().done = true;
        f.service.update();
        f.draw(batch, ShaderPassId.FORWARD);
        assertEquals(1, f.draws);
        assertEquals(0, batch.skippedDrawsLastFrame().total());
        for (int i = 0; i < 5; i++) { f.service.update(); f.draw(batch, ShaderPassId.FORWARD); }
        assertEquals(1, f.jobs.size());
        batch.dispose(); f.close();
    }

    @Test void modelsSharingOnePipelineRetainTheirOwnLabelsInTheCapture() {
        Fixture f = new Fixture();
        var capture = f.service.captureRuntime("models");
        ModelBatch batch = f.batch(null);
        Mesh otherMesh = Mesh.coloredTriangle(f.graphics, "other triangle");
        Renderable3D other = new Renderable3D(new MeshPart(otherMesh, 0, 3), new Material("other material"),
                new Matrix4(), BoundingBox.empty());
        batch.begin(f.pass, f.camera, ShaderPassId.FORWARD);
        batch.render(f.renderable); batch.render(other); batch.end();
        var discoveries = capture.snapshot().discoveries();
        assertEquals(1, discoveries.size());
        assertEquals(Map.of("triangle", "triangle", "other triangle", "other material"),
                discoveries.getFirst().origins().stream().collect(Collectors.toMap(
                        ShaderPreparationOrigin::content, ShaderPreparationOrigin::material)));
        f.service.update(); assertEquals(1, f.jobs.size());
        batch.dispose(); otherMesh.dispose(); f.close();
    }

    @Test void preloadSurvivesBatchConstructionWithoutAnotherPreparation() {
        Fixture f = new Fixture();
        var scope = f.service.createScope("level");
        f.plan.include(scope, f.renderable, ShaderPassId.FORWARD, TARGET);
        var completion = f.service.prepareAsync(scope.seal());
        f.service.update(); f.jobs.getFirst().done = true; f.service.update();
        assertTrue(completion.get().allReady());
        ModelBatch batch = f.batch(null);
        f.draw(batch, ShaderPassId.FORWARD);
        assertEquals(1, f.draws);
        assertEquals(1, f.jobs.size());
        batch.dispose(); scope.dispose(); f.close();
    }

    @Test void groupKeepsShadowAndForwardHiddenUntilBothAreReadyAtFrameBoundary() {
        Fixture f = new Fixture();
        ModelShaderGroup group = new ModelShaderGroup(f.service, f.plan).include(f.renderable,
                new ShaderPassId[] {ShaderPassId.SHADOW, ShaderPassId.FORWARD}, new RenderTargetLayout[] {TARGET, TARGET});
        ModelBatch batch = f.batch(group);
        group.beginFrame();
        f.service.update();
        assertEquals(2, f.jobs.size());
        f.jobs.getFirst().done = true;
        f.service.update(); group.beginFrame();
        f.draw(batch, ShaderPassId.SHADOW);
        f.jobs.getLast().done = true; // A worker finishing mid-frame must not change the group's decision.
        f.draw(batch, ShaderPassId.FORWARD);
        assertEquals(0, f.draws);
        assertEquals(2, batch.skippedDrawsLastFrame().pending());
        f.service.update(); group.beginFrame();
        f.draw(batch, ShaderPassId.SHADOW); f.draw(batch, ShaderPassId.FORWARD);
        assertEquals(2, f.draws);
        assertEquals(0, batch.skippedDrawsLastFrame().total());
        batch.dispose(); group.dispose(); f.close();
    }

    @Test void oneFailedRequiredPassKeepsItsWholeGroupHiddenAndDoesNotRetry() {
        Fixture f = new Fixture();
        ModelShaderGroup group = new ModelShaderGroup(f.service, f.plan).include(f.renderable,
                new ShaderPassId[] {ShaderPassId.SHADOW, ShaderPassId.FORWARD}, new RenderTargetLayout[] {TARGET, TARGET});
        ModelBatch batch = f.batch(group);
        group.beginFrame(); f.service.update();
        f.jobs.forEach(job -> job.done = true); f.jobs.getFirst().failed = true;
        for (int i = 0; i < 3; i++) {
            f.service.update(); group.beginFrame();
            f.draw(batch, ShaderPassId.SHADOW); f.draw(batch, ShaderPassId.FORWARD);
            assertEquals(2, batch.skippedDrawsLastFrame().failed());
        }
        assertEquals(0, f.draws); assertEquals(2, f.jobs.size());
        batch.dispose(); group.dispose(); f.close();
    }

    static final class Fixture {
        final Object domain = new Object();
        final List<Job> jobs = new ArrayList<>();
        final GraphicsDevice device;
        final GraphicsContext graphics;
        final ShaderPreparation service;
        final ModelShaderPlan plan;
        final RenderPass pass;
        final Mesh mesh;
        final Renderable3D renderable;
        final Camera camera = new Camera();
        int draws;
        Fixture() {
            device = proxy(GraphicsDevice.class, (p, m, a) -> switch (m.getName()) {
                case "resourceDomain" -> domain;
                case "providerId" -> ProviderId.of("test");
                case "capabilities" -> GraphicsCapabilities.conservativeRender();
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
                case "writeBuffer" -> null;
                default -> throw new AssertionError("Synchronous shader preparation forbidden: " + m);
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
                case "setVertexBuffer", "setIndexBuffer" -> null;
                default -> throw new AssertionError(m);
            });
            service = new ShaderPreparation(device, ShaderPreparationOptions.DEFAULT);
            plan = new ModelShaderPlan(graphics);
            plan.targets().register("surface", TARGET);
            mesh = Mesh.coloredTriangle(graphics, "triangle");
            renderable = new Renderable3D(new MeshPart(mesh, 0, 3), new Material("triangle"), new Matrix4(), BoundingBox.empty());
        }
        ModelBatch batch(ModelShaderGroup group) {
            return new ModelBatch(graphics, new ModelBatchConfig().preparation(service).shaderPlan(plan).shaderGroup(group));
        }
        void draw(ModelBatch batch, ShaderPassId id) { batch.begin(pass, camera, id); batch.render(renderable); batch.end(); }
        void close() {
            service.disposeAsync(); jobs.forEach(job -> job.done = true); service.update();
            plan.dispose(); mesh.dispose();
            assertFalse(service.hasPendingWork());
        }
    }

    static final class Job implements ShaderPreparationOperation {
        final ShaderPipelineRequest packet;
        boolean done, failed, disposed, released;
        Job(ShaderPipelineRequest packet) { this.packet = packet; }
        @Override public boolean isDone() { return done; }
        @Override public ShaderPreparationPhase phase() { return ShaderPreparationPhase.COMPILATION; }
        @Override public ShaderPreparedResult finish() {
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
        @Override public void cancel() { }
        @Override public void dispose() { assertTrue(done); disposed = true; }
        @Override public boolean isDisposed() { return disposed; }
    }
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
