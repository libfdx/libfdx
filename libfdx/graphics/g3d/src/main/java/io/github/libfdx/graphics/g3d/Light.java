package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;

/**
 * Defines the contract for light implementations.
 *
 * @author xpenatan
 */
public interface Light {
    /**
     * Returns the color.
     *
     * @return the color
     */
    Color color();

    /**
     * Returns the intensity.
     *
     * @return the intensity
     */
    float intensity();
}
