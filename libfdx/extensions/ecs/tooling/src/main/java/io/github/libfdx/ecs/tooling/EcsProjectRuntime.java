package io.github.libfdx.ecs.tooling;

import io.github.libfdx.Fdx;
import io.github.libfdx.ecs.World;
import io.github.libfdx.graphics.camera.Camera;

/** One independently owned world and lifecycle for an ECS project. */
public interface EcsProjectRuntime {
    void create(Fdx fdx);

    /** Returns the stable runtime world after {@link #create(Fdx)} succeeds. */
    World world();

    /** Returns the active game camera, or {@code null} for camera-free rendering. */
    default Camera gameCamera() {
        return null;
    }

    default void resize(int width, int height) {
    }

    /** Advances simulation once. The default implementation updates the runtime world. */
    default void update(float deltaTime) {
        world().update(deltaTime);
    }

    default void render(EcsRenderContext context) {
    }

    default void onFrameEnd() {
    }

    default void pause() {
    }

    default void resume() {
    }

    default void dispose() {
    }
}
