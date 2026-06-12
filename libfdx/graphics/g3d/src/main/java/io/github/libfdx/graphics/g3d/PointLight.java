package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;

/**
 * Represents a point light.
 *
 * @author xpenatan
 */
public final class PointLight implements Light {
    private Color color = Color.WHITE;
    private float intensity = 1.0f;
    private Vector3 position = Vector3.ZERO;
    private float range = 1.0f;

    /**
     * Sets the color and returns this point light.
     *
     * @param color the color
     * @return this point light for chaining
     */
    public PointLight color(Color color) {
        this.color = color != null ? color : Color.WHITE;
        return this;
    }

    /**
     * Sets the intensity and returns this point light.
     *
     * @param intensity the intensity
     * @return this point light for chaining
     */
    public PointLight intensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    /**
     * Sets the position and returns this point light.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this point light for chaining
     */
    public PointLight position(float x, float y, float z) {
        this.position = new Vector3(x, y, z);
        return this;
    }

    /**
     * Sets the range and returns this point light.
     *
     * @param range the range
     * @return this point light for chaining
     */
    public PointLight range(float range) {
        this.range = range;
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
     * Returns the position.
     *
     * @return the position
     */
    public Vector3 position() {
        return position;
    }

    /**
     * Returns the range.
     *
     * @return the range
     */
    public float range() {
        return range;
    }
}
