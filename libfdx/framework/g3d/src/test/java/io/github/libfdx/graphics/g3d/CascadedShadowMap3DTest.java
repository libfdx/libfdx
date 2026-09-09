package io.github.libfdx.graphics.g3d;

import com.sun.management.ThreadMXBean;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.collections.Array;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
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
import java.nio.ByteOrder;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class CascadedShadowMap3DTest {
    @Test void asyncCascadesShareDefinitionsWithoutCreatingAnyShaderModule() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        graphics.device.failShader = true;
        ShaderPreparation preparation = new ShaderPreparation(graphics);
        CascadedShadowMap3D maps = new CascadedShadowMap3D(graphics, 3, 64, 64, preparation, null);
        try {
            for (int i = 0; i < maps.cascadeCount(); i++) {
                assertSame(maps.shaderPlan(), maps.cascade(i).shaderPlan());
                assertEquals(maps.preparationTarget(), maps.cascade(i).preparationTarget());
            }
        } finally { maps.dispose(); preparation.dispose(); }
    }

    @Test
    void blendedCasterAlphaReachesTheShadowPassAndInvisibleCastersAreSkipped() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        DirectionalShadowMap3D shadow = new DirectionalShadowMap3D(graphics, 64, 64)
                .bounds(0, 0, 0, 20, .1f, 100).shadowFadeFraction(0);
        Renderable3D caster = renderable(graphics);
        caster.material().alphaMode(MaterialAlphaMode.BLEND);
        var casters = new Array<Renderable3D>();
        casters.add(caster);
        DirectionalLight light = new DirectionalLight().direction(-1, -1, -.5f);
        try {
            for (float alpha : new float[]{.1f, .4f, .8f, 1}) {
                caster.material().set(MaterialAttributes.baseColor(1, 1, 1, alpha));
                shadow.renderRenderables(light, casters.view());
                assertEquals(1, graphics.lastPass.drawCalls);
                assertEquals(alpha, graphics.lastPass.shadowParams[0], .00001f,
                        "The packed shadow must use the model's current opacity");
            }
            caster.material().set(MaterialAttributes.baseColor(1, 1, 1, 0));
            shadow.renderRenderables(light, casters.view());
            assertEquals(0, graphics.lastPass.drawCalls);
            caster.material().alphaMode(MaterialAlphaMode.OPAQUE);
            shadow.renderRenderables(light, casters.view());
            assertEquals(1, graphics.lastPass.shadowParams[0], .00001f,
                    "Opaque materials keep their opaque shadow contract");
        } finally {
            caster.meshPart().mesh().dispose();
            shadow.dispose();
        }
    }

    @Test
    void automaticDirectionalBiasTracksResolutionAndBoundsWithoutChangingManualBias() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        DirectionalShadowMap3D coarse = new DirectionalShadowMap3D(graphics, 1024, 1024)
                .bounds(0, 0, 0, 20, 1, 101).bias(.03f).autoBias(true);
        DirectionalShadowMap3D fine = new DirectionalShadowMap3D(graphics, 2048, 2048)
                .bounds(0, 0, 0, 20, 1, 101).autoBias(true);
        try {
            assertEquals(coarse.bias() / 2, fine.bias(), .00000001f);
            float initial = coarse.bias();
            coarse.bounds(0, 0, 0, 40, 1, 201);
            assertEquals(initial, coarse.bias(), .00000001f);
            coarse.bounds(0, 0, 0, 40, 1, 101);
            assertEquals(initial * 2, coarse.bias(), .00000001f);
            coarse.autoBias(false);
            assertEquals(.03f, coarse.bias());
        } finally { coarse.dispose(); fine.dispose(); }
    }
    private static final float EPSILON = 0.0001f;
    private static final int ALLOCATION_MEASUREMENT_ATTEMPTS = 5;
    private static final int ALLOCATION_OPERATIONS_PER_ATTEMPT = 2_000;

    @Test
    void cachedPassesTrackCameraLightCasterRevisionAndFailures() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        CascadedShadowMap3D maps = new CascadedShadowMap3D(graphics, 2, 256, 256).shadowFadeFraction(0);
        Camera camera = new Camera().viewport(64, 64).nearFar(1, 48)
                .position(0, 0, 10).direction(0, 0, -1);
        DirectionalLight light = new DirectionalLight().direction(-.5f, -1, -.25f);
        ModelInstance[] casters = new ModelInstance[0];
        try {
            assertEquals(2, maps.renderIfNeeded(light, camera, casters, 1));
            int recorded = graphics.passes;
            for (int i = 0; i < 100; i++) assertEquals(0, maps.renderIfNeeded(light, camera, casters, 1));
            assertEquals(recorded, graphics.passes);
            // Receiver-only controls must take effect without rewriting depth maps.
            maps.bias(.04f).strength(.5f).minTexelBias(1);
            assertEquals(0, maps.renderIfNeeded(light, camera, casters, 1));
            camera.position(2, 0, 10);
            assertTrue(maps.renderIfNeeded(light, camera, casters, 1) > 0);
            assertEquals(0, maps.renderIfNeeded(light, camera, casters, 1));
            light.direction(.5f, -1, .25f);
            assertEquals(2, maps.renderIfNeeded(light, camera, casters, 1));
            assertEquals(2, maps.renderIfNeeded(light, camera, casters, 2));
            assertEquals(2, maps.renderIfNeeded(light, camera, casters.clone(), 2));
            assertEquals(2, maps.renderIfNeeded(light, camera, casters, 2));
            maps.shadowFadeFraction(.2f);
            assertTrue(maps.renderIfNeeded(light, camera, casters, 2) > 0);
            assertEquals(0, maps.renderIfNeeded(light, camera, casters, 2));
            maps.invalidateCache();
            assertEquals(2, maps.renderIfNeeded(light, camera, casters, 2));
            graphics.failPassAt = graphics.passes + 2;
            assertThrows(FdxException.class, () -> maps.renderIfNeeded(light, camera, casters, 3));
            assertEquals(1, maps.lastRenderedCascadeCount());
            assertEquals(2, maps.renderIfNeeded(light, camera, casters, 3), "failed updates invalidate every cascade");
            maps.render(light, camera, casters);
            assertEquals(2, maps.lastRenderedCascadeCount());
            assertEquals(2, maps.renderIfNeeded(light, camera, casters, 3), "forced drawing invalidates optional reuse");
        } finally { maps.dispose(); }
        assertThrows(FdxException.class, () -> maps.renderIfNeeded(light, camera, casters, 3));
        assertTrue(graphics.device.textures.stream().allMatch(Texture::isDisposed));
    }

    @Test
    void budgetRejectsExcessAndFailedAllocationReleasesPreviousMaps() {
        assertEquals(2L << 20, ShadowBudget3D.LOW.estimatedBytes());
        assertEquals(16L << 20, ShadowBudget3D.BALANCED.estimatedBytes());
        assertEquals(128L << 20, ShadowBudget3D.HIGH.estimatedBytes());
        assertThrows(FdxException.class, () -> new ShadowBudget3D(4, 2048, 100, 16L << 20));
        assertThrows(FdxException.class, () -> new ShadowBudget3D(0, 512, 100, 16L << 20));
        assertThrows(FdxException.class, () -> new ShadowBudget3D(1, 512, Float.NaN, 16L << 20));
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        graphics.device.failTextureAt = 2;
        assertThrows(FdxException.class, () -> new CascadedShadowMap3D(graphics, 3, 32, 32));
        assertEquals(1, graphics.device.textures.size());
        assertTrue(graphics.device.textures.get(0).isDisposed());
        graphics.device.failTextureAt = 0;
        graphics.device.failShader = true;
        assertThrows(FdxException.class, () -> new DirectionalShadowMap3D(graphics, 32, 32));
        assertTrue(graphics.device.textures.stream().allMatch(Texture::isDisposed));
        graphics.device.failShader = false;
        CascadedShadowMap3D maps = ShadowBudget3D.LOW.create(graphics);
        assertEquals(ShadowBudget3D.LOW.estimatedBytes(), maps.estimatedBytes());
        maps.dispose();
        maps.dispose();
    }

    @Test
    void subTexelMotionReusesStabilizedCascadesWithoutSteadyAllocations() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        CascadedShadowMap3D maps = new CascadedShadowMap3D(graphics, 2, 256, 256).shadowFadeFraction(0);
        Camera camera = new Camera().projection(CameraProjection.ORTHOGRAPHIC).viewport(10, 10)
                .nearFar(1, 40).position(0, 0, 20).direction(0, 0, -1);
        DirectionalLight light = new DirectionalLight().direction(0, -1, 0);
        ModelInstance[] casters = new ModelInstance[0];
        try {
            assertEquals(2, maps.renderIfNeeded(light, camera, casters, 1));
            float[] original = new float[16], shifted = new float[16];
            maps.cascade(0).lightViewProjection().copyValues(original, 0);
            camera.position(.0001f, 0, 20);
            assertEquals(0, maps.renderIfNeeded(light, camera, casters, 1));
            maps.cascade(0).lightViewProjection().copyValues(shifted, 0);
            assertArrayEquals(original, shifted);
            for (int i = 0; i < 2_000; i++) maps.renderIfNeeded(light, camera, casters, 1);
            var platform = ManagementFactory.getThreadMXBean();
            assumeTrue(platform instanceof ThreadMXBean);
            ThreadMXBean bean = (ThreadMXBean)platform;
            assumeTrue(bean.isThreadAllocatedMemorySupported());
            bean.setThreadAllocatedMemoryEnabled(true);
            long threadId = Thread.currentThread().threadId(), minimum = Long.MAX_VALUE;
            for (int attempt = 0; attempt < 5; attempt++) {
                long before = bean.getThreadAllocatedBytes(threadId);
                for (int i = 0; i < 2_000; i++) maps.renderIfNeeded(light, camera, casters, 1);
                minimum = Math.min(minimum, bean.getThreadAllocatedBytes(threadId) - before);
            }
            assertTrue(minimum <= 1024, "Cached shadow path allocated " + minimum + " bytes over 2000 calls");
        } finally { maps.dispose(); }
    }

    @Test
    void pbrFeatureSwitchesAreCopiedAndDisableBorrowedShadowMaps() {
        FakeGraphicsContext graphics = new FakeGraphicsContext(ProviderId.of("gl"));
        CascadedShadowMap3D maps = new CascadedShadowMap3D(graphics, 2, 16, 16);
        Renderable3D renderable = renderable(graphics);
        PbrShaderConfig config = new PbrShaderConfig().enableShadows(false).enableImageBasedLighting(false);
        PbrShaderProvider provider = new PbrShaderProvider(graphics, config);
        config.enableShadows(true).enableImageBasedLighting(true);
        FakeRenderPass pass = new FakeRenderPass();
        RenderContext3D context = new RenderContext3D(graphics, new Camera(),
                new Environment3D().cascadedShadowMap(maps), null, pass);
        try {
            Shader3D shader = provider.shader(renderable, context);
            shader.begin(context); shader.render(renderable); shader.end();
            assertEquals(0, pass.shadowParams[0]);
            assertEquals(0, pass.iblParams[0]);
            assertFalse(maps.isDisposed());
            assertTrue(new PbrShaderConfig().shadowsEnabled());
            assertTrue(new PbrShaderConfig().imageBasedLightingEnabled());
        } finally { provider.dispose(); maps.dispose(); renderable.meshPart().mesh().dispose(); }
    }

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

        for (int i = 0; i < ALLOCATION_OPERATIONS_PER_ATTEMPT; i++) {
            shader.begin(context);
            shader.render(renderable);
            shader.end();
        }

        var platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().threadId();
        bean.getThreadAllocatedBytes(threadId);
        long minimumAllocated = Long.MAX_VALUE;
        int initialDrawCalls = pass.drawCalls;
        for (int attempt = 0; attempt < ALLOCATION_MEASUREMENT_ATTEMPTS; attempt++) {
            long before = bean.getThreadAllocatedBytes(threadId);
            for (int i = 0; i < ALLOCATION_OPERATIONS_PER_ATTEMPT; i++) {
                shader.begin(context);
                shader.render(renderable);
                shader.end();
            }
            long allocated = bean.getThreadAllocatedBytes(threadId) - before;
            minimumAllocated = Math.min(minimumAllocated, allocated);
        }

        assertEquals(initialDrawCalls
                + ALLOCATION_MEASUREMENT_ATTEMPTS * ALLOCATION_OPERATIONS_PER_ATTEMPT, pass.drawCalls);
        assertTrue(minimumAllocated <= 1_024L,
                "Expected no post-warm-up CPU projection churn, minimum allocated " + minimumAllocated + " bytes");
        provider.dispose();
        renderable.meshPart().mesh().dispose();
    }

    @Test
    void graphPbrAllocatesNoPerDrawObjectsAfterWarmup() {
        FakeGraphicsContext graphics =
                new FakeGraphicsContext(ProviderId.of("gl"));
        Renderable3D renderable = renderable(graphics);
        Camera camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .viewport(64.0f, 64.0f)
                .nearFar(0.1f, 48.0f)
                .position(0.0f, 0.0f, 3.0f)
                .direction(0.0f, 0.0f, -1.0f);
        Environment3D environment = new Environment3D()
                .add(new DirectionalLight()
                        .direction(-0.5f, -1.0f, -0.25f));
        AllocationRenderPass pass =
                new AllocationRenderPass();
        RenderContext3D context = new RenderContext3D(
                graphics, camera, environment, null, pass);
        PbrShaderProvider provider = new PbrShaderProvider(
                graphics, new PbrShaderConfig());
        Shader3D shader = provider.shader(renderable, context);

        for (int i = 0; i < ALLOCATION_OPERATIONS_PER_ATTEMPT; i++) {
            shader.begin(context);
            shader.render(renderable);
            shader.end();
        }

        var platformBean =
                ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().threadId();
        bean.getThreadAllocatedBytes(threadId);
        long lifecycleAllocated = Long.MAX_VALUE;
        long lifecycleNanos = 0L;
        for (int attempt = 0; attempt < ALLOCATION_MEASUREMENT_ATTEMPTS; attempt++) {
            long lifecycleBefore = bean.getThreadAllocatedBytes(threadId);
            long lifecycleStart = System.nanoTime();
            for (int i = 0; i < ALLOCATION_OPERATIONS_PER_ATTEMPT; i++) {
                shader.begin(context);
                shader.end();
            }
            long attemptNanos = System.nanoTime() - lifecycleStart;
            long attemptAllocated = bean.getThreadAllocatedBytes(threadId) - lifecycleBefore;
            if (attemptAllocated < lifecycleAllocated) {
                lifecycleAllocated = attemptAllocated;
                lifecycleNanos = attemptNanos;
            }
        }
        int initialDrawCalls = pass.drawCalls;
        long allocated = Long.MAX_VALUE;
        long drawNanos = 0L;
        for (int attempt = 0; attempt < ALLOCATION_MEASUREMENT_ATTEMPTS; attempt++) {
            shader.begin(context);
            long before = bean.getThreadAllocatedBytes(threadId);
            long drawStart = System.nanoTime();
            for (int i = 0; i < ALLOCATION_OPERATIONS_PER_ATTEMPT; i++) {
                shader.render(renderable);
            }
            long attemptNanos = System.nanoTime() - drawStart;
            long attemptAllocated = bean.getThreadAllocatedBytes(threadId) - before;
            shader.end();
            if (attemptAllocated < allocated) {
                allocated = attemptAllocated;
                drawNanos = attemptNanos;
            }
        }

        assertEquals(initialDrawCalls
                        + ALLOCATION_MEASUREMENT_ATTEMPTS * ALLOCATION_OPERATIONS_PER_ATTEMPT,
                pass.drawCalls);
        assertTrue(allocated <= 4_096L,
                "Expected no post-warm-up graph PBR churn, minimum allocated "
                        + allocated + " render bytes and "
                        + lifecycleAllocated + " lifecycle bytes");
        assertTrue(lifecycleAllocated <= 4_096L,
                "Expected no post-warm-up graph PBR lifecycle churn, minimum allocated "
                        + lifecycleAllocated + " bytes");
        System.out.printf(Locale.ROOT,
                "SHADER_GRAPH_PERF pbr_draws=%d draw_ns_per_op=%.3f "
                        + "draw_bytes=%d lifecycle_ns_per_op=%.3f "
                        + "lifecycle_bytes=%d%n",
                ALLOCATION_OPERATIONS_PER_ATTEMPT,
                drawNanos / (double)ALLOCATION_OPERATIONS_PER_ATTEMPT, allocated,
                lifecycleNanos / (double)ALLOCATION_OPERATIONS_PER_ATTEMPT, lifecycleAllocated);
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
        assertTrue(graphics.device().graphPbrModuleCreated);

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
                new Material("cascade-material"), Matrix4.IDENTITY, mesh.bounds());
    }

    private static final class FakeGraphicsContext implements GraphicsContext {
        private final ProviderId providerId;
        private final FakeGraphicsDevice device = new FakeGraphicsDevice();
        private int passes, failPassAt;
        private FakeRenderPass lastPass;
        private final CommandEncoder encoder = new CommandEncoder() {
            @Override public RenderPass beginRenderPass(RenderPassDescriptor descriptor) {
                if (++passes == failPassAt) throw new FdxException("injected pass failure");
                return lastPass = new FakeRenderPass();
            }
            @Override public ProviderId providerId() { return providerId; }
            @Override public <T> T as() { return null; }
        };
        private final GraphicsFrame frame = new GraphicsFrame() {
            @Override public CommandEncoder commandEncoder() { return encoder; }
            @Override public FrameBuffer frameBuffer() { throw new UnsupportedOperationException(); }
            @Override public TextureView colorAttachment() { throw new UnsupportedOperationException(); }
            @Override public int width() { return 64; }
            @Override public int height() { return 64; }
            @Override public ProviderId providerId() { return providerId; }
            @Override public <T> T as() { return null; }
        };

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
            return frame;
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
        private boolean graphPbrModuleCreated;
        private final List<Texture> textures = new ArrayList<>();
        private int textureAttempts, failTextureAt;
        private boolean failShader;

        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            return new FakeBuffer(descriptor.size(), descriptor.usage());
        }

        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
        }

        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            if (++textureAttempts == failTextureAt) throw new FdxException("injected texture allocation failure");
            Texture texture = new FakeTexture(descriptor.width(), descriptor.height(), descriptor.format(), descriptor.usage());
            textures.add(texture);
            return texture;
        }

        @Override
        public void writeTexture(Texture texture, ByteBuffer data) {
        }

        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            if (failShader) throw new FdxException("injected shader allocation failure");
            if (descriptor.wgslSource().contains(
                    "fdx_graph_libfdx_standard_pbr_surface")) {
                graphPbrModuleCreated = true;
            }
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
        public RenderTargetLayout targetLayout() {
            return descriptor.renderTargetLayout();
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
        private final Texture[] textureSlots = new Texture[12];
        private final boolean[] shadowMatrices = new boolean[4];
        private float[] shadowParams;
        private float[] iblParams;
        private float[] shadowCascadeSplits;
        private float[] shadowBiases;
        private float[] shadowCameraPosition;
        private float[] shadowCameraDirection;
        private int drawCalls;

        private final RenderPassCompatibility compatibility = RenderPassCompatibility.of(
                    RenderTargetLayout.color(
                            TextureFormat.RGBA8_UNORM),
                    64, 64);

        @Override
        public RenderPassCompatibility compatibility() {
            return compatibility;
        }

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
        public void setTextureBinding(int group, int binding, Texture texture) {
            if (group == 0 && (binding & 1) == 0) {
                setTexture(binding / 2, texture);
            }
        }

        @Override
        public void setTextureSamplerBinding(int group, int binding, Texture texture) {
        }

        @Override
        public void setParameterBlock(int group, int binding, ShaderParameterBlock block) {
            ByteBuffer data = block.readOnlyData().order(ByteOrder.nativeOrder());
            shadowParams = readFloat4(block, data, "shadowParams");
            iblParams = readFloat4(block, data, "iblParams");
            shadowCascadeSplits = readFloat4(block, data, "shadowCascadeSplits");
            shadowBiases = readFloat4(block, data, "shadowBiases");
            shadowCameraPosition = readFloat4(block, data, "shadowCameraPosition");
            shadowCameraDirection = readFloat4(block, data, "shadowCameraDirection");
            for (int i = 0; i < shadowMatrices.length; i++) {
                shadowMatrices[i] = block.layout().findHandle("shadowViewProjection" + i) != null;
            }
        }

        @Override
        public void setUniform1i(String name, int value) {
        }

        @Override
        public void setUniform1i(ShaderParameterHandle parameter, int value) {
        }

        @Override
        public void setUniform1f(String name, float value) {
        }

        @Override
        public void setUniform1f(ShaderParameterHandle parameter, float value) {
        }

        @Override
        public void setUniform3f(String name, float x, float y, float z) {
        }

        @Override
        public void setUniform3f(ShaderParameterHandle parameter, float x, float y, float z) {
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
        public void setUniform4f(ShaderParameterHandle parameter, float x, float y, float z, float w) {
            if ("shadowParams".equals(parameter.path())) {
                shadowParams = new float[] { x, y, z, w };
            }
            else if ("shadowCascadeSplits".equals(parameter.path())) {
                shadowCascadeSplits = new float[] { x, y, z, w };
            }
            else if ("shadowBiases".equals(parameter.path())) {
                shadowBiases = new float[] { x, y, z, w };
            }
            else if ("shadowCameraPosition".equals(parameter.path())) {
                shadowCameraPosition = new float[] { x, y, z, w };
            }
            else if ("shadowCameraDirection".equals(parameter.path())) {
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
        public void setUniformMatrix4(ShaderParameterHandle parameter, float[] values) {
            for (int i = 0; i < shadowMatrices.length; i++) {
                if (("shadowViewProjection" + i).equals(parameter.path())) {
                    shadowMatrices[i] = true;
                    return;
                }
            }
        }

        private static float[] readFloat4(ShaderParameterBlock block, ByteBuffer data, String path) {
            ShaderParameterHandle handle = block.layout().findHandle(path);
            if (handle == null) return null;
            int offset = handle.byteOffsetInt();
            return new float[] {
                    data.getFloat(offset),
                    data.getFloat(offset + Float.BYTES),
                    data.getFloat(offset + 2 * Float.BYTES),
                    data.getFloat(offset + 3 * Float.BYTES)
            };
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

    private static final class AllocationRenderPass
            implements RenderPass {
        private static final ProviderId PROVIDER_ID =
                ProviderId.of("allocation-pass");
        private static final RenderPassCompatibility COMPATIBILITY =
                RenderPassCompatibility.of(
                        RenderTargetLayout.color(
                                TextureFormat.RGBA8_UNORM),
                        64, 64);
        private int drawCalls;

        @Override
        public RenderPassCompatibility compatibility() {
            return COMPATIBILITY;
        }

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
        public void setTextureBinding(int group, int binding,
                Texture texture) {
        }

        @Override
        public void setTextureSamplerBinding(int group,
                int binding, Texture texture) {
        }

        @Override
        public void draw(int vertexCount, int instanceCount,
                int firstVertex, int firstInstance) {
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
        public <T> T as() {
            return null;
        }
    }
}
