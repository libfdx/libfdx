package io.github.libfdx.graphics.g3d;

public final class PbrShaderConfig {
    private int maxLights = 8;
    private int maxBones = 64;
    private boolean shadowsEnabled;
    private boolean imageBasedLightingEnabled;

    public PbrShaderConfig maxLights(int maxLights) {
        this.maxLights = maxLights;
        return this;
    }

    public PbrShaderConfig maxBones(int maxBones) {
        this.maxBones = maxBones;
        return this;
    }

    public PbrShaderConfig enableShadows(boolean enabled) {
        this.shadowsEnabled = enabled;
        return this;
    }

    public PbrShaderConfig enableImageBasedLighting(boolean enabled) {
        this.imageBasedLightingEnabled = enabled;
        return this;
    }

    public int maxLights() {
        return maxLights;
    }

    public int maxBones() {
        return maxBones;
    }

    public boolean shadowsEnabled() {
        return shadowsEnabled;
    }

    public boolean imageBasedLightingEnabled() {
        return imageBasedLightingEnabled;
    }
}
