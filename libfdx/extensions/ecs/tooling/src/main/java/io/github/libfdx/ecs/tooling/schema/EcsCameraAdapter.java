package io.github.libfdx.ecs.tooling.schema;

import io.github.libfdx.ecs.World;
import io.github.libfdx.graphics.camera.Camera;

/** Exposes project cameras to portable launchers and scene tools. */
public interface EcsCameraAdapter {
    /** Returns the active camera entity, or zero when no project camera is active. */
    int activeCameraEntity(World world);

    /** Returns the entity camera, or {@code null} when the entity has no camera. */
    Camera camera(World world, int entity);
}
