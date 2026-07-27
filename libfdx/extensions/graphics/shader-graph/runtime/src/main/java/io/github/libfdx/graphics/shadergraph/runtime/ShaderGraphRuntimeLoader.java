package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCacheCodec;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledCacheEntry;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCacheContext;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDocumentCompilation;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDocumentCompiler;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentCodec;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentReadResult;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeVariant;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphTechniquePass;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;

/**
 * Loads one document, selects an exact cache entry set, or compiles in memory.
 */
public final class ShaderGraphRuntimeLoader {
    public static final ShaderPassId DEFAULT_COMPUTE_PASS =
            ShaderPassId.of("compute");

    private final ShaderGraphDocumentCompiler compiler =
            new ShaderGraphDocumentCompiler();

    public ShaderGraphRuntimeAsset load(String source,
            ShaderGraphCacheContext context) {
        return load(source, context,
                ShaderPassId.FORWARD, DEFAULT_COMPUTE_PASS);
    }

    public ShaderGraphRuntimeAsset load(String source,
            ShaderGraphCacheContext context,
            ShaderPassId renderProgramPass,
            ShaderPassId computeProgramPass) {
        ShaderGraphDocumentReadResult decoded =
                ShaderGraphDocumentCodec.readResult(source);
        return load(decoded.document(), context,
                renderProgramPass, computeProgramPass,
                decoded.cacheRejections());
    }

    public ShaderGraphRuntimeAsset load(ShaderGraphDocument document,
            ShaderGraphCacheContext context) {
        return load(document, context,
                ShaderPassId.FORWARD, DEFAULT_COMPUTE_PASS,
                new ShaderGraphCompiledCacheCodec.Rejection[0]);
    }

    private ShaderGraphRuntimeAsset load(ShaderGraphDocument document,
            ShaderGraphCacheContext context,
            ShaderPassId renderProgramPass,
            ShaderPassId computeProgramPass,
            ShaderGraphCompiledCacheCodec.Rejection[] rejections) {
        if (renderProgramPass == null || computeProgramPass == null) {
            throw new FdxException(
                    "Shader graph runtime program pass cannot be null");
        }
        ShaderGraphDocumentCompilation compilation =
                compiler.compile(document, context);
        requireSuccessful(compilation);
        return switch (document.kind()) {
            case GRAPH -> new ShaderGraphRuntimeAsset(
                    compilation, rejections,
                    graph(compilation), null, null);
            case PROGRAM -> new ShaderGraphRuntimeAsset(
                    compilation, rejections, null,
                    renderProgram(compilation,
                            renderProgramPass, context),
                    null);
            case TECHNIQUE -> new ShaderGraphRuntimeAsset(
                    compilation, rejections, null,
                    renderTechnique(compilation, context), null);
            case COMPUTE_PROGRAM -> new ShaderGraphRuntimeAsset(
                    compilation, rejections, null, null,
                    computeProgram(compilation,
                            computeProgramPass, context));
            case COMPUTE_TECHNIQUE -> new ShaderGraphRuntimeAsset(
                    compilation, rejections, null, null,
                    computeTechnique(compilation, context));
        };
    }

    private static ShaderGraphRuntimeGraph graph(
            ShaderGraphDocumentCompilation compilation) {
        ShaderGraphCompiledCacheEntry main = requireEntry(
                compilation, ShaderGraphDocumentCompiler.GRAPH_MAIN,
                "", "");
        ShaderGraphCompiledCacheEntry library = requireEntry(
                compilation, ShaderGraphDocumentCompiler.GRAPH_LIBRARY,
                "", "");
        return new ShaderGraphRuntimeGraph(
                compilation.document().graph(),
                main.artifact().text(),
                library.artifact().text(),
                main.shaderInterface(),
                library.shaderInterface());
    }

