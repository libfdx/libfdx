package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderParameter;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceUse;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVisibility;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
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

final class ShaderGraphMaterialBinderTest {
    @Test
    void writesMatricesIntoRendererOwnedBlocksWithoutPerWriteStorage() {
        ShaderGraphType matrixType =
                ShaderGraphType.matrix(ShaderScalarType.F32, 3, 3);
        ShaderGraphLiteral initial = matrix(matrixType,
                1, 2, 3, 4, 5, 6, 7, 8, 9);
        ShaderGraphBuilder graph = new ShaderGraphBuilder(
                "matrix_material", ShaderGraphKind.SURFACE);
        graph.parameter(ShaderGraphParameter.of("transform", matrixType,
                ShaderGraphParameterKind.MATERIAL, initial));
        graph.output("value", "value",
                graph.parameter("transform_value", "transform"));
        ShaderGraphMaterialDefinition definition =
                ShaderGraphMaterialDefinition.compile(graph.build(),
                        new ShaderGraphCompiler(),
                        ShaderGraphCompileOptions.builder().build());
        ShaderGraphMaterialInstance material =
                new ShaderGraphMaterialInstance(definition);

        ShaderValueType reflectedType = ShaderValueType.matrix(
                ShaderScalarType.F32, 3, 3, 16).named("mat3x3<f32>");
        ShaderParameter parameter = ShaderParameter.builder(
                        "transform", "fdx_transform",
                        reflectedType, 0, 48, 16)
                .matrixStride(16)
                .build();
        ShaderParameterLayout parameterLayout =
                ShaderParameterLayout.of(48, 16, parameter);
        ShaderBinding binding = ShaderBinding.builder(1, 0,
                        "material", ShaderResourceKind.UNIFORM_BUFFER)
                .visibility(ShaderStageVisibility.FRAGMENT)
                .access(ShaderResourceAccess.READ)
                .buffer(48, 48, 16, parameterLayout)
                .build();
        ShaderReflection reflection = ShaderReflection.complete(
                ShaderProfile.PORTABLE_WEBGPU,
                new ShaderEntryPoint[] {
                        ShaderEntryPoint.builder("fragmentMain",
                                        ShaderStage.FRAGMENT)
                                .resources(ShaderResourceUse.of(1, 0, 48))
                                .build()
                },
                new ShaderBinding[] { binding }, new String[0]);
        ShaderResourceLayout resourceLayout =
                ShaderResourceLayout.all(reflection);
        ShaderParameterBlock block =
                ShaderParameterBlock.allocate(parameterLayout);
        ShaderGraphMaterialBinder binder =
                new ShaderGraphMaterialBinder(definition, 1, 0);

        binder.writeParameters(resourceLayout, material, block);
        assertEquals(1, block.revision());
        assertEquals(1.0f, block.readOnlyData().getFloat(0));
        assertEquals(4.0f, block.readOnlyData().getFloat(16));
        assertEquals(7.0f, block.readOnlyData().getFloat(32));
        binder.writeParameters(resourceLayout, material, block);
        assertEquals(1, block.revision());

        material.set("transform", matrix(matrixType,
                9, 8, 7, 6, 5, 4, 3, 2, 1));
        binder.writeParameters(resourceLayout, material, block);
        assertEquals(2, block.revision());
        assertEquals(9.0f, block.readOnlyData().getFloat(0));
        assertEquals(6.0f, block.readOnlyData().getFloat(16));
        assertEquals(3.0f, block.readOnlyData().getFloat(32));

        ShaderGraphMaterialInstance otherMaterial =
                new ShaderGraphMaterialInstance(definition);
        otherMaterial.set("transform", matrix(matrixType,
                2, 3, 4, 5, 6, 7, 8, 9, 10));
        ShaderGraphMaterialBinder otherBinder =
                new ShaderGraphMaterialBinder(definition, 1, 0);
        otherBinder.writeParameters(resourceLayout, otherMaterial, block);
        assertEquals(3, block.revision());
        assertEquals(2.0f, block.readOnlyData().getFloat(0));

        binder.writeParameters(resourceLayout, material, block);
        assertEquals(4, block.revision());
        assertEquals(9.0f, block.readOnlyData().getFloat(0));
        assertEquals(6.0f, block.readOnlyData().getFloat(16));
        assertEquals(3.0f, block.readOnlyData().getFloat(32));
    }

    private static ShaderGraphLiteral matrix(ShaderGraphType type,
            float... values) {
        ShaderGraphLiteral[] elements =
                new ShaderGraphLiteral[values.length];
        for (int i = 0; i < values.length; i++) {
            elements[i] = ShaderGraphLiteral.f32(values[i]);
        }
        return ShaderGraphLiteral.composite(type, elements);
    }
}
