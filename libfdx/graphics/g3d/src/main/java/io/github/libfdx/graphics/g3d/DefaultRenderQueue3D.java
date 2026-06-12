package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Provides the default implementation of a render queue3 d.
 *
 * @author xpenatan
 */
public final class DefaultRenderQueue3D implements RenderQueue3D {
    private final ArrayList<Renderable3D> renderables = new ArrayList<Renderable3D>();
    private final Comparator<Renderable3D> stateComparator = new Comparator<Renderable3D>() {
        @Override
        public int compare(Renderable3D left, Renderable3D right) {
            int alpha = left.material().alphaMode().compareTo(right.material().alphaMode());
            if (alpha != 0) {
                return alpha;
            }
            int material = left.material().id().compareTo(right.material().id());
            if (material != 0) {
                return material;
            }
            return left.meshPart().mesh().id().compareTo(right.meshPart().mesh().id());
        }
    };

    /**
     * Runs the clear step.
     */
    @Override
    public void clear() {
        renderables.clear();
    }

    /**
     * Runs the add step.
     *
     * @param renderable the renderable
     */
    @Override
    public void add(Renderable3D renderable) {
        if (renderable != null) {
            renderables.add(renderable);
        }
    }

    /**
     * Returns the size.
     *
     * @return the size
     */
    @Override
    public int size() {
        return renderables.size();
    }

    /**
     * Runs the get step.
     *
     * @param index the index
     * @return the get
     */
    @Override
    public Renderable3D get(int index) {
        return renderables.get(index);
    }

    /**
     * Runs the sort step.
     *
     * @param camera the camera
     */
    @Override
    public void sort(Camera camera) {
        Collections.sort(renderables, stateComparator);
    }

    /**
     * Returns the renderables.
     *
     * @return the renderables
     */
    @Override
    public List<Renderable3D> renderables() {
        return Collections.unmodifiableList(renderables);
    }
}
