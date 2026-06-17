package io.github.libfdx.tools.shader;

import java.nio.file.Path;

/**
 * Represents a shader generation report.
 *
 * @author xpenatan
 */
public final class FdxShaderGenerationReport {
    private final Path source;
    private final FdxShaderCompilerResult[] results;

    private FdxShaderGenerationReport(Path source, FdxShaderCompilerResult[] results) {
        this.source = source;
        this.results = results != null ? results.clone() : new FdxShaderCompilerResult[0];
    }

    public static FdxShaderGenerationReport of(Path source, FdxShaderCompilerResult[] results) {
        return new FdxShaderGenerationReport(source, results);
    }

    public Path source() {
        return source;
    }

    public FdxShaderCompilerResult[] results() {
        return results.clone();
    }

    public boolean success() {
        for (FdxShaderCompilerResult result : results) {
            if (!result.success()) {
                return false;
            }
        }
        return true;
    }
}
