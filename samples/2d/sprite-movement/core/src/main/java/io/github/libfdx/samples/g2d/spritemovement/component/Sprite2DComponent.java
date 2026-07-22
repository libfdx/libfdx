package io.github.libfdx.samples.g2d.spritemovement.component;

import io.github.libfdx.ecs.component.Component;
import io.github.libfdx.samples.g2d.spritemovement.SpriteMovementProject;
import io.github.libfdx.samples.g2d.spritemovement.scene.SpriteMovementSceneSchema;

/** Editable 2D sprite data. Asset paths are relative to the project's assets directory. */
public final class Sprite2DComponent implements Component {
    public String assetPath = SpriteMovementProject.PLAYER_SPRITE;
    public float width = 1.0f;
    public float height = 1.0f;
    public float red = 1.0f;
    public float green = 1.0f;
    public float blue = 1.0f;
    public float alpha = 1.0f;

    public Sprite2DComponent() {
    }

    public Sprite2DComponent(String assetPath, float width, float height) {
        this.assetPath = SpriteMovementSceneSchema.normalizeAsset(assetPath);
        this.width = positive(width);
        this.height = positive(height);
    }

    public Sprite2DComponent tint(float red, float green, float blue, float alpha) {
        this.red = clamp(red);
        this.green = clamp(green);
        this.blue = clamp(blue);
        this.alpha = clamp(alpha);
        return this;
    }

    private static float positive(float value) {
        return value > 0.0f ? value : 0.01f;
    }

    public static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
