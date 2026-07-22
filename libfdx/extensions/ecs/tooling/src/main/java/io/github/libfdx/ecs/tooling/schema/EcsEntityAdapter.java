package io.github.libfdx.ecs.tooling.schema;

import io.github.libfdx.ecs.World;

/** Maps stable scene identity and hierarchy data onto a project's ECS layout. */
public interface EcsEntityAdapter {
    /** Creates a reserved entity and assigns its stable identity and name. */
    int create(World world, long persistentId, String name);

    long persistentId(World world, int entity);

    String name(World world, int entity);

    void name(World world, int entity, String name);

    /** Returns the stable parent ID, or zero for a root entity. */
    long parentId(World world, int entity);

    void parentId(World world, int entity, long parentId);
}
