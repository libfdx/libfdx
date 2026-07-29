package io.github.libfdx.ecs.manager;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.libfdx.ecs.World;
import io.github.libfdx.graphics.camera.Camera;
import org.junit.jupiter.api.Test;

final class CameraManagerTest {
    @Test
    void storesIndependentGameAndUiCamerasForOneWorld() {
        World world = new World();
        Camera game = new Camera();
        Camera ui = new Camera();
        CameraManager cameras = world.addManager(
                new CameraManager().game(game).ui(ui),
                CameraManager.class);

        assertNull(world.getManager(CameraManager.class));

        world.flushCommands();

        assertSame(cameras, world.getManager(CameraManager.class));
        assertSame(game, cameras.game());
        assertSame(ui, cameras.ui());
    }

    @Test
    void clearsCameraReferencesWhenDetached() {
        World world = new World();
        CameraManager cameras = world.addManager(
                new CameraManager().game(new Camera()).ui(new Camera()),
                CameraManager.class);
        world.flushCommands();

        world.removeManager(CameraManager.class);
        world.flushCommands();

        assertNull(world.getManager(CameraManager.class));
        assertNull(cameras.game());
        assertNull(cameras.ui());
    }
}
