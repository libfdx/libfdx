package io.github.libfdx.ecs.schema;

import io.github.libfdx.ecs.World;
import io.github.libfdx.math.BoundingBox;

/** Supplies reusable world-space picking bounds for hosts and scene tools. */
public interface EcsBoundsAdapter {
    /** Writes bounds into {@code out}; returns false when the entity is not pickable. */
    boolean bounds(World world, int entity, BoundingBox out);
}
