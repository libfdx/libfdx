package io.github.libfdx.samples.ecs.platformer.system;

import io.github.libfdx.ecs.World;
import io.github.libfdx.samples.ecs.platformer.PlatformerConstants;
import io.github.libfdx.ecs.system.System;

abstract class BaseGameSystem implements System {
    protected World world;
    private boolean enabled = true;

    @Override
    public final void onAttach(World world) {
        this.world = world;
        attach(world);
    }

    @Override
    public final void onDetach(World world) {
        detach(world);
        this.world = null;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected void attach(World world) {
    }

    protected void detach(World world) {
    }

    protected final float deltaTime() {
        float delta = world.deltaTime();
        if (delta <= 0.0f || !Float.isFinite(delta)) {
            return 0.0f;
        }
        return Math.min(delta, PlatformerConstants.MAX_DELTA);
    }
}
