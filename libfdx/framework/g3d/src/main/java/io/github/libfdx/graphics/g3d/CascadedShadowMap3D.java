package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.ObjectIterable;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;

/**
 * Manages multiple directional shadow maps split along a view camera frustum.
 *
 * @author xpenatan
 */
public final class CascadedShadowMap3D implements Disposable {
    private static final int MAX_CASCADES = 4;
    private static final float EPSILON = 0.000001f;
    private static final float DEFAULT_MIN_TEXEL_BIAS = 2.5f;
    private final DirectionalShadowMap3D[] cascades;
    private final float[] splitDistances;
    private final float[] centerX;
    private final float[] centerY;
    private final float[] centerZ;
    private final float[] halfSizes;
    private final float[] depthHalfSizes;
    private final float[] cascadeBiases;
    private final float[] transformValues = new float[Matrix4.VALUE_COUNT];
    private final DefaultRenderQueue3D shadowRenderables = new DefaultRenderQueue3D();
    private final Vector3 viewCameraPosition = new Vector3();
    private final Vector3 viewCameraDirection = new Vector3(0.0f, 0.0f, -1.0f);
    private final Vector3 viewCameraUp = new Vector3(0.0f, 1.0f, 0.0f);
    private float lightDirectionX;
    private float lightDirectionY = -1.0f;
    private float lightDirectionZ;
    private float lightRightX = 1.0f;
    private float lightRightY;
    private float lightRightZ;
    private float lightUpX;
    private float lightUpY;
    private float lightUpZ = 1.0f;
    private float viewCameraNear = 0.1f;
    private float viewCameraFar = 1.0f;
    private float viewCameraTanHalfFov;
    private float viewCameraAspect = 1.0f;
    private float splitLambda = 0.55f;
    private float padding = 1.08f;
    private float maxDistance = Float.POSITIVE_INFINITY;
    private float baseBias = 0.30f;
    private float minTexelBias = DEFAULT_MIN_TEXEL_BIAS;
    private final int width;
    private final int height;
    private boolean disposed;