    private static ShaderGraphRenderTechnique renderProgram(
            ShaderGraphDocumentCompilation compilation,
            ShaderPassId passId,
            ShaderGraphCacheContext context) {
        ShaderGraphProgram semantic =
                compilation.document().program();
        ShaderGraphCompiledCacheEntry entry = requireEntry(
                compilation,
                ShaderGraphDocumentCompiler.RENDER_PROGRAM,
                "", "");
        ShaderGraphRenderProgram program =
                renderProgram(semantic, passId, entry,
                        compilation.document().id());
        ShaderGraphRenderVariant variant =
                ShaderGraphRenderVariant.builder("", program)
                        .profiles(context.compileOptions().profile())
                        .compiledProfile(
                                context.compileOptions().profile())
                        .build();
        return ShaderGraphRenderTechnique.of(
                semantic.id().value(),
                ShaderGraphRenderTechniquePass.builder(passId)
                        .variants(variant)
                        .build());
    }

    private static ShaderGraphRenderTechnique renderTechnique(
            ShaderGraphDocumentCompilation compilation,
            ShaderGraphCacheContext context) {
        var semantic = compilation.document().technique();
        ShaderGraphRenderTechniquePass[] passes =
                new ShaderGraphRenderTechniquePass[
                        semantic.passes().length];
        ShaderGraphTechniquePass[] sourcePasses = semantic.passes();
        for (int passIndex = 0;
                passIndex < sourcePasses.length; passIndex++) {
            ShaderGraphTechniquePass sourcePass =
                    sourcePasses[passIndex];
            ShaderGraphVariant[] sourceVariants =
                    sourcePass.variants();
            ShaderGraphRenderVariant[] variants =
                    new ShaderGraphRenderVariant[
                            sourceVariants.length];
            for (int variantIndex = 0;
                    variantIndex < sourceVariants.length;
                    variantIndex++) {
                ShaderGraphVariant source =
                        sourceVariants[variantIndex];
                ShaderGraphCompiledCacheEntry entry = requireEntry(
                        compilation,
                        ShaderGraphDocumentCompiler.RENDER_PROGRAM,
                        sourcePass.passId().value(), source.key());
                ShaderGraphRenderProgram program = renderProgram(
                        source.program(), sourcePass.passId(),
                        entry, semantic.id() + " "
                                + sourcePass.passId() + " "
                                + display(source.key()));
                ShaderGraphRenderVariant.Builder builder =
                        ShaderGraphRenderVariant.builder(
                                        source.key(), program)
                                .profiles(source.profiles())
                                .features(source.features())
                                .compiledProfile(
                                        context.compileOptions()
                                                .profile());
                if (source.fallbackKey() != null) {
                    builder.fallback(source.fallbackKey());
                }
                variants[variantIndex] = builder.build();
            }
            passes[passIndex] =
                    ShaderGraphRenderTechniquePass.builder(
                                    sourcePass.passId())
                            .pipelineState(
                                    sourcePass.pipelineState())
                            .defaultVariant(
                                    sourcePass.defaultVariantKey())
                            .variants(variants)
                            .build();
        }
        return ShaderGraphRenderTechnique.of(
                semantic.id(), passes);
    }

    private static ShaderGraphRenderProgram renderProgram(
            ShaderGraphProgram semantic, ShaderPassId passId,
            ShaderGraphCompiledCacheEntry entry, String label) {
        ShaderModuleDescriptor descriptor =
                ShaderModuleDescriptor.wgsl(
                                label, entry.artifact().text())
                        .entryPoints(
                                semantic.vertexEntryPoint(),
                                semantic.fragmentEntryPoint());
        return ShaderGraphRenderProgram.builder(passId, descriptor)
                .label(label)
                .entryPoints(semantic.vertexEntryPoint(),
                        semantic.fragmentEntryPoint())
                .build();
    }

    private static ShaderGraphComputeRuntimeTechnique computeProgram(
            ShaderGraphDocumentCompilation compilation,
            ShaderPassId passId,
            ShaderGraphCacheContext context) {
        ShaderGraphComputeProgram semantic =
                compilation.document().computeProgram();
        ShaderGraphCompiledCacheEntry entry = requireEntry(
                compilation,
                ShaderGraphDocumentCompiler.COMPUTE_PROGRAM,
                "", "");
        ShaderGraphComputeRuntimeTechnique.Variant variant =
                computeVariant("", semantic, entry,
                        new ShaderProfile[] {
                                context.compileOptions().profile()
                        },
                        new io.github.libfdx.graphics.GraphicsFeature[0],
                        null, context.compileOptions().profile(),
                        semantic.id().value());
        return ShaderGraphComputeRuntimeTechnique.of(
                semantic.id().value(),
                ShaderGraphComputeRuntimeTechnique.Pass.of(
                        passId, "", variant));
    }

