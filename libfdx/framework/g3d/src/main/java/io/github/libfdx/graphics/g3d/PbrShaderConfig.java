package io.github.libfdx.graphics.g3d;

/**
 * Configures the PBR provider. Shadow and IBL options are copied at construction. Lighting resources are
 * borrowed from each render environment. Disabling a feature skips its resource sampling;
 * it does not dispose resources or automatically stop application-owned shadow passes.
 *
 * @author xpenatan
 */
public final class PbrShaderConfig {
    private ModelShaderPlan shaderPlan;

    /** Borrows definitions prepared by an async ModelBatch. Rendering requires its prepared
     * context pass; no constructor or draw-time synchronous shader creation is permitted. */
    public PbrShaderConfig shaderPlan(ModelShaderPlan value) { shaderPlan = value; return this; }
    public ModelShaderPlan shaderPlan() { return shaderPlan; }
    private int maxLights = 8;
    private int maxBones = 64;
    private boolean shadowsEnabled = true;
    private boolean imageBasedLightingEnabled = true;

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
     * Enables sampling the environment's shadow maps (default true).
     *
     * @param enabled the enabled
     * @return this PBR shader config for chaining
     */
    public PbrShaderConfig enableShadows(boolean enabled) {
        this.shadowsEnabled = enabled;
        return this;
    }

    /**
     * Enables sampling the environment's image-based lighting (default true).
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
