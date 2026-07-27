package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.runtime.ShaderProvider;

/**
 * Stores configuration values for a model batch.
 *
 * @author xpenatan
 */
public final class ModelBatchConfig {
    private int maxLights = 8;
    private int maxBones = 64;
    private boolean instancingEnabled = true;
    private boolean gpuSkinningEnabled = true;
    private ShaderProvider3D shaderProvider;
    private ShaderProvider commonShaderProvider;

    /**
     * Sets the max lights and returns this model batch config.
     *
     * @param maxLights the max lights
     * @return this model batch config for chaining
     */
    public ModelBatchConfig maxLights(int maxLights) {
        this.maxLights = maxLights;
        return this;
    }

    /**
     * Sets the max bones and returns this model batch config.
     *
     * @param maxBones the max bones
     * @return this model batch config for chaining
     */
    public ModelBatchConfig maxBones(int maxBones) {
        this.maxBones = maxBones;
        return this;
    }

    /**
     * Sets the enable instancing and returns this model batch config.
     *
     * @param enabled the enabled
     * @return this model batch config for chaining
     */
    public ModelBatchConfig enableInstancing(boolean enabled) {
        this.instancingEnabled = enabled;
        return this;
    }

    /**
     * Sets the enable GPU skinning and returns this model batch config.
     *
     * @param enabled the enabled
     * @return this model batch config for chaining
     */
    public ModelBatchConfig enableGpuSkinning(boolean enabled) {
        this.gpuSkinningEnabled = enabled;
        return this;
    }

    /**
     * Sets the borrowed shader provider and returns this model batch config.
     *
     * <p>A {@link ModelBatch} created from this configuration does not dispose
     * the provider.</p>
     *
     * @param shaderProvider the shader provider
     * @return this model batch config for chaining
     */
    public ModelBatchConfig shaderProvider(ShaderProvider3D shaderProvider) {
        this.shaderProvider = shaderProvider;
        commonShaderProvider = null;
        return this;
    }

    /**
     * Sets the borrowed common shader provider and clears the configured
     * domain-specific provider. The last provider setter wins.
     *
     * @param shaderProvider common shader provider
     * @return this model batch config for chaining
     */
    public ModelBatchConfig shaderProvider(ShaderProvider shaderProvider) {
        commonShaderProvider = shaderProvider;
        this.shaderProvider = null;
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
     * Returns the instancing enabled.
     *
     * @return true if instancing enabled succeeds or is active; false otherwise
     */
    public boolean instancingEnabled() {
        return instancingEnabled;
    }

    /**
     * Returns the GPU skinning enabled.
     *
     * @return true if GPU skinning enabled succeeds or is active; false otherwise
     */
    public boolean gpuSkinningEnabled() {
        return gpuSkinningEnabled;
    }

    /**
     * Returns the shader provider.
     *
     * @return the shader provider
     */
    public ShaderProvider3D shaderProvider() {
        return shaderProvider;
    }

    /**
     * Returns the configured common shader provider.
     *
     * @return common provider, or {@code null}
     */
    public ShaderProvider commonShaderProvider() {
        return commonShaderProvider;
    }
}
