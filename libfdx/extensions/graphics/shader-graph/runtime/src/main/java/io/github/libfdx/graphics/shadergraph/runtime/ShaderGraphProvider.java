package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.collections.Array;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceBinding;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.MultisampleState;
import io.github.libfdx.graphics.PrimitiveState;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledPass;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiledVariant;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphPipelineState;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphTechniqueCompileResult;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphVariant;

/**
 * Provider-neutral graph runtime for a complete render technique.
 *
 * <p>The provider owns all shader modules and bounded pipeline caches. A
 * renderer resolves exactly one requested {@link ShaderPassId}; this class
 * never schedules or submits a multi-pass technique.</p>
 *
 * <p>Whole-technique replacement builds every module/interface first and
 * publishes the replacement with one revision change. Replacement must be
 * invoked at a renderer setup/frame boundary where previously borrowed
 * resolved passes are no longer in use.</p>
 */
public final class ShaderGraphProvider implements ShaderProvider, Disposable {
    private static final int DEFAULT_CACHE_CAPACITY = 64;

    private final GraphicsContext graphics;
    private final int cacheCapacity;
    private TechniqueState state;
    private long clock;
    private long revision = 1;
    private boolean disposed;

    /**
     * Source-compatible single-program construction path.
     */
    public ShaderGraphProvider(GraphicsContext graphics,
            ShaderGraphRenderProgram program) {
        if (graphics == null || program == null) {
            throw new FdxException(
                    "ShaderGraphProvider requires graphics and a program");
        }
        this.graphics = graphics;
        cacheCapacity = program.cacheCapacity();
        state = buildSingle(program);
    }

    public ShaderGraphProvider(GraphicsContext graphics,
            ShaderGraphTechniqueCompileResult technique) {
        this(graphics, technique, DEFAULT_CACHE_CAPACITY);
    }

    public ShaderGraphProvider(GraphicsContext graphics,
            ShaderGraphTechniqueCompileResult technique,
            int cacheCapacity) {
        if (graphics == null || cacheCapacity <= 0) {
            throw new FdxException(
                    "ShaderGraphProvider technique construction is invalid");
        }
        this.graphics = graphics;
        this.cacheCapacity = cacheCapacity;
        state = buildTechnique(requireSuccessful(technique));
    }

    /**
     * Creates a provider from a complete packaged render technique containing
     * graph-generated or handwritten WGSL programs.
     *
     * @param graphics graphics context
     * @param technique immutable render technique
     */
    public ShaderGraphProvider(GraphicsContext graphics,
            ShaderGraphRenderTechnique technique) {
        this(graphics, technique, DEFAULT_CACHE_CAPACITY);
    }

    public ShaderGraphProvider(GraphicsContext graphics,
            ShaderGraphRuntimeAsset asset) {
        this(graphics, requireRender(asset),
                DEFAULT_CACHE_CAPACITY);
    }

    public ShaderGraphProvider(GraphicsContext graphics,
            ShaderGraphRenderTechnique technique,
            int cacheCapacity) {
        if (graphics == null || technique == null
                || cacheCapacity <= 0) {
            throw new FdxException(
                    "ShaderGraphProvider render-technique construction is invalid");
        }
        this.graphics = graphics;
        this.cacheCapacity = cacheCapacity;
        state = buildRenderTechnique(technique);
    }

    @Override
    public boolean supportsPassResolution() {
        return true;
    }

    /**
     * Checks request compatibility without creating or caching a native
     * pipeline.
     */
    @Override
    public synchronized boolean supports(ShaderRequest request) {
        if (disposed || request == null || request.renderPass() == null
                || !graphics.device().capabilities().supports(
                        request.profile())) {
            return false;
        }
        RuntimePass pass = state.pass(request.passId());
        if (pass == null) {
            return false;
        }
        try {
            RuntimeVariant variant = select(pass, request);
            variant.validateRequest(request, request.vertexLayouts(),
                    graphics);
            return true;
        } catch (FdxException unsupported) {
            return false;
        }
    }

