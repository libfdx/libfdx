package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.KeyComparison;
import io.github.libfdx.collections.ObjectIterable;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolation;
import io.github.libfdx.graphics.shader.reflection.ShaderInterpolationSampling;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.CompareFunction;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderBuiltinUsage;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderParameter;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadRecipe;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadResolver;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadVertexLayouts;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderSkippedDraws;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderTargetLayout;
import java.util.Arrays;
import java.util.Map;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceUse;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVariable;
import io.github.libfdx.graphics.shader.reflection.ShaderStageVisibility;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.math.Matrix4;

import io.github.libfdx.math.Vector3;

/**
 * Renders a directional-light shadow map into a sampled texture.
 *
 * <p>For {@link MaterialAlphaMode#BLEND} casters, the uniform
 * {@link MaterialAttributes#BASE_COLOR} alpha scales shadow opacity. Keep it
 * synchronized with a custom material's visual fade. This depth pass does not
 * sample texture alpha or evaluate custom surface graphs.</p>
 *
 * @author xpenatan
 */
public final class DirectionalShadowMap3D implements Disposable {
    /** Draws custom shadow casters into this map's active light pass. */
    @FunctionalInterface
    public interface ShadowPassRenderer {
        void render(RenderPass pass, Matrix4 lightViewProjection);
    }

    private static final int PRIMITIVE_TOPOLOGY_COUNT = PrimitiveTopology.values().length;
    private static final int CASTER_FADE_VOLUME = 0;
    private static final int CASTER_FADE_DISABLED = 1;
    private static final int CASTER_FADE_DISTANCE = 2;
    private static final float EPSILON = 0.000001f;
    private final GraphicsContext graphics;
    private final Texture texture;
    private final DefaultRenderTarget3D target;
    private final ShadowDepthShaderProvider shaderProvider;
    private final ModelBatch batch;
    private final RenderPassDescriptor passDescriptor;
    private final ShaderPreparation preparation;
    private final float[] cachedInputs = new float[42], currentInputs = new float[42];
    private long cachedCasterRevision;
    private Object cachedCasterSet;
    private boolean cacheValid;
    // Packed shadow depth is always forward 0..1, independently of the
    // view camera's reversed-depth setting. The shader remaps this GL clip range.
    private final Camera camera = new Camera()
            .clipDepthRange(ClipDepthRange.NEGATIVE_ONE_TO_ONE);
    private final Matrix4 lightViewProjection = new Matrix4();
    private final float[] casterFadeViewProjectionValues =
            new float[Matrix4.VALUE_COUNT];
    private final float[] casterTransformValues =
            new float[Matrix4.VALUE_COUNT];
    private final Matrix4 casterCullTransform = new Matrix4();
    private final float[] casterCullValues = new float[Matrix4.VALUE_COUNT];
    private final Vector3 casterFadeCameraPosition = new Vector3();
    private final Vector3 casterFadeCameraDirection =
            new Vector3(0.0f, 0.0f, -1.0f);
    private float centerX;
    private float centerY;
    private float centerZ;
    private float halfSize = 4.5f;
    private float near = 0.1f;
    private float far = 18.0f;
    private float bias = 0.022f;
    private boolean autoBias;
    private float strength = 0.62f;
    private float shadowFadeFraction = 0.20f;
    private float casterFadeStart;
    private float casterFadeEnd;
    private int casterFadeMode = CASTER_FADE_VOLUME;
    private boolean disposed;

    /**
     * Creates a directional shadow map.
     *
     * @param graphics the graphics context
     * @param width the width in pixels
     * @param height the height in pixels
     */
    public DirectionalShadowMap3D(GraphicsContext graphics, int width, int height) {
        this(graphics, width, height, null);
    }

    /**
     * Borrows preparation for nonblocking shadow shaders. Collect shaderPlan() requirements for
     * SHADOW and preparationTarget() before drawing. Call preparation.update before passes.
     * Missing casters are omitted while the target is cleared normally.
     */
    public DirectionalShadowMap3D(GraphicsContext graphics, int width, int height, ShaderPreparation preparation) {
        this(graphics, width, height, preparation, null);
    }

    /** Borrows a renderer-owned required-pass group. Register this map's shaderPlan with
     * group.usePlan(SHADOW, plan), declare its casters and freeze the group before all passes. */
    public DirectionalShadowMap3D(GraphicsContext graphics, int width, int height,
            ShaderPreparation preparation, ModelShaderGroup group) {
        this(graphics, width, height, preparation, group, null);
    }

