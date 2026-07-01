package io.github.libfdx.ecs.system;

import io.github.libfdx.ecs.World;

public interface System {
    void onAttach(World world);

    void onDetach(World world);

    void update();

    boolean isEnabled();

    void setEnabled(boolean enabled);
}
