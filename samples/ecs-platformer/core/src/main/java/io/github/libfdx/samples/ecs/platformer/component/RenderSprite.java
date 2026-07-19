package io.github.libfdx.samples.ecs.platformer.component;

import io.github.libfdx.ecs.component.Component;

public final class RenderSprite implements Component {
    public int regionId;
    public final int layer;
    public float parallax = 1.0f;
    public float red = 1.0f;
    public float green = 1.0f;
    public float blue = 1.0f;
    public float alpha = 1.0f;

    public RenderSprite(int regionId, int layer) {
        this.regionId = regionId;
        this.layer = layer;
    }

    public RenderSprite parallax(float parallax) {
        this.parallax = parallax;
        return this;
    }

    public RenderSprite tint(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        return this;
    }
}
