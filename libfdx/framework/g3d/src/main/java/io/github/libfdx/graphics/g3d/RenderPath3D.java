package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ObjectIterable;
import io.github.libfdx.graphics.camera.Camera;

import io.github.libfdx.core.Disposable;

/**
 * Schedules 3D rendering independently from a material's
 * {@link ShadingModel}.
 *
 * <p>Implementations may use forward, forward-plus, deferred, or custom pass
 * scheduling. Materials describe surface shading and do not select this
 * frame-level path.</p>
 *
 * @author xpenatan
 */
public interface RenderPath3D extends Disposable {
    /**
     * Renders the current content.
     *
     * @param batch the batch
     * @param camera the camera
     * @param environment the environment
     * @param instances the instances
     */
    void render(Batch3D batch, Camera camera, Environment3D environment,
            ObjectIterable<? extends ModelInstance> instances);
}
