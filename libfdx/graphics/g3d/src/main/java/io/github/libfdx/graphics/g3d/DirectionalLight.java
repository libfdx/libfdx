package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;

/**
 * Represents a directional light.
 *
 * @author xpenatan
 */
public final class DirectionalLight implements Light {
    private Color color = Color.WHITE;
    private float intensity = 1.0f;
    private Vector3 direction = new Vector3(0.0f, -1.0f, 0.0f);

    /**
     * Sets the color and returns this directional light.
     *
     * @param color the color
     * @return this directional light for chaining
     */
    public DirectionalLight color(Color color) {
        this.color = color != null ? color : Color.WHITE;
        return this;
    }

    /**
     * Sets the intensity and returns this directional light.
     *
     * @param intensity the intensity
     * @return this directional light for chaining
     */
    public DirectionalLight intensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    /**
     * Sets the direction and returns this directional light.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this directional light for chaining
     */
    public DirectionalLight direction(float x, float y, float z) {
        this.direction = new Vector3(x, y, z).normalize();
        return this;
    }

    /**
     * Returns the color.
     *
     * @return the color
     */
    @Override
    public Color color() {
        return color;
    }

    /**
     * Returns the intensity.
     *
     * @return the intensity
     */
    @Override
    public float intensity() {
        return intensity;
    }

    /**
     * Returns the direction.
     *
     * @return the direction
     */
    public Vector3 direction() {
        return direction;
    }
}
