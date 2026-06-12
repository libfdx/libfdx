package io.github.libfdx.graphics.g3d;

/**
 * Represents a shader material.
 *
 * @author xpenatan
 */
public final class ShaderMaterial implements Material {
    private final String id;
    private final ShaderProvider3D shaderProvider;
    private MaterialAlphaMode alphaMode = MaterialAlphaMode.OPAQUE;
    private boolean doubleSided;

    /**
     * Creates a shader material.
     *
     * @param id the identifier
     * @param shaderProvider the shader provider
     */
    public ShaderMaterial(String id, ShaderProvider3D shaderProvider) {
        this.id = id != null ? id : "";
        this.shaderProvider = shaderProvider;
    }

    /**
     * Sets the alpha mode and returns this shader material.
     *
     * @param alphaMode the alpha mode
     * @return this shader material for chaining
     */
    public ShaderMaterial alphaMode(MaterialAlphaMode alphaMode) {
        this.alphaMode = alphaMode != null ? alphaMode : MaterialAlphaMode.OPAQUE;
        return this;
    }

    /**
     * Sets the double sided and returns this shader material.
     *
     * @param doubleSided the double sided
     * @return this shader material for chaining
     */
    public ShaderMaterial doubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
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
     * Returns the alpha mode.
     *
     * @return the alpha mode
     */
    @Override
    public MaterialAlphaMode alphaMode() {
        return alphaMode;
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
}
