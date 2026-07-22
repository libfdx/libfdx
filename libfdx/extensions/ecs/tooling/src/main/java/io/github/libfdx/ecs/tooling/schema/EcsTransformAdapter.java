package io.github.libfdx.ecs.tooling.schema;

import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.transform.Transform;

/** Maps a project's spatial component layout to the shared libFDX transform value. */
public interface EcsTransformAdapter {
    /** Returns the mutable transform, or {@code null} when the entity is not spatial. */
    Transform transform(World world, int entity);

    /** Adds the project's default spatial component when it is absent. */
    void add(World world, int entity);
}
