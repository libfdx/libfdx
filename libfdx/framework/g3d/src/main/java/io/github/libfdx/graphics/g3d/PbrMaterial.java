package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.collections.ObjectSet;
import io.github.libfdx.math.Color;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.Texture;


/**
 * Represents a pbr material.
 *
 * @author xpenatan
 */
public class PbrMaterial implements Material, Disposable {
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

    /**
     * Creates a PBR material.
     *
     * @param id the identifier
     */
    public PbrMaterial(String id) {
        this.id = id != null ? id : "";
    }

    /**
     * Sets the base color and returns this PBR material.
     *
     * @param baseColor the base color
     * @return this PBR material for chaining
     */
    public PbrMaterial baseColor(Color baseColor) {
        this.baseColor = baseColor != null ? baseColor : Color.WHITE;
        return this;
    }

    /**
     * Sets the base color and returns this PBR material.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     * @return this PBR material for chaining
     */
    public PbrMaterial baseColor(float red, float green, float blue, float alpha) {
        this.baseColor = new Color(red, green, blue, alpha);
        return this;
    }

    /**
     * Sets the base color texture and returns this PBR material.
     *
     * @param texture the texture
     * @return this PBR material for chaining
     */
    public PbrMaterial baseColorTexture(Texture texture) {
        this.baseColorTexture = texture;
        return this;
    }

    /**
     * Sets the metallic factor and returns this PBR material.
     *
     * @param metallicFactor the metallic factor
     * @return this PBR material for chaining
     */
    public PbrMaterial metallicFactor(float metallicFactor) {
        this.metallicFactor = metallicFactor;
        return this;
    }

    /**
     * Sets the roughness factor and returns this PBR material.
     *
     * @param roughnessFactor the roughness factor
     * @return this PBR material for chaining
     */
    public PbrMaterial roughnessFactor(float roughnessFactor) {
        this.roughnessFactor = roughnessFactor;
        return this;
    }

    /**
     * Sets the metallic roughness texture and returns this PBR material.
     *
     * @param texture the texture
     * @return this PBR material for chaining
     */
    public PbrMaterial metallicRoughnessTexture(Texture texture) {
        this.metallicRoughnessTexture = texture;
        return this;
    }

    /**
     * Sets the normal texture and returns this PBR material.
     *
     * @param texture the texture
     * @return this PBR material for chaining
     */
    public PbrMaterial normalTexture(Texture texture) {
        this.normalTexture = texture;
        return this;
    }

    /**
     * Sets the occlusion texture and returns this PBR material.
     *
     * @param texture the texture
     * @return this PBR material for chaining
     */
    public PbrMaterial occlusionTexture(Texture texture) {
        this.occlusionTexture = texture;
        return this;
    }

    /**
     * Sets the emissive factor and returns this PBR material.
     *
     * @param emissiveFactor the emissive factor
     * @return this PBR material for chaining
     */
    public PbrMaterial emissiveFactor(Color emissiveFactor) {
        this.emissiveFactor = emissiveFactor != null ? emissiveFactor : Color.BLACK;
        return this;
    }

    /**
     * Sets the emissive factor and returns this PBR material.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @return this PBR material for chaining
     */
    public PbrMaterial emissiveFactor(float red, float green, float blue) {
        this.emissiveFactor = new Color(red, green, blue, 1.0f);
        return this;
    }

    /**
     * Sets the emissive texture and returns this PBR material.
     *
     * @param texture the texture
     * @return this PBR material for chaining
     */
    public PbrMaterial emissiveTexture(Texture texture) {
        this.emissiveTexture = texture;
        return this;
    }

    /**
     * Sets the alpha mode and returns this PBR material.
     *
     * @param alphaMode the alpha mode
     * @return this PBR material for chaining
     */
    public PbrMaterial alphaMode(MaterialAlphaMode alphaMode) {
        this.alphaMode = alphaMode != null ? alphaMode : MaterialAlphaMode.OPAQUE;
        return this;
    }

    /**
     * Sets the alpha cutoff and returns this PBR material.
     *
     * @param alphaCutoff the alpha cutoff
     * @return this PBR material for chaining
     */
    public PbrMaterial alphaCutoff(float alphaCutoff) {
        this.alphaCutoff = alphaCutoff;
        return this;
    }

    /**
     * Sets the double sided and returns this PBR material.
     *
     * @param doubleSided the double sided
     * @return this PBR material for chaining
     */
    public PbrMaterial doubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
        return this;
    }

    /**
     * Sets the shader provider and returns this PBR material.
     *
     * @param shaderProvider the shader provider
     * @return this PBR material for chaining
     */
    public PbrMaterial shaderProvider(ShaderProvider3D shaderProvider) {
        this.shaderProvider = shaderProvider;
        return this;
    }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    @Override
    public String id() {
        return id;
    }

    /**
     * Returns the base color.
     *
     * @return the base color
     */
    public Color baseColor() {
        return baseColor;
    }

    /**
     * Returns the base color texture.
     *
     * @return the base color texture
     */
    public Texture baseColorTexture() {
        return baseColorTexture;
    }

    /**
     * Returns the metallic factor.
     *
     * @return the metallic factor
     */
    public float metallicFactor() {
        return metallicFactor;
    }

    /**
     * Returns the roughness factor.
     *
     * @return the roughness factor
     */
    public float roughnessFactor() {
        return roughnessFactor;
    }

    /**
     * Returns the metallic roughness texture.
     *
     * @return the metallic roughness texture
     */
    public Texture metallicRoughnessTexture() {
        return metallicRoughnessTexture;
    }

    /**
     * Returns the normal texture.
     *
     * @return the normal texture
     */
    public Texture normalTexture() {
        return normalTexture;
    }

    /**
     * Returns the occlusion texture.
     *
     * @return the occlusion texture
     */
    public Texture occlusionTexture() {
        return occlusionTexture;
    }

    /**
     * Returns the emissive factor.
     *
     * @return the emissive factor
     */
    public Color emissiveFactor() {
        return emissiveFactor;
    }

    /**
     * Returns the emissive texture.
     *
     * @return the emissive texture
     */
    public Texture emissiveTexture() {
        return emissiveTexture;
    }

    /**
     * Returns the alpha mode.
     *
     * @return the alpha mode
     */
    @Override
    public MaterialAlphaMode alphaMode() {
        return alphaMode;
    }

    /**
     * Returns the alpha cutoff.
     *
     * @return the alpha cutoff
     */
    public float alphaCutoff() {
        return alphaCutoff;
    }

    /**
     * Returns the double sided.
     *
     * @return true if double sided succeeds or is active; false otherwise
     */
    @Override
    public boolean doubleSided() {
        return doubleSided;
    }

    /**
     * Returns the shader provider.
     *
     * @return the shader provider
     */
    @Override
    public ShaderProvider3D shaderProvider() {
        return shaderProvider;
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
        ObjectSet<Texture> textures = new ObjectSet<Texture>();
        addTexture(textures, baseColorTexture);
        addTexture(textures, metallicRoughnessTexture);
        addTexture(textures, normalTexture);
        addTexture(textures, occlusionTexture);
        addTexture(textures, emissiveTexture);
        ObjectIterator<Texture> iterator = textures.iterator();
        while (iterator.hasNext()) {
            iterator.next().dispose();
        }
    }

    private static void addTexture(ObjectSet<Texture> textures, Texture texture) {
        if (texture != null) {
            textures.add(texture);
        }
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
}
