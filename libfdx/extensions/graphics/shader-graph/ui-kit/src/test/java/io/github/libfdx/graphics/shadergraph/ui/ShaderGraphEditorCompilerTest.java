package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.shader.target.ShaderArtifactEncoding;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphEditorCompilerTest {
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
    void reportsUnsupportedProviderBeforePreview() {
        ShaderGraphEditorDocument document = ShaderGraphEditorDocument.of(
                ShaderGraphEditorFixtures.computeProgram("unsupported"));
        ShaderGraphEditorCompileSettings settings =
                settings(document,
                        GraphicsCapabilities.conservativeRender());
        ShaderGraphDiagnostic[] preflight =
                ShaderGraphEditorCapabilityValidator.validate(
                        document, settings);

        assertTrue(Arrays.stream(preflight)
                .anyMatch(value -> value.code().equals(
                        "FDXE_PROFILE_UNSUPPORTED")));
        assertTrue(Arrays.stream(preflight)
                .anyMatch(value -> value.code().equals(
                        "FDXE_COMPUTE_UNSUPPORTED")));

        ShaderGraphEditorSession session =
                new ShaderGraphEditorSession(document);
        ShaderGraphEditorCompilation result =
                new DefaultShaderGraphEditorCompiler().compile(
                        session.beginCompilation(settings));
        RecordingPreview preview = new RecordingPreview();
        assertFalse(result.success());
        assertTrue(session.completeCompilation(result, preview,
                settings.previewMode()));
        assertEquals(0, preview.presentations);
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
            implements ShaderGraphEditorPreviewHost {
        int presentations;

        @Override
        public void present(ShaderGraphEditorCompilation compilation,
                ShaderGraphEditorPreviewMode mode) {
            presentations++;
        }
    }
}
