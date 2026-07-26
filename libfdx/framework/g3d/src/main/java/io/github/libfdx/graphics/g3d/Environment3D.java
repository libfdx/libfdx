package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an environment3 d.
 *
 * @author xpenatan
 */
public final class Environment3D {
    private Color ambientColor = new Color(0.03f, 0.03f, 0.03f, 1.0f);
    private Color fogColor = Color.CLEAR;
    private float fogStartDistance;
    private float fogEndDistance = 1.0f;
    private boolean fogEnabled;
    private boolean neutralToneMappingEnabled;
    private float exposure = 1.0f;
    private DirectionalShadowMap3D directionalShadowMap;
    private CascadedShadowMap3D cascadedShadowMap;
    private SkyEnvironment3D skyEnvironment;
    private final ArrayList<Light> lights = new ArrayList<Light>();
    private final List<Light> readOnlyLights = Collections.unmodifiableList(lights);

    /**
     * Sets the ambient color and returns this environment3 d.
     *
     * @param ambientColor the ambient color
     * @return this environment3 d for chaining
     */
    public Environment3D ambientColor(Color ambientColor) {
        this.ambientColor = ambientColor != null ? ambientColor : Color.BLACK;
        return this;
    }

    /**
     * Sets the add and returns this environment3 d.
     *
     * @param light the light
     * @return this environment3 d for chaining
     */
    public Environment3D add(Light light) {
        if (light != null) {
            lights.add(light);
        }
        return this;
    }

    /**
     * Returns the clear lights.
     *
     * @return this environment3 d for chaining
     */
    public Environment3D clearLights() {
        lights.clear();
        return this;
    }

    /**
     * Enables distance fog and returns this environment.
     *
     * @param fogColor the fog color; alpha controls the maximum fog amount
     * @param startDistance the distance at which fog starts
     * @param endDistance the distance at which fog reaches full strength
     * @return this environment for chaining
     */
    public Environment3D fog(Color fogColor, float startDistance, float endDistance) {
        if (startDistance < 0.0f) {
            throw new FdxException("Fog start distance cannot be negative");
        }
        if (endDistance <= startDistance) {
            throw new FdxException("Fog end distance must be greater than start distance");
        }
        this.fogColor = fogColor != null ? fogColor : Color.BLACK;
        fogStartDistance = startDistance;
        fogEndDistance = endDistance;
        fogEnabled = true;
        return this;
    }

    /**
     * Enables distance fog and returns this environment.
     *
     * @param red the fog red component
     * @param green the fog green component
     * @param blue the fog blue component
     * @param alpha the maximum fog amount
     * @param startDistance the distance at which fog starts
     * @param endDistance the distance at which fog reaches full strength
     * @return this environment for chaining
     */
    public Environment3D fog(float red, float green, float blue, float alpha,
            float startDistance, float endDistance) {
        return fog(new Color(red, green, blue, alpha), startDistance, endDistance);
    }

    /**
     * Disables distance fog and returns this environment.
     *
     * @return this environment for chaining
     */
    public Environment3D clearFog() {
        fogColor = Color.CLEAR;
        fogStartDistance = 0.0f;
        fogEndDistance = 1.0f;
        fogEnabled = false;
        return this;
    }

    /**
     * Enables neutral display tonemapping and returns this environment.
     *
     * @param exposure the positive scene exposure multiplier
     * @return this environment for chaining
     */
    public Environment3D neutralToneMapping(float exposure) {
        if (exposure <= 0.0f) {
            throw new FdxException("Tone mapping exposure must be greater than zero");
        }
        neutralToneMappingEnabled = true;
        this.exposure = exposure;
        return this;
    }

    /**
     * Disables display tonemapping and restores unit exposure.
     *
     * @return this environment for chaining
     */
    public Environment3D clearToneMapping() {
        neutralToneMappingEnabled = false;
        exposure = 1.0f;
        return this;
    }

