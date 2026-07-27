package io.github.libfdx.samples.shadergraph;

import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCacheContext;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentCodec;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRuntimeAsset;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRuntimeGraph;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRuntimeLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphSampleGraphsTest {
    private static final ShaderGraphCacheContext WEBGPU =
            context(ShaderProfile.PORTABLE_WEBGPU);
    private static final ShaderGraphCacheContext WEBGL2 =
            context(ShaderProfile.PORTABLE_WEBGL2);

    @Test
    void checkedAssetMatchesCodeAuthoredGraph() throws Exception {
        ShaderGraph code =
                ShaderGraphSampleGraphs.codeAuthoredSurface();
        String source = assetSource();
        ShaderGraphDocument document =
                ShaderGraphDocumentCodec.read(source);
        ShaderGraph asset = document.graph();

        assertEquals(ShaderGraphDocumentCodec.write(
                ShaderGraphDocument.of(code)), source.trim());
        assertEquals(code.semanticHash(), asset.semanticHash());
        assertNotNull(asset.parameter(ShaderGraphId.of("warmth")));
    }

    @Test
    void codeAndFileAuthoringProduceEquivalentRuntimeArtifacts()
            throws Exception {
        ShaderGraphRuntimeLoader loader =
                new ShaderGraphRuntimeLoader();
        ShaderGraphRuntimeAsset code = loader.load(
                ShaderGraphDocument.of(
                        ShaderGraphSampleGraphs.codeAuthoredSurface()),
                WEBGPU);
        ShaderGraphRuntimeAsset file =
                loader.load(assetSource(), WEBGPU);

        assertTrue(code.cacheMiss());
        assertTrue(file.cacheMiss());
        assertRuntimeGraphEquals(code.graph(), file.graph());
    }

    @Test
    void oneFileEmbeddedCacheIsOptionalExactAndReplaceable()
            throws Exception {
        ShaderGraphRuntimeLoader loader =
                new ShaderGraphRuntimeLoader();
        ShaderGraphRuntimeAsset miss =
                loader.load(assetSource(), WEBGPU);
        String embeddedSource = ShaderGraphDocumentCodec.write(
                miss.documentWithCompiledCache());

        ShaderGraphRuntimeAsset hit =
                loader.load(embeddedSource, WEBGPU);
        assertTrue(hit.cacheHit());
        assertRuntimeGraphEquals(miss.graph(), hit.graph());

        ShaderGraphRuntimeAsset uncached = loader.load(
                ShaderGraphDocumentCodec.write(
                        hit.document().withoutCompiled()),
                WEBGPU);
        assertTrue(uncached.cacheMiss());
        assertRuntimeGraphEquals(hit.graph(), uncached.graph());

        ShaderGraphRuntimeAsset otherProfile =
                loader.load(embeddedSource, WEBGL2);
        assertTrue(otherProfile.cacheMiss());
        ShaderGraphRuntimeAsset directOtherProfile =
                loader.load(hit.document().withoutCompiled(), WEBGL2);
        assertRuntimeGraphEquals(
                directOtherProfile.graph(), otherProfile.graph());
    }

    @Test
    void staleEmbeddedCacheIsRejectedAndRecompiled()
            throws Exception {
        ShaderGraphRuntimeLoader loader =
                new ShaderGraphRuntimeLoader();
        ShaderGraphRuntimeAsset original =
                loader.load(assetSource(), WEBGPU);
        ShaderGraph source = original.graph().graph();
        ShaderGraph edited = ShaderGraph.builder(
                        source.id().value() + ".edited", source.kind())
                .formatVersion(source.formatVersion())
                .parameters(source.parameters())
                .resources(source.resources())
                .nodes(source.nodes())
                .edges(source.edges())
                .outputs(source.outputs())
                .dependencies(source.dependencies())
                .build();
        ShaderGraphDocument stale = ShaderGraphDocument.of(edited)
                .withCompiledCache(
                        original.documentWithCompiledCache()
                                .compiledCache());

        ShaderGraphRuntimeAsset loaded = loader.load(
                ShaderGraphDocumentCodec.write(stale), WEBGPU);

        assertTrue(loaded.cacheMiss());
        assertTrue(loaded.cacheRejections().length > 0);
        assertEquals(edited.semanticHash(),
                loaded.document().semanticHash());
    }

    @Test
    void sampleSurfaceCompilesForPortableProfiles() throws Exception {
        ShaderGraph graph =
                ShaderGraphDocumentCodec.read(assetSource()).graph();
        ShaderGraphCompiler compiler = new ShaderGraphCompiler();

        assertSuccessful(compiler.compile(graph,
                ShaderGraphCompileOptions.builder()
                        .profile(ShaderProfile.PORTABLE_WEBGPU)
                        .build()));
        assertSuccessful(compiler.compile(graph,
                ShaderGraphCompileOptions.builder()
                        .profile(ShaderProfile.PORTABLE_WEBGL2)
                        .build()));
    }

    private static String assetSource() throws Exception {
        try (InputStream stream =
                ShaderGraphSampleGraphsTest.class.getClassLoader()
                        .getResourceAsStream(
                                ShaderGraphSampleGraphs.SURFACE_ASSET)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }

    private static void assertSuccessful(
            ShaderGraphCompileResult result) {
        assertTrue(result.success(),
                () -> diagnostics(result));
        assertTrue(!result.wgsl().isBlank());
    }

    private static void assertRuntimeGraphEquals(
            ShaderGraphRuntimeGraph expected,
            ShaderGraphRuntimeGraph actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.graph().semanticHash(),
                actual.graph().semanticHash());
        assertEquals(expected.wgsl(), actual.wgsl());
        assertEquals(expected.libraryWgsl(), actual.libraryWgsl());
        assertEquals(expected.shaderInterface(),
                actual.shaderInterface());
        assertEquals(expected.libraryInterface(),
                actual.libraryInterface());
    }

    private static ShaderGraphCacheContext context(
            ShaderProfile profile) {
        return ShaderGraphCacheContext.wgpu(
                ShaderGraphCompileOptions.builder()
                        .profile(profile)
                        .build());
    }

    private static String diagnostics(
            ShaderGraphCompileResult result) {
        StringBuilder message =
                new StringBuilder("Compilation failed");
        for (var diagnostic : result.diagnostics()) {
            message.append("\n").append(diagnostic.code())
                    .append(": ").append(diagnostic.message());
        }
        return message.toString();
    }
}
