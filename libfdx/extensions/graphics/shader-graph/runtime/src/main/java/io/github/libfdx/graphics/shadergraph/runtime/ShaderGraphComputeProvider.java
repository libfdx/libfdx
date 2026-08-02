package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ComputePipeline;
import io.github.libfdx.graphics.ComputePipelineDescriptor;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledComputePass;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledComputeVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;

import java.util.Arrays;

/**
 * Owned runtime for explicitly scheduled graph compute techniques.
 *
 * <p>This class is separate from the render-facing {@link ShaderGraphProvider}
 * because compute has no SpriteBatch/ModelBatch draw lifecycle. Both consume
 * the same headless graph/compiler contracts and neither depends on UI Kit.</p>
 */
public final class ShaderGraphComputeProvider implements Disposable {
    private final GraphicsContext graphics;
    private TechniqueState state;
    private long revision = 1;
    private boolean disposed;

    public ShaderGraphComputeProvider(GraphicsContext graphics,
            ShaderGraphComputeTechniqueCompileResult technique) {
        if (graphics == null) {
            throw new FdxException(
                    "Graph compute provider requires graphics");
        }
        this.graphics = graphics;
        graphics.device().capabilities().require(GraphicsFeature.COMPUTE);
        state = build(requireSuccessful(technique));
    }

    public ShaderGraphComputeProvider(GraphicsContext graphics,
            ShaderGraphComputeRuntimeTechnique technique) {
        if (graphics == null || technique == null) {
            throw new FdxException(
                    "Graph compute provider requires graphics "
                            + "and a runtime technique");
        }
        this.graphics = graphics;
        graphics.device().capabilities().require(
                GraphicsFeature.COMPUTE);
        state = build(technique);
    }

    public ShaderGraphComputeProvider(GraphicsContext graphics,
            ShaderGraphRuntimeAsset asset) {
        this(graphics, requireCompute(asset));
    }

    /**
     * Resolves one pass and variant. Unsupported variants use only their
     * explicitly declared fallback chain.
     */
    public synchronized ShaderGraphResolvedComputePass resolve(
            ShaderPassId passId, String variantKey,
            ShaderProfile profile) {
        requireActive();
        if (passId == null || profile == null) {
            throw new FdxException(
                    "Compute pass resolution requires pass and profile");
        }
        graphics.device().capabilities().require(profile);
        RuntimePass pass = state.pass(passId);
        if (pass == null) {
            throw new FdxException(
                    "Compute technique does not define pass " + passId);
        }
        String requested = variantKey == null
                || variantKey.trim().isEmpty()
                        ? pass.defaultVariant : variantKey.trim();
        RuntimeVariant variant = pass.variant(requested);
        if (variant == null) {
            throw new FdxException("Compute pass " + passId
                    + " does not define variant " + display(requested));
        }
        for (int depth = 0; depth <= pass.variants.length; depth++) {
            if (variant.supports(profile,
                    graphics.device().capabilities())) {
                return variant.resolved(pass.passId, revision);
            }
            String fallback = variant.fallbackKey;
            if (fallback == null) {
                throw new FdxException("Compute variant "
                        + display(variant.key)
                        + " is unsupported and has no fallback");
            }
            variant = pass.variant(fallback);
            if (variant == null) {
                throw new FdxException(
                        "Compiled compute fallback is missing: "
                                + display(fallback));
            }
        }
        throw new FdxException(
                "Compute variant fallback cycle reached runtime");
    }

    /**
     * Builds every replacement module and pipeline before one revision swap.
     */
    public synchronized void replace(
            ShaderGraphComputeTechniqueCompileResult technique) {
        requireActive();
        TechniqueState replacement =
                build(requireSuccessful(technique));
        TechniqueState previous = state;
        state = replacement;
        revision++;
        previous.dispose();
    }

    public synchronized void replace(
            ShaderGraphComputeRuntimeTechnique technique) {
        requireActive();
        TechniqueState replacement = build(technique);
        TechniqueState previous = state;
        state = replacement;
        revision++;
        previous.dispose();
    }

