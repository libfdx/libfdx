package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCacheContext;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentCodec;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechnique;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeVariant;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphPipelineState;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphRuntimeLoaderTest {
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);
    private static final ShaderGraphCacheContext CONTEXT =
            ShaderGraphCacheContext.wgpu(
                    ShaderGraphCompileOptions.builder()
                            .profile(ShaderProfile.PORTABLE_WEBGPU)
                            .build());

    @Test
    void cacheHitAndMissProduceEquivalentRuntimeAssetsForEveryKind() {
        ShaderGraphProgram program = program("program");
        ShaderGraphComputeProgram compute =
                ShaderGraphComputeProgram.builder(
                                "compute_program",
                                valueGraph("compute_graph",
                                        ShaderGraphKind.COMPUTE, 2))
                        .workgroupSize(4, 2, 1)
                        .build();
        List<ShaderGraphDocument> documents = List.of(
                ShaderGraphDocument.of(valueGraph(
                        "surface", ShaderGraphKind.SURFACE, 1)),
                ShaderGraphDocument.of(program),
                ShaderGraphDocument.of(renderTechnique(
                        "render_technique", program)),
                ShaderGraphDocument.of(compute),
                ShaderGraphDocument.of(computeTechnique(
                        "compute_technique", compute)));
        ShaderGraphRuntimeLoader loader =
                new ShaderGraphRuntimeLoader();

        for (ShaderGraphDocument document : documents) {
            ShaderGraphRuntimeAsset miss = loader.load(
                    ShaderGraphDocumentCodec.write(document),
                    CONTEXT);
            assertTrue(miss.cacheMiss(), document.kind().name());
            ShaderGraphDocument embedded =
                    miss.documentWithCompiledCache();
            ShaderGraphRuntimeAsset hit = loader.load(
                    ShaderGraphDocumentCodec.write(embedded),
                    CONTEXT);

            assertTrue(hit.cacheHit(), document.kind().name());
            assertEquals(embedded.compiledCache(),
                    hit.documentWithCompiledCache().compiledCache(),
                    document.kind().name());
            assertEquals(signature(miss), signature(hit),
                    document.kind().name());
            assertCompiledProfile(miss, ShaderProfile.PORTABLE_WEBGPU);
            assertCompiledProfile(hit, ShaderProfile.PORTABLE_WEBGPU);
        }
    }

    @Test
    void aDifferentProfileCannotReuseOrExposeTheCachedProfile() {
        ShaderGraphRuntimeLoader loader =
                new ShaderGraphRuntimeLoader();
        ShaderGraphRuntimeAsset webGpu = loader.load(
                ShaderGraphDocument.of(program("profile_program")),
                CONTEXT);
        ShaderGraphCacheContext nativeContext =
                ShaderGraphCacheContext.wgpu(
                        ShaderGraphCompileOptions.builder()
                                .profile(ShaderProfile.NATIVE)
                                .build());

        ShaderGraphRuntimeAsset nativeAsset = loader.load(
                webGpu.documentWithCompiledCache(), nativeContext);

        assertTrue(nativeAsset.cacheMiss());
        assertCompiledProfile(
                nativeAsset, ShaderProfile.NATIVE);
    }

    @Test
    void staleCompiledEntriesAreReportedThenCompiledAsAMiss() {
        ShaderGraphRuntimeLoader loader =
                new ShaderGraphRuntimeLoader();
        ShaderGraphRuntimeAsset original = loader.load(
                ShaderGraphDocument.of(valueGraph(
                        "editable", ShaderGraphKind.SURFACE, 1)),
                CONTEXT);
        ShaderGraphDocument edited = ShaderGraphDocument.of(
                        valueGraph("editable",
                                ShaderGraphKind.SURFACE, 2))
                .withCompiledCache(
                        original.documentWithCompiledCache()
                                .compiledCache());

        ShaderGraphRuntimeAsset loaded = loader.load(
                ShaderGraphDocumentCodec.write(edited), CONTEXT);

        assertTrue(loaded.cacheMiss());
        assertTrue(loaded.cacheRejections().length > 0);
        assertEquals(edited.semanticHash(),
                loaded.document().semanticHash());
        assertNotNull(loaded.graph());
    }

    @Test
    void invalidDocumentOrSemanticCompilationIsARealError() {
        ShaderGraphRuntimeLoader loader =
                new ShaderGraphRuntimeLoader();
        assertThrows(FdxException.class,
                () -> loader.load("{}", CONTEXT));

        ShaderGraph valid = valueGraph(
                "future", ShaderGraphKind.SURFACE, 1);
        ShaderGraph invalid = ShaderGraph.builder(
                        valid.id().value(), valid.kind())
                .formatVersion(99)
                .parameters(valid.parameters())
                .resources(valid.resources())
                .nodes(valid.nodes())
                .edges(valid.edges())
                .outputs(valid.outputs())
                .dependencies(valid.dependencies())
                .build();
        assertThrows(FdxException.class, () -> loader.load(
                ShaderGraphDocument.of(invalid), CONTEXT));
    }

    private static String signature(ShaderGraphRuntimeAsset asset) {
        if (asset.graph() != null) {
            return "graph\n" + asset.graph().wgsl()
                    + "\n" + asset.graph().libraryWgsl();
        }
        if (asset.renderTechnique() != null) {
            StringBuilder value = new StringBuilder("render\n");
            for (ShaderGraphRenderTechniquePass pass
                    : asset.renderTechnique().passes()) {
                value.append(pass.passId()).append(':')
                        .append(pass.defaultVariantKey()).append('\n');
                for (ShaderGraphRenderVariant variant
                        : pass.variants()) {
                    value.append(variant.key()).append(':')
                            .append(variant.program()
                                    .shader().wgslSource())
                            .append('\n');
                }
            }
            return value.toString();
        }
        StringBuilder value = new StringBuilder("compute\n");
        for (ShaderGraphComputeRuntimeTechnique.Pass pass
                : asset.computeTechnique().passes()) {
            value.append(pass.passId()).append(':')
                    .append(pass.defaultVariantKey()).append('\n');
            for (ShaderGraphComputeRuntimeTechnique.Variant variant
                    : pass.variants()) {
                value.append(variant.key()).append(':')
                        .append(variant.entryPoint()).append(':')
                        .append(variant.shader().wgslSource())
                        .append('\n');
            }
        }
        return value.toString();
    }

    private static void assertCompiledProfile(
            ShaderGraphRuntimeAsset asset, ShaderProfile profile) {
        if (asset.renderTechnique() != null) {
            for (ShaderGraphRenderTechniquePass pass
                    : asset.renderTechnique().passes()) {
                for (ShaderGraphRenderVariant variant
                        : pass.variants()) {
                    assertEquals(profile,
                            variant.compiledProfile());
                }
            }
        }
        if (asset.computeTechnique() != null) {
            for (ShaderGraphComputeRuntimeTechnique.Pass pass
                    : asset.computeTechnique().passes()) {
                for (ShaderGraphComputeRuntimeTechnique.Variant variant
                        : pass.variants()) {
                    assertEquals(profile,
                            variant.compiledProfile());
                }
            }
        }
    }

    private static ShaderGraph valueGraph(String id,
            ShaderGraphKind kind, float value) {
        ShaderGraphBuilder builder = new ShaderGraphBuilder(id, kind);
        builder.output("value", builder.constant(
                "constant", ShaderGraphLiteral.f32(value)));
        return builder.build();
    }

    private static ShaderGraphProgram program(String id) {
        ShaderGraphBuilder vertex =
                new ShaderGraphBuilder(id + "_vertex",
                        ShaderGraphKind.VERTEX);
        ShaderExpression zero = vertex.constant(
                "zero", ShaderGraphLiteral.f32(0));
        ShaderExpression one = vertex.constant(
                "one", ShaderGraphLiteral.f32(1));
        vertex.output("position", ShaderGraphStageSemantic.POSITION,
                vertex.construct("position_value", VEC4,
                        zero, zero, zero, one));

        ShaderGraphBuilder fragment =
                new ShaderGraphBuilder(id + "_fragment",
                        ShaderGraphKind.FRAGMENT);
        ShaderExpression channel = fragment.constant(
                "channel", ShaderGraphLiteral.f32(1));
        fragment.output("color",
                ShaderGraphStageSemantic.location(0),
                fragment.construct("color_value", VEC4,
                        channel, channel, channel, channel));
        return ShaderGraphProgram.builder(id,
                vertex.build(), fragment.build()).build();
    }

    private static ShaderGraphTechnique renderTechnique(
            String id, ShaderGraphProgram program) {
        return ShaderGraphTechnique.builder(id)
                .passes(ShaderGraphTechniquePass.builder(
                                ShaderPassId.FORWARD,
                                ShaderGraphPipelineState.builder()
                                        .colorTargets(
                                                ColorTargetState.opaque(
                                                        TextureFormat
                                                                .RGBA8_UNORM))
                                        .build())
                        .variants(ShaderGraphVariant.builder(
                                "", program).build())
                        .build())
                .build();
    }

    private static ShaderGraphComputeTechnique computeTechnique(
            String id, ShaderGraphComputeProgram program) {
        return ShaderGraphComputeTechnique.builder(id)
                .passes(ShaderGraphComputeTechniquePass.builder(
                                ShaderPassId.of("update"))
                        .variants(ShaderGraphComputeVariant.builder(
                                "", program).build())
                        .build())
                .build();
    }
}
