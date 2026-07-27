package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompiler;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.TextureFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphTechniqueTest {
    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphType BOOL =
            ShaderGraphType.scalar(ShaderScalarType.BOOL);
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    @Test
    void expandsSpecializesDeduplicatesAndOrdersVariantsAndPasses() {
        ShaderGraphProgram program = program("variant_program");
        ShaderGraphVariant[] variants =
                ShaderGraphVariantExpansion.booleans(program, 4,
                        "highlight");
        ShaderGraphTechniquePass forward = pass(ShaderPassId.FORWARD,
                variants);
        ShaderGraphTechniquePass shadow = pass(ShaderPassId.SHADOW,
                ShaderGraphVariant.builder("", program).build());
        ShaderGraphTechnique technique = ShaderGraphTechnique.builder(
                        "variants")
                .passes(shadow, forward)
                .maxVariants(4)
                .build();

        ShaderGraphTechniqueCompileResult result =
                new ShaderGraphTechniqueCompiler().compile(technique, null);

        assertTrue(result.success());
        assertEquals(3, technique.variantCount());
        assertEquals(ShaderPassId.FORWARD,
                technique.passes()[0].passId());
        ShaderGraphCompiledVariant normal =
                result.pass(ShaderPassId.FORWARD).variant("");
        ShaderGraphCompiledVariant highlighted =
                result.pass(ShaderPassId.FORWARD).variant("v-1");
        assertNotEquals(normal.compilation().wgsl(),
                highlighted.compilation().wgsl());
        assertSame(normal.compilation(),
                result.pass(ShaderPassId.SHADOW)
                        .variant("").compilation());
        assertEquals("", ShaderGraphVariantExpansion.key(false));
        assertEquals("v-1", ShaderGraphVariantExpansion.key(true));

        ShaderGraphTechnique reordered = ShaderGraphTechnique.builder(
                        "variants")
                .passes(forward, shadow)
                .maxVariants(4)
                .build();
        assertEquals(technique.semanticHash(),
                reordered.semanticHash());
    }

    @Test
    void boundsVariantsAndRejectsMissingOrCyclicFallbacks() {
        ShaderGraphProgram program = program("bounded");
        assertThrows(FdxException.class,
                () -> ShaderGraphVariantExpansion.booleans(program,
                        1, "highlight"));

        ShaderGraphVariant missing = ShaderGraphVariant.builder(
                        "", program)
                .fallback("missing")
                .build();
        assertThrows(FdxException.class,
                () -> pass(ShaderPassId.FORWARD, missing));

        ShaderGraphVariant first = ShaderGraphVariant.builder(
                        "", program)
                .fallback("second").build();
        ShaderGraphVariant second = ShaderGraphVariant.builder(
                        "second", program)
                .fallback("").build();
        assertThrows(FdxException.class,
                () -> pass(ShaderPassId.FORWARD, first, second));
    }

    @Test
    void capabilityFallbacksAndCompleteStateAreExplicit() {
        ShaderGraphProgram program = program("fallback");
        ShaderGraphVariant normal = ShaderGraphVariant.builder(
                "", program).build();
        ShaderGraphVariant nativeOnly = ShaderGraphVariant.builder(
                        "native", program)
                .profiles(ShaderProfile.NATIVE)
                .fallback("")
                .build();
        ShaderGraphTechniquePass pass = pass(
                ShaderPassId.FORWARD, nativeOnly, normal);
        GraphicsCapabilities webGpu = GraphicsCapabilities.builder()
                .profile(ShaderProfile.PORTABLE_WEBGPU)
                .colorFormats(TextureFormat.RGBA8_UNORM)
                .sampleCounts(1)
                .limits(GraphicsLimits.builder()
                        .maxBindGroups(4)
                        .maxBindingsPerGroup(8)
                        .maxVertexBuffers(4)
                        .maxVertexAttributes(8)
                        .maxColorAttachments(1)
                        .maxUniformBufferBindingSize(65536)
                        .build())
                .build();

        assertTrue(normal.supports(ShaderProfile.PORTABLE_WEBGPU,
                webGpu));
        assertTrue(!nativeOnly.supports(
                ShaderProfile.PORTABLE_WEBGPU, webGpu));
        pass.pipelineState().validate(
                pass.pipelineState().targetLayout(),
                new io.github.libfdx.graphics.VertexLayout[0],
                webGpu);
        assertThrows(FdxException.class, () ->
                pass.pipelineState().validate(
                        io.github.libfdx.graphics.RenderTargetLayout.color(
                                TextureFormat.BGRA8_UNORM),
                        new io.github.libfdx.graphics.VertexLayout[0],
                        webGpu));
    }

    @Test
    void roundTripsProgramsPipelineStateAndTechniques() {
        ShaderGraphProgram program = program("codec_program");
        ShaderGraphVariant normal = ShaderGraphVariant.builder(
                "", program).build();
        ShaderGraphTechnique technique = ShaderGraphTechnique.builder(
                        "codec_technique")
                .passes(pass(ShaderPassId.FORWARD, normal))
                .build();

        ShaderGraphProgram decodedProgram =
                ShaderGraphProgramCodec.read(
                        ShaderGraphProgramCodec.write(program));
        ShaderGraphTechnique decodedTechnique =
                ShaderGraphTechniqueCodec.read(
                        ShaderGraphTechniqueCodec.write(
                                technique));

        assertEquals(program.semanticHash(),
                decodedProgram.semanticHash());
        assertEquals(technique.semanticHash(),
                decodedTechnique.semanticHash());
        assertEquals(
                technique.passes()[0].pipelineState(),
                ShaderGraphPipelineStateCodec.read(
                        ShaderGraphPipelineStateCodec.write(
                                technique.passes()[0]
                                        .pipelineState())));
    }

    private static ShaderGraphTechniquePass pass(ShaderPassId id,
            ShaderGraphVariant... variants) {
        return ShaderGraphTechniquePass.builder(id,
                        ShaderGraphPipelineState.builder()
                                .colorTargets(ColorTargetState.opaque(
                                        TextureFormat.RGBA8_UNORM))
                                .build())
                .variants(variants)
                .build();
    }

    private static ShaderGraphProgram program(String id) {
        ShaderGraphBuilder vertex = new ShaderGraphBuilder(
                id + "_vertex", ShaderGraphKind.VERTEX);
        ShaderExpression zero = vertex.constant("zero",
                ShaderGraphLiteral.f32(0));
        ShaderExpression one = vertex.constant("one",
                ShaderGraphLiteral.f32(1));
        vertex.output("position", ShaderGraphStageSemantic.POSITION,
                vertex.construct("position_value", VEC4,
                        zero, zero, zero, one));

        ShaderGraphBuilder fragment = new ShaderGraphBuilder(
                id + "_fragment", ShaderGraphKind.FRAGMENT);
        fragment.parameter(ShaderGraphParameter.of("highlight", BOOL,
                ShaderGraphParameterKind.STATIC_SWITCH,
                ShaderGraphLiteral.bool(false)));
        ShaderExpression enabled = fragment.parameter(
                "highlight_value", "highlight");
        ShaderExpression low = fragment.construct("low", VEC4,
                fragment.constant("low_r", ShaderGraphLiteral.f32(0.1f)),
                fragment.constant("low_g", ShaderGraphLiteral.f32(0.2f)),
                fragment.constant("low_b", ShaderGraphLiteral.f32(0.3f)),
                fragment.constant("low_a", ShaderGraphLiteral.f32(1)));
        ShaderExpression high = fragment.construct("high", VEC4,
                fragment.constant("high_r", ShaderGraphLiteral.f32(1)),
                fragment.constant("high_g", ShaderGraphLiteral.f32(0.8f)),
                fragment.constant("high_b", ShaderGraphLiteral.f32(0.2f)),
                fragment.constant("high_a", ShaderGraphLiteral.f32(1)));
        fragment.output("color", ShaderGraphStageSemantic.location(0),
                fragment.branch("select_color", enabled, high, low));

        return ShaderGraphProgram.builder(id, vertex.build(),
                fragment.build()).build();
    }
}
