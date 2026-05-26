package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;

public final class PointLight implements Light {
    private Color color = Color.WHITE;
    private float intensity = 1.0f;
    private Vector3 position = Vector3.ZERO;
    private float range = 1.0f;

    public PointLight color(Color color) {
        this.color = color != null ? color : Color.WHITE;
        return this;
    }

    public PointLight intensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    public PointLight position(float x, float y, float z) {
        this.position = new Vector3(x, y, z);
        return this;
    }

    public PointLight range(float range) {
        this.range = range;
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

    public float range() {
        return range;
    }
}