    @Override
    public synchronized ResolvedShaderPass resolve(ShaderRequest request) {
        requireActive();
        if (request == null || request.renderPass() == null) {
            throw new FdxException(
                    "Shader graph resolution requires a request and exact render-pass compatibility");
        }
        TechniqueState current = state;
        RuntimePass pass = current.pass(request.passId());
        if (pass == null) {
            throw new FdxException("Shader graph technique does not define "
                    + "requested pass " + request.passId());
        }
        graphics.device().capabilities().require(request.profile());
        RuntimeVariant variant = select(pass, request);
        VertexLayout[] vertexLayouts = request.vertexLayouts();
        variant.validateRequest(request, vertexLayouts,
                graphics);

        for (CacheEntry entry : current.cache) {
            if (entry != null && entry.matches(variant, request,
                    vertexLayouts)) {
                entry.lastUse = ++clock;
                return ResolvedShaderPass.of(pass.passId,
                        entry.pipeline, variant.resourceLayout,
                        variant.defaultResources(), revision);
            }
        }

        RenderPipeline pipeline = createPipeline(variant, request,
                vertexLayouts);
        int slot = emptyOrOldest(current.cache);
        if (current.cache[slot] != null) {
            current.cache[slot].pipeline.dispose();
        }
        current.cache[slot] = new CacheEntry(variant, request,
                vertexLayouts, pipeline, ++clock);
        return ResolvedShaderPass.of(pass.passId, pipeline,
                variant.resourceLayout, variant.defaultResources(),
                revision);
    }

    /**
     * Atomically replaces every pass and variant after all replacement shader
     * modules and interfaces have been created successfully.
     *
     * <p>If preparation throws, the current technique and revision remain
     * unchanged.</p>
     */
    public synchronized void replace(
            ShaderGraphTechniqueCompileResult technique) {
        requireActive();
        TechniqueState replacement =
                buildTechnique(requireSuccessful(technique));
        TechniqueState previous = state;
        state = replacement;
        revision++;
        previous.dispose();
    }

    /**
     * Atomically replaces a packaged render technique. All replacement shader
     * modules and reflected interfaces are created before publication.
     *
     * @param technique complete replacement technique
     */
    public synchronized void replace(
            ShaderGraphRenderTechnique technique) {
        requireActive();
        if (technique == null) {
            throw new FdxException(
                    "Replacement render technique cannot be null");
        }
        TechniqueState replacement =
                buildRenderTechnique(technique);
        TechniqueState previous = state;
        state = replacement;
        revision++;
        previous.dispose();
    }

    public synchronized void replace(ShaderGraphRuntimeAsset asset) {
        replace(requireRender(asset));
    }

    @Override
    public synchronized long revision() {
        observeDefaultResourceRevisions();
        return revision;
    }

    public synchronized int cachedPipelineCount() {
        int count = 0;
        for (CacheEntry entry : state.cache) {
            if (entry != null) {
                count++;
            }
        }
        return count;
    }

    public synchronized int passCount() {
        return state.passes.length;
    }

    private static ShaderGraphRenderTechnique requireRender(
            ShaderGraphRuntimeAsset asset) {
        if (asset == null || asset.renderTechnique() == null) {
            throw new FdxException(
                    "Shader graph runtime asset has no render technique");
        }
        return asset.renderTechnique();
    }

    private TechniqueState buildSingle(ShaderGraphRenderProgram program) {
        Array<ModuleEntry> modules = new Array<ModuleEntry>();
        try {
            ModuleEntry module = module(modules, program.shader(),
                    program.vertexEntryPoint(),
                    program.fragmentEntryPoint());
            RuntimeVariant variant = RuntimeVariant.renderProgram(
                    program, module);
            return new TechniqueState(new RuntimePass[] {
                    new RuntimePass(program.passId(), "",
                            new RuntimeVariant[] { variant })
            }, modules.toArray(new ModuleEntry[0]), cacheCapacity);
        } catch (RuntimeException failure) {
            disposeModules(modules);
            throw failure;
        }
    }

