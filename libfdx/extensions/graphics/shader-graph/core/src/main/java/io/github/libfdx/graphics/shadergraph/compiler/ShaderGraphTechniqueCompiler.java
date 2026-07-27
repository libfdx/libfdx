package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledPass;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphProgramCompileResult;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechnique;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.core.FdxException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles every pass/variant in a technique before exposing any of them.
 */
public final class ShaderGraphTechniqueCompiler {
    private final ShaderGraphProgramCompiler programCompiler;

    public ShaderGraphTechniqueCompiler() {
        this(new ShaderGraphProgramCompiler());
    }

    public ShaderGraphTechniqueCompiler(
            ShaderGraphProgramCompiler programCompiler) {
        if (programCompiler == null) {
            throw new FdxException(
                    "Technique compiler requires a program compiler");
        }
        this.programCompiler = programCompiler;
    }

    public ShaderGraphTechniqueCompileResult compile(
            ShaderGraphTechnique technique,
            ShaderGraphCompileOptions options) {
        if (technique == null) {
            throw new FdxException(
                    "Shader graph technique cannot be null");
        }
        ShaderGraphCompileOptions actual = options != null ? options
                : ShaderGraphCompileOptions.builder().build();
        Map<String, ShaderGraphProgramCompileResult> deduplicated =
                new HashMap<>();
        List<ShaderGraphDiagnostic> diagnostics = new ArrayList<>();
        ShaderGraphTechniquePass[] sourcePasses = technique.passes();
        ShaderGraphCompiledPass[] passes =
                new ShaderGraphCompiledPass[sourcePasses.length];
        for (int passIndex = 0; passIndex < sourcePasses.length;
                passIndex++) {
            ShaderGraphVariant[] sourceVariants =
                    sourcePasses[passIndex].variants();
            ShaderGraphCompiledVariant[] variants =
                    new ShaderGraphCompiledVariant[sourceVariants.length];
            for (int variantIndex = 0;
                    variantIndex < sourceVariants.length; variantIndex++) {
                ShaderGraphVariant variant =
                        sourceVariants[variantIndex];
                String key = variant.program().semanticHash();
                ShaderGraphProgramCompileResult compilation =
                        deduplicated.get(key);
                if (compilation == null) {
                    compilation = programCompiler.compile(
                            variant.program(), actual);
                    deduplicated.put(key, compilation);
                    for (ShaderGraphDiagnostic diagnostic
                            : compilation.diagnostics()) {
                        diagnostics.add(diagnostic);
                    }
                }
                variants[variantIndex] =
                        new ShaderGraphCompiledVariant(variant, compilation);
            }
            passes[passIndex] = new ShaderGraphCompiledPass(
                    sourcePasses[passIndex], variants);
        }
        diagnostics.sort(null);
        return new ShaderGraphTechniqueCompileResult(technique, passes,
                diagnostics.toArray(ShaderGraphDiagnostic[]::new));
    }
}