    // Cascades share definitions but retain their own light matrices, targets and draw state.
    DirectionalShadowMap3D(GraphicsContext graphics, int width, int height,
            ShaderPreparation preparation, ModelShaderGroup group, ModelShaderPlan sharedPlan) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new FdxException("Shadow map dimensions must be greater than zero");
        }
        if (group != null && group.preparation() != preparation) throw new FdxException("Shadow group and preparation must match");
        this.graphics = graphics;
        this.preparation = preparation;
        texture = graphics.device().createTexture(TextureDescriptor
                .rgba8RenderTarget("directional shadow map", width, height)
                .filter(TextureFilter.NEAREST));
        ShadowDepthShaderProvider createdShader = null;
        ModelBatch createdBatch = null;
        try {
            target = new DefaultRenderTarget3D(width, height, texture.view());
            shaderProvider = createdShader = new ShadowDepthShaderProvider(graphics, sharedPlan);
            batch = createdBatch = new ModelBatch(graphics, new ModelBatchConfig().shaderProvider(shaderProvider)
                    .preparation(preparation).shaderPlan(shaderProvider.plan).shaderGroup(group));
            passDescriptor = RenderPassDescriptor.color(target.colorAttachment(0),
                    LoadOp.clear(1,1,1,0), StoreOp.store()).depthClear(1).label("directional shadow map pass");
            if (preparation != null) shaderProvider.plan.targets().register("directional-shadow", preparationTarget());
        } catch (RuntimeException | Error failure) {
            if (createdBatch != null) closeAfterFailure(createdBatch, failure);
            if (createdShader != null) closeAfterFailure(createdShader, failure);
            closeAfterFailure(texture, failure);
            throw failure;
        }
    }

    /**
     * Sets the light-space bounds and returns this shadow map.
     *
     * @param centerX the center x coordinate
     * @param centerY the center y coordinate
     * @param centerZ the center z coordinate
     * @param halfSize the half size of the orthographic light area
     * @param near the near distance
     * @param far the far distance
     * @return this shadow map for chaining
     */
    public DirectionalShadowMap3D bounds(float centerX, float centerY, float centerZ,
            float halfSize, float near, float far) {
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(centerZ)
                || !Float.isFinite(halfSize) || !Float.isFinite(near) || !Float.isFinite(far)) {
            throw new FdxException("Shadow bounds must be finite");
        }
        if (halfSize <= 0.0f) {
            throw new FdxException("Shadow map half size must be greater than zero");
        }
        if (near <= 0.0f || far <= near) {
            throw new FdxException("Shadow map near/far range is invalid");
        }
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.halfSize = halfSize;
        this.near = near;
        this.far = far;
        return this;
    }

    /**
     * Sets the depth comparison bias and returns this shadow map.
     *
     * @param bias the depth comparison bias
     * @return this shadow map for chaining
     */
    public DirectionalShadowMap3D bias(float bias) {
        if (!Float.isFinite(bias)) throw new FdxException("Shadow bias must be finite");
        this.bias = Math.max(0.0f, bias);
        return this;
    }

    /**
     * Enables resolution-aware receiver bias. It uses half a light-space texel
     * converted to normalized depth; the PBR receiver additionally compensates
     * each filter tap for surface slope. Recomputed after bounds changes.
     * Defaults to false, preserving the explicitly configured bias.
     * This is a scale-aware starting point, not a guarantee for arbitrary geometry.
     *
     * @param enabled whether to derive bias from this map's resolution and bounds
     * @return this shadow map for chaining
     */
    public DirectionalShadowMap3D autoBias(boolean enabled) {
        autoBias = enabled;
        return this;
    }

    /**
     * Sets the shadow strength and returns this shadow map.
     *
     * @param strength the shadow strength from 0 to 1
     * @return this shadow map for chaining
     */
    public DirectionalShadowMap3D strength(float strength) {
        if (!Float.isFinite(strength)) throw new FdxException("Shadow strength must be finite");
        this.strength = Math.max(0.0f, Math.min(1.0f, strength));
        return this;
    }

    /**
     * Sets the fraction of the shadow volume used to fade complete caster
     * shadows before their bounds reach its limit.
     *
     * @param fraction the fade fraction from 0 to 0.5
     * @return this shadow map for chaining
     */
    public DirectionalShadowMap3D shadowFadeFraction(float fraction) {
        if (!Float.isFinite(fraction)) {
            throw new FdxException("Shadow fade fraction must be finite");
        }
        shadowFadeFraction = Math.max(0.0f, Math.min(0.5f, fraction));
        return this;
    }

    /**
     * Renders model instances into this shadow map.
     *
     * @param light the directional light
     * @param instances the model instances
     */
    public void render(DirectionalLight light, ModelInstance[] instances) {
        ensureNotDisposed();
        if (instances == null) {
            throw new FdxException("ModelInstance array cannot be null");
        }
        RenderPass pass = beginPass(light);
        try {
            batch.begin(pass, camera, ShaderPassId.SHADOW);
            for (int i = 0; i < instances.length; i++) {
                if (instances[i] != null) {
                    batch.render(instances[i]);
                }
            }
            batch.end();
        }
        finally {
            pass.end();
        }
    }

    /**
     * Renders model instances into this shadow map.
     *
     * @param light the directional light
     * @param instances the model instances
     */
    public void render(DirectionalLight light, ObjectIterable<? extends ModelInstance> instances) {
        ensureNotDisposed();
        if (instances == null) {
            throw new FdxException("ModelInstance iterable cannot be null");
        }
        RenderPass pass = beginPass(light);
        try {
            batch.begin(pass, camera, ShaderPassId.SHADOW);
            if (instances instanceof ArrayView<?>) {
                ArrayView<?> values = (ArrayView<?>)instances;
                for (int i = 0; i < values.size(); i++) {
                    ModelInstance instance = (ModelInstance) values.get(i);
                    if (instance != null) {
                        batch.render(instance);
                    }
                }
            }
            else {
                ObjectIterator<? extends ModelInstance> iterator = instances.iterator();
                while (iterator.hasNext()) {
                    ModelInstance instance = iterator.next();
                    if (instance != null) {
                        batch.render(instance);
                    }
                }
            }
            batch.end();
        }
        finally {
            pass.end();
        }
    }

    /**
     * Renders custom shadow casters in the same pass and coordinate system used
     * by the standard model shadow path.
     *
     * @param light directional light defining the shadow camera
     * @param renderer custom pass renderer
     */
    public void render(DirectionalLight light, ShadowPassRenderer renderer) {
        ensureNotDisposed();
        if(renderer == null) {
            throw new FdxException("ShadowPassRenderer cannot be null");
        }
        RenderPass pass = beginPass(light);
        try {
            renderer.render(pass, lightViewProjection);
        }
        finally {
            pass.end();
        }
    }

    void renderRenderables(DirectionalLight light,
            ArrayView<Renderable3D> renderables) {
        ensureNotDisposed();
        RenderPass pass = beginPass(light);
        try {
            batch.begin(pass, camera, ShaderPassId.SHADOW);
            for (int i = 0; i < renderables.size(); i++) {
                Renderable3D renderable = renderables.get(i);
                if (intersectsLightVolume(renderable) && casterOpacity(renderable) > 0.0f) {
                    batch.render(renderable);
                }
            }
            batch.end();
        }
        finally {
            pass.end();
        }
    }

    boolean renderRenderablesIfNeeded(DirectionalLight light, ArrayView<Renderable3D> renderables,
            Object casterSet, long casterRevision) {
        ensureNotDisposed();
        updateLightCamera(light);
        lightViewProjection.copyValues(currentInputs, 0);
        System.arraycopy(casterFadeViewProjectionValues, 0, currentInputs, 16, 16);
        currentInputs[32]=casterFadeMode; currentInputs[33]=shadowFadeFraction;
        currentInputs[34]=casterFadeStart; currentInputs[35]=casterFadeEnd;
        currentInputs[36]=casterFadeCameraPosition.x(); currentInputs[37]=casterFadeCameraPosition.y();
        currentInputs[38]=casterFadeCameraPosition.z(); currentInputs[39]=casterFadeCameraDirection.x();
        currentInputs[40]=casterFadeCameraDirection.y(); currentInputs[41]=casterFadeCameraDirection.z();
        if (preparation == null && cacheValid && cachedCasterSet == casterSet && cachedCasterRevision == casterRevision
                && Arrays.equals(currentInputs, cachedInputs)) return false;
        renderRenderables(light, renderables);
        System.arraycopy(currentInputs, 0, cachedInputs, 0, currentInputs.length);
        cachedCasterSet=casterSet; cachedCasterRevision=casterRevision; cacheValid=true;
        return true;
    }

    /** Borrowed definitions for this map; valid only for the preparation constructor. */
    public ModelShaderPlan shaderPlan() {
        ensureNotDisposed();
        if (preparation == null) throw new FdxException("This shadow map uses synchronous shader creation");
        return shaderProvider.plan;
    }

    /** Attachment compatibility for collecting SHADOW requirements before drawing. */
    public RenderTargetLayout preparationTarget() { return passDescriptor.compatibility().targetLayout(); }

    /** Logical caster omissions in the current preparation frame, summed across this map's passes. */
    public ShaderSkippedDraws skippedDrawsLastFrame() { return batch.skippedDrawsLastFrame(); }

    /** Resolves this map's portable depth recipes; register current target roles on its shader plan first. */
    public ShaderPreloadResolver.Resolution resolvePreload(ShaderPreloadRecipe recipe) {
        ModelShaderPlan plan = shaderPlan();
        if (!recipe.factory().equals("libfdx.directional-shadow")) return ShaderPreloadResolver.Resolution.requiresInput("Register factory " + recipe.factory());
        if (recipe.version() != 1 || !recipe.conditions().isEmpty()) return ShaderPreloadResolver.Resolution.stale("Shadow recipe schema/conditions changed");
        RenderTargetLayout target = plan.targets().apply(recipe.targetRole());
        if (target == null) return ShaderPreloadResolver.Resolution.requiresInput("Map target role " + recipe.targetRole());
        ShaderRequest request = ShaderRequest.builder(ShaderPassId.SHADOW).profile(ModelShaderPlan.profile(graphics.device()))
                .renderPass(RenderPassCompatibility.layout(target)).variantKey(recipe.parameters().get("variant"))
                .topology(PrimitiveTopology.valueOf(recipe.parameters().get("topology")))
                .vertexLayouts(ShaderPreloadVertexLayouts.decode(recipe.parameters().get("vertexLayouts"))).build();
        return shaderProvider.supports(request) ? ShaderPreloadResolver.Resolution.resolved(shaderProvider, request)
                : ShaderPreloadResolver.Resolution.unsupported("Shadow recipe is incompatible with this target or mesh");
    }

    /** Invalidates optional cascade reuse after external/custom writes to this map. */
    public void invalidateCache() { cacheValid=false; cachedCasterSet=null; }

    private static void closeAfterFailure(Disposable owned, Throwable failure) {
        try { owned.dispose(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
    }

    /**
     * Returns the shadow texture.
     *
     * @return the shadow texture
     */
    public Texture texture() {
        return texture;
    }

    /**
     * Returns the light view projection matrix.
     *
     * @return the light view projection matrix
     */
    public Matrix4 lightViewProjection() {
        return lightViewProjection;
    }

    /**
     * Returns the effective normalized depth comparison bias. In automatic mode
     * this is recalculated from the current bounds and texture resolution.
     *
     * @return the depth comparison bias
     */
    public float bias() {
        return autoBias ? Math.max(0.0000002f,
                halfSize / Math.min(texture.width(), texture.height()) / (far - near)) : bias;
    }

    /**
     * Returns the shadow strength.
     *
     * @return the shadow strength
     */
    public float strength() {
        return strength;
    }

    /**
     * Returns the fraction of the shadow volume used for complete-caster
     * fading.
     *
     * @return the shadow fade fraction
     */
    public float shadowFadeFraction() {
        return shadowFadeFraction;
    }

    void disableCasterFade() {
        casterFadeMode = CASTER_FADE_DISABLED;
    }

    void casterDistanceFade(Vector3 cameraPosition, Vector3 cameraDirection,
            float fadeStart, float fadeEnd) {
        if (cameraPosition == null || cameraDirection == null
                || fadeEnd <= fadeStart) {
            disableCasterFade();
            return;
        }
        casterFadeCameraPosition.set(cameraPosition);
        casterFadeCameraDirection.set(cameraDirection);
        float directionLength = length(casterFadeCameraDirection.x(),
                casterFadeCameraDirection.y(),
                casterFadeCameraDirection.z());
        if (directionLength <= EPSILON) {
            disableCasterFade();
            return;
        }
        casterFadeCameraDirection.scale(1.0f / directionLength);
        casterFadeStart = fadeStart;
        casterFadeEnd = fadeEnd;
        casterFadeMode = CASTER_FADE_DISTANCE;
    }

    private RenderPass beginPass(DirectionalLight light) {
        invalidateCache();
        updateLightCamera(light);
        GraphicsFrame frame = graphics.currentFrame();
        return frame.commandEncoder().beginRenderPass(passDescriptor);
    }

    private void updateLightCamera(DirectionalLight light) {
        if (light == null) {
            throw new FdxException("DirectionalLight cannot be null");
        }
        Vector3 direction = light.direction();
        float directionX = direction.x();
        float directionY = direction.y();
        float directionZ = direction.z();
        float length = (float)Math.sqrt(directionX * directionX + directionY * directionY
                + directionZ * directionZ);
        if (length <= 0.000001f) {
            directionX = 0.0f;
            directionY = -1.0f;
            directionZ = 0.0f;
        }
        else {
            float invLength = 1.0f / length;
            directionX *= invLength;
            directionY *= invLength;
            directionZ *= invLength;
        }

        configureLightCamera(directionX, directionY, directionZ,
                halfSize, near, far);
        lightViewProjection.set(camera.combined());
        lightViewProjection.copyValues(casterFadeViewProjectionValues, 0);

        // A regular shadow map needs real raster coverage outside the logical
        // limit. Otherwise a long projected shadow can still hit the texture
        // boundary while its caster is fading. The logical matrix above is
        // retained for opacity calculation; this expanded matrix is the one
        // used to render and sample the shadow map.
        if (casterFadeMode == CASTER_FADE_VOLUME
                && shadowFadeFraction > 0.0f) {
            float lateralGuard = halfSize * 2.0f * shadowFadeFraction;
            float depthGuard = (far - near) * shadowFadeFraction;
            configureLightCamera(directionX, directionY, directionZ,
                    halfSize + lateralGuard, near,
                    far + depthGuard * 2.0f);
            lightViewProjection.set(camera.combined());
        }
    }

    private void configureLightCamera(float directionX, float directionY,
            float directionZ, float cameraHalfSize, float cameraNear,
            float cameraFar) {
        float distance = (cameraNear + cameraFar) * 0.5f;
        float eyeX = centerX - directionX * distance;
        float eyeY = centerY - directionY * distance;
        float eyeZ = centerZ - directionZ * distance;
        camera.projection(CameraProjection.ORTHOGRAPHIC)
                .viewport(cameraHalfSize * 2.0f,
                        cameraHalfSize * 2.0f)
                .zoom(1.0f)
                .nearFar(cameraNear, cameraFar)
                .position(eyeX, eyeY, eyeZ)
                .lookAt(centerX, centerY, centerZ);
        setStableLightUp(directionX, directionY, directionZ);
    }

    /** Conservative per-cascade rejection in the fitted light camera, never the view camera. */
    private boolean intersectsLightVolume(Renderable3D renderable) {
        if (renderable.material().shaderProvider() != null
                || renderable.material().shaderBinding() != null) return true;
        BoundingBox bounds = renderable.cullingBounds();
        if (bounds == null) return true;
        Vector3 min = bounds.min(), max = bounds.max();
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) return true;
        casterCullTransform.setToMul(lightViewProjection, renderable.worldTransform())
                .copyValues(casterCullValues, 0);
        int outside = 63;
        for (int corner = 0; corner < 8; corner++) {
            float x = (corner & 1) == 0 ? min.x() : max.x();
            float y = (corner & 2) == 0 ? min.y() : max.y();
            float z = (corner & 4) == 0 ? min.z() : max.z();
            float px = transformX(casterCullValues, x, y, z);
            float py = transformY(casterCullValues, x, y, z);
            float pz = transformZ(casterCullValues, x, y, z);
            float w = casterCullValues[3]*x + casterCullValues[7]*y
                    + casterCullValues[11]*z + casterCullValues[15];
            if (!Float.isFinite(px) || !Float.isFinite(py) || !Float.isFinite(pz) || !Float.isFinite(w)) return true;
            outside &= (px < -w ? 1 : 0) | (px > w ? 2 : 0)
                    | (py < -w ? 4 : 0) | (py > w ? 8 : 0)
                    | (pz < -w ? 16 : 0) | (pz > w ? 32 : 0);
            if (outside == 0) return true;
        }
        return false;
    }

    private float casterOpacity(Renderable3D renderable) {
        return materialCasterOpacity(renderable.material()) * casterVolumeOpacity(renderable);
    }

    /** Uniform base-color alpha fades BLEND casters; texture alpha is not sampled by this pass. */
    static float materialCasterOpacity(Material material) {
        return material.alphaMode() == MaterialAlphaMode.BLEND
                ? Math.max(0, Math.min(1, MaterialAttributes.baseColor(material).alpha())) : 1;
    }

    private float casterVolumeOpacity(Renderable3D renderable) {
        if (casterFadeMode == CASTER_FADE_DISABLED || renderable == null) {
            return 1.0f;
        }
        BoundingBox bounds = renderable.cullingBounds() != null ? renderable.cullingBounds() : renderable.bounds();
        if (bounds == null) {
            return 1.0f;
        }
        renderable.worldTransform().copyValues(casterTransformValues, 0);
        Vector3 min = bounds.min();
        Vector3 max = bounds.max();
        if (casterFadeMode == CASTER_FADE_DISTANCE) {
            // Drive the complete draw from one stable model-space point. Using
            // the farthest bounds corner makes large casters finish their fade
            // too early and causes separately rendered pieces of a model to
            // disappear in a visible sequence. The expanded shadow-map guard
            // keeps the complete projected bounds available while this
            // center-based opacity reaches zero.
            float localX = (min.x() + max.x()) * 0.5f;
            float localY = (min.y() + max.y()) * 0.5f;
            float localZ = (min.z() + max.z()) * 0.5f;
            float worldX = transformX(casterTransformValues,
                    localX, localY, localZ);
            float worldY = transformY(casterTransformValues,
                    localX, localY, localZ);
            float worldZ = transformZ(casterTransformValues,
                    localX, localY, localZ);
            float centerDistance = dot(
                    worldX - casterFadeCameraPosition.x(),
                    worldY - casterFadeCameraPosition.y(),
                    worldZ - casterFadeCameraPosition.z(),
                    casterFadeCameraDirection.x(),
                    casterFadeCameraDirection.y(),
                    casterFadeCameraDirection.z());
            return 1.0f - smoothstep(casterFadeStart, casterFadeEnd,
                    centerDistance);
        }
        if (shadowFadeFraction <= 0.0f) {
            return 1.0f;
        }
        float closestEdge = Float.POSITIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            float localX = (corner & 1) == 0 ? min.x() : max.x();
            float localY = (corner & 2) == 0 ? min.y() : max.y();
            float localZ = (corner & 4) == 0 ? min.z() : max.z();
            float worldX = transformX(casterTransformValues,
                    localX, localY, localZ);
            float worldY = transformY(casterTransformValues,
                    localX, localY, localZ);
            float worldZ = transformZ(casterTransformValues,
                    localX, localY, localZ);
            float clipX = transformX(casterFadeViewProjectionValues,
                    worldX, worldY, worldZ);
            float clipY = transformY(casterFadeViewProjectionValues,
                    worldX, worldY, worldZ);
            float clipZ = transformZ(casterFadeViewProjectionValues,
                    worldX, worldY, worldZ);
            float clipW = casterFadeViewProjectionValues[3] * worldX
                    + casterFadeViewProjectionValues[7] * worldY
                    + casterFadeViewProjectionValues[11] * worldZ
                    + casterFadeViewProjectionValues[15];
            if (Math.abs(clipW) <= EPSILON) {
                return 0.0f;
            }
            float inverseW = 1.0f / clipW;
            float edgeX = (1.0f - Math.abs(clipX * inverseW)) * 0.5f;
            float edgeY = (1.0f - Math.abs(clipY * inverseW)) * 0.5f;
            float edgeZ = (1.0f - Math.abs(clipZ * inverseW)) * 0.5f;
            closestEdge = Math.min(closestEdge,
                    Math.min(edgeX, Math.min(edgeY, edgeZ)));
        }
        return smoothstep(0.0f, shadowFadeFraction, closestEdge);
    }

    private static float transformX(float[] matrix, float x, float y,
            float z) {
        return matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
    }

    private static float transformY(float[] matrix, float x, float y,
            float z) {
        return matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
    }

    private static float transformZ(float[] matrix, float x, float y,
            float z) {
        return matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
    }

    private static float dot(float x, float y, float z,
            float axisX, float axisY, float axisZ) {
        return x * axisX + y * axisY + z * axisZ;
    }

    private static float length(float x, float y, float z) {
        return (float)Math.sqrt(x * x + y * y + z * z);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge1 <= edge0) {
            return value >= edge1 ? 1.0f : 0.0f;
        }
        float t = Math.max(0.0f, Math.min(1.0f,
                (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    private void setStableLightUp(float directionX, float directionY, float directionZ) {
        float upX = -directionX * directionY;
        float upY = 1.0f - directionY * directionY;
        float upZ = -directionZ * directionY;
        float length = (float)Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        if (length <= 0.0001f) {
            upX = -directionX * directionZ;
            upY = -directionY * directionZ;
            upZ = 1.0f - directionZ * directionZ;
            length = (float)Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        }
        if (length <= 0.0001f) {
            camera.up(1.0f, 0.0f, 0.0f);
            return;
        }
        float invLength = 1.0f / length;
        camera.up(upX * invLength, upY * invLength, upZ * invLength);
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("DirectionalShadowMap3D has been disposed");
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
        invalidateCache();
        Throwable failure = null;
        try { batch.dispose(); } catch (RuntimeException | Error ex) { failure = ex; }
        try { shaderProvider.dispose(); } catch (RuntimeException | Error ex) {
            if (failure == null) failure = ex; else if (ex != failure) failure.addSuppressed(ex);
        }
        try { texture.dispose(); } catch (RuntimeException | Error ex) {
            if (failure == null) failure = ex; else if (ex != failure) failure.addSuppressed(ex);
        }
        if (failure instanceof RuntimeException ex) throw ex;
        if (failure instanceof Error ex) throw ex;
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

    private final class ShadowDepthShaderProvider implements PreparedShaderProvider3D, Disposable {
        private final ShadowDepthShader shader;
        private ShadowDepthShader skinnedShader;
        private final ModelShaderPlan plan;
        private final boolean ownsPlan;

        ShadowDepthShaderProvider(GraphicsContext graphics, ModelShaderPlan sharedPlan) {
            ownsPlan = preparation != null && sharedPlan == null;
            plan = ownsPlan ? new ModelShaderPlan(graphics, this) : sharedPlan;
            shader = new ShadowDepthShader(graphics,
                    DirectionalShadowMap3D.this, false);
        }

        @Override
        public ModelShaderPlan preparationPlan() { return plan; }
        @Override
        public GraphicsDevice preparationDevice() { return graphics.device(); }
        @Override
        public boolean supports(ShaderRequest request) {
            if (isDisposed() || !request.passId().equals(ShaderPassId.SHADOW) || request.renderPass() == null
                    || !request.renderPass().targetLayout().hasDepthStencil()
                    || request.renderPass().targetLayout().colorAttachmentCount() != 1
                    || request.renderPass().targetLayout().colorFormat(0) != TextureFormat.RGBA8_UNORM) return false;
            VertexLayout[] layouts = request.vertexLayouts();
            return layouts.length == 1 && layouts[0].attributeCount() > 0
                    && layouts[0].attribute(0).location() == 0 && layouts[0].attribute(0).format() == VertexFormat.FLOAT32X3;
        }
        @Override
        public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
            boolean skinned = request.variantKey().startsWith("skinned");
            return graphics.device().prepareRenderPipeline(new ShaderPipelineRequest(
                    ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> ShaderModuleDescriptor.wgsl(
                            "directional shadow depth", ShadowDepthShader.source(skinned))),
                    new RenderPipelineDescriptor().label("directional shadow depth")
                            .renderTargetLayout(request.renderPass().targetLayout()).primitiveTopology(request.topology())
                            .depthStencilState(DepthStencilState.builder(TextureFormat.DEPTH32_FLOAT)
                                    .depthWriteEnabled(true).depthCompare(CompareFunction.LESS_EQUAL).build())
                            .vertexLayouts(request.vertexLayouts()), request.passId(), revision()));
        }
        @Override
        public ShaderPreloadRecipe preloadRecipe(ShaderRequest request, String role) {
            return new ShaderPreloadRecipe("libfdx.directional-shadow", 1, role, Map.of(
                    "variant", request.variantKey(), "topology", request.topology().name(),
                    "vertexLayouts", ShaderPreloadVertexLayouts.encode(request.vertexLayouts())), Map.of());
        }

        @Override
        public Shader3D shader(Renderable3D renderable, RenderContext3D context) {
            ShadowDepthShader selected=shader;
            if (renderable != null && renderable.meshPart().mesh().hasPbrSkinning()) {
                if (skinnedShader == null) skinnedShader=new ShadowDepthShader(graphics,DirectionalShadowMap3D.this,true);
                selected=skinnedShader;
            }
            if (!selected.canRender(renderable)) {
                throw new FdxException("Shadow shader requires meshes with a position attribute at location 0");
            }
            return selected;
        }

        @Override
        public void dispose() {
            Throwable first=null;
            try { shader.dispose(); } catch (RuntimeException | Error failure) { first=failure; }
            try { if (skinnedShader != null) skinnedShader.dispose(); }
            catch (RuntimeException | Error failure) { if (first==null) first=failure; else first.addSuppressed(failure); }
            try { if (ownsPlan) plan.dispose(); }
            catch (RuntimeException | Error failure) { if (first==null) first=failure; else first.addSuppressed(failure); }
            if (first instanceof RuntimeException) throw (RuntimeException)first;
            if (first instanceof Error) throw (Error)first;
        }

        @Override
        public boolean isDisposed() {
            return shader.isDisposed();
        }
    }

    private static final class ShadowDepthShader implements Shader3D {
        private static final String SOURCE = """
                struct VertexInput {
                    @location(0) position : vec3f,
                    //__SKIN_INPUTS__
                };
                struct VertexOutput {
                    @builtin(position) position : vec4f,
                    @location(0) depth : f32,
                };
                struct Uniforms {
                    model : mat4x4<f32>,
                    viewProjection : mat4x4<f32>,
                    shadowParams : vec4f,
                    //__SKIN_UNIFORMS__
                };
                @group(0) @binding(0) var<uniform> uniforms : Uniforms;
                @vertex
                fn vertexMain(input : VertexInput) -> VertexOutput {
                    var output : VertexOutput;
                    var localPosition = vec4f(input.position, 1.0);
                    //__SKIN_TRANSFORM__
                    let clip = uniforms.viewProjection * uniforms.model * localPosition;
                    output.position = clip;
                    // libFDX cameras use the portable OpenGL-style -w..w
                    // clip-depth convention. WGSL/WebGPU clips z to 0..w,
                    // which discarded the near half of an orthographic shadow
                    // volume. Remap only the raster position; the separately
                    // encoded depth below remains in the same 0..1 space used
                    // by PBR shadow sampling on every provider.
                    output.position.z = clip.z * 0.5 + clip.w * 0.5;
                    // Keep this value unclamped until after raster clipping.
                    // Clamping per vertex corrupts the interpolated depth when
                    // a large triangle crosses the light camera near/far plane.
                    output.depth = (clip.z / clip.w) * 0.5 + 0.5;
                    return output;
                }
                @fragment
                fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                    let depth = clamp(input.depth, 0.0, 0.999999);
                    let raw = fract(depth * vec2f(1.0, 255.0));
                    return vec4f(
                            raw.x - raw.y / 255.0,
                            raw.y,
                            0.0,
                            uniforms.shadowParams.x);
                }
                """;
        private static final ShaderValueType MATRIX4 = ShaderValueType
                .matrix(ShaderScalarType.F32, 4, 4, 16)
                .named("mat4x4<f32>");
        private static final ShaderValueType FLOAT4 = ShaderValueType
                .vector(ShaderScalarType.F32, 4)
                .named("vec4<f32>");
        private static final ShaderParameterLayout UNIFORM_LAYOUT =
                ShaderParameterLayout.of(144, 16,
                        ShaderParameter.of("model", MATRIX4, 0, 64, 16),
                        ShaderParameter.of("viewProjection", MATRIX4, 64, 64, 16),
                        ShaderParameter.of("shadowParams", FLOAT4, 128, 16, 16));
        private static final int SKINNED_SIZE=160+SkinningShader3D.MAX_BONES*64;
        private static final ShaderParameterLayout SKINNED_LAYOUT=ShaderParameterLayout.of(SKINNED_SIZE,16,
                ShaderParameter.of("model",MATRIX4,0,64,16),
                ShaderParameter.of("viewProjection",MATRIX4,64,64,16),
                ShaderParameter.of("shadowParams",FLOAT4,128,16,16),
                ShaderParameter.of("skinningParams",FLOAT4,144,16,16),
                ShaderParameter.of("boneMatrices",ShaderValueType.array(MATRIX4,SkinningShader3D.MAX_BONES,64)
                        .named("array<mat4x4<f32>, 64>"),160,SkinningShader3D.MAX_BONES*64,16));
        private static final ShaderReflection REFLECTION = reflection(false);
        private static final ShaderReflection SKINNED_REFLECTION = reflection(true);
        private final GraphicsContext graphics;
        private final DirectionalShadowMap3D shadowMap;
        private final ShaderModule shaderModule;
        private final boolean skinned;
        private final ObjectMap<VertexLayout, RenderPipeline[]> pipelines =
                new ObjectMap<VertexLayout, RenderPipeline[]>(KeyComparison.IDENTITY);
        private final ShaderParameterBlock uniformBlock;
        private final ShaderParameterHandle modelHandle,viewProjectionHandle,shadowParamsHandle,skinningHandle;
        private final ShaderParameterHandle[] boneHandles;
        private final float[] boneValues;
        private final float[] modelMatrix = new float[Matrix4.VALUE_COUNT];
        private final float[] viewProjectionMatrix = new float[Matrix4.VALUE_COUNT];
        private RenderContext3D context;
        private boolean disposed;

        ShadowDepthShader(GraphicsContext graphics,
                DirectionalShadowMap3D shadowMap, boolean skinned) {
            this.graphics = graphics;
            this.shadowMap = shadowMap;
            this.skinned=skinned;
            ShaderParameterLayout layout=skinned ? SKINNED_LAYOUT : UNIFORM_LAYOUT;
            uniformBlock=ShaderParameterBlock.allocate(layout);
            modelHandle=layout.requireHandle("model"); viewProjectionHandle=layout.requireHandle("viewProjection");
            shadowParamsHandle=layout.requireHandle("shadowParams");
            skinningHandle=skinned ? layout.requireHandle("skinningParams") : null;
            boneHandles=new ShaderParameterHandle[skinned ? SkinningShader3D.MAX_BONES : 0];
            boneValues=new float[boneHandles.length*16];
            for (int i=0;i<boneHandles.length;i++) boneHandles[i]=layout.requireArrayElementHandle("boneMatrices",i);
            shaderModule = shadowMap.preparation == null ? graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl(
                    "directional shadow depth", source(skinned))) : null;
        }

        private static String source(boolean skinned) {
            if (!skinned) return SOURCE;
            return SkinningShader3D.FUNCTION+SOURCE.replace("//__SKIN_INPUTS__",
                    "@location(6) joints : vec4f, @location(7) weights : vec4f,")
                    .replace("//__SKIN_UNIFORMS__","skinningParams : vec4f, boneMatrices : array<mat4x4<f32>, 64>,")
                    .replace("//__SKIN_TRANSFORM__","localPosition = skinTransform(input.joints, input.weights) * localPosition;");
        }

        @Override
        public boolean canRender(Renderable3D renderable) {
            if (renderable == null || renderable.meshPart() == null) {
                return false;
            }
            VertexLayout layout = renderable.meshPart().mesh().vertexLayout();
            if (layout.attributeCount() == 0) {
                return false;
            }
            VertexAttribute position = layout.attribute(0);
            return skinned == renderable.meshPart().mesh().hasPbrSkinning() && position.location() == 0
                    && position.format() == VertexFormat.FLOAT32X3;
        }

        @Override
        public void begin(RenderContext3D context) {
            if (disposed) {
                throw new FdxException("Shadow depth shader has been disposed");
            }
            this.context = context;
        }

        @Override
        public void render(Renderable3D renderable) {
            if (context == null) {
                throw new FdxException("Shader3D.begin() must be called before render");
            }
            MeshPart meshPart = renderable.meshPart();
            Mesh mesh = meshPart.mesh();
            if (skinned) {
                SkinningPalette palette=renderable.skinningPalette();
                int count=palette == null ? 0 : palette.size();
                if (count>SkinningShader3D.MAX_BONES)
                    throw new FdxException("Shadow skin exceeds "+SkinningShader3D.MAX_BONES+" bones; use CPU skinning");
                uniformBlock.setFloat4(skinningHandle,count,0,0,0);
                if (count>0) {
                    palette.copyValues(boneValues);
                    for (int i=0;i<count;i++) uniformBlock.setFloatMatrix(boneHandles[i],boneValues,i*16);
                }
            }
            RenderPass pass = context.pass();
            if (shadowMap.preparation != null) {
                if (context.preparedShaderPass() == null) throw new FdxException("Shadow renderer requires a prepared pass");
                pass.setPipeline(context.preparedShaderPass().pipeline());
            } else pass.setPipeline(pipeline(mesh.vertexLayout(), meshPart.primitiveTopology()));
            pass.setVertexBuffer(mesh.vertexBuffer());
            renderable.worldTransform().copyValues(modelMatrix, 0);
            context.camera().combined().copyValues(viewProjectionMatrix, 0);
            uniformBlock.setFloatMatrix(modelHandle, modelMatrix, 0);
            uniformBlock.setFloatMatrix(viewProjectionHandle,
                    viewProjectionMatrix, 0);
            uniformBlock.setFloat4(shadowParamsHandle,
                    shadowMap.casterOpacity(renderable), 0.0f, 0.0f, 0.0f);
            pass.setParameterBlock(0, 0, uniformBlock);
            int indexCount = meshPart.indexCount() > 0 ? meshPart.indexCount() : mesh.indexCount();
            if (indexCount > 0) {
                pass.setIndexBuffer(mesh.indexBuffer());
                pass.drawIndexed(indexCount, 1, meshPart.firstIndex(), 0, 0);
                return;
            }
            int vertexCount = meshPart.vertexCount() > 0 ? meshPart.vertexCount() : mesh.vertexCount();
            pass.draw(vertexCount, 1, meshPart.firstVertex(), 0);
        }

        private static ShaderReflection reflection(boolean skinned) {
            int size=skinned ? SKINNED_SIZE : 144;
            ShaderParameterLayout layout=skinned ? SKINNED_LAYOUT : UNIFORM_LAYOUT;
            ShaderValueType f32 =
                    ShaderValueType.scalar(ShaderScalarType.F32);
            ShaderValueType float3 =
                    ShaderValueType.vector(ShaderScalarType.F32, 3);
            ShaderValueType float4 =
                    ShaderValueType.vector(ShaderScalarType.F32, 4);
            ShaderStageVariable vertexPosition = ShaderStageVariable.of(
                    "input.position", "position", 0, -1, -1, float3,
                    ShaderInterpolation.PERSPECTIVE,
                    ShaderInterpolationSampling.CENTER);
            ShaderStageVariable[] inputs=skinned ? new ShaderStageVariable[]{vertexPosition,
                    ShaderStageVariable.of("input.joints","joints",6,-1,-1,float4,ShaderInterpolation.PERSPECTIVE,ShaderInterpolationSampling.CENTER),
                    ShaderStageVariable.of("input.weights","weights",7,-1,-1,float4,ShaderInterpolation.PERSPECTIVE,ShaderInterpolationSampling.CENTER)}
                    : new ShaderStageVariable[]{vertexPosition};
            ShaderStageVariable vertexDepth = ShaderStageVariable.of(
                    "<retval>.depth", "depth", 0, -1, -1, f32,
                    ShaderInterpolation.PERSPECTIVE,
                    ShaderInterpolationSampling.CENTER);
            ShaderStageVariable fragmentDepth = ShaderStageVariable.of(
                    "input.depth", "depth", 0, -1, -1, f32,
                    ShaderInterpolation.PERSPECTIVE,
                    ShaderInterpolationSampling.CENTER);
            ShaderStageVariable fragmentColor = ShaderStageVariable.of(
                    "<retval>", "", 0, -1, -1, float4,
                    ShaderInterpolation.PERSPECTIVE,
                    ShaderInterpolationSampling.CENTER);
            ShaderBinding uniforms = ShaderBinding.builder(0, 0,
                            "uniforms", ShaderResourceKind.UNIFORM_BUFFER)
                    .visibility(ShaderStageVisibility.of(
                            ShaderStage.VERTEX, ShaderStage.FRAGMENT))
                    .access(ShaderResourceAccess.READ)
                    .buffer(size, size, 16, layout)
                    .build();
            return ShaderReflection.complete(ShaderProfile.PORTABLE_WEBGPU,
                    new ShaderEntryPoint[] {
                            ShaderEntryPoint.builder("vertexMain",
                                            ShaderStage.VERTEX)
                                    .builtins(ShaderBuiltinUsage.POSITION, -1)
                                    .inputs(inputs)
                                    .outputs(vertexDepth)
                                    .resources(ShaderResourceUse.of(0, 0, size))
                                    .build(),
                            ShaderEntryPoint.builder("fragmentMain",
                                            ShaderStage.FRAGMENT)
                                    .builtins(ShaderBuiltinUsage.POSITION, -1)
                                    .inputs(fragmentDepth)
                                    .resources(ShaderResourceUse.of(0, 0, size))
                                    .outputs(fragmentColor)
                                    .build()
                    },
                    new ShaderBinding[] { uniforms }, new String[0]);
        }

        @Override
        public void end() {
            context = null;
        }

        private RenderPipeline pipeline(VertexLayout vertexLayout, PrimitiveTopology topology) {
            PrimitiveTopology actualTopology = topology != null ? topology : PrimitiveTopology.TRIANGLE_LIST;
            RenderPipeline[] variants = pipelines.get(vertexLayout);
            if (variants == null) {
                variants = new RenderPipeline[PRIMITIVE_TOPOLOGY_COUNT];
                pipelines.put(vertexLayout, variants);
            }
            int slot = actualTopology.ordinal();
            RenderPipeline pipeline = variants[slot];
            if (pipeline == null) {
                pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                        .shader(shaderModule, TextureFormat.RGBA8_UNORM)
                        .label("directional shadow depth")
                        .shaderReflection(skinned ? SKINNED_REFLECTION : REFLECTION)
                        .colorTargets(ColorTargetState.opaque(
                                TextureFormat.RGBA8_UNORM))
                        .primitiveTopology(actualTopology)
                        .depthStencilState(DepthStencilState.builder(TextureFormat.DEPTH32_FLOAT)
                                .depthWriteEnabled(true)
                                .depthCompare(CompareFunction.LESS_EQUAL).build())
                        .vertexLayout(vertexLayout));
                variants[slot] = pipeline;
            }
            return pipeline;
        }

        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            Throwable first=null;
            ObjectIterator<RenderPipeline[]> iterator = pipelines.values().iterator();
            while (iterator.hasNext()) {
                RenderPipeline[] variants = iterator.next();
                for (int i = 0; i < variants.length; i++) {
                    if (variants[i] != null) {
                        try { variants[i].dispose(); }
                        catch (RuntimeException | Error failure) { if (first==null) first=failure; else first.addSuppressed(failure); }
                    }
                }
            }
            pipelines.clear();
            try { if (shaderModule != null) shaderModule.dispose(); }
            catch (RuntimeException | Error failure) { if (first==null) first=failure; else first.addSuppressed(failure); }
            if (first instanceof RuntimeException) throw (RuntimeException)first;
            if (first instanceof Error) throw (Error)first;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

}
