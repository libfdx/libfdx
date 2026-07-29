package io.github.libfdx.ecs.system;

/** Participates in a world's simulation update phase. */
public interface UpdateSystem extends System {
    /** Advances this system using the delta time exposed by its attached world. */
    void update();
}
