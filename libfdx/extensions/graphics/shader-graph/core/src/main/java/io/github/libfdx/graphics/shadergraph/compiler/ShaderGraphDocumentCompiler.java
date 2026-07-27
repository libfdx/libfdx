package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCacheKey;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledArtifact;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCache;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCacheEntry;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledInterface;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeVariant;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles one semantic document in memory or reuses an exact embedded cache.
 */
public final class ShaderGraphDocumentCompiler {
    public static final String GRAPH_MAIN = "graph-main";
    public static final String GRAPH_LIBRARY = "graph-library";
    public static final String RENDER_PROGRAM = "render-program";
    public static final String COMPUTE_PROGRAM = "compute-program";

    public ShaderGraphDocumentCompilation compile(
            ShaderGraphDocument document,
            ShaderGraphCacheContext context) {
        if (document == null || context == null) {
            throw new FdxException(
                    "Shader graph document compilation is incomplete");
        }
        if (!ShaderGraphCacheContext.WGSL_FORMAT.equals(
                context.artifactFormat())) {
            throw new FdxException(
                    "The built-in shader graph document compiler "
                            + "currently emits WGSL only");
        }
        Expected[] expected = expected(document, context);
        ShaderGraphCompiledCache existing =
                document.compiledCache();
        if (existing != null) {
            ShaderGraphCompiledCacheEntry[] hits =
                    new ShaderGraphCompiledCacheEntry[expected.length];
            boolean all = true;
            for (int i = 0; i < expected.length; i++) {
                ShaderGraphCompiledCache.Lookup lookup =
                        existing.lookup(expected[i].key);
                hits[i] = lookup.entry();
                if (!lookup.hit()
                        || hits[i].artifact().encoding()
                                != ShaderGraphCompiledArtifact
                                        .Encoding.TEXT) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return new ShaderGraphDocumentCompilation(
                        document, true, hits,
                        new ShaderGraphDiagnostic[0]);
            }
        }
        return compileMiss(document, context);
    }

    private ShaderGraphDocumentCompilation compileMiss(
            ShaderGraphDocument document,
            ShaderGraphCacheContext context) {
        ShaderGraphCompileOptions options =
                context.compileOptions();
        return switch (document.kind()) {
            case GRAPH -> compileGraph(document, context,
                    new ShaderGraphCompiler().compile(
                            document.graph(), options));
            case PROGRAM -> compileProgram(document, context,
                    new ShaderGraphProgramCompiler().compile(
                            document.program(), options));
            case COMPUTE_PROGRAM -> compileComputeProgram(
                    document, context,
                    new ShaderGraphComputeProgramCompiler().compile(
                            document.computeProgram(), options));
            case TECHNIQUE -> compileTechnique(document, context,
                    new ShaderGraphTechniqueCompiler().compile(
                            document.technique(), options));
            case COMPUTE_TECHNIQUE -> compileComputeTechnique(
                    document, context,
                    new ShaderGraphComputeTechniqueCompiler().compile(
                            document.computeTechnique(), options));
        };
    }

    private static ShaderGraphDocumentCompilation compileGraph(
            ShaderGraphDocument document,
            ShaderGraphCacheContext context,
            ShaderGraphCompileResult result) {
        if (!result.success()) {
            return failure(document, result.diagnostics());
        }
        ShaderGraphCompiledInterface main =
                ShaderGraphCompiledInterfaceBuilder.graph(
                        document.graph(),
                        context.interfaceAbiVersion(), true);
        ShaderGraphCompiledInterface library =
                ShaderGraphCompiledInterfaceBuilder.graph(
                        document.graph(),
                        context.interfaceAbiVersion(), false);
        return success(document,
                entry(document, context, main, GRAPH_MAIN,
                        "", "", result.wgsl()),
                entry(document, context, library, GRAPH_LIBRARY,
                        "", "", result.libraryWgsl()));
    }

    private static ShaderGraphDocumentCompilation compileProgram(
            ShaderGraphDocument document,
            ShaderGraphCacheContext context,
            ShaderGraphProgramCompileResult result) {
        if (!result.success()) {
            return failure(document, result.diagnostics());
        }
        ShaderGraphCompiledInterface shaderInterface =
                ShaderGraphCompiledInterfaceBuilder.program(
                        document.program(),
                        context.interfaceAbiVersion());
        return success(document,
                entry(document, context, shaderInterface,
                        RENDER_PROGRAM, "", "", result.wgsl()));
    }

    private static ShaderGraphDocumentCompilation compileComputeProgram(
            ShaderGraphDocument document,
            ShaderGraphCacheContext context,
            ShaderGraphComputeCompileResult result) {
        if (!result.success()) {
            return failure(document, result.diagnostics());
        }
        ShaderGraphCompiledInterface shaderInterface =
                ShaderGraphCompiledInterfaceBuilder.computeProgram(
                        document.computeProgram(),
                        context.interfaceAbiVersion());
        return success(document,
                entry(document, context, shaderInterface,
                        COMPUTE_PROGRAM, "", "", result.wgsl()));
    }

