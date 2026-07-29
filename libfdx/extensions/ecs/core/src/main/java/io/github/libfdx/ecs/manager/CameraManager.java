package io.github.libfdx.ecs.manager;

import io.github.libfdx.ecs.World;
import io.github.libfdx.graphics.camera.Camera;

/**
 * Stores the active game and UI cameras for one ECS world.
 *
 * <p>Applications register one manager instance with each independently owned
 * world. Runtime and tool hosts can then select the appropriate camera from
 * world state. The manager retains camera references but does not create,
 * render, or dispose cameras.</p>
 */
public final class CameraManager implements Manager {
    private Camera game;
    private Camera ui;

    /** Returns the active game camera, or {@code null} when none is selected. */
    public Camera game() {
        return game;
    }

    /**
     * Selects the active game camera, or clears the game slot when {@code camera} is {@code null}.
     *
     * @param camera the camera to select, or {@code null} to clear the game slot
     * @return this manager for chaining
     */
    public CameraManager game(Camera camera) {
        game = camera;
        return this;
    }

    /** Returns the active UI camera, or {@code null} when none is selected. */
    public Camera ui() {
        return ui;
    }

    /**
     * Selects the active UI camera, or clears the UI slot when {@code camera} is {@code null}.
     *
     * @param camera the camera to select, or {@code null} to clear the UI slot
     * @return this manager for chaining
     */
    public CameraManager ui(Camera camera) {
        ui = camera;
        return this;
    }

    /** Clears both camera slots without disposing either camera. */
    public CameraManager clear() {
        game = null;
        ui = null;
        return this;
    }

    @Override
    public void onAttach(World world) {
    }

    @Override
    public void onDetach(World world) {
        clear();
    }
}