    private TechniqueState buildTechnique(
            ShaderGraphTechniqueCompileResult technique) {
        Array<ModuleEntry> modules = new Array<ModuleEntry>();
        try {
            ShaderGraphCompiledPass[] compiledPasses =
                    technique.passes();
            RuntimePass[] passes =
                    new RuntimePass[compiledPasses.length];
            for (int passIndex = 0; passIndex < passes.length;
                    passIndex++) {
                ShaderGraphCompiledPass compiledPass =
                        compiledPasses[passIndex];
                ShaderGraphCompiledVariant[] compiledVariants =
                        compiledPass.variants();
                RuntimeVariant[] variants =
                        new RuntimeVariant[compiledVariants.length];
                for (int variantIndex = 0;
                        variantIndex < variants.length; variantIndex++) {
                    ShaderGraphCompiledVariant compiled =
                            compiledVariants[variantIndex];
                    validateProgramLimits(compiled.variant().program());
                    ShaderModuleDescriptor descriptor =
                            ShaderModuleDescriptor.wgsl(
                                            technique.technique().id() + " "
                                                    + compiledPass.pass().passId()
                                                    + " "
                                                    + displayKey(compiled
                                                            .variant().key()),
                                            compiled.compilation().wgsl())
                                    .entryPoints(
                                            compiled.compilation()
                                                    .vertexEntryPoint(),
                                            compiled.compilation()
                                                    .fragmentEntryPoint());
                    ModuleEntry module = module(modules, descriptor,
                            compiled.compilation().vertexEntryPoint(),
                            compiled.compilation().fragmentEntryPoint());
                    variants[variantIndex] = RuntimeVariant.compiled(
                            compiled.variant(),
                            compiledPass.pass().pipelineState(),
                            module, technique.technique().id() + " "
                                    + compiledPass.pass().passId());
                }
                passes[passIndex] = new RuntimePass(
                        compiledPass.pass().passId(),
                        compiledPass.pass().defaultVariantKey(),
                        variants);
            }
            return new TechniqueState(passes,
                    modules.toArray(new ModuleEntry[0]), cacheCapacity);
        } catch (RuntimeException failure) {
            disposeModules(modules);
            throw failure;
        }
    }

    private TechniqueState buildRenderTechnique(
            ShaderGraphRenderTechnique technique) {
        Array<ModuleEntry> modules = new Array<ModuleEntry>();
        try {
            ShaderGraphRenderTechniquePass[] sourcePasses =
                    technique.passes();
            RuntimePass[] passes =
                    new RuntimePass[sourcePasses.length];
            for (int passIndex = 0;
                    passIndex < sourcePasses.length; passIndex++) {
                ShaderGraphRenderTechniquePass sourcePass =
                        sourcePasses[passIndex];
                ShaderGraphRenderVariant[] sourceVariants =
                        sourcePass.variants();
                RuntimeVariant[] variants =
                        new RuntimeVariant[sourceVariants.length];
                for (int variantIndex = 0;
                        variantIndex < sourceVariants.length;
                        variantIndex++) {
                    ShaderGraphRenderVariant sourceVariant =
                            sourceVariants[variantIndex];
                    ShaderGraphRenderProgram program =
                            sourceVariant.program();
                    if (!program.passId().equals(
                            sourcePass.passId())) {
                        throw new FdxException(
                                "Render program pass does not match technique pass "
                                        + sourcePass.passId());
                    }
                    ModuleEntry module = module(modules,
                            program.shader(),
                            program.vertexEntryPoint(),
                            program.fragmentEntryPoint());
                    variants[variantIndex] =
                            RuntimeVariant.renderArtifact(
                                    sourceVariant, module,
                                    sourcePass.pipelineState(),
                                    technique.id() + " "
                                            + sourcePass.passId());
                }
                passes[passIndex] = new RuntimePass(
                        sourcePass.passId(),
                        sourcePass.defaultVariantKey(),
                        variants);
            }
            return new TechniqueState(passes,
                    modules.toArray(new ModuleEntry[0]),
                    cacheCapacity);
        } catch (RuntimeException failure) {
            disposeModules(modules);
            throw failure;
        }
    }

