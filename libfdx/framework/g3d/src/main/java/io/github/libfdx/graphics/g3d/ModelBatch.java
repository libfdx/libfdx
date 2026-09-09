package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.ObjectIterable;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassColorAttachment;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDepthStencilAttachment;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationState;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.runtime.ShaderSkippedDraws;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.math.Color;

/**
 * Represents a model batch.
 *
 * @author xpenatan
 */
public final class ModelBatch implements Batch3D {
    private final ShaderPreparation preparation;
    private ModelShaderPlan modelShaderPlan;
    private boolean ownsModelShaderPlan;
    private ModelPreparedShaders preparedShaders;
    private final ModelShaderGroup shaderGroup;
    private final ShaderSkippedDraws synchronousSkipped = new ShaderSkippedDraws();

    /** Logical renderables omitted across this frame's passes. Read after end(); each pass
     * submission counts separately. Counts reset at the next preparation update boundary. */
    public ShaderSkippedDraws skippedDrawsLastFrame() {
        return preparedShaders != null ? preparedShaders.skipped : synchronousSkipped;
    }
    private final GraphicsContext graphics;
    private final DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
    private FrustumCuller3D culler;
    private boolean frustumCulling;
    private int lastFlushCulledCount,lastFlushVisibleCount;
    private final Environment3D defaultEnvironment = new Environment3D();
    // The depth clear value is applied per pass in begin(), not here: it has
    // to match the active clip depth range, and a field initializer would latch
    // whatever was set when this batch happened to be constructed.
    private final RenderPassDescriptor framePassDescriptor = new RenderPassDescriptor()
            .label("model batch pass");
    private RenderPassDescriptor targetPassDescriptor = new RenderPassDescriptor()
            .label("model batch target pass");
    private TextureView[] targetColors = new TextureView[0];
    private TextureView[] targetResolves = new TextureView[0];
    private TextureView targetDepth;
    private int targetWidth, targetHeight;
    private float targetDepthClear = Float.NaN;
    private final RenderContext3D context;
    private ShaderProvider3D ownedShaderProvider;
    private Disposable[] retiredOwnedProviders = new Disposable[2];
    private int retiredOwnedProviderCount;
    private Environment3D environment = defaultEnvironment;
    private ShaderProvider3D shaderProvider;
    private ShaderProvider commonShaderProvider;
    private long commonShaderRevision = -1;
    private ShaderMaterialBinding[] pendingMaterialBindings =
            new ShaderMaterialBinding[8];
    private long[] pendingMaterialRevisions = new long[8];
    private int pendingMaterialBindingCount;
    private RenderPass pass;
    private boolean ownsPass;
    private boolean drawing;
    private boolean disposed;

    /**
     * Creates a model batch.
     *
     * @param graphics the graphics context
     */
    public ModelBatch(GraphicsContext graphics) {
        this(graphics, new ModelBatchConfig());
    }

