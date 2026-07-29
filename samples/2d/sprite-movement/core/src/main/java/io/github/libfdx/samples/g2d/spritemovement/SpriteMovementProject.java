package io.github.libfdx.samples.g2d.spritemovement;

import io.github.libfdx.Fdx;
import io.github.libfdx.ecs.EcsProject;
import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.manager.CameraManager;
import io.github.libfdx.samples.g2d.spritemovement.input.KeyboardMovementInput;
import io.github.libfdx.samples.g2d.spritemovement.render.SpriteMovementRenderSystem;
import io.github.libfdx.samples.g2d.spritemovement.scene.SpriteMovementScenes;
import io.github.libfdx.samples.g2d.spritemovement.system.PlayerControlSystem;
import java.nio.charset.StandardCharsets;

/** Portable 2D Sprite Movement entry used by standalone launchers and external hosts. */
public final class SpriteMovementProject implements EcsProject {
    public static final String PROJECT_ID = "io.github.libfdx.samples.g2d.spritemovement";
    public static final String PLAYER_SPRITE = "sprites/player.png";
    public static final String WALL_TILE = "tiles/wall.png";
    public static final String DEFAULT_SCENE = "scenes/main.fdxscene";

    private final long exitAfterFrames;

    public SpriteMovementProject() {
        this(0L);
    }

    public SpriteMovementProject(long exitAfterFrames) {
        this.exitAfterFrames = Math.max(0L, exitAfterFrames);
    }

    @Override
    public void initialize(Fdx fdx, World world) {
        if (fdx == null || world == null) {
            throw new IllegalArgumentException("fdx and world cannot be null.");
        }
        world.scenes().projectId(PROJECT_ID);
        SpriteMovementScenes.configure(world.scenes());
        CameraManager cameras = new CameraManager();
        SpriteMovementRenderSystem renderer =
                new SpriteMovementRenderSystem(fdx, cameras, exitAfterFrames);

        requireRegistration(world.addManager(cameras, CameraManager.class), "camera manager");
        requireRegistration(world.addSystem(new PlayerControlSystem(
                new KeyboardMovementInput(fdx.input()))), "player control system");
        requireRegistration(world.addSystem(renderer), "render system");
        world.flushCommands();

        world.scenes().apply(readDefaultScene(fdx));
        renderer.refreshCamera(1, 1);
    }

    private static <T> T requireRegistration(T value, String label) {
        if (value == null) {
            throw new IllegalStateException("Could not register " + label + ".");
        }
        return value;
    }

    private static String readDefaultScene(Fdx fdx) {
        try {
            return fdx.files().internal(DEFAULT_SCENE).readString(StandardCharsets.UTF_8).get();
        } catch (RuntimeException missingProjectPath) {
            int separator = DEFAULT_SCENE.lastIndexOf('/');
            String packagedPath = separator >= 0
                    ? DEFAULT_SCENE.substring(separator + 1)
                    : DEFAULT_SCENE;
            try {
                return fdx.files().internal(packagedPath).readString(StandardCharsets.UTF_8).get();
            } catch (RuntimeException missingPackagedPath) {
                missingPackagedPath.addSuppressed(missingProjectPath);
                throw missingPackagedPath;
            }
        }
    }
}