    private ModuleEntry module(Array<ModuleEntry> modules,
            ShaderModuleDescriptor descriptor, String vertexEntryPoint,
            String fragmentEntryPoint) {
        String key = descriptor.source() + '\0' + vertexEntryPoint
                + '\0' + fragmentEntryPoint;
        for (ModuleEntry module : modules) {
            if (module.key.equals(key)) {
                return module;
            }
        }
        ShaderModule shader = graphics.device().createShaderModule(
                descriptor);
        ShaderReflection moduleReflection = shader.reflection();
        ShaderReflection descriptorReflection =
                descriptor.reflection();
        if (moduleReflection.complete()
                && descriptorReflection.complete()
                && !moduleReflection.physicallyEquivalent(
                        descriptorReflection)) {
            shader.dispose();
            throw new FdxException(
                    "Shader graph module reflection does not match its verified descriptor interface");
        }
        ShaderReflection reflection = moduleReflection.complete()
                ? moduleReflection : descriptorReflection;
        if (!reflection.complete()) {
            shader.dispose();
            throw new FdxException(
                    "Shader graph runtime requires complete reflected shader interface");
        }
        ShaderResourceLayout layout = ShaderResourceLayout.render(
                reflection, vertexEntryPoint,
                fragmentEntryPoint);
        layout.validate(graphics.device().capabilities());
        ModuleEntry result = new ModuleEntry(key, shader, layout);
        modules.add(result);
        return result;
    }

    private void validateProgramLimits(ShaderGraphProgram program) {
        validateGraphLimits(program.vertex());
        validateGraphLimits(program.fragment());
        boolean material = hasMaterial(program.vertex())
                || hasMaterial(program.fragment());
        if (material) {
            validateBinding(program.materialGroup(),
                    program.materialBinding(), "material uniform");
        }
    }

    private void validateGraphLimits(ShaderGraph graph) {
        for (ShaderGraphResource resource : graph.resources()) {
            validateBinding(resource.group(), resource.binding(),
                    "resource " + resource.id());
        }
    }

    private void validateBinding(int group, int binding, String label) {
        var limits = graphics.device().capabilities().limits();
        if (group >= limits.maxBindGroups()
                || binding >= limits.maxBindingsPerGroup()) {
            throw new FdxException("Shader graph " + label
                    + " binding @group(" + group + ") @binding("
                    + binding + ") exceeds provider limits "
                    + limits.maxBindGroups() + " groups and "
                    + limits.maxBindingsPerGroup()
                    + " bindings per group");
        }
    }

    private static boolean hasMaterial(ShaderGraph graph) {
        for (ShaderGraphParameter parameter : graph.parameters()) {
            if (parameter.kind()
                    == ShaderGraphParameterKind.MATERIAL) {
                return true;
            }
        }
        return false;
    }

    private RuntimeVariant select(RuntimePass pass,
            ShaderRequest request) {
        String requested = request.variantKey().isEmpty()
                ? pass.defaultVariantKey : request.variantKey();
        RuntimeVariant variant = pass.variant(requested);
        if (variant == null) {
            throw new FdxException("Shader pass " + pass.passId
                    + " has no variant " + displayKey(requested));
        }
        for (int depth = 0; depth <= pass.variants.length; depth++) {
            if (variant.supports(request, graphics)) {
                return variant;
            }
            String fallback = variant.fallbackKey();
            if (fallback == null) {
                throw new FdxException("Shader variant "
                        + displayKey(variant.key)
                        + " is unsupported by profile "
                        + request.profile()
                        + " and declares no fallback");
            }
            variant = pass.variant(fallback);
            if (variant == null) {
                throw new FdxException(
                        "Compiled shader variant fallback is missing: "
                                + displayKey(fallback));
            }
        }
        throw new FdxException(
                "Shader variant fallback cycle reached runtime");
    }

