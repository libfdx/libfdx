package io.github.libfdx.tools.shader;

import io.github.libfdx.graphics.ShaderTarget;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates shader compiler outputs for a WGSL file.
 *
 * @author xpenatan
 */
public final class FdxShaderBundleGenerator {
    private final FdxShaderCompiler compiler;
    private final FdxShaderCompilerOptions options;

    public FdxShaderBundleGenerator(FdxShaderCompiler compiler, FdxShaderCompilerOptions options) {
        this.compiler = compiler;
        this.options = options != null ? options : FdxShaderCompilerOptions.defaultOptions();
    }

    public FdxShaderGenerationReport generate(Path sourcePath) throws IOException {
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        ShaderTarget[] targets = options.targets();
        FdxShaderCompilerResult[] results = new FdxShaderCompilerResult[targets.length];
        for (int i = 0; i < targets.length; i++) {
            results[i] = compiler.compile(FdxShaderCompilerRequest.builder(source, targets[i]).build());
        }
        return FdxShaderGenerationReport.of(sourcePath, results);
    }
}
