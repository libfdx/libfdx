package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.shader.target.ShaderArtifactEncoding;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.ui.UiCustomContext;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphEditorCompilerTest {
    @Test
    void disconnectedBranchSurvivesSavingAndIsValidatedWhenWiredToAlpha() {
        var graph = ShaderGraphEditorFixtures.graph("scratch", ShaderGraphKind.SURFACE);
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(graph));
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        try {
            String original = editor.compileNow().canonicalWgsl();
            assertFalse(original.isEmpty());
            var palette = ShaderGraphEditorPalette.standard().templates();
            var add = Arrays.stream(palette).filter(t -> t.label().equals("Add")).findFirst().orElseThrow();
            var time = Arrays.stream(palette).filter(t -> t.label().equals("Time")).findFirst().orElseThrow();
            var sum = add.create("scratch_add");
            session.addNode(sum, 100, 100);
            assertEquals(original, editor.compileNow().canonicalWgsl());

            var clock = time.create("scratch_time");
            session.addNode(clock, 20, 20);
            var clockOutput = ShaderEndpoint.of("scratch_time", clock.outputs()[0].id().value());
            session.connect(clockOutput, ShaderEndpoint.of("scratch_add", sum.inputs()[0].id().value()));
            var unused = editor.compileNow();
            assertTrue(unused.success(), diagnostics(unused));
            assertEquals(original, unused.canonicalWgsl());
            assertFalse(session.activeGraph().usesTime());
            var saved = ShaderGraphEditorPersistence.write(session.document(), session.layout());
            var loaded = ShaderGraphEditorPersistence.read(saved);
            assertEquals(session.activeGraph(), loaded.document().graph());

            session.bindMaterialOutput(MaterialOutputChannel.ALPHA,
                    ShaderEndpoint.of("scratch_add", sum.outputs()[0].id().value()));
            var broken = editor.compileNow();
            assertFalse(broken.success());
            assertTrue(Arrays.stream(broken.diagnostics()).anyMatch(d -> d.code().equals("FDXG_INPUT_MISSING")
                    && sum.id().equals(d.nodeId()) && sum.inputs()[1].id().equals(d.portId())));
            session.connect(clockOutput, ShaderEndpoint.of("scratch_add", sum.inputs()[1].id().value()));
            var fixed = editor.compileNow();
            assertTrue(fixed.success(), diagnostics(fixed));
            assertTrue(fixed.canonicalWgsl().contains("fdx_time_seconds"));
            assertTrue(session.activeGraph().usesTime());
        } finally {
            editor.dispose();
        }
    }

    @Test
    void livePreviewCompilesSemanticEditsOnceAndKeepsTheLastWorkingSurface() {
        var session = new ShaderGraphEditorSession(
                ShaderGraphEditorDocument.of(ShaderGraphEditorFixtures.editableFunction("live")));
        var preview = new RecordingPreview();
        var enabled = settings(session.document(), ShaderGraphEditorFixtures.capabilities());
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler(),
                ShaderGraphEditorPalette.standard(), preview, enabled);
        try {
            assertTrue(editor.updatePreview());
            var first = session.lastGoodCompilation();
            assertEquals(1, preview.presentations);
            session.moveNode("sum", 50, 80);
            assertTrue(editor.updatePreview());
            assertSame(first, session.latestCompilation());
            session.removeNodes("a");
            assertTrue(editor.updatePreview());
            var failed = session.latestCompilation();
            assertFalse(failed.success());
            assertSame(first, session.lastGoodCompilation());
            assertTrue(editor.updatePreview());
            assertSame(failed, session.latestCompilation());
            assertEquals(1, preview.presentations);
            editor.settings(ShaderGraphEditorCompileSettings.builder().previewMode(ShaderGraphEditorPreviewMode.NONE).build());
            session.undo();
            assertFalse(editor.updatePreview());
            editor.settings(enabled);
            assertTrue(editor.updatePreview());
            assertEquals(2, preview.presentations);
        } finally {
            editor.dispose();
        }
    }

    @Test
    void compilesEveryEditorDocumentKindToCanonicalWgsl() {
        List<ShaderGraphEditorDocument> documents = List.of(
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph("compile_function",
                                ShaderGraphKind.FUNCTION)),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph("compile_surface",
                                ShaderGraphKind.SURFACE)),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph("compile_vertex",
                                ShaderGraphKind.VERTEX)),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph("compile_fragment",
                                ShaderGraphKind.FRAGMENT)),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.graph("compile_compute",
                                ShaderGraphKind.COMPUTE)),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.program(
                                "compile_program")),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.computeProgram(
                                "compile_compute_program")),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.technique(
                                "compile_technique")),
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures.computeTechnique(
                                "compile_compute_technique")));

        DefaultShaderGraphEditorCompiler compiler =
                new DefaultShaderGraphEditorCompiler();
        for (ShaderGraphEditorDocument document : documents) {
            ShaderGraphEditorSession session =
                    new ShaderGraphEditorSession(document);
            ShaderGraphEditorCompileSettings settings = settings(document,
                    ShaderGraphEditorFixtures.capabilities());
            ShaderGraphEditorCompilation result = compiler.compile(
                    session.beginCompilation(settings));
            assertTrue(result.success(),
                    document.type() + ": " + diagnostics(result));
            assertFalse(result.canonicalWgsl().isEmpty(),
                    document.type().name());
            assertTrue(session.completeCompilation(result));
            assertSame(result, session.lastGoodCompilation());
        }
    }

    @Test
    void rejectsStaleResultsAndKeepsLastGoodPreviewOnFailure() {
        ShaderGraphEditorSession session = new ShaderGraphEditorSession(
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures
                                .editableFunction("async")));
        ShaderGraphEditorCompileSettings settings =
                settings(session.document(),
                        ShaderGraphEditorFixtures.capabilities());
        DefaultShaderGraphEditorCompiler compiler =
                new DefaultShaderGraphEditorCompiler();
        RecordingPreview preview = new RecordingPreview();

        ShaderGraphEditorCompileRequest older =
                session.beginCompilation(settings);
        ShaderGraphEditorCompileRequest newer =
                session.beginCompilation(settings);
        ShaderGraphEditorCompilation olderResult =
                compiler.compile(older);
        ShaderGraphEditorCompilation newerResult =
                compiler.compile(newer);

        assertFalse(session.completeCompilation(olderResult, preview,
                settings.previewMode()));
        assertEquals(0, preview.presentations);
        assertTrue(session.completeCompilation(newerResult, preview,
                settings.previewMode()));
        assertEquals(1, preview.presentations);
        assertSame(newerResult, session.lastGoodCompilation());

        session.replaceGraph("Make invalid",
                ShaderGraphEditorFixtures.invalidGraph("async"));
        ShaderGraphEditorCompilation failed = compiler.compile(
                session.beginCompilation(settings));
        assertFalse(failed.success());
        assertTrue(session.completeCompilation(failed, preview,
                settings.previewMode()));
        assertEquals(1, preview.presentations);
        assertSame(newerResult, session.lastGoodCompilation());
        assertSame(failed, session.latestCompilation());
    }

    @Test
    void renderPreviewAcceptsPortableGraphOnRenderOnlyDevice() {
        var document = ShaderGraphEditorDocument.of(ShaderGraphEditorFixtures.graph("surface", ShaderGraphKind.SURFACE));
        var session = new ShaderGraphEditorSession(document);
        var preview = new RecordingPreview();
        var view = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler(),
                ShaderGraphEditorPalette.standard(), preview, settings(document, GraphicsCapabilities.conservativeRender()));
        try {
            assertTrue(view.updatePreview());
            assertTrue(session.latestCompilation().success());
            assertEquals(1, preview.presentations);
        } finally {
            view.dispose();
        }
    }

    @Test
    void previewCapabilitiesDoNotChangeGraphCompilation() {
        ShaderGraphEditorDocument document = ShaderGraphEditorDocument.of(
                ShaderGraphEditorFixtures.computeProgram("unsupported"));
        ShaderGraphEditorCompileSettings settings = ShaderGraphEditorCompileSettings.builder()
                .profile(ShaderProfile.PORTABLE_WEBGL2)
                .capabilities(GraphicsCapabilities.conservativeRender())
                .previewMode(ShaderGraphEditorPreviewMode.COMPUTE_DATA)
                .build();
        ShaderGraphDiagnostic[] preflight =
                ShaderGraphEditorCapabilityValidator.validate(
                        document, settings);

        assertTrue(Arrays.stream(preflight)
                .anyMatch(value -> value.code().equals(
                        "FDXE_COMPUTE_UNSUPPORTED")));

        ShaderGraphEditorSession session =
                new ShaderGraphEditorSession(document);
        RecordingPreview preview = new RecordingPreview();
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler(),
                ShaderGraphEditorPalette.standard(), preview, settings);
        try {
            assertFalse(editor.updatePreview());
            var compiled = session.latestCompilation();
            assertTrue(compiled.success(), diagnostics(compiled));
            assertEquals(0, preview.presentations);
            editor.settings(settings(document, ShaderGraphEditorFixtures.capabilities()));
            assertTrue(editor.updatePreview());
            assertEquals(compiled.canonicalWgsl(), session.latestCompilation().canonicalWgsl());
            assertEquals(1, preview.presentations);
        } finally {
            editor.dispose();
        }
    }

    @Test
    void targetTranslationFailurePreservesTheCompiledGraph() {
        var document = ShaderGraphEditorDocument.of(ShaderGraphEditorFixtures.editableFunction("translation"));
        var session = new ShaderGraphEditorSession(document);
        var compiler = new DefaultShaderGraphEditorCompiler((request, source) -> {
            throw new IllegalStateException("translator unavailable");
        });
        var result = compiler.compile(session.beginCompilation(settings(document, null)));
        assertTrue(result.success());
        assertEquals(0, result.artifacts().length);
        assertTrue(Arrays.stream(result.diagnostics()).anyMatch(value -> value.code().equals("FDXE_TARGET_COMPILE_FAILURE")));
        assertTrue(session.completeCompilation(result));
        assertSame(result, session.lastGoodCompilation());
    }

    @Test
    void exposesCustomTargetArtifactsAndInspectorDiagnostics() {
        ShaderGraphEditorArtifactCompiler adapter =
                (request, canonicalWgsl) -> new ShaderGraphEditorArtifact[] {
                        ShaderGraphEditorArtifact.text(
                                request.settings().target().value(),
                                request.settings().format().id(),
                                request.settings().environment().id(),
                                "test-compiler", "module", "",
                                "// translated\n" + canonicalWgsl, true)
                };
        ShaderGraphEditorDocument document = ShaderGraphEditorDocument.of(
                ShaderGraphEditorFixtures
                        .editableFunction("artifact"));
        ShaderGraphEditorSession session =
                new ShaderGraphEditorSession(document);
        ShaderGraphEditorCompileSettings settings =
                settings(document,
                        ShaderGraphEditorFixtures.capabilities());
        ShaderGraphEditorCompilation result =
                new DefaultShaderGraphEditorCompiler(adapter).compile(
                        session.beginCompilation(settings));

        assertTrue(result.success(), diagnostics(result));
        assertEquals(1, result.artifacts().length);
        assertEquals(ShaderArtifactEncoding.TEXT,
                result.artifacts()[0].encoding());
        assertTrue(result.artifacts()[0].verified());
        assertTrue(result.artifacts()[0].text()
                .startsWith("// translated"));
        assertTrue(session.completeCompilation(result));

        ShaderGraphEditorInspectorModel inspector =
                ShaderGraphEditorInspectorModel.inspect(session, settings);
        assertTrue(Arrays.stream(inspector.sections())
                .anyMatch(section -> section.id()
                        .equals("compilation")));
        assertTrue(Arrays.stream(inspector.sections())
                .flatMap(section -> Arrays.stream(section.fields()))
                .anyMatch(field -> field.id()
                        .startsWith("artifact.")));
    }

    private static ShaderGraphEditorCompileSettings settings(
            ShaderGraphEditorDocument document,
            GraphicsCapabilities capabilities) {
        return ShaderGraphEditorCompileSettings.builder()
                .profile(ShaderProfile.PORTABLE_WEBGPU)
                .capabilities(capabilities)
                .previewMode(ShaderGraphEditorPreviewMode
                        .defaultFor(document))
                .build();
    }

    private static String diagnostics(
            ShaderGraphEditorCompilation compilation) {
        StringBuilder result = new StringBuilder();
        for (ShaderGraphDiagnostic diagnostic
                : compilation.diagnostics()) {
            result.append(diagnostic.code()).append(": ")
                    .append(diagnostic.message()).append('\n');
        }
        return result.toString();
    }

    private static final class RecordingPreview
            implements ShaderGraphEditorPreviewSurface {
        int presentations;

        @Override
        public void build(UiCustomContext context) {
        }

        @Override
        public void present(ShaderGraphEditorCompilation compilation,
                ShaderGraphEditorPreviewMode mode) {
            presentations++;
        }
    }
}