    /**
     * Sets the sky environment used by PBR image-based-lighting style shading.
     * <p>The environment does not own the sky environment; callers may share it with a
     * matching {@link SkyboxRenderer3D} configuration.</p>
     *
     * @param skyEnvironment the sky environment, or null to disable sky environment lighting
     * @return this environment for chaining
     */
    public Environment3D skyEnvironment(SkyEnvironment3D skyEnvironment) {
        this.skyEnvironment = skyEnvironment;
        return this;
    }

    /**
     * Clears sky environment lighting and returns this environment.
     *
     * @return this environment for chaining
     */
    public Environment3D clearSkyEnvironment() {
        skyEnvironment = null;
        return this;
    }

    /**
     * Sets the directional shadow map and returns this environment.
     * <p>The environment does not own the shadow map; callers remain responsible for disposing it.</p>
     *
     * @param shadowMap the directional shadow map, or null to disable shadows
     * @return this environment for chaining
     */
    public Environment3D directionalShadowMap(DirectionalShadowMap3D shadowMap) {
        directionalShadowMap = shadowMap;
        return this;
    }

    /**
     * Sets the cascaded shadow map and returns this environment.
     * <p>The environment does not own the shadow map; callers remain responsible for disposing it.</p>
     *
     * @param shadowMap the cascaded shadow map, or null to disable cascaded shadows
     * @return this environment for chaining
     */
    public Environment3D cascadedShadowMap(CascadedShadowMap3D shadowMap) {
        cascadedShadowMap = shadowMap;
        return this;
    }

    /**
     * Clears the directional shadow map and returns this environment.
     *
     * @return this environment for chaining
     */
    public Environment3D clearDirectionalShadowMap() {
        directionalShadowMap = null;
        return this;
    }

    /**
     * Clears the cascaded shadow map and returns this environment.
     *
     * @return this environment for chaining
     */
    public Environment3D clearCascadedShadowMap() {
        cascadedShadowMap = null;
        return this;
    }

    /**
     * Returns the ambient color.
     *
     * @return the ambient color
     */
    public Color ambientColor() {
        return ambientColor;
    }

    /**
     * Returns the sky environment.
     *
     * @return the sky environment, or null when disabled
     */
    public SkyEnvironment3D skyEnvironment() {
        return skyEnvironment;
    }

    /**
     * Returns the lights.
     *
     * @return the lights
     */
    public List<Light> lights() {
        return readOnlyLights;
    }

    /**
     * Returns whether distance fog is enabled.
     *
     * @return true when fog is enabled
     */
    public boolean fogEnabled() {
        return fogEnabled;
    }

    /**
     * Returns whether neutral display tonemapping is enabled.
     *
     * @return true when neutral tonemapping is enabled
     */
    public boolean neutralToneMappingEnabled() {
        return neutralToneMappingEnabled;
    }

    /**
     * Returns the scene exposure multiplier.
     *
     * @return the positive exposure multiplier
     */
    public float exposure() {
        return exposure;
    }

    /**
     * Returns the fog color.
     *
     * @return the fog color
     */
    public Color fogColor() {
        return fogColor;
    }

    /**
     * Returns the fog start distance.
     *
     * @return the fog start distance
     */
    public float fogStartDistance() {
        return fogStartDistance;
    }

    /**
     * Returns the fog end distance.
     *
     * @return the fog end distance
     */
    public float fogEndDistance() {
        return fogEndDistance;
    }

    /**
     * Returns the directional shadow map.
     *
     * @return the directional shadow map, or null when disabled
     */
    public DirectionalShadowMap3D directionalShadowMap() {
        return directionalShadowMap;
    }

    /**
     * Returns the cascaded shadow map.
     *
     * @return the cascaded shadow map, or null when disabled
     */
    public CascadedShadowMap3D cascadedShadowMap() {
        return cascadedShadowMap;
    }
}
