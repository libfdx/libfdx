package io.github.libfdx.ecs.event;

import io.github.libfdx.ecs.World;

public interface EventListener {
    void onEvent(World world, Event event);
}
