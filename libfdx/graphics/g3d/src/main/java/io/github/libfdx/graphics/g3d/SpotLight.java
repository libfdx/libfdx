package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;

/**
 * Represents a spot light.
 *
 * @author xpenatan
 */
public final class SpotLight implements Light {
    private Color color = Color.WHITE;
    private float intensity = 1.0f;
    private Vector3 position = Vector3.ZERO;
    private Vector3 direction = new Vector3(0.0f, -1.0f, 0.0f);
    private float range = 1.0f;
    private float innerConeDegrees = 15.0f;
    private float outerConeDegrees = 30.0f;

    /**
     * Sets the color and returns this spot light.
     *
     * @param color the color
     * @return this spot light for chaining
     */
    public SpotLight color(Color color) {
        this.color = color != null ? color : Color.WHITE;
        return this;
    }

    /**
     * Sets the intensity and returns this spot light.
     *
     * @param intensity the intensity
     * @return this spot light for chaining
     */
    public SpotLight intensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    /**
     * Sets the position and returns this spot light.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this spot light for chaining
     */
    public SpotLight position(float x, float y, float z) {
        this.position = new Vector3(x, y, z);
        return this;
    }

    /**
     * Sets the direction and returns this spot light.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this spot light for chaining
     */
    public SpotLight direction(float x, float y, float z) {
        this.direction = new Vector3(x, y, z).normalize();
        return this;
    }

    /**
     * Sets the range and returns this spot light.
     *
     * @param range the range
     * @return this spot light for chaining
     */
    public SpotLight range(float range) {
        this.range = range;
        return this;
    }

    /**
     * Sets the cone and returns this spot light.
     *
     * @param innerConeDegrees the inner cone degrees
     * @param outerConeDegrees the outer cone degrees
     * @return this spot light for chaining
     */
    public SpotLight cone(float innerConeDegrees, float outerConeDegrees) {
        this.innerConeDegrees = innerConeDegrees;
        this.outerConeDegrees = outerConeDegrees;
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
     * Returns the direction.
     *
     * @return the direction
     */
    public Vector3 direction() {
        return direction;
    }

    /**
     * Returns the range.
     *
     * @return the range
     */
    public float range() {
        return range;
    }

    /**
     * Returns the inner cone degrees.
     *
     * @return the inner cone degrees
     */
    public float innerConeDegrees() {
        return innerConeDegrees;
    }

    /**
     * Returns the outer cone degrees.
     *
     * @return the outer cone degrees
     */
    public float outerConeDegrees() {
        return outerConeDegrees;
    }
}