    /**
     * Creates a model batch.
     *
     * @param graphics the graphics context
     * @param config the configuration
     */
    public ModelBatch(GraphicsContext graphics, ModelBatchConfig config) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        context = new RenderContext3D(graphics, null, null, null, null,
                ShaderPassId.FORWARD);
        if (config == null) {
            throw new FdxException("ModelBatchConfig cannot be null");
        }
        this.graphics = graphics;
        preparation = config.preparation();
        shaderGroup = config.shaderGroup();
        if (shaderGroup != null && shaderGroup.preparation() != preparation) {
            throw new FdxException("Model shader group and preparation must match");
        }
        if (preparation != null) {
            ModelShaderPlan rendererPlan = config.shaderProvider() instanceof PreparedShaderProvider3D renderer
                    ? renderer.preparationPlan() : null;
            if (config.shaderPlan() != null && (config.commonShaderProvider() != null
                    || config.shaderProvider() != null && rendererPlan != config.shaderPlan())) {
                throw new FdxException("Configure providers on the shared model shader plan");
            }
            modelShaderPlan = config.shaderPlan() != null ? config.shaderPlan()
                    : rendererPlan != null ? rendererPlan
                    : new ModelShaderPlan(graphics, config.commonShaderProvider());
            ownsModelShaderPlan = config.shaderPlan() == null && rendererPlan == null;
            modelShaderPlan.requireDomain(preparation.device());
            preparedShaders = new ModelPreparedShaders(preparation, modelShaderPlan);
        } else {
            if (config.shaderPlan() != null) throw new FdxException("Model shader plan requires a preparation service");
            modelShaderPlan = null;
            ownsModelShaderPlan = false;
            preparedShaders = null;
        }
        if (preparation != null && config.shaderProvider() == null) {
            ownedShaderProvider = new PbrShaderProvider(graphics, new PbrShaderConfig()
                    .maxLights(config.maxLights()).maxBones(config.maxBones()).shaderPlan(modelShaderPlan));
            shaderProvider = ownedShaderProvider;
        } else if (config.shaderProvider() != null) {
            shaderProvider = config.shaderProvider();
        } else if (config.commonShaderProvider() != null) {
            configureCommonProvider(config.commonShaderProvider(),
                    config.maxLights(), config.maxBones());
        } else {
            ownedShaderProvider = new PbrShaderProvider(graphics, new PbrShaderConfig()
                    .maxLights(config.maxLights())
                    .maxBones(config.maxBones()));
            shaderProvider = ownedShaderProvider;
        }
    }

    /**
     * Begins the operation.
     *
     * @param camera the camera
     */
    @Override
    public void begin(Camera camera) {
        begin(LoadOp.load(), camera);
    }

    /**
     * Begins an explicit shader-technique pass on the current frame.
     *
     * @param camera camera
     * @param shaderPassId requested pass
     */
    public void begin(Camera camera, ShaderPassId shaderPassId) {
        begin(LoadOp.load(), camera, shaderPassId);
    }

    /**
     * Begins the operation.
     *
     * @param loadOp the load op
     * @param camera the camera
     */
    @Override
    public void begin(LoadOp loadOp, Camera camera) {
        begin(loadOp, camera, ShaderPassId.FORWARD);
    }

    /**
     * Begins an explicit shader-technique pass on the current frame.
     *
     * @param loadOp color load operation
     * @param camera camera
     * @param shaderPassId requested pass
     */
    public void begin(LoadOp loadOp, Camera camera,
            ShaderPassId shaderPassId) {
        ensureNotDisposed();
        ensureCamera(camera);
        ensureShaderPass(shaderPassId);
        GraphicsFrame frame = graphics.currentFrame();
        framePassDescriptor
                .depthClear(ClipDepthRange.getDefault().depthClearValue())
                .colorAttachment(frame.colorAttachment())
                .colorLoadOp(loadOp != null ? loadOp : LoadOp.load())
                .colorStoreOp(StoreOp.store());
        pass = frame.commandEncoder().beginRenderPass(framePassDescriptor);
        ownsPass = true;
        context.reset(camera, environment, null, pass,
                shaderPassId);
        if (modelShaderPlan != null) modelShaderPlan.targets().register("surface", context.renderPassCompatibility().targetLayout());
        snapshotCommonProvider();
        drawing = true;
    }

    /**
     * Begins the operation.
     *
     * @param pass the pass
     * @param camera the camera
     */
    @Override
    public void begin(RenderPass pass, Camera camera) {
        begin(pass, camera, ShaderPassId.FORWARD);
    }

    /**
     * Begins an explicit shader-technique pass in an external render pass.
     *
     * @param pass active render pass
     * @param camera camera
     * @param shaderPassId requested pass
     */
    public void begin(RenderPass pass, Camera camera,
            ShaderPassId shaderPassId) {
        ensureNotDisposed();
        ensureCamera(camera);
        ensureShaderPass(shaderPassId);
        if (pass == null) {
            throw new FdxException("RenderPass cannot be null");
        }
        this.pass = pass;
        ownsPass = false;
        context.reset(camera, environment, null, pass,
                shaderPassId);
        snapshotCommonProvider();
        drawing = true;
    }

    /**
     * Begins the operation.
     *
     * @param target the target value
     * @param camera the camera
     */
    @Override
    public void begin(RenderTarget3D target, Camera camera) {
        begin(target, camera, ShaderPassId.FORWARD);
    }

    /**
     * Begins an explicit shader-technique pass in a model render target.
     *
     * @param target render target
     * @param camera camera
     * @param shaderPassId requested pass
     */
    public void begin(RenderTarget3D target, Camera camera,
            ShaderPassId shaderPassId) {
        ensureNotDisposed();
        ensureCamera(camera);
        ensureShaderPass(shaderPassId);
        if (target == null) {
            throw new FdxException("RenderTarget3D cannot be null");
        }
        prepareTargetPass(target);
        GraphicsFrame frame = graphics.currentFrame();
        pass = frame.commandEncoder().beginRenderPass(targetPassDescriptor);
        ownsPass = true;
        context.reset(camera, environment, target, pass,
                shaderPassId);
        snapshotCommonProvider();
        drawing = true;
    }

    // Rebuild attachment metadata only after a view/layout/clip-depth change.
    private void prepareTargetPass(RenderTarget3D target) {
        int count = target.colorAttachmentCount();
        if (count <= 0) throw new FdxException("Model target requires a color attachment");
        float clear = ClipDepthRange.getDefault().depthClearValue();
        boolean changed = count != targetColors.length || target.depthAttachment() != targetDepth
                || target.width() != targetWidth || target.height() != targetHeight || clear != targetDepthClear;
        for (int i = 0; !changed && i < count; i++) changed = targetColors[i] != target.colorAttachment(i)
                || targetResolves[i] != target.resolveAttachment(i);
        if (!changed) return;
        var colors = new RenderPassColorAttachment[count];
        var formats = new TextureFormat[count];
        TextureView[] views = new TextureView[count];
        TextureView[] resolves = new TextureView[count];
        for (int i = 0; i < count; i++) {
            views[i] = target.colorAttachment(i);
            if (views[i] == null) throw new FdxException("Model target color attachment cannot be null");
            resolves[i] = target.resolveAttachment(i);
            colors[i] = resolves[i] == null
                    ? RenderPassColorAttachment.of(views[i], LoadOp.load(), StoreOp.store())
                    : RenderPassColorAttachment.resolve(views[i], resolves[i], LoadOp.load(), StoreOp.store());
            formats[i] = views[i].format();
        }
        TextureView depth = target.depthAttachment();
        var descriptor = new RenderPassDescriptor().label("model batch target pass").colorAttachments(colors);
        if (depth != null) descriptor.depthStencilAttachment(RenderPassDepthStencilAttachment.of(
                depth, LoadOp.clear(clear,0,0,0), StoreOp.store(), LoadOp.load(), StoreOp.store()));
        else descriptor.depthClear(clear);
        descriptor.compatibility(RenderPassCompatibility.of(
                RenderTargetLayout.of(formats,
                        depth == null ? TextureFormat.DEPTH32_FLOAT : depth.format(), views[0].sampleCount()),
                target.width(), target.height()));
        descriptor.validate(graphics.device().capabilities());
        targetPassDescriptor = descriptor; targetColors = views; targetResolves = resolves; targetDepth = depth;
        targetWidth = target.width(); targetHeight = target.height(); targetDepthClear = clear;
    }

    /**
     * Sets the environment and returns this model batch.
     *
     * @param environment the environment
     * @return this model batch for chaining
     */
    @Override
    public ModelBatch environment(Environment3D environment) {
        this.environment = environment != null ? environment : defaultEnvironment;
        return this;
    }

    /** Enables optional conservative culling at flush, before sorting/shader selection/GPU submission.
     * Defaults to false. Uses that flush's camera and current transforms. Missing/uncertain bounds stay visible;
     * DefaultModelInstance supplies current-pose bounds for prepared skins. Each shadow pass uses its own camera.
     * Call only outside begin/end. This option neither updates animation bounds nor performs occlusion culling. */
    public ModelBatch frustumCulling(boolean enabled) {
        ensureNotDisposed();
        if(drawing)throw new FdxException("Culling cannot be changed while ModelBatch is drawing");
        if(enabled&&culler==null)culler=new FrustumCuller3D();
        frustumCulling=enabled;return this;
    }
    public boolean frustumCulling(){return frustumCulling;}
    /** Renderables removed by the last nonempty flush; zero when culling was disabled. */
    public int lastFlushCulledCount(){return lastFlushCulledCount;}
    /** Renderables retained by the last nonempty flush, before shader submission (which may fail). */
    public int lastFlushVisibleCount(){return lastFlushVisibleCount;}

    /**
     * Sets the borrowed shader provider and returns this model batch.
     *
     * <p>The provider is not disposed by this batch. It may be replaced only
     * outside a {@link #begin} / {@link #end} drawing operation.</p>
     *
     * @param shaderProvider the shader provider
     * @return this model batch for chaining
     */
    @Override
    public ModelBatch shaderProvider(ShaderProvider3D shaderProvider) {
        ensureNotDisposed();
        if (shaderProvider == null) {
            throw new FdxException("ShaderProvider3D cannot be null");
        }
        if (drawing) {
            throw new FdxException("ShaderProvider3D cannot be replaced while ModelBatch is drawing");
        }
        retireOwnedShaderProvider();
        commonShaderProvider = null;
        commonShaderRevision = -1;
        this.shaderProvider = shaderProvider;
        return this;
    }

    /**
     * Sets a borrowed common shader provider and installs an internal G3D
     * adapter. The provider itself is never disposed by this batch.
     *
     * @param shaderProvider common shader provider
     * @return this model batch for chaining
     */
    public ModelBatch shaderProvider(ShaderProvider shaderProvider) {
        ensureNotDisposed();
        if (shaderProvider == null) {
            throw new FdxException("ShaderProvider cannot be null");
        }
        if (drawing) {
            throw new FdxException(
                    "ShaderProvider cannot be replaced while ModelBatch is drawing");
        }
        retireOwnedShaderProvider();
        PbrShaderConfig defaults = new PbrShaderConfig();
        configureCommonProvider(shaderProvider,
                defaults.maxLights(), defaults.maxBones());
        return this;
    }

    /**
     * Renders the current content.
     *
     * @param instance the instance
     */
    @Override
    public void render(ModelInstance instance) {
        ensureDrawing();
        if (instance == null) {
            throw new FdxException("ModelInstance cannot be null");
        }
        int first = queue.size();
        instance.collectRenderables(queue);
        for (int i = first; i < queue.size(); i++) {
            captureMaterialBinding(queue.get(i).material());
        }
    }

    /**
     * Renders the current content.
     *
     * @param renderable the renderable
     */
    @Override
    public void render(Renderable3D renderable) {
        ensureDrawing();
        if (renderable == null) {
            throw new FdxException("Renderable3D cannot be null");
        }
        queue.add(renderable);
        captureMaterialBinding(renderable.material());
    }

    /**
     * Renders the current content.
     *
     * @param instances the instances
     */
    @Override
    public void render(ObjectIterable<? extends ModelInstance> instances) {
        ensureDrawing();
        if (instances == null) {
            throw new FdxException("ModelInstance iterable cannot be null");
        }
        if (instances instanceof ArrayView<?>) {
            ArrayView<?> values = (ArrayView<?>)instances;
            for (int i = 0; i < values.size(); i++) {
                render((ModelInstance) values.get(i));
            }
            return;
        }
        ObjectIterator<? extends ModelInstance> iterator = instances.iterator();
        while (iterator.hasNext()) {
            render(iterator.next());
        }
    }

    /**
     * Submits queued renderables. Failure ends the active shader and discards the
     * remaining queue; the caller must still end this batch and its borrowed pass.
     */
    @Override
    public void flush() {
        ensureDrawing();
        if (queue.size() == 0) {
            return;
        }
        Shader3D activeShader = null;
        Throwable failure = null;
        try {
        validatePendingMaterialBindings();
        lastFlushCulledCount=frustumCulling?queue.cull(culler.update(context.camera())):0;
        lastFlushVisibleCount=queue.size();
        queue.sort(context.camera());
        for (int i = 0; i < queue.size(); i++) {
            Renderable3D renderable = queue.get(i);
            ShaderProvider3D provider = renderable.material().shaderProvider();
            if (preparedShaders != null) {
                ShaderProvider3D selectedProvider = provider != null ? provider : shaderProvider;
                if (provider == null && selectedProvider != ownedShaderProvider
                        && (!(selectedProvider instanceof PreparedShaderProvider3D renderer)
                        || renderer.preparationPlan() != modelShaderPlan)) {
                    preparedShaders.unsupported(renderable, context.shaderPassId(), context.renderPassCompatibility().targetLayout());
                    continue;
                }
                ResolvedShaderPass prepared = shaderGroup != null
                        ? shaderGroup.resolve(renderable, context.shaderPassId(), context.renderPassCompatibility().targetLayout(), modelShaderPlan)
                        : preparedShaders.resolve(renderable, context.shaderPassId(), context.renderPassCompatibility().targetLayout());
                if (prepared == null && shaderGroup != null) preparedShaders.skipped.record(shaderGroup.state(renderable), 1);
                if (prepared == null) continue;
                context.preparedShaderPass(prepared);
            }
            Shader3D shader = (provider != null ? provider : shaderProvider).shader(renderable, context);
            if (shader != activeShader) {
                if (activeShader != null) {
                    Shader3D previous = activeShader;
                    activeShader = null;
                    previous.end();
                }
                activeShader = shader;
                activeShader.begin(context);
            }
            activeShader.render(renderable);
            context.preparedShaderPass(null);
        }
        } catch (RuntimeException | Error next) { failure = next; }
        if (activeShader != null) {
            try { activeShader.end(); } catch (RuntimeException | Error next) { failure = appendFailure(failure,next); }
        }
        queue.clear();
        context.preparedShaderPass(null);
        pendingMaterialBindingCount = 0;
        rethrowFailure(failure);
    }

    /**
     * Flushes and ends the batch, releasing frame/pass references even when a shader
     * fails. Ends an owned pass; a supplied borrowed pass remains the caller's responsibility.
     */
    @Override
    public void end() {
        ensureDrawing();
        Throwable failure = null;
        try { flush(); } catch (RuntimeException | Error next) { failure = next; }
        drawing = false;
        queue.clear(); pendingMaterialBindingCount = 0;
        context.clear();
        if (ownsPass) {
            try { pass.end(); } catch (RuntimeException | Error next) { failure = appendFailure(failure,next); }
        }
        pass = null;
        ownsPass = false;
        rethrowFailure(failure);
    }

    private static Throwable appendFailure(Throwable first, Throwable next) {
        if (first == null) return next; if (first != next) first.addSuppressed(next); return first;
    }
    private static void rethrowFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }

    private void ensureCamera(Camera camera) {
        if (camera == null) {
            throw new FdxException("Camera cannot be null");
        }
    }

    private void ensureShaderPass(ShaderPassId shaderPassId) {
        if (shaderPassId == null) {
            throw new FdxException("ShaderPassId cannot be null");
        }
    }

    private void configureCommonProvider(ShaderProvider provider,
            int maxLights, int maxBones) {
        if (shaderGroup != null) throw new FdxException("Replace the shader group's definitions rather than its batch provider");
        if (provider == null || !provider.supportsPassResolution()) {
            throw new FdxException(
                    "ModelBatch common shader provider must support pass resolution");
        }
        commonShaderProvider = provider;
        if (preparation != null) {
            ModelShaderPlan replacement = new ModelShaderPlan(graphics, provider);
            PbrShaderProvider adapter = new PbrShaderProvider(graphics, new PbrShaderConfig()
                    .maxLights(maxLights).maxBones(maxBones).shaderPlan(replacement));
            if (preparedShaders != null) preparedShaders.dispose();
            if (ownsModelShaderPlan) modelShaderPlan.dispose();
            modelShaderPlan = replacement;
            ownsModelShaderPlan = true;
            preparedShaders = new ModelPreparedShaders(preparation, replacement);
            ownedShaderProvider = adapter;
            shaderProvider = adapter;
            commonShaderRevision = provider.revision();
            return;
        }
        ownedShaderProvider = PbrShaderProvider.common(graphics,
                new PbrShaderConfig().maxLights(maxLights)
                        .maxBones(maxBones),
                provider);
        shaderProvider = ownedShaderProvider;
        commonShaderRevision = provider.revision();
    }

    private void snapshotCommonProvider() {
        if (preparedShaders != null) preparedShaders.beginFrame();
        if (commonShaderProvider != null) {
            commonShaderRevision =
                    commonShaderProvider.revision();
        }
    }

    private void ensureCommonProviderStable() {
        if (commonShaderProvider == null) {
            return;
        }
        long revision = commonShaderProvider.revision();
        if (revision == commonShaderRevision) {
            return;
        }
        if (queue.size() > 0) {
            throw new FdxException(
                    "ModelBatch shader provider changed while renderables were pending");
        }
        commonShaderRevision = revision;
    }

    private void captureMaterialBinding(Material material) {
        ShaderMaterialBinding binding = material != null
                ? material.shaderBinding() : null;
        if (binding == null) {
            return;
        }
        for (int i = 0; i < pendingMaterialBindingCount; i++) {
            if (pendingMaterialBindings[i] == binding) {
                if (pendingMaterialRevisions[i]
                        != binding.revision()) {
                    throw new FdxException(
                            "Shader material changed while renderables were pending");
                }
                return;
            }
        }
        if (pendingMaterialBindingCount
                == pendingMaterialBindings.length) {
            ShaderMaterialBinding[] largerBindings =
                    new ShaderMaterialBinding[
                            pendingMaterialBindings.length * 2];
            long[] largerRevisions = new long[
                    pendingMaterialRevisions.length * 2];
            System.arraycopy(pendingMaterialBindings, 0,
                    largerBindings, 0,
                    pendingMaterialBindings.length);
            System.arraycopy(pendingMaterialRevisions, 0,
                    largerRevisions, 0,
                    pendingMaterialRevisions.length);
            pendingMaterialBindings = largerBindings;
            pendingMaterialRevisions = largerRevisions;
        }
        pendingMaterialBindings[pendingMaterialBindingCount] =
                binding;
        pendingMaterialRevisions[pendingMaterialBindingCount] =
                binding.revision();
        pendingMaterialBindingCount++;
    }

    private void validatePendingMaterialBindings() {
        for (int i = 0; i < pendingMaterialBindingCount; i++) {
            if (pendingMaterialBindings[i].revision()
                    != pendingMaterialRevisions[i]) {
                throw new FdxException(
                        "Shader material changed while renderables were pending");
            }
        }
    }

    private void ensureDrawing() {
        ensureNotDisposed();
        if (!drawing || pass == null || context.pass() == null) {
            throw new FdxException("ModelBatch.begin() must be called before rendering");
        }
        ensureCommonProviderStable();
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("ModelBatch has been disposed");
        }
    }

    private void retireOwnedShaderProvider() {
        if (!(ownedShaderProvider instanceof Disposable disposable)) {
            ownedShaderProvider = null;
            return;
        }
        if (retiredOwnedProviderCount
                == retiredOwnedProviders.length) {
            Disposable[] larger = new Disposable[
                    retiredOwnedProviders.length * 2];
            System.arraycopy(retiredOwnedProviders, 0, larger, 0,
                    retiredOwnedProviders.length);
            retiredOwnedProviders = larger;
        }
        retiredOwnedProviders[retiredOwnedProviderCount++] =
                disposable;
        ownedShaderProvider = null;
    }

    private void disposeOwnedShaderProviders() {
        if (ownedShaderProvider instanceof Disposable disposable) {
            disposable.dispose();
        }
        ownedShaderProvider = null;
        for (int i = 0; i < retiredOwnedProviderCount; i++) {
            retiredOwnedProviders[i].dispose();
            retiredOwnedProviders[i] = null;
        }
        retiredOwnedProviderCount = 0;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (preparedShaders != null) preparedShaders.dispose();
        if (ownsModelShaderPlan) modelShaderPlan.dispose();
        if (disposed) {
            return;
        }
        disposed = true;
        disposeOwnedShaderProviders();
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
