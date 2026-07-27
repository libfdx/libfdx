package io.github.libfdx.graphics.g3d;

/**
 * Defines the contract for material implementations.
 *
 * @author xpenatan
 */
public interface Material {
    /**
     * Returns the ID.
     *
     * @return the ID
     */
    String id();

    /**
     * Returns the alpha mode.
     *
     * @return the alpha mode
     */
    MaterialAlphaMode alphaMode();

    /**
     * Returns the double sided.
     *
     * @return true if double sided succeeds or is active; false otherwise
     */
    boolean doubleSided();

    /**
     * Returns the shader provider.
     *
     * @return the shader provider
     */
    ShaderProvider3D shaderProvider();

    /**
     * Returns optional provider-neutral shader values/resources.
     *
     * @return material binding, or {@code null}
     */
    default ShaderMaterialBinding shaderBinding() {
        return null;
    }
}