    public synchronized void replace(ShaderGraphRuntimeAsset asset) {
        replace(requireCompute(asset));
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized int passCount() {
        return state.passes.length;
    }

    public synchronized int pipelineCount() {
        return state.pipelines.length;
    }

    private TechniqueState build(
            ShaderGraphComputeTechniqueCompileResult technique) {
        Array<ModuleEntry> modules = new Array<ModuleEntry>();
        Array<PipelineEntry> pipelines = new Array<PipelineEntry>();
        try {
            ShaderGraphCompiledComputePass[] compiledPasses =
                    technique.passes();
            RuntimePass[] passes =
                    new RuntimePass[compiledPasses.length];
            for (int passIndex = 0; passIndex < passes.length;
                    passIndex++) {
                ShaderGraphCompiledComputeVariant[] compiledVariants =
                        compiledPasses[passIndex].variants();
                RuntimeVariant[] variants =
                        new RuntimeVariant[compiledVariants.length];
                for (int variantIndex = 0;
                        variantIndex < variants.length; variantIndex++) {
                    ShaderGraphCompiledComputeVariant compiled =
                            compiledVariants[variantIndex];
                    compiled.variant().program().validate(
                            graphics.device().capabilities());
                    ModuleEntry module = module(modules,
                            technique.technique().id() + " "
                                    + compiledPasses[passIndex].pass()
                                            .passId()
                                    + " "
                                    + display(compiled.variant().key()),
                            compiled.compilation().wgsl());
                    PipelineEntry pipeline = pipeline(pipelines, module,
                            compiled.compilation().entryPoint(),
                            compiled.variant().program().semanticHash());
                    variants[variantIndex] =
                            new RuntimeVariant(
                                    compiled.variant().key(),
                                    compiled.variant().profiles(),
                                    compiled.variant().features(),
                                    compiled.variant().fallbackKey(),
                                    null,
                                    compiled.variant().program()
                                            .workgroupX(),
                                    compiled.variant().program()
                                            .workgroupY(),
                                    compiled.variant().program()
                                            .workgroupZ(),
                                    pipeline);
                }
                passes[passIndex] = new RuntimePass(
                        compiledPasses[passIndex].pass().passId(),
                        compiledPasses[passIndex].pass()
                                .defaultVariantKey(),
                        variants);
            }
            return new TechniqueState(passes,
                    modules.toArray(new ModuleEntry[0]),
                    pipelines.toArray(new PipelineEntry[0]));
        } catch (RuntimeException | Error failure) {
            disposePipelines(pipelines);
            disposeModules(modules);
            throw failure;
        }
    }

    private TechniqueState build(
            ShaderGraphComputeRuntimeTechnique technique) {
        if (technique == null) {
            throw new FdxException(
                    "Graph compute runtime technique cannot be null");
        }
        Array<ModuleEntry> modules = new Array<ModuleEntry>();
        Array<PipelineEntry> pipelines = new Array<PipelineEntry>();
        try {
            ShaderGraphComputeRuntimeTechnique.Pass[] sourcePasses =
                    technique.passes();
            RuntimePass[] passes =
                    new RuntimePass[sourcePasses.length];
            for (int passIndex = 0;
                    passIndex < passes.length; passIndex++) {
                ShaderGraphComputeRuntimeTechnique.Pass sourcePass =
                        sourcePasses[passIndex];
                ShaderGraphComputeRuntimeTechnique.Variant[]
                        sourceVariants = sourcePass.variants();
                RuntimeVariant[] variants =
                        new RuntimeVariant[sourceVariants.length];
                for (int variantIndex = 0;
                        variantIndex < variants.length;
                        variantIndex++) {
                    ShaderGraphComputeRuntimeTechnique.Variant source =
                            sourceVariants[variantIndex];
                    validateWorkgroup(source.workgroupX(),
                            source.workgroupY(), source.workgroupZ());
                    String label = technique.id() + " "
                            + sourcePass.passId() + " "
                            + display(source.key());
                    ModuleEntry module = module(modules, label,
                            source.shader());
                    PipelineEntry pipeline = pipeline(pipelines,
                            module, source.entryPoint(),
                            source.shader().source() + '\0'
                                    + source.entryPoint());
                    variants[variantIndex] = new RuntimeVariant(
                            source.key(), source.profiles(),
                            source.features(), source.fallbackKey(),
                            source.compiledProfile(),
                            source.workgroupX(), source.workgroupY(),
                            source.workgroupZ(), pipeline);
                }
                passes[passIndex] = new RuntimePass(
                        sourcePass.passId(),
                        sourcePass.defaultVariantKey(), variants);
            }
            return new TechniqueState(passes,
                    modules.toArray(new ModuleEntry[0]),
                    pipelines.toArray(new PipelineEntry[0]));
        } catch (RuntimeException | Error failure) {
            disposePipelines(pipelines);
            disposeModules(modules);
            throw failure;
        }
    }

    private ModuleEntry module(Array<ModuleEntry> modules, String label,
            String source) {
        return module(modules, label,
                ShaderModuleDescriptor.wgsl(label, source));
    }

    private ModuleEntry module(Array<ModuleEntry> modules,
            String label, ShaderModuleDescriptor descriptor) {
        String source = descriptor.wgslSource();
        String moduleKey = source
                + (descriptor.targetArtifact() != null
                        ? '\0' + descriptor.targetArtifact()
                                .cacheKey()
                        : "");
        for (int i = 0; i < modules.size(); i++) {
            ModuleEntry module = modules.get(i);
            if (module.key.equals(moduleKey)) {
                return module;
            }
        }
        ShaderModule shader = graphics.device().createShaderModule(
                descriptor.label(label));
        ShaderReflection moduleReflection = shader.reflection();
        ShaderReflection descriptorReflection =
                descriptor.reflection();
        if (moduleReflection.complete()
                && descriptorReflection.complete()
                && !moduleReflection.physicallyEquivalent(
                        descriptorReflection)) {
            shader.dispose();
            throw new FdxException(
                    "Graph compute module reflection differs from its verified descriptor");
        }
        ShaderReflection reflection = moduleReflection.complete()
                ? moduleReflection : descriptorReflection;
        if (!reflection.complete()) {
            shader.dispose();
            throw new FdxException(
                    "Graph compute runtime requires complete reflection");
        }
        ModuleEntry result = new ModuleEntry(moduleKey, shader,
                reflection);
        modules.add(result);
        return result;
    }

    private void validateWorkgroup(int x, int y, int z) {
        if (x <= 0 || y <= 0 || z <= 0) {
            throw new FdxException(
                    "Packaged compute workgroup dimensions must be positive");
        }
        GraphicsLimits limits =
                graphics.device().capabilities().limits();
        if (x > limits.maxComputeWorkgroupSizeX()
                || y > limits.maxComputeWorkgroupSizeY()
                || z > limits.maxComputeWorkgroupSizeZ()
                || (long) x * y * z
                        > limits.maxComputeInvocationsPerWorkgroup()) {
            throw new FdxException(
                    "Packaged compute workgroup size exceeds provider limits");
        }
    }

    private PipelineEntry pipeline(Array<PipelineEntry> pipelines,
            ModuleEntry module, String entryPoint, String key) {
        String physicalKey = key + '\0' + entryPoint;
        for (int i = 0; i < pipelines.size(); i++) {
            PipelineEntry pipeline = pipelines.get(i);
            if (pipeline.key.equals(physicalKey)) {
                return pipeline;
            }
        }
        ShaderResourceLayout layout = ShaderResourceLayout.compute(
                module.reflection, entryPoint);
        layout.validate(graphics.device().capabilities());
        ComputePipeline pipeline = graphics.device().createComputePipeline(
                ComputePipelineDescriptor.shader(module.shader)
                        .label("graph compute " + key)
                        .entryPoint(entryPoint)
                        .resourceLayout(layout));
        PipelineEntry result = new PipelineEntry(
                physicalKey, pipeline, layout);
        pipelines.add(result);
        return result;
    }

    private static ShaderGraphComputeTechniqueCompileResult
            requireSuccessful(
                    ShaderGraphComputeTechniqueCompileResult result) {
        if (result == null || !result.success()) {
            StringBuilder message = new StringBuilder(
                    "Shader graph compute technique compilation failed");
            if (result != null) {
                for (ShaderGraphDiagnostic diagnostic
                        : result.diagnostics()) {
                    message.append("\n[").append(diagnostic.code())
                            .append("] ")
                            .append(diagnostic.message());
                }
            }
            throw new FdxException(message.toString());
        }
        return result;
    }

    private static ShaderGraphComputeRuntimeTechnique requireCompute(
            ShaderGraphRuntimeAsset asset) {
        if (asset == null || asset.computeTechnique() == null) {
            throw new FdxException(
                    "Shader graph runtime asset has no compute technique");
        }
        return asset.computeTechnique();
    }

    private void requireActive() {
        if (disposed) {
            throw new FdxException(
                    "Shader graph compute provider is disposed");
        }
    }

    @Override
    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        state.dispose();
    }