    private RenderPipeline createPipeline(RuntimeVariant variant,
            ShaderRequest request, VertexLayout[] vertexLayouts) {
        if (variant.pipelineState == null) {
            return createRenderProgramPipeline(variant, request,
                    vertexLayouts);
        }
        ShaderGraphPipelineState state = variant.pipelineState;
        RenderTargetLayout targets = request.renderPass().targetLayout();
        RenderPipelineDescriptor descriptor =
                RenderPipelineDescriptor.shader(variant.module.shader,
                                targets.colorAttachmentCount() > 0
                                        ? targets.colorFormat(0)
                                        : TextureFormat.UNKNOWN)
                        .label(variant.label)
                        .vertexEntryPoint(variant.vertexEntryPoint)
                        .fragmentEntryPoint(variant.fragmentEntryPoint)
                        .resourceLayout(variant.resourceLayout)
                        .renderTargetLayout(targets)
                        .primitiveState(state.primitive())
                        .vertexLayouts(vertexLayouts)
                        .colorTargets(state.colorTargets())
                        .multisampleState(state.multisample());
        if (state.depthStencil() != null) {
            descriptor.depthStencilState(state.depthStencil());
        }
        descriptor.validate(graphics.device().capabilities());
        return graphics.device().createRenderPipeline(descriptor);
    }

