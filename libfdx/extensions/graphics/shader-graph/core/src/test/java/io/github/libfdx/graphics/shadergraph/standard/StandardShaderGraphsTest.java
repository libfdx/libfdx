package io.github.libfdx.graphics.shadergraph.standard;

import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompiler;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardShaderGraphsTest {
    @Test
    void standardLibraryFunctionsCompileDeterministically() {
        ShaderGraph[] graphs = StandardShaderGraphs.all();
        Set<String> categories = new HashSet<>();
        for (ShaderGraph graph : graphs) {
            ShaderGraphCompileResult first = new ShaderGraphCompiler()
                    .compile(graph, ShaderGraphCompileOptions.builder()
                            .library(StandardShaderGraphs.library())
                            .build());
            ShaderGraphCompileResult second = new ShaderGraphCompiler()
                    .compile(graph, ShaderGraphCompileOptions.builder()
                            .library(StandardShaderGraphs.library())
                            .build());
            assertTrue(first.success(), graph.id() + ": "
                    + diagnostics(first));
            assertEquals(first.wgsl(), second.wgsl());
            assertNotNull(StandardShaderGraphs.library()
                    .resolve(graph.id()));
            String value = graph.id().value();
            int firstDot = value.indexOf('.');
            int secondDot = value.indexOf('.', firstDot + 1);
            categories.add(value.substring(firstDot + 1, secondDot));
        }
        assertTrue(categories.contains("math"));
        assertTrue(categories.contains("coordinate"));
        assertTrue(categories.contains("texture"));
        assertTrue(categories.contains("normal"));
        assertTrue(categories.contains("color"));
        assertTrue(categories.contains("lighting"));
        assertTrue(categories.contains("brdf"));
        assertTrue(categories.contains("shadow"));
        assertTrue(categories.contains("fog"));
        assertTrue(categories.contains("post"));
    }

    private static String diagnostics(ShaderGraphCompileResult result) {
        StringBuilder value = new StringBuilder();
        for (ShaderGraphDiagnostic diagnostic : result.diagnostics()) {
            value.append(diagnostic.code()).append('=')
                    .append(diagnostic.message()).append(' ');
        }
        return value.toString();
    }

    @Test
    void fullscreenAndPostProcessTechniquesCompileDeterministically() {
        ShaderGraphTechnique fullscreen =
                StandardFullscreenTechnique.fullscreen(
                        io.github.libfdx.graphics.TextureFormat.RGBA8_UNORM);
        ShaderGraphTechnique post =
                StandardFullscreenTechnique.postProcess(
                        io.github.libfdx.graphics.TextureFormat.RGBA8_UNORM);
        ShaderGraphTechniqueCompiler compiler =
                new ShaderGraphTechniqueCompiler();
        ShaderGraphCompileOptions options =
                ShaderGraphCompileOptions.builder()
                        .library(StandardShaderGraphs.library())
                        .build();
        ShaderGraphTechniqueCompileResult first =
                compiler.compile(fullscreen, options);
        ShaderGraphTechniqueCompileResult second =
                compiler.compile(fullscreen, options);
        ShaderGraphTechniqueCompileResult postResult =
                compiler.compile(post, options);
        assertTrue(first.success(), diagnostics(first));
        assertTrue(postResult.success(), diagnostics(postResult));
        assertEquals(first.passes()[0].variants()[0]
                        .compilation().wgsl(),
                second.passes()[0].variants()[0]
                        .compilation().wgsl());
        assertTrue(postResult.passes()[0].variants()[0]
                .compilation().wgsl().contains(
                        "fdx_graph_libfdx_post_reinhard_tone_map"));
    }

    private static String diagnostics(
            ShaderGraphTechniqueCompileResult result) {
        StringBuilder value = new StringBuilder();
        for (ShaderGraphDiagnostic diagnostic :
                result.diagnostics()) {
            value.append(diagnostic.code()).append(": ")
                    .append(diagnostic.message()).append('\n');
        }
        return value.toString();
    }
}
