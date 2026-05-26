package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.Texture;

import java.util.HashSet;
import java.util.Set;

public final class PbrMaterial implements Material, Disposable {
    private final String id;
    private Color baseColor = Color.WHITE;
    private Texture baseColorTexture;
    private float metallicFactor = 0.0f;
    private float roughnessFactor = 1.0f;
    private Texture metallicRoughnessTexture;
    private Texture normalTexture;
    private Texture occlusionTexture;
    private Color emissiveFactor = Color.BLACK;
    private Texture emissiveTexture;
    private MaterialAlphaMode alphaMode = MaterialAlphaMode.OPAQUE;
    private float alphaCutoff = 0.5f;
    private boolean doubleSided;
    private ShaderProvider3D shaderProvider;
    private boolean disposed;

    public PbrMaterial(String id) {
        this.id = id != null ? id : "";
    }

    public PbrMaterial baseColor(Color baseColor) {
        this.baseColor = baseColor != null ? baseColor : Color.WHITE;
        return this;
    }

    public PbrMaterial baseColor(float red, float green, float blue, float alpha) {
        this.baseColor = new Color(red, green, blue, alpha);
        return this;
    }

    public PbrMaterial baseColorTexture(Texture texture) {
        this.baseColorTexture = texture;
        return this;
    }

    public PbrMaterial metallicFactor(float metallicFactor) {
        this.metallicFactor = metallicFactor;
        return this;
    }

    public PbrMaterial roughnessFactor(float roughnessFactor) {
        this.roughnessFactor = roughnessFactor;
        return this;
    }

    public PbrMaterial metallicRoughnessTexture(Texture texture) {
        this.metallicRoughnessTexture = texture;
        return this;
    }

    public PbrMaterial normalTexture(Texture texture) {
        this.normalTexture = texture;
        return this;
    }

    public PbrMaterial occlusionTexture(Texture texture) {
        this.occlusionTexture = texture;
        return this;
    }

    public PbrMaterial emissiveFactor(Color emissiveFactor) {
        this.emissiveFactor = emissiveFactor != null ? emissiveFactor : Color.BLACK;
        return this;
    }

    public PbrMaterial emissiveFactor(float red, float green, float blue) {
        this.emissiveFactor = new Color(red, green, blue, 1.0f);
        return this;
    }

    public PbrMaterial emissiveTexture(Texture texture) {
        this.emissiveTexture = texture;
        return this;
    }

    public PbrMaterial alphaMode(MaterialAlphaMode alphaMode) {
        this.alphaMode = alphaMode != null ? alphaMode : MaterialAlphaMode.OPAQUE;
        return this;
    }

    public PbrMaterial alphaCutoff(float alphaCutoff) {
        this.alphaCutoff = alphaCutoff;
        return this;
    }

    public PbrMaterial doubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
        return this;
    }

    public PbrMaterial shaderProvider(ShaderProvider3D shaderProvider) {
        this.shaderProvider = shaderProvider;
        return this;
    }

    @Override
    public String id() {
        return id;
    }

    public Color baseColor() {
        return baseColor;
    }

    public Texture baseColorTexture() {
        return baseColorTexture;
    }

    public float metallicFactor() {
        return metallicFactor;
    }

    public float roughnessFactor() {
        return roughnessFactor;
    }

    public Texture metallicRoughnessTexture() {
        return metallicRoughnessTexture;
    }

    public Texture normalTexture() {
        return normalTexture;
    }

    public Texture occlusionTexture() {
        return occlusionTexture;
    }

    public Color emissiveFactor() {
        return emissiveFactor;
    }

    public Texture emissiveTexture() {
        return emissiveTexture;
    }

    @Override
    public MaterialAlphaMode alphaMode() {
        return alphaMode;
    }

    public float alphaCutoff() {
        return alphaCutoff;
    }

    @Override
    public boolean doubleSided() {
        return doubleSided;
    }

    @Override
    public ShaderProvider3D shaderProvider() {
        return shaderProvider;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        Set<Texture> textures = new HashSet<Texture>();
        textures.add(baseColorTexture);
        textures.add(metallicRoughnessTexture);
        textures.add(normalTexture);
        textures.add(occlusionTexture);
        textures.add(emissiveTexture);
        for (Texture texture : textures) {
            if (texture != null) {
                texture.dispose();
            }
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