    private RenderPipeline createRenderProgramPipeline(
            RuntimeVariant variant,
            ShaderRequest request, VertexLayout[] vertexLayouts) {
        ShaderGraphRenderProgram program = variant.renderProgram;
        RenderTargetLayout targets = request.renderPass().targetLayout();
        ColorTargetState[] colors =
                new ColorTargetState[targets.colorAttachmentCount()];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = ColorTargetState.alpha(
                    targets.colorFormat(i));
        }
        RenderPipelineDescriptor descriptor = RenderPipelineDescriptor
                .shader(variant.module.shader,
                        colors.length > 0 ? targets.colorFormat(0)
                                : TextureFormat.UNKNOWN)
                .label(program.label())
                .vertexEntryPoint(program.vertexEntryPoint())
                .fragmentEntryPoint(program.fragmentEntryPoint())
                .resourceLayout(variant.resourceLayout)
                .renderTargetLayout(targets)
                .primitiveState(PrimitiveState.of(request.topology(),
                        program.frontFace(), program.cullMode()))
                .vertexLayouts(vertexLayouts)
                .colorTargets(colors)
                .multisampleState(MultisampleState.of(
                        targets.sampleCount(), -1, false));
        if (targets.hasDepthStencil()) {
            descriptor.depthStencilState(DepthStencilState
                    .builder(targets.depthStencilFormat())
                    .depthWriteEnabled(program.depthWrite())
                    .depthCompare(program.depthCompare())
                    .build());
        }
        descriptor.validate(graphics.device().capabilities());
        return graphics.device().createRenderPipeline(descriptor);
    }

    private static ShaderGraphTechniqueCompileResult requireSuccessful(
            ShaderGraphTechniqueCompileResult result) {
        if (result != null && result.success()) {
            return result;
        }
        StringBuilder message = new StringBuilder(
                "Shader graph provider requires a successful technique compilation");
        if (result != null) {
            for (ShaderGraphDiagnostic diagnostic
                    : result.diagnostics()) {
                message.append('\n').append(diagnostic.code())
                        .append(": ").append(diagnostic.message());
            }
        }
        throw new FdxException(message.toString());
    }

    private static int emptyOrOldest(CacheEntry[] cache) {
        int oldest = 0;
        long oldestUse = Long.MAX_VALUE;
        for (int i = 0; i < cache.length; i++) {
            if (cache[i] == null) {
                return i;
            }
            if (cache[i].lastUse < oldestUse) {
                oldestUse = cache[i].lastUse;
                oldest = i;
            }
        }
        return oldest;
    }

    private void requireActive() {
        if (disposed) {
            throw new FdxException(
                    "ShaderGraphProvider has been disposed");
        }
    }

    private void observeDefaultResourceRevisions() {
        if (disposed) {
            return;
        }
        boolean changed = false;
        for (RuntimePass pass : state.passes) {
            for (RuntimeVariant variant : pass.variants) {
                if (variant.observeDefaultResourceRevision()) {
                    changed = true;
                }
            }
        }
        if (changed) {
            revision++;
        }
    }

    @Override
    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        state.dispose();
        revision++;
    }

    @Override
    public synchronized boolean isDisposed() {
        return disposed;
    }

    private static void disposeModules(Array<ModuleEntry> modules) {
        for (ModuleEntry module : modules) {
            module.shader.dispose();
        }
        modules.clear();
    }

    private static String displayKey(String key) {
        return key == null || key.isEmpty() ? "<default>" : key;
    }

    private static final class TechniqueState {
        final RuntimePass[] passes;
        final ModuleEntry[] modules;
        final CacheEntry[] cache;

        TechniqueState(RuntimePass[] passes, ModuleEntry[] modules,
                int cacheCapacity) {
            this.passes = passes;
            this.modules = modules;
            cache = new CacheEntry[cacheCapacity];
        }

        RuntimePass pass(ShaderPassId id) {
            for (RuntimePass pass : passes) {
                if (pass.passId.equals(id)) {
                    return pass;
                }
            }
            return null;
        }

        void dispose() {
            for (int i = 0; i < cache.length; i++) {
                if (cache[i] != null) {
                    cache[i].pipeline.dispose();
                    cache[i] = null;
                }
            }
            for (ModuleEntry module : modules) {
                module.shader.dispose();
            }
        }
    }

    private static final class RuntimePass {
        final ShaderPassId passId;
        final String defaultVariantKey;
        final RuntimeVariant[] variants;

        RuntimePass(ShaderPassId passId, String defaultVariantKey,
                RuntimeVariant[] variants) {
            this.passId = passId;
            this.defaultVariantKey = defaultVariantKey;
            this.variants = variants;
        }

        RuntimeVariant variant(String key) {
            for (RuntimeVariant variant : variants) {
                if (variant.key.equals(key)) {
                    return variant;
                }
            }
            return null;
        }
    }

    private static final class RuntimeVariant {
        final String key;
        final ShaderGraphVariant graphVariant;
        final ShaderGraphRenderVariant renderVariant;
        final ShaderGraphPipelineState pipelineState;
        final ShaderGraphRenderProgram renderProgram;
        final ModuleEntry module;
        final ShaderResourceLayout resourceLayout;
        final String label;
        final String vertexEntryPoint;
        final String fragmentEntryPoint;
        long defaultResourceIdentity = -1;
        long defaultResourceRevision = -1;

        private RuntimeVariant(String key,
                ShaderGraphVariant graphVariant,
                ShaderGraphRenderVariant renderVariant,
                ShaderGraphPipelineState pipelineState,
                ShaderGraphRenderProgram renderProgram,
                ModuleEntry module, String label,
                String vertexEntryPoint,
                String fragmentEntryPoint) {
            this.key = key;
            this.graphVariant = graphVariant;
            this.renderVariant = renderVariant;
            this.pipelineState = pipelineState;
            this.renderProgram = renderProgram;
            this.module = module;
            resourceLayout = module.resourceLayout;
            this.label = label;
            this.vertexEntryPoint = vertexEntryPoint;
            this.fragmentEntryPoint = fragmentEntryPoint;
            var defaults = defaultResources();
            if (defaults != null) {
                defaultResourceIdentity = defaults.identity();
                defaultResourceRevision = defaults.revision();
            }
        }

        static RuntimeVariant renderProgram(
                ShaderGraphRenderProgram program,
                ModuleEntry module) {
            return new RuntimeVariant("", null, null, null,
                    program, module, program.label(),
                    program.vertexEntryPoint(),
                    program.fragmentEntryPoint());
        }

        static RuntimeVariant compiled(ShaderGraphVariant variant,
                ShaderGraphPipelineState pipelineState,
                ModuleEntry module, String label) {
            return new RuntimeVariant(variant.key(), variant, null,
                    pipelineState, null, module, label,
                    variant.program().vertexEntryPoint(),
                    variant.program().fragmentEntryPoint());
        }

        static RuntimeVariant renderArtifact(
                ShaderGraphRenderVariant variant,
                ModuleEntry module,
                ShaderGraphPipelineState pipelineState,
                String label) {
            ShaderGraphRenderProgram program = variant.program();
            return new RuntimeVariant(variant.key(), null,
                    variant, pipelineState, program, module, label,
                    program.vertexEntryPoint(),
                    program.fragmentEntryPoint());
        }

        boolean supports(ShaderRequest request,
                GraphicsContext graphics) {
            if (graphVariant != null) {
                return graphVariant.supports(request.profile(),
                        graphics.device().capabilities());
            }
            return renderVariant == null
                    || renderVariant.supports(request.profile(),
                            graphics.device().capabilities());
        }

        String fallbackKey() {
            if (graphVariant != null) {
                return graphVariant.fallbackKey();
            }
            return renderVariant != null
                    ? renderVariant.fallbackKey() : null;
        }

        io.github.libfdx.graphics.shader.runtime.ShaderResourceBinding
                defaultResources() {
            return renderProgram != null
                    ? renderProgram.defaultResources() : null;
        }

        boolean observeDefaultResourceRevision() {
            var defaults = defaultResources();
            if (defaults == null) {
                return false;
            }
            long identity = defaults.identity();
            long resourceRevision = defaults.revision();
            if (identity == defaultResourceIdentity
                    && resourceRevision == defaultResourceRevision) {
                return false;
            }
            defaultResourceIdentity = identity;
            defaultResourceRevision = resourceRevision;
            return true;
        }

        void validateRequest(ShaderRequest request,
                VertexLayout[] vertexLayouts,
                GraphicsContext graphics) {
            if (pipelineState == null) {
                VertexLayout[] expected =
                        renderProgram.vertexLayouts();
                if (expected.length > 0
                        && !java.util.Arrays.equals(expected,
                                vertexLayouts)) {
                    throw new FdxException(
                            "Shader render program vertex layout does not match request");
                }
                request.renderPass().targetLayout().validate(
                        graphics.device().capabilities());
                return;
            }
            if (request.topology()
                    != pipelineState.primitive().topology()) {
                throw new FdxException("Shader technique pass topology is "
                        + pipelineState.primitive().topology()
                        + ", request is " + request.topology());
            }
            pipelineState.validate(
                    request.renderPass().targetLayout(),
                    vertexLayouts, graphics.device().capabilities());
        }
    }

    private static final class ModuleEntry {
        final String key;
        final ShaderModule shader;
        final ShaderResourceLayout resourceLayout;

        ModuleEntry(String key, ShaderModule shader,
                ShaderResourceLayout resourceLayout) {
            this.key = key;
            this.shader = shader;
            this.resourceLayout = resourceLayout;
        }
    }

    private static final class CacheEntry {
        final RuntimeVariant variant;
        final RenderTargetLayout targets;
        final io.github.libfdx.graphics.PrimitiveTopology topology;
        final VertexLayout[] vertexLayouts;
        final RenderPipeline pipeline;
        long lastUse;

        CacheEntry(RuntimeVariant variant, ShaderRequest request,
                VertexLayout[] vertexLayouts, RenderPipeline pipeline,
                long lastUse) {
            this.variant = variant;
            targets = request.renderPass().targetLayout();
            topology = request.topology();
            this.vertexLayouts = vertexLayouts.clone();
            this.pipeline = pipeline;
            this.lastUse = lastUse;
        }

        boolean matches(RuntimeVariant variant,
                ShaderRequest request, VertexLayout[] layouts) {
            if (this.variant != variant
                    || !targets.equals(
                            request.renderPass().targetLayout())
                    || topology != request.topology()
                    || vertexLayouts.length != layouts.length) {
                return false;
            }
            for (int i = 0; i < layouts.length; i++) {
                if (!vertexLayouts[i].equals(layouts[i])) {
                    return false;
                }
            }
            return true;
        }
    }
}
