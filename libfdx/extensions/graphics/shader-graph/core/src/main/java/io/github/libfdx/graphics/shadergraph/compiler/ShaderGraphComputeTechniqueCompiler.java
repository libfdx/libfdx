package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledComputePass;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledComputeVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeCompileResult;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechnique;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.core.FdxException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles every pass/variant before exposing a compute technique.
 */
public final class ShaderGraphComputeTechniqueCompiler {
    private final ShaderGraphComputeProgramCompiler programCompiler;

    public ShaderGraphComputeTechniqueCompiler() {
        this(new ShaderGraphComputeProgramCompiler());
    }

    public ShaderGraphComputeTechniqueCompiler(
            ShaderGraphComputeProgramCompiler programCompiler) {
        if (programCompiler == null) {
            throw new FdxException(
                    "Compute technique compiler requires a program compiler");
        }
        this.programCompiler = programCompiler;
    }

    public ShaderGraphComputeTechniqueCompileResult compile(
            ShaderGraphComputeTechnique technique,
            ShaderGraphCompileOptions options) {
        if (technique == null) {
            throw new FdxException(
                    "Shader graph compute technique cannot be null");
        }
        ShaderGraphCompileOptions actual = options != null ? options
                : ShaderGraphCompileOptions.builder().build();
        Map<String, ShaderGraphComputeCompileResult> deduplicated =
                new HashMap<>();
        List<ShaderGraphDiagnostic> diagnostics = new ArrayList<>();
        ShaderGraphComputeTechniquePass[] source =
                technique.passes();
        ShaderGraphCompiledComputePass[] passes =
                new ShaderGraphCompiledComputePass[source.length];
        for (int passIndex = 0; passIndex < source.length;
                passIndex++) {
            ShaderGraphComputeVariant[] sourceVariants =
                    source[passIndex].variants();
            ShaderGraphCompiledComputeVariant[] variants =
                    new ShaderGraphCompiledComputeVariant[
                            sourceVariants.length];
            for (int variantIndex = 0;
                    variantIndex < sourceVariants.length;
                    variantIndex++) {
                ShaderGraphComputeVariant variant =
                        sourceVariants[variantIndex];
                String key = variant.program().semanticHash();
                ShaderGraphComputeCompileResult compilation =
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
                        new ShaderGraphCompiledComputeVariant(
                                variant, compilation);
            }
            passes[passIndex] = new ShaderGraphCompiledComputePass(
                    source[passIndex], variants);
        }
        diagnostics.sort(null);
        return new ShaderGraphComputeTechniqueCompileResult(
                technique, passes,
                diagnostics.toArray(ShaderGraphDiagnostic[]::new));
    }
}
