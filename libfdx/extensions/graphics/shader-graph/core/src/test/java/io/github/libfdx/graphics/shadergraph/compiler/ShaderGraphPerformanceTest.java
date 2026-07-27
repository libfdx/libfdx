package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphPipelineState;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompiler;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariantExpansion;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.TextureFormat;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphPerformanceTest {
    private static final ShaderGraphType BOOL =
            ShaderGraphType.scalar(ShaderScalarType.BOOL);
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    @Test
    void measuresDefaultVariantBudgetCompilation() {
        ShaderGraphProgram program = switchedProgram(7);
        String[] switches = switchIds(7);

        long expansionStart = System.nanoTime();
        ShaderGraphVariant[] variants =
                ShaderGraphVariantExpansion.booleans(
                        program,
                        ShaderGraphTechnique.DEFAULT_MAX_VARIANTS,
                        switches);
        long expansionNanos = System.nanoTime() - expansionStart;

        ShaderGraphTechnique technique =
                ShaderGraphTechnique.builder(
                                "performance_variants")
                        .passes(ShaderGraphTechniquePass.builder(
                                        ShaderPassId.FORWARD,
                                        ShaderGraphPipelineState
                                                .builder()
                                                .colorTargets(
                                                        ColorTargetState
                                                                .opaque(
                                                                        TextureFormat
                                                                                .RGBA8_UNORM))
                                                .build())
                                .variants(variants)
                                .build())
                        .build();

        long compileStart = System.nanoTime();
        ShaderGraphTechniqueCompileResult result =
                new ShaderGraphTechniqueCompiler().compile(
                        technique, null);
        long compileNanos = System.nanoTime() - compileStart;

        assertTrue(result.success(), diagnostics(result));
        assertEquals(ShaderGraphTechnique.DEFAULT_MAX_VARIANTS,
                technique.variantCount());
        assertEquals(ShaderGraphTechnique.DEFAULT_MAX_VARIANTS,
                result.passes()[0].variants().length);
        assertThrows(FdxException.class,
                () -> ShaderGraphVariantExpansion.booleans(
                        program,
                        ShaderGraphTechnique.DEFAULT_MAX_VARIANTS,
                        switchIds(8)));
        assertThrows(FdxException.class,
                () -> ShaderGraphVariantExpansion.booleans(
                        program,
                        ShaderGraphTechnique.HARD_MAX_VARIANTS,
                        switchIds(11)));

        System.out.printf(Locale.ROOT,
                "SHADER_GRAPH_PERF variants=%d expansion_ms=%.3f "
                        + "compile_ms=%.3f compile_us_per_variant=%.3f%n",
                technique.variantCount(),
                expansionNanos / 1_000_000.0,
                compileNanos / 1_000_000.0,
                compileNanos / 1_000.0
                        / technique.variantCount());
    }

    private static ShaderGraphProgram switchedProgram(int count) {
        ShaderGraphBuilder vertex = new ShaderGraphBuilder(
                "performance_vertex", ShaderGraphKind.VERTEX);
        vertex.output("position",
                ShaderGraphStageSemantic.POSITION,
                vertex.constant("position_value",
                        ShaderGraphLiteral.composite(VEC4,
                                ShaderGraphLiteral.f32(0),
                                ShaderGraphLiteral.f32(0),
                                ShaderGraphLiteral.f32(0),
                                ShaderGraphLiteral.f32(1))));

        ShaderGraphBuilder fragment = new ShaderGraphBuilder(
                "performance_fragment", ShaderGraphKind.FRAGMENT);
        ShaderExpression color = fragment.constant("base_color",
                ShaderGraphLiteral.composite(VEC4,
                        ShaderGraphLiteral.f32(0),
                        ShaderGraphLiteral.f32(0),
                        ShaderGraphLiteral.f32(0),
                        ShaderGraphLiteral.f32(1)));
        for (int i = 0; i < count; i++) {
            String id = "switch_" + i;
            fragment.parameter(ShaderGraphParameter.of(
                    id, BOOL,
                    ShaderGraphParameterKind.STATIC_SWITCH,
                    ShaderGraphLiteral.bool(false)));
            ShaderExpression enabled = fragment.parameter(
                    id + "_value", id);
            ShaderExpression enabledColor = fragment.constant(
                    id + "_color",
                    ShaderGraphLiteral.composite(VEC4,
                            ShaderGraphLiteral.f32(
                                    (i + 1.0f) / (count + 1.0f)),
                            ShaderGraphLiteral.f32(0.5f),
                            ShaderGraphLiteral.f32(0.25f),
                            ShaderGraphLiteral.f32(1)));
            color = fragment.branch(id + "_branch",
                    enabled, enabledColor, color);
        }
        fragment.output("color",
                ShaderGraphStageSemantic.location(0), color);
        return ShaderGraphProgram.builder("performance_program",
                vertex.build(), fragment.build()).build();
    }

    private static String[] switchIds(int count) {
        String[] result = new String[count];
        for (int i = 0; i < result.length; i++) {
            result[i] = "switch_" + i;
        }
        return result;
    }

    private static String diagnostics(
            ShaderGraphTechniqueCompileResult result) {
        StringBuilder value = new StringBuilder();
        for (ShaderGraphDiagnostic diagnostic
                : result.diagnostics()) {
            value.append(diagnostic.code()).append(": ")
                    .append(diagnostic.message()).append('\n');
        }
        return value.toString();
    }
}
