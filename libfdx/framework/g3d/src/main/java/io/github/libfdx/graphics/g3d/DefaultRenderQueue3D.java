package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.graphics.camera.Camera;

import java.util.Comparator;

/**
 * Provides the default implementation of a render queue3 d.
 *
 * @author xpenatan
 */
public final class DefaultRenderQueue3D implements RenderQueue3D {
    private final Array<Renderable3D> renderables = new Array<Renderable3D>();
    private final ArrayView<Renderable3D> readOnlyRenderables = renderables.view();
    private Renderable3D[] sortScratch = new Renderable3D[0];
    private final Comparator<Renderable3D> stateComparator = new Comparator<Renderable3D>() {
        @Override
        public int compare(Renderable3D left, Renderable3D right) {
            int alpha = left.material().alphaMode().compareTo(right.material().alphaMode());
            if (alpha != 0) {
                return alpha;
            }
            int shadingModel = left.material().shadingModel()
                    .compareTo(right.material().shadingModel());
            if (shadingModel != 0) {
                return shadingModel;
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
        int size = renderables.size();
        if (size < 2) {
            return;
        }
        ensureSortScratch(size);
        int width = 1;
        while (width < size) {
            for (int left = 0; left < size; left += width * 2) {
                int middle = Math.min(left + width, size);
                int right = Math.min(left + width * 2, size);
                merge(left, middle, right);
            }
            for (int i = 0; i < size; i++) {
                renderables.set(i, sortScratch[i]);
            }
            if (width > size / 2) {
                break;
            }
            width *= 2;
        }
        for (int i = 0; i < size; i++) {
            sortScratch[i] = null;
        }
    }

    private void ensureSortScratch(int size) {
        if (sortScratch.length >= size) {
            return;
        }
        int capacity = Math.max(8, sortScratch.length);
        while (capacity < size) {
            capacity *= 2;
        }
        sortScratch = new Renderable3D[capacity];
    }

    private void merge(int left, int middle, int right) {
        int leftIndex = left;
        int rightIndex = middle;
        int output = left;
        while (leftIndex < middle && rightIndex < right) {
            Renderable3D leftValue = renderables.get(leftIndex);
            Renderable3D rightValue = renderables.get(rightIndex);
            if (stateComparator.compare(leftValue, rightValue) <= 0) {
                sortScratch[output++] = leftValue;
                leftIndex++;
            }
            else {
                sortScratch[output++] = rightValue;
                rightIndex++;
            }
        }
        while (leftIndex < middle) {
            sortScratch[output++] = renderables.get(leftIndex++);
        }
        while (rightIndex < right) {
            sortScratch[output++] = renderables.get(rightIndex++);
        }
    }

    /**
     * Returns the renderables.
     *
     * @return the renderables
     */
    @Override
    public ArrayView<Renderable3D> renderables() {
        return readOnlyRenderables;
    }
}
