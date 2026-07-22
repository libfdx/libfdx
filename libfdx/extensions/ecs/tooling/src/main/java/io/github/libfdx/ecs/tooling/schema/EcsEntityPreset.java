package io.github.libfdx.ecs.tooling.schema;

import io.github.libfdx.ecs.World;

/** A named component layout that a tool can apply to a newly created entity. */
public interface EcsEntityPreset {
    String id();

    String name();

    void populate(World world, int entity);
}