    private static ShaderGraphComputeRuntimeTechnique computeTechnique(
            ShaderGraphDocumentCompilation compilation,
            ShaderGraphCacheContext context) {
        var semantic = compilation.document().computeTechnique();
        ShaderGraphComputeTechniquePass[] sourcePasses =
                semantic.passes();
        ShaderGraphComputeRuntimeTechnique.Pass[] passes =
                new ShaderGraphComputeRuntimeTechnique.Pass[
                        sourcePasses.length];
        for (int passIndex = 0;
                passIndex < sourcePasses.length; passIndex++) {
            ShaderGraphComputeTechniquePass sourcePass =
                    sourcePasses[passIndex];
            ShaderGraphComputeVariant[] sourceVariants =
                    sourcePass.variants();
            ShaderGraphComputeRuntimeTechnique.Variant[] variants =
                    new ShaderGraphComputeRuntimeTechnique.Variant[
                            sourceVariants.length];
            for (int variantIndex = 0;
                    variantIndex < sourceVariants.length;
                    variantIndex++) {
                ShaderGraphComputeVariant source =
                        sourceVariants[variantIndex];
                ShaderGraphCompiledCacheEntry entry = requireEntry(
                        compilation,
                        ShaderGraphDocumentCompiler.COMPUTE_PROGRAM,
                        sourcePass.passId().value(), source.key());
                variants[variantIndex] = computeVariant(
                        source.key(), source.program(), entry,
                        source.profiles(), source.features(),
                        source.fallbackKey(),
                        context.compileOptions().profile(),
                        semantic.id() + " "
                                + sourcePass.passId() + " "
                                + display(source.key()));
            }
            passes[passIndex] =
                    ShaderGraphComputeRuntimeTechnique.Pass.of(
                            sourcePass.passId(),
                            sourcePass.defaultVariantKey(),
                            variants);
        }
        return ShaderGraphComputeRuntimeTechnique.of(
                semantic.id(), passes);
    }

    private static ShaderGraphComputeRuntimeTechnique.Variant
            computeVariant(String key,
                    ShaderGraphComputeProgram program,
                    ShaderGraphCompiledCacheEntry entry,
                    ShaderProfile[] profiles,
                    io.github.libfdx.graphics.GraphicsFeature[] features,
                    String fallbackKey, ShaderProfile compiledProfile,
                    String label) {
        ShaderModuleDescriptor descriptor =
                ShaderModuleDescriptor.wgsl(
                        label, entry.artifact().text());
        return ShaderGraphComputeRuntimeTechnique.Variant.of(
                key, descriptor, program.entryPoint(),
                profiles, features, fallbackKey, compiledProfile,
                program.workgroupX(), program.workgroupY(),
                program.workgroupZ());
    }

    private static ShaderGraphCompiledCacheEntry requireEntry(
            ShaderGraphDocumentCompilation compilation,
            String unit, String pass, String variant) {
        ShaderGraphCompiledCacheEntry entry =
                compilation.entry(unit, pass, variant);
        if (entry == null) {
            throw new FdxException(
                    "Shader graph runtime compilation is missing "
                            + unit + " " + pass + " "
                            + display(variant));
        }
        return entry;
    }

    private static void requireSuccessful(
            ShaderGraphDocumentCompilation compilation) {
        if (compilation.success()) {
            return;
        }
        StringBuilder message = new StringBuilder(
                "Shader graph document compilation failed");
        for (ShaderGraphDiagnostic diagnostic
                : compilation.diagnostics()) {
            message.append("\n[").append(diagnostic.code())
                    .append("] ").append(diagnostic.message());
        }
        throw new FdxException(message.toString());
    }

    private static String display(String key) {
        return key == null || key.isEmpty() ? "<default>" : key;
    }
}
