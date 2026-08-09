package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderBuiltinUsage;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolation;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolationSampling;
import io.github.libfdx.graphics.shader.ShaderLanguage;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVariable;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCacheContext;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphProviderMigrationTest {
    @Test
    void renderProgramsRetainCompatibleBlendDefaultAndAllowOpaqueOutput() {
        ShaderModuleDescriptor shader = ShaderModuleDescriptor.wgsl(
                "blend-state", "@vertex fn vertexMain() {}")
                .entryPoints("vertexMain", "fragmentMain");

        assertTrue(ShaderGraphRenderProgram.builder(
                ShaderPassId.FORWARD, shader).build().alphaBlend());
        assertFalse(ShaderGraphRenderProgram.builder(
                ShaderPassId.FORWARD, shader)
                .alphaBlend(false)
                .build()
                .alphaBlend());
    }

    @Test
    void handwrittenAndGraphGeneratedWgslShareOneTechnique()
            throws Exception {
        String handwritten = """
                @vertex
                fn vertexMain() -> @builtin(position) vec4f {
                    return vec4f(0.0, 0.0, 0.0, 1.0);
                }
                @fragment
                fn fragmentMain() -> @location(0) vec4f {
                    return vec4f(1.0, 0.0, 0.0, 1.0);
                }
                """;
        ShaderGraphRenderProgram handwrittenProgram =
                ShaderGraphRenderProgram.builder(ShaderPassId.FORWARD,
                                ShaderModuleDescriptor
                                        .wgsl("handwritten",
                                                handwritten)
                                        .entryPoints("vertexMain",
                                                "fragmentMain")
                                        .reflection(reflection(
                                                "vertexMain",
                                                "fragmentMain")))
                        .build();

        ShaderGraphType vec4 = ShaderGraphType.vector(
                ShaderScalarType.F32, 4);
        ShaderGraphBuilder surface = new ShaderGraphBuilder(
                "migration_surface", ShaderGraphKind.SURFACE);
        surface.output("color", "baseColor",
                surface.constant("green",
                        ShaderGraphLiteral.composite(vec4,
                                ShaderGraphLiteral.f32(0),
                                ShaderGraphLiteral.f32(1),
                                ShaderGraphLiteral.f32(0),
                                ShaderGraphLiteral.f32(1))));
        var generated = new ShaderGraphCompiler().compile(
                surface.build(),
                ShaderGraphCompileOptions.builder().build());
        assertTrue(generated.success());
        ShaderGraphRenderProgram graphProgram =
                ShaderGraphRenderProgram.builder(ShaderPassId.FORWARD,
                                ShaderModuleDescriptor
                                        .wgsl("graph-generated",
                                                generated.wgsl())
                                        .entryPoints(
                                                "fdx_graph_vertex",
                                                "fdx_graph_fragment")
                                        .reflection(reflection(
                                                "fdx_graph_vertex",
                                                "fdx_graph_fragment")))
                        .build();

        ShaderGraphRenderTechnique technique =
                ShaderGraphRenderTechnique.of("migration",
                        ShaderGraphRenderTechniquePass
                                .builder(ShaderPassId.FORWARD)
                                .variants(
                                        ShaderGraphRenderVariant
                                                .builder("",
                                                        handwrittenProgram)
                                                .build(),
                                        ShaderGraphRenderVariant
                                                .builder("graph",
                                                        graphProgram)
                                                .build())
                                .build());
        FakeGraphicsContext graphics =
                new FakeGraphicsContext();
        ShaderGraphProvider provider =
                new ShaderGraphProvider(graphics, technique, 4);
        ShaderRequest handwrittenRequest = request("");
        ShaderRequest graphRequest = request("graph");

        assertTrue(provider.supports(handwrittenRequest));
        assertTrue(provider.supports(graphRequest));
        var handwrittenResolved =
                provider.resolve(handwrittenRequest);
        var graphResolved = provider.resolve(graphRequest);
        assertNotSame(handwrittenResolved.pipeline(),
                graphResolved.pipeline());
        assertEquals(2, provider.cachedPipelineCount());
        assertTrue(graphics.device.sources.stream()
                .anyMatch(handwritten::equals));
        assertTrue(graphics.device.sources.stream()
                .anyMatch(source -> source.contains(
                        "fdx_graph_migration_surface")));

        provider.dispose();
        assertEquals(2, graphics.device.disposedModules);
        assertEquals(2, graphics.device.disposedPipelines);
    }

    @Test
    void renderPipelineCacheIsBoundedAndDisposesEvictions() {
        String source = """
                @vertex
                fn vertexMain() -> @builtin(position) vec4f {
                    return vec4f(0.0, 0.0, 0.0, 1.0);
                }
                @fragment
                fn fragmentMain() -> @location(0) vec4f {
                    return vec4f(1.0);
                }
                """;
        ShaderGraphRenderProgram program =
                ShaderGraphRenderProgram.builder(
                                ShaderPassId.FORWARD,
                                ShaderModuleDescriptor
                                        .wgsl("bounded-cache", source)
                                        .entryPoints("vertexMain",
                                                "fragmentMain")
                                        .reflection(reflection(
                                                "vertexMain",
                                                "fragmentMain")))
                        .build();
        ShaderGraphRenderTechnique technique =
                ShaderGraphRenderTechnique.of("bounded-cache",
                        ShaderGraphRenderTechniquePass
                                .builder(ShaderPassId.FORWARD)
                                .variants(
                                        ShaderGraphRenderVariant
                                                .builder("", program)
                                                .build())
                                .build());
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        ShaderGraphProvider provider =
                new ShaderGraphProvider(graphics, technique, 2);

        var triangles = provider.resolve(request("",
                PrimitiveTopology.TRIANGLE_LIST));
        var lines = provider.resolve(request("",
                PrimitiveTopology.LINE_LIST));
        provider.resolve(request("",
                PrimitiveTopology.TRIANGLE_STRIP));

        assertEquals(2, provider.cachedPipelineCount());
        assertEquals(1, graphics.device.disposedPipelines);
        assertSame(lines.pipeline(),
                provider.resolve(request("",
                        PrimitiveTopology.LINE_LIST)).pipeline());
        assertNotSame(triangles.pipeline(),
                provider.resolve(request("",
                        PrimitiveTopology.TRIANGLE_LIST)).pipeline());
        assertEquals(2, provider.cachedPipelineCount());
        assertEquals(2, graphics.device.disposedPipelines);

        provider.dispose();
        assertEquals(1, graphics.device.disposedModules);
        assertEquals(4, graphics.device.disposedPipelines);
    }

    @Test
    void failedDocumentReloadKeepsLastGoodRuntimeTechnique() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        ShaderGraphRuntimeLoader loader =
                new ShaderGraphRuntimeLoader();
        ShaderGraphCacheContext context =
                ShaderGraphCacheContext.wgpu(
                        ShaderGraphCompileOptions.builder()
                                .profile(
                                        ShaderProfile.PORTABLE_WEBGPU)
                                .capabilities(
                                        graphics.device()
                                                .capabilities())
                                .build());
        ShaderGraphRuntimeAsset initial = loader.load(
                ShaderGraphDocument.of(program(
                        "reload", 0.25f)), context);
        ShaderGraphProvider provider =
                new ShaderGraphProvider(graphics, initial);
        long revision = provider.revision();
        int modules = graphics.device.sources.size();

        ShaderGraph invalidGraph = stageGraph(
                "invalid_vertex", ShaderGraphKind.VERTEX,
                0.5f);
        ShaderGraph invalidVersion = ShaderGraph.builder(
                        invalidGraph.id().value(),
                        invalidGraph.kind())
                .formatVersion(99)
                .parameters(invalidGraph.parameters())
                .resources(invalidGraph.resources())
                .nodes(invalidGraph.nodes())
                .edges(invalidGraph.edges())
                .outputs(invalidGraph.outputs())
                .dependencies(invalidGraph.dependencies())
                .build();
        assertThrows(FdxException.class, () -> loader.load(
                ShaderGraphDocument.of(
                        ShaderGraphProgram.builder(
                                        "invalid", invalidVersion,
                                        stageGraph(
                                                "invalid_fragment",
                                                ShaderGraphKind.FRAGMENT,
                                                0.5f))
                                .build()),
                context));
        assertEquals(revision, provider.revision());
        assertEquals(modules, graphics.device.sources.size());

        ShaderGraphRuntimeAsset replacement = loader.load(
                ShaderGraphDocument.of(program(
                        "reload", 0.75f)), context);
        provider.replace(replacement);
        assertEquals(revision + 1, provider.revision());
        assertEquals(modules + 1, graphics.device.sources.size());
        assertEquals(1, graphics.device.disposedModules);
        provider.dispose();
    }

    private static ShaderGraphProgram program(
            String id, float color) {
        return ShaderGraphProgram.builder(id,
                stageGraph(id + "_vertex",
                        ShaderGraphKind.VERTEX, color),
                stageGraph(id + "_fragment",
                        ShaderGraphKind.FRAGMENT, color))
                .build();
    }

    private static ShaderGraph stageGraph(String id,
            ShaderGraphKind kind, float value) {
        ShaderGraphType vec4 = ShaderGraphType.vector(
                ShaderScalarType.F32, 4);
        ShaderGraphBuilder graph =
                new ShaderGraphBuilder(id, kind);
        ShaderExpression scalar = graph.constant(
                "scalar", ShaderGraphLiteral.f32(value));
        ShaderExpression vector = graph.construct(
                "vector", vec4,
                scalar, scalar, scalar,
                graph.constant("one",
                        ShaderGraphLiteral.f32(1)));
        graph.output(kind == ShaderGraphKind.VERTEX
                        ? "position" : "color",
                kind == ShaderGraphKind.VERTEX
                        ? ShaderGraphStageSemantic.POSITION
                        : ShaderGraphStageSemantic.location(0),
                vector);
        return graph.build();
    }

    private static ShaderRequest request(String variant) {
        return request(variant, PrimitiveTopology.TRIANGLE_LIST);
    }

    private static ShaderRequest request(String variant,
            PrimitiveTopology topology) {
        return ShaderRequest.builder(ShaderPassId.FORWARD)
                .profile(ShaderProfile.PORTABLE_WEBGPU)
                .renderPass(RenderPassCompatibility.layout(
                        RenderTargetLayout.color(
                                TextureFormat.RGBA8_UNORM)))
                .topology(topology)
                .variantKey(variant)
                .build();
    }

    private static ShaderReflection reflection(String vertex,
            String fragment) {
        ShaderStageVariable color = ShaderStageVariable.of(
                "color", "", 0, -1, -1,
                ShaderValueType.vector(ShaderScalarType.F32, 4),
                ShaderInterpolation.PERSPECTIVE,
                ShaderInterpolationSampling.CENTER);
        return ShaderReflection.complete(
                ShaderProfile.PORTABLE_WEBGPU,
                new ShaderEntryPoint[] {
                        ShaderEntryPoint.builder(vertex,
                                        ShaderStage.VERTEX)
                                .builtins(
                                        ShaderBuiltinUsage.POSITION,
                                        -1)
                                .build(),
                        ShaderEntryPoint.builder(fragment,
                                        ShaderStage.FRAGMENT)
                                .outputs(color)
                                .build()
                },
                new io.github.libfdx.graphics.shader.reflection.ShaderBinding[0],
                new String[0]);
    }

    private static final class FakeGraphicsContext
            implements GraphicsContext {
        private final FakeGraphicsDevice device =
                new FakeGraphicsDevice();

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
            return null;
        }

        @Override
        public void clear(float red, float green, float blue,
                float alpha) {
        }

        @Override
        public ProviderId providerId() {
            return ProviderId.of("wgpu");
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeGraphicsDevice
            implements GraphicsDevice {
        private final GraphicsCapabilities capabilities =
                GraphicsCapabilities.builder()
                        .profile(ShaderProfile.PORTABLE_WEBGPU)
                        .colorFormats(TextureFormat.RGBA8_UNORM)
                        .sampleCounts(1)
                        .build();
        private final List<String> sources =
                new ArrayList<>();
        private int disposedModules;
        private int disposedPipelines;

        @Override
        public GraphicsCapabilities capabilities() {
            return capabilities;
        }

        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Texture createTexture(
                TextureDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTexture(Texture texture,
                ByteBuffer data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShaderModule createShaderModule(
                ShaderModuleDescriptor descriptor) {
            sources.add(descriptor.wgslSource());
            return new ShaderModule() {
                private boolean disposed;

                @Override
                public ShaderLanguage language() {
                    return descriptor.language();
                }

                @Override
                public ShaderReflection reflection() {
                    if (descriptor.reflection().complete()) {
                        return ShaderReflection.empty();
                    }
                    return ShaderGraphProviderMigrationTest.reflection(
                            descriptor.vertexEntryPoint(),
                            descriptor.fragmentEntryPoint());
                }

                @Override
                public void dispose() {
                    if (!disposed) {
                        disposed = true;
                        disposedModules++;
                    }
                }

                @Override
                public boolean isDisposed() {
                    return disposed;
                }

                @Override
                public ProviderId providerId() {
                    return ProviderId.of("wgpu");
                }

                @Override
                public <T> T as() {
                    return null;
                }
            };
        }

        @Override
        public RenderPipeline createRenderPipeline(
                RenderPipelineDescriptor descriptor) {
            return new RenderPipeline() {
                private boolean disposed;

                @Override
                public RenderTargetLayout targetLayout() {
                    return descriptor.renderTargetLayout();
                }

                @Override
                public void dispose() {
                    if (!disposed) {
                        disposed = true;
                        disposedPipelines++;
                    }
                }

                @Override
                public boolean isDisposed() {
                    return disposed;
                }

                @Override
                public ProviderId providerId() {
                    return ProviderId.of("wgpu");
                }

                @Override
                public <T> T as() {
                    return null;
                }
            };
        }

        @Override
        public ProviderId providerId() {
            return ProviderId.of("wgpu");
        }

        @Override
        public <T> T as() {
            return null;
        }
    }
}
