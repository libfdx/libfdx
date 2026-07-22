package io.github.libfdx.samples.g2d.spritemovement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.component.TransformComponent;
import io.github.libfdx.ecs.tooling.scene.EcsSceneCodec;
import io.github.libfdx.ecs.tooling.scene.EcsSceneDocument;
import io.github.libfdx.ecs.tooling.schema.EcsComponentDescriptor;
import io.github.libfdx.ecs.tooling.schema.EcsProjectSchema;
import io.github.libfdx.ecs.tooling.schema.EcsPropertyDescriptor;
import io.github.libfdx.samples.g2d.spritemovement.component.PlayerControlComponent;
import io.github.libfdx.samples.g2d.spritemovement.component.Sprite2DComponent;
import io.github.libfdx.samples.g2d.spritemovement.component.WallComponent;
import io.github.libfdx.samples.g2d.spritemovement.input.MovementInput;
import io.github.libfdx.samples.g2d.spritemovement.scene.SpriteMovementSceneSchema;
import io.github.libfdx.samples.g2d.spritemovement.system.PlayerControlSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SpriteMovementProjectTest {
    private static final float EPSILON = 0.000001f;

    @Test
    void checkedInSceneUsesThePortableSchemaAndRoundTripsDeterministically() throws Exception {
        SpriteMovementProject project = new SpriteMovementProject();
        EcsProjectSchema schema = project.schema();
        assertNotNull(schema.transforms());
        assertNotNull(schema.cameras());
        assertNotNull(schema.bounds());
        assertNotNull(schema.assets());
        assertEquals(4, schema.presetCount());

        String source = Files.readString(Path.of(SpriteMovementProject.DEFAULT_SCENE));
        EcsSceneCodec codec = new EcsSceneCodec(project);
        EcsSceneDocument document = codec.read(source);
        World world = new World();
        codec.apply(world, document);

        assertEquals(6, world.entityCount());
        int player = SpriteMovementSceneSchema.findByPersistentId(world, 1L);
        assertTrue(player != 0);
        assertEquals(SpriteMovementProject.PLAYER_SPRITE,
                world.require(player, Sprite2DComponent.class).assetPath);
        assertNotNull(world.require(player, PlayerControlComponent.class));
        assertNotNull(world.require(player, TransformComponent.class));

        int wall = SpriteMovementSceneSchema.findByPersistentId(world, 2L);
        assertEquals(SpriteMovementProject.WALL_TILE, world.require(wall, Sprite2DComponent.class).assetPath);
        assertNotNull(world.require(wall, WallComponent.class));
        assertEquals(codec.write(document), codec.write(codec.read(codec.write(document))));
    }

    @Test
    void typedInspectorPropertyAndOrdinaryEcsSystemEditRuntimeState() throws Exception {
        SpriteMovementProject project = new SpriteMovementProject();
        EcsSceneCodec codec = new EcsSceneCodec(project);
        MutableMovementInput input = new MutableMovementInput();
        World world = new World();
        world.addSystem(new PlayerControlSystem(input));
        world.flushCommands();
        codec.apply(world, codec.read(Files.readString(Path.of(SpriteMovementProject.DEFAULT_SCENE))));
        int player = SpriteMovementSceneSchema.findByPersistentId(world, 1L);

        EcsComponentDescriptor<PlayerControlComponent> descriptor =
                project.schema().component(PlayerControlComponent.class);
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
        float playerWidth = SpriteMovementRuntime.toClipSize(1.0f, viewportWidth);
        float playerHeight = SpriteMovementRuntime.toClipSize(1.0f, viewportHeight);

        assertEquals(0.25f, playerWidth, EPSILON);
        assertEquals(1.0f / 3.0f, playerHeight, EPSILON);
        assertEquals(-0.125f,
                SpriteMovementRuntime.toClipCenter(0.0f, 0.0f, viewportWidth) - playerWidth * 0.5f,
                EPSILON);
        assertEquals(-1.0f / 6.0f,
                SpriteMovementRuntime.toClipCenter(0.0f, 0.0f, viewportHeight) - playerHeight * 0.5f,
                EPSILON);
        assertEquals(0.5f, SpriteMovementRuntime.toClipCenter(5.0f, 3.0f, viewportWidth), EPSILON);
        assertEquals(0.125f, SpriteMovementRuntime.toClipSize(-1.0f, viewportWidth * 2.0f), EPSILON);
    }

    @Test
    void runtimeRepeatsWallTextureAtHalfWorldUnitIntervals() {
        assertEquals(1, SpriteMovementRuntime.wallTileCount(0.0f));
        assertEquals(1, SpriteMovementRuntime.wallTileCount(0.5f));
        assertEquals(2, SpriteMovementRuntime.wallTileCount(0.51f));
        assertEquals(8, SpriteMovementRuntime.wallTileCount(4.0f));
        assertEquals(13, SpriteMovementRuntime.wallTileCount(6.5f));
        assertEquals(8, SpriteMovementRuntime.wallTileCount(-4.0f));
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
