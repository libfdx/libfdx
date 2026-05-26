package io.github.libfdx.graphics.g3d;

public final class ModelBatchConfig {
    private int maxLights = 8;
    private int maxBones = 64;
    private boolean instancingEnabled = true;
    private boolean gpuSkinningEnabled = true;
    private ShaderProvider3D shaderProvider;

    public ModelBatchConfig maxLights(int maxLights) {
        this.maxLights = maxLights;
        return this;
    }

    public ModelBatchConfig maxBones(int maxBones) {
        this.maxBones = maxBones;
        return this;
    }

    public ModelBatchConfig enableInstancing(boolean enabled) {
        this.instancingEnabled = enabled;
        return this;
    }

    public ModelBatchConfig enableGpuSkinning(boolean enabled) {
        this.gpuSkinningEnabled = enabled;
        return this;
    }

    public ModelBatchConfig shaderProvider(ShaderProvider3D shaderProvider) {
        this.shaderProvider = shaderProvider;
        return this;
    }

    public int maxLights() {
        return maxLights;
    }

    public int maxBones() {
        return maxBones;
    }

    public boolean instancingEnabled() {
        return instancingEnabled;
    }

    public boolean gpuSkinningEnabled() {
        return gpuSkinningEnabled;
    }

    public ShaderProvider3D shaderProvider() {
        return shaderProvider;
    }
}
