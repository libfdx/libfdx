package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;

public final class DirectionalLight implements Light {
    private Color color = Color.WHITE;
    private float intensity = 1.0f;
    private Vector3 direction = new Vector3(0.0f, -1.0f, 0.0f);

    public DirectionalLight color(Color color) {
        this.color = color != null ? color : Color.WHITE;
        return this;
    }

    public DirectionalLight intensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    public DirectionalLight direction(float x, float y, float z) {
        this.direction = new Vector3(x, y, z).normalize();
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

    public Vector3 direction() {
        return direction;
    }
}