    @Override
    public synchronized boolean isDisposed() {
        return disposed;
    }

    private static String display(String key) {
        return key == null || key.isEmpty() ? "<default>" : key;
    }

    private static void disposePipelines(
            Array<PipelineEntry> pipelines) {
        for (int i = pipelines.size() - 1; i >= 0; i--) {
            pipelines.get(i).pipeline.dispose();
        }
    }

    private static void disposeModules(Array<ModuleEntry> modules) {
        for (int i = modules.size() - 1; i >= 0; i--) {
            modules.get(i).shader.dispose();
        }
    }

    private record ModuleEntry(String key, ShaderModule shader,
            ShaderReflection reflection) {
    }

    private record PipelineEntry(String key, ComputePipeline pipeline,
            ShaderResourceLayout resourceLayout) {
    }

    private static final class RuntimeVariant {
        private final String key;
        private final ShaderProfile[] profiles;
        private final GraphicsFeature[] features;
        private final String fallbackKey;
        private final ShaderProfile compiledProfile;
        private final int workgroupX;
        private final int workgroupY;
        private final int workgroupZ;
        private final PipelineEntry pipeline;
        private long resolvedRevision = -1;
        private ShaderGraphResolvedComputePass resolved;

        private RuntimeVariant(String key,
                ShaderProfile[] profiles,
                GraphicsFeature[] features, String fallbackKey,
                ShaderProfile compiledProfile,
                int workgroupX, int workgroupY, int workgroupZ,
                PipelineEntry pipeline) {
            this.key = key;
            this.profiles = profiles.clone();
            this.features = features.clone();
            this.fallbackKey = fallbackKey;
            this.compiledProfile = compiledProfile;
            this.workgroupX = workgroupX;
            this.workgroupY = workgroupY;
            this.workgroupZ = workgroupZ;
            this.pipeline = pipeline;
        }

