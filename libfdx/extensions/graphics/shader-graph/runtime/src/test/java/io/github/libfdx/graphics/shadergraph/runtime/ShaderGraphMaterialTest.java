package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShaderGraphMaterialTest {
    @Test
    void materialInstancesAreTypedRevisionedAndDefinitionOwned() {
        ShaderGraphMaterialDefinition definition = compile(graph("gain", ""));
        ShaderGraphMaterialInstance first =
                new ShaderGraphMaterialInstance(definition);
        ShaderGraphMaterialInstance second =
                new ShaderGraphMaterialInstance(definition);

        assertNotEquals(first.identity(), second.identity());
        assertEquals(0, first.revision());
        first.set("gain", ShaderGraphLiteral.f32(0.5f));
        assertEquals(1, first.revision());
        first.set("gain", ShaderGraphLiteral.f32(0.5f));
        assertEquals(1, first.revision());
        assertThrows(FdxException.class,
                () -> first.set("gain", ShaderGraphLiteral.i32(1)));
        assertThrows(FdxException.class,
                () -> first.set("missing", ShaderGraphLiteral.f32(1)));
    }

    @Test
    void materialCannotClaimRendererOwnedSemantics() {
        assertThrows(FdxException.class,
                () -> compile(graph("bad", "environment.light")));
    }

    private static ShaderGraphMaterialDefinition compile(ShaderGraph graph) {
        return ShaderGraphMaterialDefinition.compile(graph,
                new ShaderGraphCompiler(),
                ShaderGraphCompileOptions.builder().build());
    }

    private static ShaderGraph graph(String parameter, String semantic) {
        ShaderGraphType f32 = ShaderGraphType.scalar(ShaderScalarType.F32);
        ShaderGraphBuilder builder =
                new ShaderGraphBuilder("material_test", ShaderGraphKind.SURFACE);
        builder.parameter(ShaderGraphParameter.semantic(parameter, f32,
                ShaderGraphParameterKind.MATERIAL,
                ShaderGraphLiteral.f32(1), semantic));
        ShaderExpression value = builder.parameter("value", parameter);
        builder.output("alpha", "alpha", value);
        return builder.build();
    }
}
