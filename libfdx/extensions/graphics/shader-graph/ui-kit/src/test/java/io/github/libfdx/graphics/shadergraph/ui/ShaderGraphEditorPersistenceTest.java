package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCacheContext;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDocumentCompiler;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentCodec;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLibrary;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphEditorPersistenceTest {
    @Test
    void saveWithCompiledCacheIsAtomicAndPlainSaveAllowsUnfinishedGraphs() {
        ShaderGraphEditorDocument document =
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph(
                                "compiled_surface",
                                ShaderGraphKind.SURFACE));
        ShaderGraphEditorLayout layout =
                ShaderGraphEditorLayout.forDocument(document);
        ShaderGraphCacheContext context =
                context(document, ShaderProfile.PORTABLE_WEBGPU);

        ShaderGraphEditorSaveResult saved =
                ShaderGraphEditorPersistence.writeWithCompiledCache(
                        document, layout, context);

        assertTrue(saved.success());
        assertEquals(0, saved.cacheHits());
        assertEquals(1, saved.cacheMisses());
        assertTrue(saved.savedDocument()
                .shaderDocument().hasCompiled());
        var decoded = ShaderGraphDocumentCodec.read(saved.source());
        assertTrue(decoded.hasEditor());
        assertTrue(decoded.hasCompiled());
        assertTrue(new ShaderGraphDocumentCompiler()
                .compile(decoded, context).cacheHit());

        ShaderGraphEditorLayout moved = layout.withViewport(
                layout.activeGraphId(), 91.0f, -12.0f, 1.5f);
        var layoutOnly = ShaderGraphDocumentCodec.read(
                ShaderGraphEditorPersistence.write(
                        saved.savedDocument(), moved));
        assertEquals(decoded.compiledCache(),
                layoutOnly.compiledCache());
        assertEquals(decoded.semanticHash(),
                layoutOnly.semanticHash());

        ShaderGraphEditorSession session =
                new ShaderGraphEditorSession(
                        document, moved);
        long semanticRevision = session.semanticRevision();
        long layoutRevision = session.layoutRevision();
        session.adoptSavedDocument(
                saved.savedDocument());
        assertTrue(session.document()
                .shaderDocument().hasCompiled());
        assertEquals(semanticRevision,
                session.semanticRevision());
        assertEquals(layoutRevision, session.layoutRevision());
        String node = session.activeGraph().nodes()[0]
                .id().value();
        session.renameNode(node, node + "_edited");
        assertFalse(session.document()
                .shaderDocument().hasCompiled());

        ShaderGraphEditorDocument unfinished =
                ShaderGraphEditorDocument.of(
                        new ShaderGraphBuilder("unfinished",
                                ShaderGraphKind.SURFACE).build());
        ShaderGraphEditorLayout unfinishedLayout =
                ShaderGraphEditorLayout.forDocument(unfinished);
        String unfinishedSource =
                ShaderGraphEditorPersistence.write(
                        unfinished, unfinishedLayout);
        assertFalse(ShaderGraphDocumentCodec.read(
                unfinishedSource).hasCompiled());

        ShaderGraphEditorSaveResult failed =
                ShaderGraphEditorPersistence.writeWithCompiledCache(
                        unfinished, unfinishedLayout,
                        context(unfinished,
                                ShaderProfile.PORTABLE_WEBGPU));
        assertFalse(failed.success());
        assertTrue(failed.diagnostics().length > 0);
        assertThrows(FdxException.class, failed::source);
        assertFalse(unfinished.shaderDocument().hasCompiled());
    }

    @Test
    void multiContextFailureCannotPublishAPartialCache() {
        ShaderGraphEditorDocument document =
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph(
                                "atomic_surface",
                                ShaderGraphKind.SURFACE));
        ShaderGraphCompileOptions options = options(
                document, ShaderProfile.PORTABLE_WEBGPU);
        ShaderGraphCacheContext unsupported =
                ShaderGraphCacheContext.builder(options)
                        .compiler("test-glsl", "1")
                        .libraries("standard-nodes-v1",
                                "standard-wgsl-v1")
                        .target("opengl-glsl", "glsl",
                                "opengl-test")
                        .interfaceAbiVersion(
                                ShaderGraphCacheContext.INTERFACE_ABI)
                        .build();

        ShaderGraphEditorSaveResult failed =
                ShaderGraphEditorPersistence.writeWithCompiledCache(
                        document,
                        ShaderGraphEditorLayout.forDocument(document),
                        ShaderGraphCacheContext.wgpu(options),
                        unsupported);

        assertFalse(failed.success());
        assertEquals(1, failed.cacheMisses());
        assertTrue(failed.diagnostics().length > 0);
        assertFalse(document.shaderDocument().hasCompiled());
        assertThrows(FdxException.class, failed::savedDocument);
    }

    @Test
    void compilingAnotherProfilePreservesExistingEntries() {
        ShaderGraphEditorDocument document =
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph(
                                "profiles",
                                ShaderGraphKind.SURFACE));
        ShaderGraphEditorLayout layout =
                ShaderGraphEditorLayout.forDocument(document);
        ShaderGraphEditorSaveResult webGpu =
                ShaderGraphEditorPersistence.writeWithCompiledCache(
                        document, layout,
                        context(document,
                                ShaderProfile.PORTABLE_WEBGPU));
        ShaderGraphEditorSaveResult nativeProfile =
                ShaderGraphEditorPersistence.writeWithCompiledCache(
                        webGpu.savedDocument(), layout,
                        context(webGpu.savedDocument(),
                                ShaderProfile.NATIVE));

        assertTrue(nativeProfile.success());
        assertEquals(4, nativeProfile.savedDocument()
                .shaderDocument().compiledCache().size());
    }

    @Test
    void corruptLayoutNeverPreventsSemanticLoad() {
        ShaderGraphEditorDocument document = ShaderGraphEditorDocument.of(
                ShaderGraphEditorFixtures.technique("recover"));
        String corruptEditor = ShaderGraphDocumentCodec.write(
                document.shaderDocument().withEditorJson(
                        "{\"asset\":\"unknown-editor-state\"}"));
        ShaderGraphEditorLoadResult result =
                ShaderGraphEditorPersistence.read(corruptEditor);

        assertEquals(document, result.document());
        assertTrue(result.recoveredEditorState());
        assertFalse(result.editorWarning().isEmpty());
        assertEquals(document.graphs().length,
                result.layout().graphs().length);

        String source = ShaderGraphEditorPersistence.write(
                result.document(), result.layout());
        ShaderGraphEditorLoadResult roundTrip =
                ShaderGraphEditorPersistence.read(source);
        assertEquals(document, roundTrip.document());
        assertEquals(result.layout(), roundTrip.layout());
        assertFalse(roundTrip.recoveredEditorState());
        assertTrue(ShaderGraphDocumentCodec.read(source).hasEditor());
    }

    @Test
    void editsEveryHeadlessAssetKindWithoutLosingContainerData() {
        List<ShaderGraphEditorDocument> documents = List.of(
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph("function",
                                ShaderGraphKind.FUNCTION)),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph("surface",
                                ShaderGraphKind.SURFACE)),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph("vertex",
                                ShaderGraphKind.VERTEX)),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph("fragment",
                                ShaderGraphKind.FRAGMENT)),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph("compute",
                                ShaderGraphKind.COMPUTE)),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.program("program")),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures
                                .computeProgram("compute_program")),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures
                                .technique("technique")),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures
                                .computeTechnique(
                                        "compute_technique")));

        for (ShaderGraphEditorDocument source : documents) {
            ShaderGraphEditorSession session =
                    new ShaderGraphEditorSession(source);
            ShaderGraph active = session.activeGraph();
            String nodeId = active.nodes()[0].id().value();
            String renamed = nodeId + "_edited";
            session.renameNode(nodeId, renamed);

            ShaderGraphEditorDocument edited = session.document();
            assertNotEquals(source.semanticHash(),
                    edited.semanticHash(), source.type().name());
            assertTrue(session.activeGraph().node(
                    ShaderGraphId.of(renamed)) != null);
            ShaderGraphEditorLoadResult loaded =
                    ShaderGraphEditorPersistence.read(
                            ShaderGraphEditorPersistence.write(
                                    edited, session.layout()));
            ShaderGraphEditorDocument decoded = loaded.document();
            assertEquals(edited, decoded, source.type().name());
            assertEquals(source.type(), decoded.type());
            assertEquals(session.layout(), loaded.layout());
        }
    }

    @Test
    void typedProgramInspectorEditsPreserveStageGraphs() {
        ShaderGraphProgram source =
                ShaderGraphEditorFixtures.program("linkage");
        ShaderGraphEditorDocument edited =
                ShaderGraphDocumentEdits.programLinkage(
                        ShaderGraphEditorDocument.of(source),
                        "customVertex", "customFragment", 5, 7);

        assertEquals("customVertex",
                edited.program().vertexEntryPoint());
        assertEquals("customFragment",
                edited.program().fragmentEntryPoint());
        assertEquals(5, edited.program().materialGroup());
        assertEquals(7, edited.program().materialBinding());
        assertEquals(source.vertex(), edited.program().vertex());
        assertEquals(source.fragment(), edited.program().fragment());

        ShaderGraphEditorDocument compute =
                ShaderGraphDocumentEdits.computeEntryPoint(
                        ShaderGraphEditorDocument.of(
                                ShaderGraphEditorFixtures
                                        .computeProgram("entry")),
                        "dispatchMain", 8, 4, 2);
        assertEquals("dispatchMain",
                compute.computeProgram().entryPoint());
        assertEquals(8, compute.computeProgram().workgroupX());
        assertEquals(4, compute.computeProgram().workgroupY());
        assertEquals(2, compute.computeProgram().workgroupZ());
    }

    private static ShaderGraphCacheContext context(
            ShaderGraphEditorDocument document,
            ShaderProfile profile) {
        return ShaderGraphCacheContext.wgpu(
                options(document, profile));
    }

    private static ShaderGraphCompileOptions options(
            ShaderGraphEditorDocument document,
            ShaderProfile profile) {
        return ShaderGraphCompileOptions.builder()
                .profile(profile)
                .library(ShaderGraphLibrary.of(
                        document.graphs()))
                .build();
    }
}
