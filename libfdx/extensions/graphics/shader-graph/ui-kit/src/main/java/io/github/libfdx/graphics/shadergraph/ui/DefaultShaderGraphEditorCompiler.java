package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledComputePass;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledComputeVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledPass;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeCompileResult;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeProgramCompiler;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeTechniqueCompiler;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnosticSeverity;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLibrary;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphProgramCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphProgramCompiler;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompiler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Default provider-independent editor compiler. WGSL is always canonical;
 * translated target artifacts are supplied by an optional host adapter.
 */
public final class DefaultShaderGraphEditorCompiler
        implements ShaderGraphEditorCompiler {
    private final ShaderGraphCompiler graphCompiler;
    private final ShaderGraphProgramCompiler programCompiler;
    private final ShaderGraphComputeProgramCompiler computeProgramCompiler;
    private final ShaderGraphTechniqueCompiler techniqueCompiler;
    private final ShaderGraphComputeTechniqueCompiler computeTechniqueCompiler;
    private final ShaderGraphEditorArtifactCompiler artifactCompiler;

    public DefaultShaderGraphEditorCompiler() {
        this(null);
    }

    public DefaultShaderGraphEditorCompiler(
            ShaderGraphEditorArtifactCompiler artifactCompiler) {
        graphCompiler = new ShaderGraphCompiler();
        programCompiler = new ShaderGraphProgramCompiler();
        computeProgramCompiler = new ShaderGraphComputeProgramCompiler();
        techniqueCompiler = new ShaderGraphTechniqueCompiler(programCompiler);
        computeTechniqueCompiler =
                new ShaderGraphComputeTechniqueCompiler(computeProgramCompiler);
        this.artifactCompiler = artifactCompiler;
    }

    @Override
    public ShaderGraphEditorCompilation compile(
            ShaderGraphEditorCompileRequest request) {
        if (request == null || request.document() == null
                || request.settings() == null) {
            throw new IllegalArgumentException(
                    "Shader graph editor compile request is incomplete");
        }
        ShaderGraphEditorDocument document = request.document();
        List<ShaderGraphDiagnostic> diagnostics = new ArrayList<>();
        addUnique(diagnostics, ShaderGraphEditorCapabilityValidator.validate(
                document, request.settings()));
        String wgsl = "";
        try {
            ShaderGraphCompileOptions options = options(request);
            Compilation canonical = compileCanonical(document, options);
            wgsl = canonical.wgsl;
            addUnique(diagnostics, canonical.diagnostics);
        } catch (RuntimeException failure) {
            addFailure(diagnostics, document, "FDXE_COMPILE_FAILURE",
                    "Shader graph compilation failed: " + message(failure));
        }

        ShaderGraphEditorArtifact[] artifacts =
                new ShaderGraphEditorArtifact[0];
        if (!wgsl.isEmpty() && !hasErrors(diagnostics)
                && artifactCompiler != null) {
            try {
                ShaderGraphEditorArtifact[] translated =
                        artifactCompiler.compile(request, wgsl);
                artifacts = translated != null ? translated
                        : new ShaderGraphEditorArtifact[0];
                for (ShaderGraphEditorArtifact artifact : artifacts) {
                    if (artifact == null) {
                        throw new IllegalStateException(
                                "Target compiler returned a null artifact");
                    }
                }
            } catch (RuntimeException failure) {
                artifacts = new ShaderGraphEditorArtifact[0];
                addFailure(diagnostics, document,
                        "FDXE_TARGET_COMPILE_FAILURE",
                        "Target artifact compilation failed: "
                                + message(failure));
            }
        }
        return new ShaderGraphEditorCompilation(request.generation(),
                request.semanticRevision(), document.semanticHash(), wgsl,
                diagnostics.toArray(ShaderGraphDiagnostic[]::new), artifacts);
    }

    private Compilation compileCanonical(ShaderGraphEditorDocument document,
            ShaderGraphCompileOptions options) {
        return switch (document.type()) {
            case GRAPH -> compileGraph(document.graph(), options);
            case PROGRAM -> {
                ShaderGraphProgramCompileResult result =
                        programCompiler.compile(document.program(), options);
                yield new Compilation(result.wgsl(), result.diagnostics());
            }
            case COMPUTE_PROGRAM -> {
                ShaderGraphComputeCompileResult result =
                        computeProgramCompiler.compile(
                                document.computeProgram(), options);
                yield new Compilation(result.wgsl(), result.diagnostics());
            }
            case TECHNIQUE -> compileTechnique(
                    techniqueCompiler.compile(document.technique(), options));
            case COMPUTE_TECHNIQUE -> compileTechnique(
                    computeTechniqueCompiler.compile(
                            document.computeTechnique(), options));
        };
    }

    private Compilation compileGraph(ShaderGraph graph,
            ShaderGraphCompileOptions options) {
        if (graph.kind() == ShaderGraphKind.COMPUTE) {
            ShaderGraphComputeProgram program = ShaderGraphComputeProgram
                    .builder(graph.id().value() + ".editor-preview", graph)
                    .entryPoint("computeMain")
                    .workgroupSize(1, 1, 1)
                    .build();
            ShaderGraphComputeCompileResult result =
                    computeProgramCompiler.compile(program, options);
            return new Compilation(result.wgsl(), result.diagnostics());
        }
        ShaderGraphCompileResult result = graphCompiler.compile(graph, options);
        return new Compilation(result.wgsl(), result.diagnostics());
    }

    private static Compilation compileTechnique(
            ShaderGraphTechniqueCompileResult result) {
        StringBuilder source = new StringBuilder();
        Set<String> emitted = new HashSet<>();
        for (ShaderGraphCompiledPass pass : result.passes()) {
            for (ShaderGraphCompiledVariant variant : pass.variants()) {
                ShaderGraphProgramCompileResult compilation =
                        variant.compilation();
                if (!compilation.wgsl().isEmpty()
                        && emitted.add(compilation.semanticHash())) {
                    source.append("// pass ")
                            .append(pass.pass().passId())
                            .append(", variant ")
                            .append(displayKey(variant.variant().key()))
                            .append('\n')
                            .append(compilation.wgsl()).append('\n');
                }
            }
        }
        return new Compilation(source.toString(), result.diagnostics());
    }

    private static Compilation compileTechnique(
            ShaderGraphComputeTechniqueCompileResult result) {
        StringBuilder source = new StringBuilder();
        Set<String> emitted = new HashSet<>();
        for (ShaderGraphCompiledComputePass pass : result.passes()) {
            for (ShaderGraphCompiledComputeVariant variant
                    : pass.variants()) {
                ShaderGraphComputeCompileResult compilation =
                        variant.compilation();
                if (!compilation.wgsl().isEmpty()
                        && emitted.add(compilation.semanticHash())) {
                    source.append("// compute pass ")
                            .append(pass.pass().passId())
                            .append(", variant ")
                            .append(displayKey(variant.variant().key()))
                            .append('\n')
                            .append(compilation.wgsl()).append('\n');
                }
            }
        }
        return new Compilation(source.toString(), result.diagnostics());
    }

    private static ShaderGraphCompileOptions options(
            ShaderGraphEditorCompileRequest request) {
        return ShaderGraphCompileOptions.builder()
                .profile(request.settings().profile())
                .capabilities(request.settings().capabilities())
                .library(ShaderGraphLibrary.of(request.document().graphs()))
                .build();
    }

    private static void addUnique(List<ShaderGraphDiagnostic> destination,
            ShaderGraphDiagnostic[] source) {
        for (ShaderGraphDiagnostic diagnostic : source) {
            if (!destination.contains(diagnostic)) {
                destination.add(diagnostic);
            }
        }
    }

    private static void addFailure(List<ShaderGraphDiagnostic> diagnostics,
            ShaderGraphEditorDocument document, String code, String message) {
        diagnostics.add(new ShaderGraphDiagnostic(
                ShaderGraphDiagnosticSeverity.ERROR, code, message,
                document.graphs()[0].id(), null, null));
    }

    private static boolean hasErrors(
            List<ShaderGraphDiagnostic> diagnostics) {
        for (ShaderGraphDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity()
                    == ShaderGraphDiagnosticSeverity.ERROR) {
                return true;
            }
        }
        return false;
    }

    private static String displayKey(String value) {
        return value == null || value.isEmpty() ? "<default>" : value;
    }

    private static String message(Throwable failure) {
        return failure.getMessage() != null
                ? failure.getMessage() : failure.getClass().getSimpleName();
    }

    private record Compilation(String wgsl,
            ShaderGraphDiagnostic[] diagnostics) {
    }
}