    /**
     * Creates a cascaded shadow map set.
     *
     * @param graphics the graphics context
     * @param cascadeCount the cascade count from 1 to 4
     * @param width the width in pixels for each cascade
     * @param height the height in pixels for each cascade
     */
    public CascadedShadowMap3D(GraphicsContext graphics, int cascadeCount, int width, int height) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        if (cascadeCount <= 0 || cascadeCount > MAX_CASCADES) {
            throw new FdxException("Cascaded shadow map count must be between 1 and 4");
        }
        cascades = new DirectionalShadowMap3D[cascadeCount];
        splitDistances = new float[cascadeCount];
        centerX = new float[cascadeCount];
        centerY = new float[cascadeCount];
        centerZ = new float[cascadeCount];
        halfSizes = new float[cascadeCount];
        depthHalfSizes = new float[cascadeCount];
        cascadeBiases = new float[cascadeCount];
        this.width = width;
        this.height = height;
        for (int i = 0; i < cascades.length; i++) {
            cascades[i] = new DirectionalShadowMap3D(graphics, width, height);
        }
    }

    /**
     * Sets the blend between uniform and logarithmic split placement.
     *
     * @param splitLambda 0 uses uniform splits, 1 uses logarithmic splits
     * @return this cascaded shadow map for chaining
     */
    public CascadedShadowMap3D splitLambda(float splitLambda) {
        if (Float.isNaN(splitLambda)) {
            throw new FdxException("Cascade split lambda cannot be NaN");
        }
        this.splitLambda = Math.max(0.0f, Math.min(1.0f, splitLambda));
        return this;
    }

    /**
     * Sets the world-space padding applied to each cascade bounds.
     *
     * @param padding the padding multiplier
     * @return this cascaded shadow map for chaining
     */
    public CascadedShadowMap3D padding(float padding) {
        if (padding <= 0.0f || Float.isNaN(padding)) {
            throw new FdxException("Cascade padding must be greater than zero");
        }
        this.padding = padding;
        return this;
    }

    /**
     * Limits the farthest camera distance covered by the cascades.
     *
     * @param maxDistance the maximum covered distance
     * @return this cascaded shadow map for chaining
     */
    public CascadedShadowMap3D maxDistance(float maxDistance) {
        if (maxDistance <= 0.0f || Float.isNaN(maxDistance) || Float.isInfinite(maxDistance)) {
            throw new FdxException("Cascade max distance must be finite and greater than zero");
        }
        this.maxDistance = maxDistance;
        return this;
    }

    /**
     * Clears the maximum distance limit and returns this cascaded shadow map.
     *
     * @return this cascaded shadow map for chaining
     */
    public CascadedShadowMap3D clearMaxDistance() {
        maxDistance = Float.POSITIVE_INFINITY;
        return this;
    }

    /**
     * Sets the base world-space depth bias used to compute every cascade bias.
     *
     * @param bias the base world-space depth bias
     * @return this cascaded shadow map for chaining
     */
    public CascadedShadowMap3D bias(float bias) {
        baseBias = Math.max(0.0f, bias);
        for (int i = 0; i < cascades.length; i++) {
            cascades[i].bias(bias);
        }
        return this;
    }

    /**
     * Sets the minimum world-space bias floor in shadow-map texels.
     *
     * @param texels the minimum texel bias
     * @return this cascaded shadow map for chaining
     */
    public CascadedShadowMap3D minTexelBias(float texels) {
        if (Float.isNaN(texels)) {
            throw new FdxException("Cascade minimum texel bias cannot be NaN");
        }
        minTexelBias = Math.max(0.0f, texels);
        return this;
    }

    /**
     * Sets the shadow strength on every cascade.
     *
     * @param strength the shadow strength from 0 to 1
     * @return this cascaded shadow map for chaining
     */
    public CascadedShadowMap3D strength(float strength) {
        for (int i = 0; i < cascades.length; i++) {
            cascades[i].strength(strength);
        }
        return this;
    }

    /**
     * Updates cascade split distances and light-space bounds from the view camera.
     *
     * @param viewCamera the scene view camera
     * @return this cascaded shadow map for chaining
     */
    public CascadedShadowMap3D update(Camera viewCamera) {
        ensureNotDisposed();
        if (viewCamera == null) {
            throw new FdxException("View camera cannot be null");
        }
        float near = viewCamera.near();
        float far = Math.min(viewCamera.far(), maxDistance);
        if (far <= near) {
            throw new FdxException("Cascade max distance must be greater than the camera near plane");
        }
        viewCameraPosition.set(viewCamera.position());
        viewCameraDirection.set(viewCamera.direction());
        viewCameraUp.set(viewCamera.up());
        viewCameraNear = near;
        viewCameraFar = far;
        viewCameraAspect = Math.max(viewCamera.viewportWidth() / Math.max(viewCamera.viewportHeight(), EPSILON),
                EPSILON);
        viewCameraTanHalfFov = viewCamera.projection() == CameraProjection.PERSPECTIVE
                ? (float)Math.tan(Math.toRadians(viewCamera.fieldOfView()) * 0.5) : 0.0f;
        for (int i = 0; i < cascades.length; i++) {
            float start = i == 0 ? near : splitDistances[i - 1];
            float end = i == cascades.length - 1 ? far : splitDistance(near, far, i + 1);
            splitDistances[i] = end;
            updateCascadeBounds(viewCamera, i, start, end);
        }
        return this;
    }

    /**
     * Updates and stabilizes cascade bounds for a directional light.
     *
     * @param light the directional light
     * @param viewCamera the scene view camera
     * @return this cascaded shadow map for chaining
     */
    public CascadedShadowMap3D update(DirectionalLight light,
            Camera viewCamera) {
        update(viewCamera);
        updateLightSpaceBounds(light, null);
        return this;
    }

    /**
     * Renders model instances into every cascade.
     *
     * @param light the directional light
     * @param viewCamera the scene view camera
     * @param instances the model instances
     */
    public void render(DirectionalLight light, Camera viewCamera, ModelInstance[] instances) {
        ensureNotDisposed();
        if (instances == null) {
            throw new FdxException("ModelInstance array cannot be null");
        }
        shadowRenderables.clear();
        try {
            for (int i = 0; i < instances.length; i++) {
                if (instances[i] != null) {
                    instances[i].collectRenderables(shadowRenderables);
                }
            }
            renderCollected(light, viewCamera);
        }
        finally {
            shadowRenderables.clear();
        }
    }

    /**
     * Renders model instances into every cascade.
     *
     * @param light the directional light
     * @param viewCamera the scene view camera
     * @param instances the model instances
     */
    public void render(DirectionalLight light, Camera viewCamera,
            ObjectIterable<? extends ModelInstance> instances) {
        ensureNotDisposed();
        if (instances == null) {
            throw new FdxException("ModelInstance iterable cannot be null");
        }
        shadowRenderables.clear();
        try {
            if (instances instanceof ArrayView<?>) {
                ArrayView<?> values = (ArrayView<?>)instances;
                for (int i = 0; i < values.size(); i++) {
                    ModelInstance instance = (ModelInstance)values.get(i);
                    if (instance != null) {
                        instance.collectRenderables(shadowRenderables);
                    }
                }
            }
            else {
                ObjectIterator<? extends ModelInstance> iterator = instances.iterator();
                while (iterator.hasNext()) {
                    ModelInstance instance = iterator.next();
                    if (instance != null) {
                        instance.collectRenderables(shadowRenderables);
                    }
                }
            }
            renderCollected(light, viewCamera);
        }
        finally {
            shadowRenderables.clear();
        }
    }

    /**
     * Returns the cascade count.
     *
     * @return the cascade count
     */
    public int cascadeCount() {
        return cascades.length;
    }

    /**
     * Returns a cascade shadow map.
     *
     * @param index the cascade index
     * @return the cascade shadow map
     */
    public DirectionalShadowMap3D cascade(int index) {
        return cascades[checkedIndex(index)];
    }

    /**
     * Returns the first cascade shadow map.
     *
     * @return the first shadow map
     */
    public DirectionalShadowMap3D activeShadowMap() {
        return cascades[0];
    }

    /**
     * Returns the camera-space split distance for a cascade.
     *
     * @param index the cascade index
     * @return the split distance
     */
    public float splitDistance(int index) {
        return splitDistances[checkedIndex(index)];
    }

    /**
     * Returns the world-space cascade center x coordinate.
     *
     * @param index the cascade index
     * @return the center x coordinate
     */
    public float cascadeCenterX(int index) {
        return centerX[checkedIndex(index)];
    }

    /**
     * Returns the world-space cascade center y coordinate.
     *
     * @param index the cascade index
     * @return the center y coordinate
     */
    public float cascadeCenterY(int index) {
        return centerY[checkedIndex(index)];
    }

    /**
     * Returns the world-space cascade center z coordinate.
     *
     * @param index the cascade index
     * @return the center z coordinate
     */
    public float cascadeCenterZ(int index) {
        return centerZ[checkedIndex(index)];
    }

    /**
     * Returns the orthographic half-size for a cascade.
     *
     * @param index the cascade index
     * @return the cascade half-size
     */
    public float cascadeHalfSize(int index) {
        return halfSizes[checkedIndex(index)];
    }

    /**
     * Returns the light-space depth half-size for a cascade caster volume.
     *
     * @param index the cascade index
     * @return the cascade depth half-size
     */
    public float cascadeDepthHalfSize(int index) {
        return depthHalfSizes[checkedIndex(index)];
    }

    /**
     * Returns the computed depth comparison bias for a cascade.
     *
     * @param index the cascade index
     * @return the normalized cascade bias
     */
    public float cascadeBias(int index) {
        return cascadeBiases[checkedIndex(index)];
    }

    /**
     * Returns the camera position used to split the cascades.
     *
     * @return the cascade view camera position
     */
    public Vector3 viewCameraPosition() {
        return viewCameraPosition;
    }

    /**
     * Returns the camera direction used to split the cascades.
     *
     * @return the cascade view camera direction
     */
    public Vector3 viewCameraDirection() {
        return viewCameraDirection;
    }

    /**
     * Returns the camera up vector used to split the cascades.
     *
     * @return the cascade view camera up vector
     */
    public Vector3 viewCameraUp() {
        return viewCameraUp;
    }

    /**
     * Returns the near plane used to split the cascades.
     *
     * @return the cascade view camera near plane
     */
    public float viewCameraNear() {
        return viewCameraNear;
    }

    /**
     * Returns the far distance used to split the cascades.
     *
     * @return the cascade view camera far distance
     */
    public float viewCameraFar() {
        return viewCameraFar;
    }

    /**
     * Returns tan(fieldOfView / 2) for the camera used to split the cascades.
     *
     * @return the cascade view camera half-FOV tangent
     */
    public float viewCameraTanHalfFov() {
        return viewCameraTanHalfFov;
    }

    /**
     * Returns the aspect ratio used to split the cascades.
     *
     * @return the cascade view camera aspect ratio
     */
    public float viewCameraAspect() {
        return viewCameraAspect;
    }

    /**
     * Returns the split lambda.
     *
     * @return the split lambda
     */
    public float splitLambda() {
        return splitLambda;
    }

    /**
     * Returns the cascade padding multiplier.
     *
     * @return the padding multiplier
     */
    public float padding() {
        return padding;
    }

    /**
     * Returns the maximum covered camera distance.
     *
     * @return the maximum covered camera distance
     */
    public float maxDistance() {
        return maxDistance;
    }

    /**
     * Returns the minimum bias floor in shadow-map texels.
     *
     * @return the minimum texel bias
     */
    public float minTexelBias() {
        return minTexelBias;
    }

    private float splitDistance(float near, float far, int splitIndex) {
        float ratio = (float)splitIndex / cascades.length;
        float uniform = near + (far - near) * ratio;
        float logarithmic = near * (float)Math.pow(far / near, ratio);
        return logarithmic * splitLambda + uniform * (1.0f - splitLambda);
    }

    private void updateCascadeBounds(Camera viewCamera, int index, float start, float end) {
        Vector3 position = viewCamera.position();
        Vector3 direction = viewCamera.direction();
        float centerDistance = (start + end) * 0.5f;
        centerX[index] = position.x() + direction.x() * centerDistance;
        centerY[index] = position.y() + direction.y() * centerDistance;
        centerZ[index] = position.z() + direction.z() * centerDistance;

        float halfDepth = (end - start) * 0.5f;
        float radius;
        if (viewCamera.projection() == CameraProjection.PERSPECTIVE) {
            float aspect = Math.max(viewCamera.viewportWidth() / Math.max(viewCamera.viewportHeight(), EPSILON),
                    EPSILON);
            float tangent = (float)Math.tan(Math.toRadians(viewCamera.fieldOfView()) * 0.5);
            float farHalfHeight = tangent * end;
            float farHalfWidth = farHalfHeight * aspect;
            radius = (float)Math.sqrt(farHalfWidth * farHalfWidth
                    + farHalfHeight * farHalfHeight
                    + halfDepth * halfDepth);
        }
        else {
            float halfWidth = viewCamera.viewportWidth() * viewCamera.zoom() * 0.5f;
            float halfHeight = viewCamera.viewportHeight() * viewCamera.zoom() * 0.5f;
            radius = (float)Math.sqrt(halfWidth * halfWidth + halfHeight * halfHeight + halfDepth * halfDepth);
        }
        float paddedRadius = Math.max(EPSILON, radius * padding);
        // Center stabilization can shift a cascade by half a texel. Keep one
        // texel of guard space so that shift cannot clip a receiver corner.
        float stabilizationGuard = paddedRadius * 2.0f
                / Math.max(1, Math.min(width, height));
        halfSizes[index] = paddedRadius + stabilizationGuard;
        depthHalfSizes[index] = halfSizes[index];
        applyCascadeBounds(index);
    }

    private void renderCollected(DirectionalLight light, Camera viewCamera) {
        update(viewCamera);
        ArrayView<Renderable3D> renderables = shadowRenderables.renderables();
        updateLightSpaceBounds(light, renderables);
        for (int i = 0; i < cascades.length; i++) {
            cascades[i].renderRenderables(light, renderables);
        }
    }

    private void updateLightSpaceBounds(DirectionalLight light,
            ArrayView<Renderable3D> renderables) {
        updateLightBasis(light);
        for (int index = 0; index < cascades.length; index++) {
            float halfSize = halfSizes[index];
            float texelSizeX = halfSize * 2.0f / Math.max(width, 1);
            float texelSizeY = halfSize * 2.0f / Math.max(height, 1);

            float sourceCenterX = centerX[index];
            float sourceCenterY = centerY[index];
            float sourceCenterZ = centerZ[index];
            float centerRight = dot(sourceCenterX, sourceCenterY,
                    sourceCenterZ, lightRightX, lightRightY, lightRightZ);
            float centerUp = dot(sourceCenterX, sourceCenterY,
                    sourceCenterZ, lightUpX, lightUpY, lightUpZ);
            float snappedRight = snap(centerRight, texelSizeX);
            float snappedUp = snap(centerUp, texelSizeY);
            float rightOffset = snappedRight - centerRight;
            float upOffset = snappedUp - centerUp;
            float snappedCenterX = sourceCenterX
                    + lightRightX * rightOffset + lightUpX * upOffset;
            float snappedCenterY = sourceCenterY
                    + lightRightY * rightOffset + lightUpY * upOffset;
            float snappedCenterZ = sourceCenterZ
                    + lightRightZ * rightOffset + lightUpZ * upOffset;

            float receiverDepthCenter = dot(sourceCenterX, sourceCenterY,
                    sourceCenterZ, lightDirectionX, lightDirectionY,
                    lightDirectionZ);
            float receiverDepthMax = receiverDepthCenter + halfSize;
            float depthMin = receiverDepthCenter - halfSize;
            if (renderables != null) {
                float areaMinRight = snappedRight - halfSize;
                float areaMaxRight = snappedRight + halfSize;
                float areaMinUp = snappedUp - halfSize;
                float areaMaxUp = snappedUp + halfSize;
                for (int i = 0; i < renderables.size(); i++) {
                    float casterMin = casterDepthMin(renderables.get(i),
                            areaMinRight, areaMaxRight, areaMinUp, areaMaxUp,
                            receiverDepthMax);
                    if (casterMin < depthMin) {
                        depthMin = casterMin;
                    }
                }
            }

            float depthGuard = Math.max(texelSizeX, texelSizeY);
            depthMin -= depthGuard;
            float depthMax = receiverDepthMax + depthGuard;
            float depthCenter = (depthMin + depthMax) * 0.5f;
            depthHalfSizes[index] = Math.max(EPSILON,
                    (depthMax - depthMin) * 0.5f);
            float depthOffset = depthCenter - receiverDepthCenter;
            centerX[index] = snappedCenterX + lightDirectionX * depthOffset;
            centerY[index] = snappedCenterY + lightDirectionY * depthOffset;
            centerZ[index] = snappedCenterZ + lightDirectionZ * depthOffset;
            applyCascadeBounds(index);
        }
    }

    private float casterDepthMin(Renderable3D renderable,
            float areaMinRight, float areaMaxRight,
            float areaMinUp, float areaMaxUp, float receiverDepthMax) {
        BoundingBox bounds = renderable.bounds();
        Matrix4 transform = renderable.worldTransform();
        transform.copyValues(transformValues, 0);
        Vector3 min = bounds.min();
        Vector3 max = bounds.max();
        float casterMinRight = Float.POSITIVE_INFINITY;
        float casterMaxRight = Float.NEGATIVE_INFINITY;
        float casterMinUp = Float.POSITIVE_INFINITY;
        float casterMaxUp = Float.NEGATIVE_INFINITY;
        float casterMinDepth = Float.POSITIVE_INFINITY;

        for (int corner = 0; corner < 8; corner++) {
            float x = (corner & 1) == 0 ? min.x() : max.x();
            float y = (corner & 2) == 0 ? min.y() : max.y();
            float z = (corner & 4) == 0 ? min.z() : max.z();
            float worldX = transformValues[0] * x + transformValues[4] * y
                    + transformValues[8] * z + transformValues[12];
            float worldY = transformValues[1] * x + transformValues[5] * y
                    + transformValues[9] * z + transformValues[13];
            float worldZ = transformValues[2] * x + transformValues[6] * y
                    + transformValues[10] * z + transformValues[14];
            float right = dot(worldX, worldY, worldZ,
                    lightRightX, lightRightY, lightRightZ);
            float up = dot(worldX, worldY, worldZ,
                    lightUpX, lightUpY, lightUpZ);
            float depth = dot(worldX, worldY, worldZ,
                    lightDirectionX, lightDirectionY, lightDirectionZ);
            casterMinRight = Math.min(casterMinRight, right);
            casterMaxRight = Math.max(casterMaxRight, right);
            casterMinUp = Math.min(casterMinUp, up);
            casterMaxUp = Math.max(casterMaxUp, up);
            casterMinDepth = Math.min(casterMinDepth, depth);
        }

        if (casterMaxRight < areaMinRight || casterMinRight > areaMaxRight
                || casterMaxUp < areaMinUp || casterMinUp > areaMaxUp
                || casterMinDepth > receiverDepthMax) {
            return Float.POSITIVE_INFINITY;
        }
        return casterMinDepth;
    }

    private void updateLightBasis(DirectionalLight light) {
        if (light == null) {
            throw new FdxException("DirectionalLight cannot be null");
        }
        Vector3 direction = light.direction();
        lightDirectionX = direction.x();
        lightDirectionY = direction.y();
        lightDirectionZ = direction.z();
        float directionLength = length(lightDirectionX, lightDirectionY,
                lightDirectionZ);
        if (directionLength <= EPSILON) {
            lightDirectionX = 0.0f;
            lightDirectionY = -1.0f;
            lightDirectionZ = 0.0f;
        }
        else {
            float inverseDirectionLength = 1.0f / directionLength;
            lightDirectionX *= inverseDirectionLength;
            lightDirectionY *= inverseDirectionLength;
            lightDirectionZ *= inverseDirectionLength;
        }

        float sourceUpX = -lightDirectionX * lightDirectionY;
        float sourceUpY = 1.0f - lightDirectionY * lightDirectionY;
        float sourceUpZ = -lightDirectionZ * lightDirectionY;
        float sourceUpLength = length(sourceUpX, sourceUpY, sourceUpZ);
        if (sourceUpLength <= 0.0001f) {
            sourceUpX = -lightDirectionX * lightDirectionZ;
            sourceUpY = -lightDirectionY * lightDirectionZ;
            sourceUpZ = 1.0f - lightDirectionZ * lightDirectionZ;
            sourceUpLength = length(sourceUpX, sourceUpY, sourceUpZ);
        }
        if (sourceUpLength <= 0.0001f) {
            sourceUpX = 1.0f;
            sourceUpY = 0.0f;
            sourceUpZ = 0.0f;
        }
        else {
            float inverseUpLength = 1.0f / sourceUpLength;
            sourceUpX *= inverseUpLength;
            sourceUpY *= inverseUpLength;
            sourceUpZ *= inverseUpLength;
        }

        lightRightX = lightDirectionY * sourceUpZ
                - lightDirectionZ * sourceUpY;
        lightRightY = lightDirectionZ * sourceUpX
                - lightDirectionX * sourceUpZ;
        lightRightZ = lightDirectionX * sourceUpY
                - lightDirectionY * sourceUpX;
        float rightLength = length(lightRightX, lightRightY, lightRightZ);
        if (rightLength <= EPSILON) {
            lightRightX = 1.0f;
            lightRightY = 0.0f;
            lightRightZ = 0.0f;
        }
        else {
            float inverseRightLength = 1.0f / rightLength;
            lightRightX *= inverseRightLength;
            lightRightY *= inverseRightLength;
            lightRightZ *= inverseRightLength;
        }
        lightUpX = lightRightY * lightDirectionZ
                - lightRightZ * lightDirectionY;
        lightUpY = lightRightZ * lightDirectionX
                - lightRightX * lightDirectionZ;
        lightUpZ = lightRightX * lightDirectionY
                - lightRightY * lightDirectionX;
    }

    private void applyCascadeBounds(int index) {
        float cascadeNear = 0.1f;
        float cascadeFar = cascadeNear + depthHalfSizes[index] * 2.0f;
        float depthRange = Math.max(cascadeFar - cascadeNear, EPSILON);
        float texelSizeWorld = Math.max(
                halfSizes[index] * 2.0f / Math.max(width, 1),
                halfSizes[index] * 2.0f / Math.max(height, 1));
        cascadeBiases[index] = Math.max(baseBias, minTexelBias * texelSizeWorld) / depthRange;
        cascades[index]
                .bounds(centerX[index], centerY[index], centerZ[index], halfSizes[index], cascadeNear, cascadeFar)
                .bias(cascadeBiases[index]);
    }

    private static float snap(float value, float step) {
        if (step <= EPSILON) {
            return value;
        }
        return (float)(Math.floor(value / step + 0.5) * step);
    }

    private static float dot(float x, float y, float z,
            float axisX, float axisY, float axisZ) {
        return x * axisX + y * axisY + z * axisZ;
    }

    private static float length(float x, float y, float z) {
        return (float)Math.sqrt(x * x + y * y + z * z);
    }

    private int checkedIndex(int index) {
        if (index < 0 || index >= cascades.length) {
            throw new FdxException("Cascade index out of range: " + index);
        }
        return index;
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("CascadedShadowMap3D has been disposed");
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
        for (int i = 0; i < cascades.length; i++) {
            cascades[i].dispose();
        }
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true when disposed
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
