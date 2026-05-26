package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;

public final class SpotLight implements Light {
    private Color color = Color.WHITE;
    private float intensity = 1.0f;
    private Vector3 position = Vector3.ZERO;
    private Vector3 direction = new Vector3(0.0f, -1.0f, 0.0f);
    private float range = 1.0f;
    private float innerConeDegrees = 15.0f;
    private float outerConeDegrees = 30.0f;

    public SpotLight color(Color color) {
        this.color = color != null ? color : Color.WHITE;
        return this;
    }

    public SpotLight intensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    public SpotLight position(float x, float y, float z) {
        this.position = new Vector3(x, y, z);
        return this;
    }

    public SpotLight direction(float x, float y, float z) {
        this.direction = new Vector3(x, y, z).normalize();
        return this;
    }

    public SpotLight range(float range) {
        this.range = range;
        return this;
    }

    public SpotLight cone(float innerConeDegrees, float outerConeDegrees) {
        this.innerConeDegrees = innerConeDegrees;
        this.outerConeDegrees = outerConeDegrees;
        return this;
    }

    @Override
    public Color color() {
        return color;
    }

    @Override
    public float intensity() {
        return intensity;
    }

    public Vector3 position() {
        return position;
    }

    public Vector3 direction() {
        return direction;
    }

    public float range() {
        return range;
    }

    public float innerConeDegrees() {
        return innerConeDegrees;
    }

    public float outerConeDegrees() {
        return outerConeDegrees;
    }
}
