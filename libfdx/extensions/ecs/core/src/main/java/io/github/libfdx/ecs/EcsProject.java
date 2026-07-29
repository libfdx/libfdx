package io.github.libfdx.ecs;

import io.github.libfdx.Fdx;

/**
 * Initializes one host-owned ECS world with project-specific state.
 *
 * <p>A host constructs a fresh project entry for every independent world,
 * invokes this method once, and then owns update, rendering, and teardown.
 * Project capabilities are registered in the supplied world as managers and
 * systems. Every supplied world already owns its intrinsic scene manager;
 * projects only configure it when they have custom persistent or editor-visible
 * component data.</p>
 */
@FunctionalInterface
public interface EcsProject {
    /**
     * Initializes one new project world.
     *
     * @param fdx the portable libFDX runtime root
     * @param world the new world owned by the host
     */
    void initialize(Fdx fdx, World world);
}
