package io.github.libfdx.ecs.manager;

import io.github.libfdx.ecs.World;

public interface Manager {
    void onAttach(World world);

    void onDetach(World world);
}
