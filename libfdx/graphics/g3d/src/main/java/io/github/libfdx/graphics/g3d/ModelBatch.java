package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Camera;
import io.github.libfdx.math.Color;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.StoreOp;

/**
 * Represents a model batch.
 *
 * @author xpenatan
 */
public final class ModelBatch implements Batch3D {
    private final GraphicsContext graphics;
    private final DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
    private final boolean ownsDefaultShaderProvider;
    private Environment3D environment = new Environment3D();
    private ShaderProvider3D shaderProvider;
    private RenderContext3D context;
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
        if (config == null) {
            throw new FdxException("ModelBatchConfig cannot be null");
        }
        this.graphics = graphics;
        if (config.shaderProvider() != null) {
            shaderProvider = config.shaderProvider();
            ownsDefaultShaderProvider = false;
        }
        else {
            shaderProvider = new PbrShaderProvider(graphics, new PbrShaderConfig()
                    .maxLights(config.maxLights())
                    .maxBones(config.maxBones()));
            ownsDefaultShaderProvider = true;
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
     * Begins the operation.
     *
     * @param loadOp the load op
     * @param camera the camera
     */
    @Override
    public void begin(LoadOp loadOp, Camera camera) {
        ensureNotDisposed();
        ensureCamera(camera);
        GraphicsFrame frame = graphics.currentFrame();
        pass = frame.commandEncoder().beginRenderPass(RenderPassDescriptor
                .color(frame.colorAttachment(), loadOp != null ? loadOp : LoadOp.load(), StoreOp.store())
                .depthClear(1.0f)
                .label("model batch pass"));
        ownsPass = true;
        context = new RenderContext3D(graphics, camera, environment, null, pass);
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
        ensureNotDisposed();
        ensureCamera(camera);
        if (pass == null) {
            throw new FdxException("RenderPass cannot be null");
        }
        this.pass = pass;
        ownsPass = false;
        context = new RenderContext3D(graphics, camera, environment, null, pass);
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
        ensureNotDisposed();
        ensureCamera(camera);
        if (target == null) {
            throw new FdxException("RenderTarget3D cannot be null");
        }
        GraphicsFrame frame = graphics.currentFrame();
        pass = frame.commandEncoder().beginRenderPass(RenderPassDescriptor
                .color(target.colorAttachment(0), LoadOp.load(), StoreOp.store())
                .depthClear(1.0f)
                .label("model batch target pass"));
        ownsPass = true;
        context = new RenderContext3D(graphics, camera, environment, target, pass);
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
        this.environment = environment != null ? environment : new Environment3D();
        return this;
    }

    /**
     * Sets the shader provider and returns this model batch.
     *
     * @param shaderProvider the shader provider
     * @return this model batch for chaining
     */
    @Override
    public ModelBatch shaderProvider(ShaderProvider3D shaderProvider) {
        if (shaderProvider == null) {
            throw new FdxException("ShaderProvider3D cannot be null");
        }
        this.shaderProvider = shaderProvider;
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
        instance.collectRenderables(queue);
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
    }

    /**
     * Ends the operation.
     */
    @Override
    public void end() {
        ensureDrawing();
        flush();
        drawing = false;
        context = null;
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

    private void ensureDrawing() {
        ensureNotDisposed();
        if (!drawing || pass == null || context == null) {
            throw new FdxException("ModelBatch.begin() must be called before rendering");
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("ModelBatch has been disposed");
        }
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
        if (ownsDefaultShaderProvider && shaderProvider instanceof Disposable) {
            ((Disposable)shaderProvider).dispose();
        }
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
