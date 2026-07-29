package io.github.libfdx.samples.g2d.spritemovement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.TransformComponent;
import io.github.libfdx.ecs.scene.EcsSceneDocument;
import io.github.libfdx.ecs.scene.SceneManager;
import io.github.libfdx.ecs.schema.EcsComponentDescriptor;
import io.github.libfdx.ecs.schema.EcsPropertyDescriptor;
import io.github.libfdx.samples.g2d.spritemovement.component.PlayerControlComponent;
import io.github.libfdx.samples.g2d.spritemovement.component.Sprite2DComponent;
import io.github.libfdx.samples.g2d.spritemovement.component.WallComponent;
import io.github.libfdx.samples.g2d.spritemovement.input.MovementInput;
import io.github.libfdx.samples.g2d.spritemovement.render.SpriteMovementRenderSystem;
import io.github.libfdx.samples.g2d.spritemovement.scene.SpriteMovementScenes;
import io.github.libfdx.samples.g2d.spritemovement.system.PlayerControlSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SpriteMovementProjectTest {
    private static final float EPSILON = 0.000001f;

    @Test
    void checkedInSceneUsesThePortableSchemaAndRoundTripsDeterministically() throws Exception {
        World world = configuredWorld();
        SceneManager scenes = world.scenes();
        assertNotNull(scenes.transforms());
        assertNotNull(scenes.bounds());
        assertNotNull(scenes.assets());
        assertEquals(4, scenes.presetCount());

        String source = Files.readString(Path.of(SpriteMovementProject.DEFAULT_SCENE));
        EcsSceneDocument document = scenes.read(source);
        scenes.apply(document);

        assertEquals(6, world.entityCount());
        int player = SpriteMovementScenes.findByPersistentId(world, 1L);
        assertTrue(player != 0);
        assertEquals(SpriteMovementProject.PLAYER_SPRITE,
                world.require(player, Sprite2DComponent.class).assetPath);
        assertNotNull(world.require(player, PlayerControlComponent.class));
        assertNotNull(world.require(player, TransformComponent.class));

        int wall = SpriteMovementScenes.findByPersistentId(world, 2L);
        assertEquals(SpriteMovementProject.WALL_TILE, world.require(wall, Sprite2DComponent.class).assetPath);
        assertNotNull(world.require(wall, WallComponent.class));
        assertEquals(scenes.write(document), scenes.write(scenes.read(scenes.write(document))));
    }

    @Test
    void typedInspectorPropertyAndOrdinaryEcsSystemEditRuntimeState() throws Exception {
        MutableMovementInput input = new MutableMovementInput();
        World world = configuredWorld();
        world.addSystem(new PlayerControlSystem(input));
        world.flushCommands();
        world.scenes().apply(Files.readString(Path.of(SpriteMovementProject.DEFAULT_SCENE)));
        int player = SpriteMovementScenes.findByPersistentId(world, 1L);

        EcsComponentDescriptor<PlayerControlComponent> descriptor =
                world.scenes().component(PlayerControlComponent.class);
        EcsPropertyDescriptor<PlayerControlComponent> speed = descriptor.property("speed");
        speed.floatValue(world.require(player, PlayerControlComponent.class), 0, 4.0f);
        input.horizontal = 1.0f;
        world.update(0.5f);

        assertEquals(2.0f, world.require(player, TransformComponent.class).transform.x());
        assertEquals(2.0f, world.require(player, TransformComponent.class).transform.matrix().values()[12]);
    }

    @Test
    void projectAssetsAndManifestAreCheckedIn() {
        assertTrue(Files.isRegularFile(Path.of("fdx-project.json")));
        assertTrue(Files.isRegularFile(Path.of("assets", SpriteMovementProject.PLAYER_SPRITE)));
        assertTrue(Files.isRegularFile(Path.of("assets", SpriteMovementProject.WALL_TILE)));
        assertTrue(Files.isRegularFile(Path.of(SpriteMovementProject.DEFAULT_SCENE)));
    }

    @Test
    void runtimeMapsWorldBoundsIntoSpriteBatchClipSpace() {
        float viewportWidth = 8.0f;
        float viewportHeight = 6.0f;
        float playerWidth = SpriteMovementRenderSystem.toClipSize(1.0f, viewportWidth);
        float playerHeight = SpriteMovementRenderSystem.toClipSize(1.0f, viewportHeight);

        assertEquals(0.25f, playerWidth, EPSILON);
        assertEquals(1.0f / 3.0f, playerHeight, EPSILON);
        assertEquals(-0.125f,
                SpriteMovementRenderSystem.toClipCenter(0.0f, 0.0f, viewportWidth) - playerWidth * 0.5f,
                EPSILON);
        assertEquals(-1.0f / 6.0f,
                SpriteMovementRenderSystem.toClipCenter(0.0f, 0.0f, viewportHeight) - playerHeight * 0.5f,
                EPSILON);
        assertEquals(0.5f, SpriteMovementRenderSystem.toClipCenter(5.0f, 3.0f, viewportWidth), EPSILON);
        assertEquals(0.125f, SpriteMovementRenderSystem.toClipSize(-1.0f, viewportWidth * 2.0f), EPSILON);
    }

    @Test
    void runtimeRepeatsWallTextureAtHalfWorldUnitIntervals() {
        assertEquals(1, SpriteMovementRenderSystem.wallTileCount(0.0f));
        assertEquals(1, SpriteMovementRenderSystem.wallTileCount(0.5f));
        assertEquals(2, SpriteMovementRenderSystem.wallTileCount(0.51f));
        assertEquals(8, SpriteMovementRenderSystem.wallTileCount(4.0f));
        assertEquals(13, SpriteMovementRenderSystem.wallTileCount(6.5f));
        assertEquals(8, SpriteMovementRenderSystem.wallTileCount(-4.0f));
    }

    private static World configuredWorld() {
        World world = new World();
        world.scenes().projectId(SpriteMovementProject.PROJECT_ID);
        SpriteMovementScenes.configure(world.scenes());
        return world;
    }

    private static final class MutableMovementInput implements MovementInput {
        float horizontal;
        float vertical;

        @Override
        public float horizontal() {
            return horizontal;
        }

        @Override
        public float vertical() {
            return vertical;
        }
    }
}
