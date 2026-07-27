package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechnique;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;

/**
 * Immutable edits for program and technique inspectors.
 */
public final class ShaderGraphDocumentEdits {
    private ShaderGraphDocumentEdits() {
    }

    public static ShaderGraphEditorDocument programLinkage(ShaderGraphEditorDocument document,
            String vertexEntryPoint, String fragmentEntryPoint, int materialGroup, int materialBinding) {
        ShaderGraphProgram program = requireProgram(document);
        return ShaderGraphEditorDocument.of(ShaderGraphProgram
                .builder(program.id().value(), program.vertex(), program.fragment())
                .entryPoints(vertexEntryPoint, fragmentEntryPoint)
                .materialBinding(materialGroup, materialBinding)
                .build());
    }

    public static ShaderGraphEditorDocument computeEntryPoint(ShaderGraphEditorDocument document,
            String entryPoint, int workgroupX, int workgroupY, int workgroupZ) {
        ShaderGraphComputeProgram program = requireComputeProgram(document);
        return ShaderGraphEditorDocument.of(ShaderGraphComputeProgram
                .builder(program.id().value(), program.graph())
                .entryPoint(entryPoint)
                .workgroupSize(workgroupX, workgroupY, workgroupZ)
                .build());
    }

    public static ShaderGraphEditorDocument replacePass(ShaderGraphEditorDocument document,
            ShaderGraphTechniquePass replacement) {
        if (document == null || document.technique() == null || replacement == null) {
            throw new FdxException("Render technique pass replacement is incomplete");
        }
        ShaderGraphTechnique technique = document.technique();
        ShaderGraphTechniquePass[] passes = technique.passes();
        replace(passes, replacement);
        return ShaderGraphEditorDocument.of(ShaderGraphTechnique.builder(technique.id())
                .passes(passes).maxVariants(technique.maxVariants()).build());
    }

    public static ShaderGraphEditorDocument replacePass(ShaderGraphEditorDocument document,
            ShaderGraphComputeTechniquePass replacement) {
        if (document == null || document.computeTechnique() == null || replacement == null) {
            throw new FdxException("Compute technique pass replacement is incomplete");
        }
        ShaderGraphComputeTechnique technique = document.computeTechnique();
        ShaderGraphComputeTechniquePass[] passes = technique.passes();
        replace(passes, replacement);
        return ShaderGraphEditorDocument.of(ShaderGraphComputeTechnique.builder(technique.id())
                .passes(passes).maxVariants(technique.maxVariants()).build());
    }

    private static ShaderGraphProgram requireProgram(ShaderGraphEditorDocument document) {
        if (document == null || document.program() == null) {
            throw new FdxException("Shader graph editor document is not a render program");
        }
        return document.program();
    }

    private static ShaderGraphComputeProgram requireComputeProgram(ShaderGraphEditorDocument document) {
        if (document == null || document.computeProgram() == null) {
            throw new FdxException("Shader graph editor document is not a compute program");
        }
        return document.computeProgram();
    }

    private static void replace(ShaderGraphTechniquePass[] passes, ShaderGraphTechniquePass replacement) {
        int index = pass(passes, replacement.passId());
        if (index < 0) {
            throw new FdxException("Render technique has no pass " + replacement.passId());
        }
        passes[index] = replacement;
    }

    private static void replace(ShaderGraphComputeTechniquePass[] passes,
            ShaderGraphComputeTechniquePass replacement) {
        int index = pass(passes, replacement.passId());
        if (index < 0) {
            throw new FdxException("Compute technique has no pass " + replacement.passId());
        }
        passes[index] = replacement;
    }

    private static int pass(ShaderGraphTechniquePass[] passes, ShaderPassId id) {
        for (int i = 0; i < passes.length; i++) {
            if (passes[i].passId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static int pass(ShaderGraphComputeTechniquePass[] passes, ShaderPassId id) {
        for (int i = 0; i < passes.length; i++) {
            if (passes[i].passId().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