        private ShaderGraphResolvedComputePass resolved(
                ShaderPassId passId, long revision) {
            if (resolved == null
                    || resolvedRevision != revision) {
                resolved = new ShaderGraphResolvedComputePass(passId,
                        key, pipeline.pipeline,
                        pipeline.resourceLayout,
                        workgroupX, workgroupY, workgroupZ,
                        revision);
                resolvedRevision = revision;
            }
            return resolved;
        }

        private boolean supports(ShaderProfile profile,
                GraphicsCapabilities capabilities) {
            if (profile == null || capabilities == null
                    || !capabilities.supports(profile)
                    || !capabilities.supports(
                            GraphicsFeature.COMPUTE)
                    || compiledProfile != null
                            && compiledProfile != profile
                    || profiles.length > 0
                            && Arrays.binarySearch(profiles,
                                    profile) < 0) {
                return false;
            }
            for (GraphicsFeature feature : features) {
                if (!capabilities.supports(feature)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class RuntimePass {
        private final ShaderPassId passId;
        private final String defaultVariant;
        private final RuntimeVariant[] variants;

        private RuntimePass(ShaderPassId passId, String defaultVariant,
                RuntimeVariant[] variants) {
            this.passId = passId;
            this.defaultVariant = defaultVariant;
            this.variants = variants;
        }

        private RuntimeVariant variant(String key) {
            for (RuntimeVariant variant : variants) {
                if (variant.key.equals(key)) {
                    return variant;
                }
            }
            return null;
        }
    }

    private static final class TechniqueState {
        private final RuntimePass[] passes;
        private final ModuleEntry[] modules;
        private final PipelineEntry[] pipelines;

        private TechniqueState(RuntimePass[] passes,
                ModuleEntry[] modules, PipelineEntry[] pipelines) {
            this.passes = passes;
            this.modules = modules;
            this.pipelines = pipelines;
        }

        private RuntimePass pass(ShaderPassId id) {
            for (RuntimePass pass : passes) {
                if (pass.passId.equals(id)) {
                    return pass;
                }
            }
            return null;
        }

        private void dispose() {
            for (int i = pipelines.length - 1; i >= 0; i--) {
                pipelines[i].pipeline.dispose();
            }
            for (int i = modules.length - 1; i >= 0; i--) {
                modules[i].shader.dispose();
            }
        }
    }
}
