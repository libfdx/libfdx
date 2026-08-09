package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PbrGraphCustomizationTest {
    @Test
    void composesTheSameGraphIntoStaticAndSkinnedPbrTemplates() {
        ShaderGraphMaterialDefinition definition =
                ShaderGraphMaterialDefinition.compile(
                        StandardPbrSurfaceGraph.create(),
                        new ShaderGraphCompiler(),
                        ShaderGraphCompileOptions.builder().build());
        var compiler = new ShaderGraphCompiler();
        var options = ShaderGraphCompileOptions.builder().build();
        var vertexGraph = StandardPbrVertexGraph.create();
        var lightingGraph = StandardPbrLightingGraph.create();
        PbrGraphCustomization customization =
                new PbrGraphCustomization(definition,
                        vertexGraph,
                        compiler.compile(vertexGraph, options),
                        lightingGraph,
                        compiler.compile(lightingGraph, options));

        String staticSource = customization.shader(false).source();
        String skinnedSource = customization.shader(true).source();
        assertFalse(staticSource.contains("__PBR_SURFACE_GRAPH_"));
        assertFalse(skinnedSource.contains("__PBR_SURFACE_GRAPH_"));
        assertFalse(staticSource.contains("__PBR_VERTEX_GRAPH_"));
        assertFalse(staticSource.contains("__PBR_LIGHTING_GRAPH_"));
        assertTrue(staticSource.contains("fdx_graph_libfdx_standard_pbr_surface"));
        assertTrue(staticSource.contains(
                "fdx_graph_libfdx_standard_pbr_vertex"));
        assertTrue(staticSource.contains(
                "fdx_graph_libfdx_standard_pbr_lighting"));
        assertTrue(skinnedSource.contains("fdx_graph_libfdx_standard_pbr_surface"));
        assertTrue(staticSource.contains("uniforms.fdx_tint"));
        assertFalse(staticSource.contains("fdx_graph_material"));
        assertNotEquals(staticSource, skinnedSource);

        var staticUniform = customization.shader(false).reflection()
                .requireBinding(1, 0);
        var skinnedUniform = customization.shader(true).reflection()
                .requireBinding(1, 0);
        long staticBaseSize = PbrShaderParameters.staticReflection()
                .requireBinding(1, 0).minimumBindingSize();
        long skinnedBaseSize = PbrShaderParameters.skinnedReflection()
                .requireBinding(1, 0).minimumBindingSize();
        assertEquals(staticBaseSize,
                staticUniform.bufferLayout()
                        .requireHandle("fdx_emissive_gain")
                        .byteOffset());
        assertEquals(skinnedBaseSize,
                skinnedUniform.bufferLayout()
                        .requireHandle("fdx_emissive_gain")
                        .byteOffset());
        assertEquals(staticBaseSize + 16,
                staticUniform.bufferLayout().requireHandle("fdx_tint")
                        .byteOffset());
        assertEquals(skinnedBaseSize + 16,
                skinnedUniform.bufferLayout().requireHandle("fdx_tint")
                        .byteOffset());
        assertEquals(staticBaseSize + 32,
                staticUniform.minimumBindingSize());
        assertEquals(skinnedBaseSize + 32,
                skinnedUniform.minimumBindingSize());

        var material = customization.newMaterialInstance();
        assertEquals(0, material.revision());
        material.set("emissive_gain", ShaderGraphLiteral.f32(2));
        assertEquals(1, material.revision());
    }
}
