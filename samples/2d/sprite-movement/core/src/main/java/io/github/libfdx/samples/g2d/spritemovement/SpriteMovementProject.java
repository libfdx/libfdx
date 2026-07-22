package io.github.libfdx.samples.g2d.spritemovement;

import io.github.libfdx.ecs.tooling.EcsProject;
import io.github.libfdx.ecs.tooling.EcsProjectRuntime;
import io.github.libfdx.ecs.tooling.schema.EcsProjectSchema;
import io.github.libfdx.samples.g2d.spritemovement.scene.SpriteMovementSceneSchema;

/** Portable 2D Sprite Movement project used by standalone launchers and the desktop editor. */
public final class SpriteMovementProject extends EcsProject {
    public static final String PROJECT_ID = "io.github.libfdx.samples.g2d.spritemovement";
    public static final String PLAYER_SPRITE = "sprites/player.png";
    public static final String WALL_TILE = "tiles/wall.png";
    public static final String DEFAULT_SCENE = "scenes/main.fdxscene";

    private final long exitAfterFrames;

    public SpriteMovementProject() {
        this(0L);
    }

    public SpriteMovementProject(long exitAfterFrames) {
        super(PROJECT_ID, "libFDX 2D Sprite Movement", "assets", DEFAULT_SCENE);
        this.exitAfterFrames = Math.max(0L, exitAfterFrames);
    }

    @Override
    public EcsProjectSchema schema() {
        return SpriteMovementSceneSchema.schema();
    }

    @Override
    public EcsProjectRuntime createRuntime() {
        return new SpriteMovementRuntime(this, exitAfterFrames);
    }
}
