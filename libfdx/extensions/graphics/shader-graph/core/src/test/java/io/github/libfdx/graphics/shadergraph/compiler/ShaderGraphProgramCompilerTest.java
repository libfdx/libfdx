package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphProgramCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphProgramCompilerTest {
    private static final ShaderGraphType F32 =
            ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final ShaderGraphType BOOL =
            ShaderGraphType.scalar(ShaderScalarType.BOOL);
    private static final ShaderGraphType VEC2 =
            ShaderGraphType.vector(ShaderScalarType.F32, 2);
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    @Test
    void linksCompleteStagesWithCustomEntriesMrtDepthAndControlledWgsl() {
        ShaderGraph vertex = vertex(VEC2, "uv0");
        ShaderGraph fragment = fragment(VEC2, "uv0", true);
        ShaderGraphProgram program = ShaderGraphProgram.builder(
                        "complete_stages", vertex, fragment)
                .entryPoints("deformVertex", "lightFragment")
                .materialBinding(2, 0)
                .build();

        ShaderGraphProgramCompileResult result =
                new ShaderGraphProgramCompiler().compile(program,
                        ShaderGraphCompileOptions.builder()
                                .profile(ShaderProfile.PORTABLE_WEBGPU)
                                .build());

        assertTrue(result.success(), diagnostics(result));
        assertEquals("deformVertex", result.vertexEntryPoint());
        assertEquals("lightFragment", result.fragmentEntryPoint());
        assertTrue(result.wgsl().contains("@vertex\nfn deformVertex("));
        assertTrue(result.wgsl().contains("@fragment\nfn lightFragment("));
        assertTrue(result.wgsl().contains("@builtin(position)"));
        assertTrue(result.wgsl().contains("@location(1)"));
        assertTrue(result.wgsl().contains("@builtin(frag_depth)"));
        assertTrue(result.wgsl().contains("@group(2) @binding(0)"));
        assertTrue(result.wgsl().contains("dpdx("));
        assertTrue(result.wgsl().contains("discard;"));
        assertTrue(result.wgsl().contains("length("));
        assertFalse(result.wgsl().contains("fdx_graph_vertex("));
        assertTrue(Arrays.stream(result.sourceMap())
                .anyMatch(value -> value.nodeId().value()
                        .equals("custom_lighting")));
    }

    @Test
    void reportsStageLinkAndDuplicateSlotFailuresWithoutThrowing() {
        ShaderGraph vertex = vertex(VEC2, "uv0");
        ShaderGraph fragment = fragment(VEC4, "uv0", false);
        ShaderGraphProgramCompileResult mismatch =
                new ShaderGraphProgramCompiler().compile(
                        ShaderGraphProgram.builder("mismatch", vertex, fragment)
                                .build(), null);
        assertCode(mismatch, "FDXG_STAGE_LINK_TYPE");

        ShaderGraphBuilder duplicate = new ShaderGraphBuilder(
                "duplicate_vertex", ShaderGraphKind.VERTEX);
        duplicate.parameter(stageInput("a", VEC2,
                ShaderGraphStageSemantic.location(0)));
        duplicate.parameter(stageInput("b", VEC2,
                ShaderGraphStageSemantic.location(0)));
        ShaderExpression a = duplicate.parameter("a_node", "a");
        ShaderExpression zero = duplicate.floatValue(0);
        ShaderExpression one = duplicate.floatValue(1);
        duplicate.output("position", ShaderGraphStageSemantic.POSITION,
                duplicate.construct("position_value", VEC4, a, zero, one));
        ShaderGraphProgramCompileResult duplicateResult =
                new ShaderGraphProgramCompiler().compile(
                        ShaderGraphProgram.builder("duplicate",
                                duplicate.build(),
                                simpleFragment()).build(), null);
        assertCode(duplicateResult, "FDXG_VERTEX_INPUT_LOCATION");
    }

    @Test
    void rejectsHiddenCustomIdentifiersAndProducesDeterministicPrograms() {
        ShaderGraphBuilder invalid = new ShaderGraphBuilder(
                "invalid_custom", ShaderGraphKind.FRAGMENT);
        ShaderExpression color = invalid.customWgsl("hidden", VEC4,
                "textureLoad(hiddenTexture, vec2<i32>(0), 0)");
        invalid.output("color", ShaderGraphStageSemantic.location(0), color);
        ShaderGraphProgramCompileResult rejected =
                new ShaderGraphProgramCompiler().compile(
                        ShaderGraphProgram.builder("invalid",
                                vertex(VEC2, "uv0"), invalid.build()).build(),
                        null);
        assertCode(rejected, "FDXG_OPERATION");

        ShaderGraphProgram first = ShaderGraphProgram.builder(
                        "stable", vertex(VEC2, "uv0"),
                        fragment(VEC2, "uv0", true))
                .entryPoints("v", "f").materialBinding(3, 4).build();
        ShaderGraphProgram second = ShaderGraphProgram.builder(
                        "stable", vertex(VEC2, "uv0"),
                        fragment(VEC2, "uv0", true))
                .entryPoints("v", "f").materialBinding(3, 4).build();
        ShaderGraphProgramCompileResult firstResult =
                new ShaderGraphProgramCompiler().compile(first, null);
        ShaderGraphProgramCompileResult secondResult =
                new ShaderGraphProgramCompiler().compile(second, null);
        assertTrue(firstResult.success(), diagnostics(firstResult));
        assertEquals(first.semanticHash(), second.semanticHash());
        assertEquals(firstResult.wgsl(), secondResult.wgsl());
    }

    private static ShaderGraph vertex(ShaderGraphType varyingType,
            String varyingSemantic) {
        ShaderGraphBuilder builder = new ShaderGraphBuilder(
                "complete_vertex", ShaderGraphKind.VERTEX);
        builder.parameter(stageInput("position", VEC2,
                ShaderGraphStageSemantic.location(0)));
        builder.parameter(stageInput("uv", VEC2,
                ShaderGraphStageSemantic.location(1)));
        builder.parameter(ShaderGraphParameter.of("deformation", F32,
                ShaderGraphParameterKind.MATERIAL,
                ShaderGraphLiteral.f32(0)));
        ShaderExpression position = builder.parameter(
                "position_input", "position");
        ShaderExpression deformation = builder.parameter(
                "deformation_input", "deformation");
        ShaderExpression deformed = builder.customWgsl("custom_deformation",
                VEC2, "$0 + vec2<f32>($1, 0.0)",
                position, deformation);
        ShaderExpression zero = builder.floatValue(0);
        ShaderExpression one = builder.floatValue(1);
        builder.output("clip_position", ShaderGraphStageSemantic.POSITION,
                builder.construct("clip", VEC4, deformed, zero, one));
        ShaderExpression varying = builder.parameter("uv_input", "uv");
        if (!VEC2.equals(varyingType)) {
            ShaderExpression x = builder.member("uv_x", varying, "x", F32);
            varying = builder.construct("expanded_uv", varyingType,
                    x, x, x, x);
        }
        builder.output("varying_uv", varyingSemantic, varying);
        return builder.build();
    }

    private static ShaderGraph fragment(ShaderGraphType inputType,
            String varyingSemantic, boolean complete) {
        ShaderGraphBuilder builder = new ShaderGraphBuilder(
                "complete_fragment", ShaderGraphKind.FRAGMENT);
        builder.parameter(stageInput("varying_uv", inputType,
                varyingSemantic));
        ShaderExpression varying = builder.parameter(
                "varying_input", "varying_uv");
        if (!complete) {
            builder.output("color", ShaderGraphStageSemantic.location(0),
                    builder.construct("color", VEC4,
                            builder.floatValue(1), builder.floatValue(0),
                            builder.floatValue(0), builder.floatValue(1)));
            return builder.build();
        }
        ShaderExpression derivative = builder.derivativeX("uv_dx", varying);
        ShaderExpression color = builder.customWgsl("custom_lighting", VEC4,
                "vec4<f32>($0.x, $0.y, length($1), 1.0)",
                varying, derivative);
        ShaderExpression discardCondition = builder.customWgsl(
                "discard_condition", BOOL, "$0.x < 0.0", varying);
        ShaderExpression discarded = builder.discardIf(
                "discard_transparent", discardCondition);
        ShaderExpression keptColor = builder.branch("keep_color", discarded,
                color, color);
        builder.output("color0", ShaderGraphStageSemantic.location(0),
                keptColor);
        builder.output("color1", ShaderGraphStageSemantic.location(1),
                builder.customWgsl("second_target", VEC4,
                        "vec4<f32>(1.0 - $0.rgb, $0.a)", color));
        builder.output("depth", ShaderGraphStageSemantic.FRAGMENT_DEPTH,
                builder.floatValue(0.5f));
        return builder.build();
    }

    private static ShaderGraph simpleFragment() {
        ShaderGraphBuilder builder = new ShaderGraphBuilder(
                "simple_fragment", ShaderGraphKind.FRAGMENT);
        builder.output("color", ShaderGraphStageSemantic.location(0),
                builder.construct("color", VEC4,
                        builder.floatValue(1), builder.floatValue(0),
                        builder.floatValue(0), builder.floatValue(1)));
        return builder.build();
    }

    private static ShaderGraphParameter stageInput(String id,
            ShaderGraphType type, String semantic) {
        return ShaderGraphParameter.semantic(id, type,
                ShaderGraphParameterKind.STAGE_INPUT, null, semantic);
    }

    private static void assertCode(ShaderGraphProgramCompileResult result,
            String code) {
        assertFalse(result.success(), diagnostics(result));
        assertTrue(Arrays.stream(result.diagnostics())
                .anyMatch(value -> value.code().equals(code)),
                diagnostics(result));
    }

    private static String diagnostics(ShaderGraphProgramCompileResult result) {
        StringBuilder message = new StringBuilder();
        for (ShaderGraphDiagnostic diagnostic : result.diagnostics()) {
            message.append(diagnostic.code()).append(": ")
                    .append(diagnostic.message()).append('\n');
        }
        return message.toString();
    }
}
