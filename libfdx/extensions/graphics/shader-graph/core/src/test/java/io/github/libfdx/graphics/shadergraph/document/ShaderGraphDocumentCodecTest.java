package io.github.libfdx.graphics.shadergraph.document;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCacheKey;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledArtifact;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCache;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCacheEntry;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledInterface;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphCodec;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphDocumentCodecTest {
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    @Test
    void roundTripsEverySemanticKindThroughOneDocumentFormat() {
        ShaderGraph graph = valueGraph("function", ShaderGraphKind.FUNCTION);
        ShaderGraphProgram program = program("program");
        ShaderGraphComputeProgram computeProgram =
                ShaderGraphComputeProgram.builder("compute_program",
                        valueGraph("compute", ShaderGraphKind.COMPUTE))
                        .workgroupSize(4, 2, 1)
                        .build();
        ShaderGraphTechnique technique = ShaderGraphTechnique.builder(
                        "technique")
                .passes(ShaderGraphTechniquePass.builder(
                                ShaderPassId.FORWARD, pipelineState())
                        .variants(ShaderGraphVariant.builder(
                                "", program).build())
                        .build())
                .build();
        ShaderGraphComputeTechnique computeTechnique =
                ShaderGraphComputeTechnique.builder("compute_technique")
                        .passes(ShaderGraphComputeTechniquePass.builder(
                                        ShaderPassId.of("update"))
                                .variants(ShaderGraphComputeVariant.builder(
                                        "", computeProgram).build())
                                .build())
                        .build();

        List<ShaderGraphDocument> documents = List.of(
                ShaderGraphDocument.of(graph),
                ShaderGraphDocument.of(program),
                ShaderGraphDocument.of(computeProgram),
                ShaderGraphDocument.of(technique),
                ShaderGraphDocument.of(computeTechnique));

        for (ShaderGraphDocument document : documents) {
            String source = ShaderGraphDocumentCodec.write(document);
            ShaderGraphDocument decoded =
                    ShaderGraphDocumentCodec.read(source);

            assertEquals(document, decoded, document.kind().name());
            assertEquals(document.semanticHash(),
                    decoded.semanticHash(), document.kind().name());
            assertEquals(source, ShaderGraphDocumentCodec.write(decoded),
                    document.kind().name());
            assertTrue(source.startsWith("{\"format\":2,\"semantic\":{"));
        }
    }

    @Test
    void editorAndCompiledBlocksDoNotAffectSemanticIdentity() {
        ShaderGraphDocument semantic =
                ShaderGraphDocument.of(valueGraph(
                        "identity", ShaderGraphKind.SURFACE));
        ShaderGraphDocument decorated = semantic
                .withEditorJson("{\"zoom\":1,\"nodes\":{\"b\":2,\"a\":1}}")
                .withCompiledCache(cache(semantic));
        ShaderGraphDocument moved = semantic
                .withEditorJson("{\"zoom\":2,\"nodes\":{\"a\":7}}");

        assertEquals(semantic.semanticHash(), decorated.semanticHash());
        assertEquals(semantic.semanticHash(), moved.semanticHash());
        assertEquals(semantic.semanticSource(), decorated.semanticSource());
        assertNotEquals(ShaderGraphDocumentCodec.write(semantic),
                ShaderGraphDocumentCodec.write(decorated));

        ShaderGraphDocument decoded = ShaderGraphDocumentCodec.read(
                ShaderGraphDocumentCodec.write(decorated));
        assertTrue(decoded.hasEditor());
        assertTrue(decoded.hasCompiled());
        assertEquals("{\"nodes\":{\"a\":1,\"b\":2},\"zoom\":1}",
                decoded.editorJson());
        assertEquals(decorated.compiledCache(),
                decoded.compiledCache());

        ShaderGraphDocument stripped = decoded
                .withoutEditor().withoutCompiled();
        assertFalse(stripped.hasEditor());
        assertFalse(stripped.hasCompiled());
        assertEquals(semantic, stripped);
    }

    @Test
    void requiresTheDocumentEnvelopeAndSemanticValue() {
        ShaderGraph graph = valueGraph("strict", ShaderGraphKind.FUNCTION);

        assertThrows(FdxException.class, () ->
                ShaderGraphDocumentCodec.read(
                        ShaderGraphCodec.write(graph)));
        assertThrows(FdxException.class, () ->
                ShaderGraphDocumentCodec.read(
                        "{\"format\":2,\"semantic\":null}"));
        assertThrows(FdxException.class, () ->
                ShaderGraphDocumentCodec.read(
                        "{\"format\":1,\"semantic\":{}}"));
    }

    @Test
    void opaqueOptionalDataCannotBlockSemanticLoading() {
        ShaderGraphDocument document = ShaderGraphDocument.of(
                valueGraph("optional", ShaderGraphKind.FUNCTION));
        String source = ShaderGraphDocumentCodec.write(document);
        String withUnknownOptionalValues = source.substring(
                0, source.length() - 1)
                + ",\"editor\":42,\"compiled\":\"broken\"}";

        ShaderGraphDocumentReadResult result =
                ShaderGraphDocumentCodec.readResult(
                        withUnknownOptionalValues);
        ShaderGraphDocument decoded = result.document();

        assertEquals(document.semanticHash(), decoded.semanticHash());
        assertEquals("42", decoded.editorJson());
        assertFalse(decoded.hasCompiled());
        assertTrue(result.rejectedCompiledEntries());
    }

    private static ShaderGraph valueGraph(String id,
            ShaderGraphKind kind) {
        ShaderGraphBuilder builder = new ShaderGraphBuilder(id, kind);
        builder.output("value", builder.constant(
                "constant", ShaderGraphLiteral.f32(1)));
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

    private static ShaderGraphPipelineState pipelineState() {
        return ShaderGraphPipelineState.builder()
                .colorTargets(ColorTargetState.opaque(
                        TextureFormat.RGBA8_UNORM))
                .build();
    }

    private static ShaderGraphCompiledCache cache(
            ShaderGraphDocument document) {
        ShaderGraphCompiledInterface shaderInterface =
                ShaderGraphCompiledInterface.empty(
                        "fdx-graph-interface-v1");
        ShaderGraphCacheKey key = ShaderGraphCacheKey.builder(
                        document.semanticHash())
                .dependencyHash(PortableSha256.hashUtf8(""))
                .compiler("libfdx-shader-graph", "1")
                .libraries("1", "1")
                .profile("fdx-wgsl-webgpu",
                        PortableSha256.hashUtf8("portable"))
                .target("wgpu-wgsl", "wgsl", "wgpu")
                .verifier("", "")
                .optionsHash(PortableSha256.hashUtf8("default"))
                .interfaceAbiVersion(shaderInterface.abiVersion())
                .compilationUnit("graph-library")
                .pass("")
                .variant("")
                .entryPointsHash(
                        shaderInterface.entryPointsHash())
                .build();
        return ShaderGraphCompiledCache.of(
                ShaderGraphCompiledCacheEntry.of(key,
                        ShaderGraphCompiledArtifact.text(
                                "wgsl", "fn cached() {}"),
                        shaderInterface));
    }
}
