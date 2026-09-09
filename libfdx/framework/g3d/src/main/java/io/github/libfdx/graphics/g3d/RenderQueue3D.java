package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.graphics.camera.Camera;

/**
 * Defines the contract for render queue3 d implementations.
 *
 * @author xpenatan
 */
public interface RenderQueue3D {
    /**
     * Runs the clear step.
     */
    void clear();

    /**
     * Runs the add step.
     *
     * @param renderable the renderable
     */
    void add(Renderable3D renderable);

    /**
     * Returns the size.
     *
     * @return the size
     */
    int size();

    /**
     * Runs the get step.
     *
     * @param index the index
     * @return the get
     */
    Renderable3D get(int index);

    /**
     * Orders queued draws for the supplied camera. The default queue groups
     * opaque and masked draws by state before sorting blended draws back to
     * front using transformed local-bounds centers.
     *
     * @param camera the non-null camera used for view-depth ordering
     */
    void sort(Camera camera);

    /**
     * Returns the renderables.
     *
     * @return the renderables
     */
    ArrayView<Renderable3D> renderables();
}