    private static ShaderGraphDocumentCompilation compileTechnique(
            ShaderGraphDocument document,
            ShaderGraphCacheContext context,
            ShaderGraphTechniqueCompileResult result) {
        if (!result.success()) {
            return failure(document, result.diagnostics());
        }
        List<ShaderGraphCompiledCacheEntry> entries =
                new ArrayList<ShaderGraphCompiledCacheEntry>();
        for (ShaderGraphCompiledPass pass : result.passes()) {
            for (ShaderGraphCompiledVariant variant
                    : pass.variants()) {
                ShaderGraphCompiledInterface shaderInterface =
                        ShaderGraphCompiledInterfaceBuilder.program(
                                variant.variant().program(),
                                context.interfaceAbiVersion());
                entries.add(entry(document, context,
                        shaderInterface, RENDER_PROGRAM,
                        pass.pass().passId().value(),
                        variant.variant().key(),
                        variant.compilation().wgsl()));
            }
        }
        return success(document, entries.toArray(
                ShaderGraphCompiledCacheEntry[]::new));
    }

    private static ShaderGraphDocumentCompilation compileComputeTechnique(
            ShaderGraphDocument document,
            ShaderGraphCacheContext context,
            ShaderGraphComputeTechniqueCompileResult result) {
        if (!result.success()) {
            return failure(document, result.diagnostics());
        }
        List<ShaderGraphCompiledCacheEntry> entries =
                new ArrayList<ShaderGraphCompiledCacheEntry>();
        for (ShaderGraphCompiledComputePass pass : result.passes()) {
            for (ShaderGraphCompiledComputeVariant variant
                    : pass.variants()) {
                ShaderGraphCompiledInterface shaderInterface =
                        ShaderGraphCompiledInterfaceBuilder
                                .computeProgram(
                                        variant.variant().program(),
                                        context.interfaceAbiVersion());
                entries.add(entry(document, context,
                        shaderInterface, COMPUTE_PROGRAM,
                        pass.pass().passId().value(),
                        variant.variant().key(),
                        variant.compilation().wgsl()));
            }
        }
        return success(document, entries.toArray(
                ShaderGraphCompiledCacheEntry[]::new));
    }

    private static Expected[] expected(ShaderGraphDocument document,
            ShaderGraphCacheContext context) {
        List<Expected> values = new ArrayList<Expected>();
        switch (document.kind()) {
            case GRAPH -> {
                values.add(expected(document, context,
                        ShaderGraphCompiledInterfaceBuilder
                                .graphEntryPoints(
                                        document.graph(), true),
                        GRAPH_MAIN, "", ""));
                values.add(expected(document, context,
                        ShaderGraphCompiledInterfaceBuilder
                                .graphEntryPoints(
                                        document.graph(), false),
                        GRAPH_LIBRARY, "", ""));
            }
            case PROGRAM -> values.add(expected(document, context,
                    ShaderGraphCompiledInterfaceBuilder
                            .programEntryPoints(document.program()),
                    RENDER_PROGRAM, "", ""));
            case COMPUTE_PROGRAM -> values.add(expected(
                    document, context,
                    ShaderGraphCompiledInterfaceBuilder
                            .computeProgramEntryPoints(
                                    document.computeProgram()),
                    COMPUTE_PROGRAM, "", ""));
            case TECHNIQUE -> {
                for (ShaderGraphTechniquePass pass
                        : document.technique().passes()) {
                    for (ShaderGraphVariant variant
                            : pass.variants()) {
                        values.add(expected(document, context,
                                ShaderGraphCompiledInterfaceBuilder
                                        .programEntryPoints(
                                                variant.program()),
                                RENDER_PROGRAM,
                                pass.passId().value(),
                                variant.key()));
                    }
                }
            }
            case COMPUTE_TECHNIQUE -> {
                for (ShaderGraphComputeTechniquePass pass
                        : document.computeTechnique().passes()) {
                    for (ShaderGraphComputeVariant variant
                            : pass.variants()) {
                        values.add(expected(document, context,
                                ShaderGraphCompiledInterfaceBuilder
                                        .computeProgramEntryPoints(
                                                variant.program()),
                                COMPUTE_PROGRAM,
                                pass.passId().value(),
                                variant.key()));
                    }
                }
            }
        }
        return values.toArray(Expected[]::new);
    }

    private static Expected expected(ShaderGraphDocument document,
            ShaderGraphCacheContext context,
            ShaderGraphCompiledInterface.EntryPoint[] entryPoints,
            String unit, String pass, String variant) {
        return new Expected(context.key(document,
                ShaderGraphCompiledInterface.entryPointsHash(entryPoints),
                unit, pass, variant));
    }

    private static ShaderGraphCompiledCacheEntry entry(
            ShaderGraphDocument document,
            ShaderGraphCacheContext context,
            ShaderGraphCompiledInterface shaderInterface,
            String unit, String pass, String variant,
            String source) {
        return ShaderGraphCompiledCacheEntry.of(
                context.key(document, shaderInterface,
                        unit, pass, variant),
                ShaderGraphCompiledArtifact.text(
                        context.artifactFormat(), source),
                shaderInterface);
    }

    private static ShaderGraphDocumentCompilation success(
            ShaderGraphDocument document,
            ShaderGraphCompiledCacheEntry... entries) {
        return new ShaderGraphDocumentCompilation(document,
                false, entries, new ShaderGraphDiagnostic[0]);
    }

    private static ShaderGraphDocumentCompilation failure(
            ShaderGraphDocument document,
            ShaderGraphDiagnostic[] diagnostics) {
        return new ShaderGraphDocumentCompilation(document,
                false, new ShaderGraphCompiledCacheEntry[0],
                diagnostics);
    }

    private record Expected(ShaderGraphCacheKey key) {
    }
}
