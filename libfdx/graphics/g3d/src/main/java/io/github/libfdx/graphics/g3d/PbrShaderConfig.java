package io.github.libfdx.graphics.g3d;

/**
 * Stores configuration values for a pbr shader.
 *
 * @author xpenatan
 */
public final class PbrShaderConfig {
    private int maxLights = 8;
    private int maxBones = 64;
    private boolean shadowsEnabled;
    private boolean imageBasedLightingEnabled;

    /**
     * Sets the max lights and returns this PBR shader config.
     *
     * @param maxLights the max lights
     * @return this PBR shader config for chaining
     */
    public PbrShaderConfig maxLights(int maxLights) {
        this.maxLights = maxLights;
        return this;
    }

    /**
     * Sets the max bones and returns this PBR shader config.
     *
     * @param maxBones the max bones
     * @return this PBR shader config for chaining
     */
    public PbrShaderConfig maxBones(int maxBones) {
        this.maxBones = maxBones;
        return this;
    }

    /**
     * Sets the enable shadows and returns this PBR shader config.
     *
     * @param enabled the enabled
     * @return this PBR shader config for chaining
     */
    public PbrShaderConfig enableShadows(boolean enabled) {
        this.shadowsEnabled = enabled;
        return this;
    }

    /**
     * Sets the enable image based lighting and returns this PBR shader config.
     *
     * @param enabled the enabled
     * @return this PBR shader config for chaining
     */
    public PbrShaderConfig enableImageBasedLighting(boolean enabled) {
        this.imageBasedLightingEnabled = enabled;
        return this;
    }

    /**
     * Returns the max lights.
     *
     * @return the max lights
     */
    public int maxLights() {
        return maxLights;
    }

    /**
     * Returns the max bones.
     *
     * @return the max bones
     */
    public int maxBones() {
        return maxBones;
    }

    /**
     * Returns the shadows enabled.
     *
     * @return true if shadows enabled succeeds or is active; false otherwise
     */
    public boolean shadowsEnabled() {
        return shadowsEnabled;
    }

    /**
     * Returns the image based lighting enabled.
     *
     * @return true if image based lighting enabled succeeds or is active; false otherwise
     */
    public boolean imageBasedLightingEnabled() {
        return imageBasedLightingEnabled;
    }
}
