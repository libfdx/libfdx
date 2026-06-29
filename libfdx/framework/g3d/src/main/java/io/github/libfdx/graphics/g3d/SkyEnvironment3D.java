package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Color;

/**
 * Describes a procedural sky environment used by PBR lighting.
 *
 * @author xpenatan
 */
public final class SkyEnvironment3D {
    private Color zenithColor = new Color(0.16f, 0.32f, 0.64f, 1.0f);
    private Color horizonColor = new Color(0.62f, 0.76f, 0.92f, 1.0f);
    private Color nadirColor = new Color(0.26f, 0.24f, 0.22f, 1.0f);
    private Color sunColor = new Color(1.0f, 0.82f, 0.48f, 1.0f);
    private float sunDirectionX = 0.35f;
    private float sunDirectionY = 0.82f;
    private float sunDirectionZ = -0.45f;
    private float diffuseIntensity = 0.65f;
    private float specularIntensity = 0.55f;
    private float sunIntensity = 0.28f;
    private float horizonBlend = 0.42f;

    /**
     * Sets the upper sky color and returns this environment.
     *
     * @param red the red component
     * @param green the green component
     * @param blue the blue component
     * @return this environment for chaining
     */
    public SkyEnvironment3D zenithColor(float red, float green, float blue) {
        zenithColor = color(red, green, blue, 1.0f);
        return this;
    }

    /**
     * Sets the horizon color and returns this environment.
     *
     * @param red the red component
     * @param green the green component
     * @param blue the blue component
     * @return this environment for chaining
     */
    public SkyEnvironment3D horizonColor(float red, float green, float blue) {
        horizonColor = color(red, green, blue, 1.0f);
        return this;
    }

    /**
     * Sets the lower sky color and returns this environment.
     *
     * @param red the red component
     * @param green the green component
     * @param blue the blue component
     * @return this environment for chaining
     */
    public SkyEnvironment3D nadirColor(float red, float green, float blue) {
        nadirColor = color(red, green, blue, 1.0f);
        return this;
    }

    /**
     * Sets the sun color and returns this environment.
     *
     * @param red the red component
     * @param green the green component
     * @param blue the blue component
     * @return this environment for chaining
     */
    public SkyEnvironment3D sunColor(float red, float green, float blue) {
        sunColor = color(red, green, blue, 1.0f);
        return this;
    }

    /**
     * Sets the world-space sun direction and returns this environment.
     *
     * @param x the x direction
     * @param y the y direction
     * @param z the z direction
     * @return this environment for chaining
     */
    public SkyEnvironment3D sunDirection(float x, float y, float z) {
        validateFinite(x, "Sky environment sun direction x");
        validateFinite(y, "Sky environment sun direction y");
        validateFinite(z, "Sky environment sun direction z");
        float len = (float)Math.sqrt(x * x + y * y + z * z);
        if (len <= 0.0f) {
            throw new FdxException("Sky environment sun direction cannot be zero length");
        }
        float invLen = 1.0f / len;
        sunDirectionX = x * invLen;
        sunDirectionY = y * invLen;
        sunDirectionZ = z * invLen;
        return this;
    }

    /**
     * Sets diffuse and specular environment intensity and returns this environment.
     *
     * @param diffuseIntensity the diffuse intensity
     * @param specularIntensity the specular intensity
     * @return this environment for chaining
     */
    public SkyEnvironment3D intensity(float diffuseIntensity, float specularIntensity) {
        validateFinite(diffuseIntensity, "Sky environment diffuse intensity");
        validateFinite(specularIntensity, "Sky environment specular intensity");
        this.diffuseIntensity = Math.max(0.0f, diffuseIntensity);
        this.specularIntensity = Math.max(0.0f, specularIntensity);
        return this;
    }

    /**
     * Sets the sun reflection intensity and returns this environment.
     *
     * @param sunIntensity the sun intensity
     * @return this environment for chaining
     */
    public SkyEnvironment3D sunIntensity(float sunIntensity) {
        validateFinite(sunIntensity, "Sky environment sun intensity");
        this.sunIntensity = Math.max(0.0f, sunIntensity);
        return this;
    }

    /**
     * Sets the horizon blend and returns this environment.
     *
     * @param horizonBlend the horizon blend from 0 to 1
     * @return this environment for chaining
     */
    public SkyEnvironment3D horizonBlend(float horizonBlend) {
        validateFinite(horizonBlend, "Sky environment horizon blend");
        this.horizonBlend = clamp(horizonBlend, 0.0f, 1.0f);
        return this;
    }

    /**
     * Returns the zenith color.
     *
     * @return the zenith color
     */
    public Color zenithColor() {
        return zenithColor;
    }

    /**
     * Returns the horizon color.
     *
     * @return the horizon color
     */
    public Color horizonColor() {
        return horizonColor;
    }

    /**
     * Returns the nadir color.
     *
     * @return the nadir color
     */
    public Color nadirColor() {
        return nadirColor;
    }

    /**
     * Returns the sun color.
     *
     * @return the sun color
     */
    public Color sunColor() {
        return sunColor;
    }

    /**
     * Returns the sun direction x component.
     *
     * @return the sun direction x component
     */
    public float sunDirectionX() {
        return sunDirectionX;
    }

    /**
     * Returns the sun direction y component.
     *
     * @return the sun direction y component
     */
    public float sunDirectionY() {
        return sunDirectionY;
    }

    /**
     * Returns the sun direction z component.
     *
     * @return the sun direction z component
     */
    public float sunDirectionZ() {
        return sunDirectionZ;
    }

    /**
     * Returns the diffuse environment intensity.
     *
     * @return the diffuse environment intensity
     */
    public float diffuseIntensity() {
        return diffuseIntensity;
    }

    /**
     * Returns the specular environment intensity.
     *
     * @return the specular environment intensity
     */
    public float specularIntensity() {
        return specularIntensity;
    }

    /**
     * Returns the sun reflection intensity.
     *
     * @return the sun reflection intensity
     */
    public float sunIntensity() {
        return sunIntensity;
    }

    /**
     * Returns the horizon blend.
     *
     * @return the horizon blend
     */
    public float horizonBlend() {
        return horizonBlend;
    }

    private static Color color(float red, float green, float blue, float alpha) {
        validateFinite(red, "Sky environment color red");
        validateFinite(green, "Sky environment color green");
        validateFinite(blue, "Sky environment color blue");
        validateFinite(alpha, "Sky environment color alpha");
        return new Color(clamp(red, 0.0f, 1.0f), clamp(green, 0.0f, 1.0f),
                clamp(blue, 0.0f, 1.0f), clamp(alpha, 0.0f, 1.0f));
    }

    private static void validateFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new FdxException(name + " must be finite");
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
