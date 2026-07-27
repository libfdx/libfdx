package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnosticSeverity;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs the capability checks that must be visible before a preview host is
 * asked to create provider resources.
 */
public final class ShaderGraphEditorCapabilityValidator {
    private ShaderGraphEditorCapabilityValidator() {
    }

    public static ShaderGraphDiagnostic[] validate(
            ShaderGraphEditorDocument document,
            ShaderGraphEditorCompileSettings settings) {
        if (document == null || settings == null) {
            throw new FdxException("Shader graph editor capability validation is incomplete");
        }
        List<ShaderGraphDiagnostic> diagnostics = new ArrayList<>();
        ShaderGraph graph = firstGraph(document);
        if (!settings.previewMode().supports(document)) {
            error(diagnostics, graph, "FDXE_PREVIEW_MODE",
                    "Preview mode " + settings.previewMode()
                            + " does not support " + document.type());
        }
        GraphicsCapabilities capabilities = settings.capabilities();
        if (capabilities == null) {
            return diagnostics.toArray(ShaderGraphDiagnostic[]::new);
        }
        if (!capabilities.supports(settings.profile())) {
            error(diagnostics, graph, "FDXE_PROFILE_UNSUPPORTED",
                    "The selected provider does not support shader profile "
                            + settings.profile());
        }
        if (containsCompute(document)
                && !capabilities.supports(GraphicsFeature.COMPUTE)) {
            error(diagnostics, graph, "FDXE_COMPUTE_UNSUPPORTED",
                    "The selected provider does not support compute shaders");
        }
        if (document.technique() != null) {
            for (ShaderGraphTechniquePass pass : document.technique().passes()) {
                boolean supported = false;
                for (ShaderGraphVariant variant : pass.variants()) {
                    supported |= variant.supports(settings.profile(), capabilities);
                }
                if (!supported) {
                    error(diagnostics, graph, "FDXE_VARIANT_UNSUPPORTED",
                            "Render pass " + pass.passId()
                                    + " has no variant for the selected profile/provider");
                }
                try {
                    pass.pipelineState().targetLayout().validate(capabilities);
                } catch (RuntimeException failure) {
                    error(diagnostics, graph, "FDXE_PASS_STATE_UNSUPPORTED",
                            "Render pass " + pass.passId() + " is unsupported: "
                                    + message(failure));
                }
            }
        }
        if (document.computeTechnique() != null) {
            for (ShaderGraphComputeTechniquePass pass
                    : document.computeTechnique().passes()) {
                boolean supported = false;
                for (ShaderGraphComputeVariant variant : pass.variants()) {
                    supported |= variant.supports(settings.profile(), capabilities);
                }
                if (!supported) {
                    error(diagnostics, graph, "FDXE_VARIANT_UNSUPPORTED",
                            "Compute pass " + pass.passId()
                                    + " has no variant for the selected profile/provider");
                }
            }
        }
        diagnostics.sort(null);
        return diagnostics.toArray(ShaderGraphDiagnostic[]::new);
    }

    private static boolean containsCompute(ShaderGraphEditorDocument document) {
        if (document.computeProgram() != null || document.computeTechnique() != null) {
            return true;
        }
        for (ShaderGraph graph : document.graphs()) {
            if (graph.kind() == ShaderGraphKind.COMPUTE) {
                return true;
            }
        }
        return false;
    }

    private static ShaderGraph firstGraph(ShaderGraphEditorDocument document) {
        ShaderGraph[] graphs = document.graphs();
        if (graphs.length == 0) {
            throw new FdxException("Shader graph editor document has no graph");
        }
        return graphs[0];
    }

    private static void error(List<ShaderGraphDiagnostic> diagnostics,
            ShaderGraph graph, String code, String message) {
        diagnostics.add(new ShaderGraphDiagnostic(
                ShaderGraphDiagnosticSeverity.ERROR, code, message,
                graph.id(), null, null));
    }

    private static String message(Throwable failure) {
        return failure.getMessage() != null
                ? failure.getMessage() : failure.getClass().getSimpleName();
    }
}
