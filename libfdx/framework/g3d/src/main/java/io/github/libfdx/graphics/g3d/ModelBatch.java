package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.math.Color;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.TextureView;


/**
 * Represents a model batch.
 *
 * @author xpenatan
 */
public final class ModelBatch implements Batch3D {
    private final GraphicsContext graphics;
    private final DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
    private final Environment3D defaultEnvironment = new Environment3D();
    private final RenderPassDescriptor framePassDescriptor = new RenderPassDescriptor()
            .label("model batch pass")
            .depthClear(1.0f);
    private final RenderPassDescriptor targetPassDescriptor = new RenderPassDescriptor()
            .label("model batch target pass")
            .depthClear(1.0f);
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
        if (config.shaderProvider() != null) {
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
                .colorAttachment(frame.colorAttachment())
                .colorLoadOp(loadOp != null ? loadOp : LoadOp.load())
                .colorStoreOp(StoreOp.store());
        pass = frame.commandEncoder().beginRenderPass(framePassDescriptor);
        ownsPass = true;
        context.reset(camera, environment, null, pass,
                shaderPassId);
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
        if (target.colorAttachmentCount() != 1) {
            throw new FdxException("ModelBatch currently supports exactly one RenderTarget3D color attachment");
        }
        if (target.depthAttachment() != null) {
            throw new FdxException("ModelBatch does not yet support an explicit RenderTarget3D depth attachment");
        }
        TextureView colorAttachment = target.colorAttachment(0);
        if (colorAttachment == null) {
            throw new FdxException("RenderTarget3D color attachment cannot be null");
        }
        GraphicsFrame frame = graphics.currentFrame();
        targetPassDescriptor
                .colorAttachment(colorAttachment)
                .colorLoadOp(LoadOp.load())
                .colorStoreOp(StoreOp.store());
        pass = frame.commandEncoder().beginRenderPass(targetPassDescriptor);
        ownsPass = true;
        context.reset(camera, environment, target, pass,
                shaderPassId);
        snapshotCommonProvider();
        drawing = true;
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
    public void render(Iterable<? extends ModelInstance> instances) {
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
        for (ModelInstance instance : instances) {
            render(instance);
        }
    }

    /**
     * Runs the flush step.
     */
    @Override
    public void flush() {
        ensureDrawing();
        if (queue.size() == 0) {
            return;
        }
        validatePendingMaterialBindings();
        queue.sort(context.camera());
        Shader3D activeShader = null;
        for (int i = 0; i < queue.size(); i++) {
            Renderable3D renderable = queue.get(i);
            ShaderProvider3D provider = renderable.material().shaderProvider();
            Shader3D shader = (provider != null ? provider : shaderProvider).shader(renderable, context);
            if (shader != activeShader) {
                if (activeShader != null) {
                    activeShader.end();
                }
                activeShader = shader;
                activeShader.begin(context);
            }
            activeShader.render(renderable);
        }
        if (activeShader != null) {
            activeShader.end();
        }
        queue.clear();
        pendingMaterialBindingCount = 0;
    }

    /**
     * Ends the operation.
     */
    @Override
    public void end() {
        ensureDrawing();
        flush();
        drawing = false;
        context.clear();
        if (ownsPass) {
            pass.end();
        }
        pass = null;
        ownsPass = false;
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
        if (provider == null || !provider.supportsPassResolution()) {
            throw new FdxException(
                    "ModelBatch common shader provider must support pass resolution");
        }
        commonShaderProvider = provider;
        ownedShaderProvider = PbrShaderProvider.common(graphics,
                new PbrShaderConfig().maxLights(maxLights)
                        .maxBones(maxBones),
                provider);
        shaderProvider = ownedShaderProvider;
        commonShaderRevision = provider.revision();
    }

    private void snapshotCommonProvider() {
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
