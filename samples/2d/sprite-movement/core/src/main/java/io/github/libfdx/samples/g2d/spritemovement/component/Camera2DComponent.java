package io.github.libfdx.samples.g2d.spritemovement.component;

import io.github.libfdx.ecs.component.Component;
import io.github.libfdx.graphics.camera.Camera;

/** Editable orthographic camera data with a reusable runtime camera. */
public final class Camera2DComponent implements Component {
    public boolean primary;
    public float viewportHeight = 6.0f;
    public float near = 0.1f;
    public float far = 100.0f;
    public final Camera camera = new Camera();

    public Camera2DComponent() {
    }

    public Camera2DComponent(boolean primary) {
        this.primary = primary;
    }
}
