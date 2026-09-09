package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;

import java.util.Comparator;

/**
 * Groups opaque and masked draws by state, then orders blended draws back to
 * front by their transformed local-bounds centers along the camera direction.
 * Equal depths use state order; equal state keys retain submission order.
 *
 * <p>Sorting reuses its storage after capacity growth. Bounds are not updated
 * for animation automatically, and object sorting does not resolve arbitrary
 * intersecting transparent geometry. This queue is application-thread owned.</p>
 *
 * @author xpenatan
 */
public final class DefaultRenderQueue3D implements RenderQueue3D {
    private final Array<Renderable3D> renderables = new Array<Renderable3D>();
    private final ArrayView<Renderable3D> readOnlyRenderables = renderables.view();
    private Renderable3D[] sortScratch = new Renderable3D[0];
    private double[] sortDepths = new double[0];
    private double[] sortDepthScratch = new double[0];
    private final float[] transformValues = new float[Matrix4.VALUE_COUNT];
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

    /** Removes definitely invisible entries in place, preserving submission order and releasing their references.
     * The caller updates the borrowed culler first. Returns the number removed; reuses existing storage. */
    public int cull(FrustumCuller3D culler) {
        if(culler==null)throw new FdxException("Frustum culler cannot be null");
        int previous=renderables.size(),visible=0;
        for(int i=0;i<previous;i++) {
            Renderable3D renderable=renderables.get(i);
            if(culler.isVisible(renderable))renderables.set(visible++,renderable);
        }
        renderables.truncate(visible);return previous-visible;
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
     * Sorts opaque/masked state groups before back-to-front blended draws.
     *
     * @param camera the non-null camera whose current position and direction
     * determine view depth, independently of projection or clip-depth range
     * @throws FdxException if the camera is null or a computed blended depth
     * is not finite
     */
    @Override
    public void sort(Camera camera) {
        if (camera == null) {
            throw new FdxException("Render queue camera cannot be null");
        }
        int size = renderables.size();
        if (size < 2) {
            return;
        }
        ensureSortScratch(size);
        for (int i = 0; i < size; i++) {
            Renderable3D renderable = renderables.get(i);
            sortDepths[i] = renderable.material().alphaMode() == MaterialAlphaMode.BLEND
                    ? viewDepth(renderable, camera) : 0.0;
        }
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
            System.arraycopy(sortDepthScratch, 0, sortDepths, 0, size);
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
        sortDepths = new double[capacity];
        sortDepthScratch = new double[capacity];
    }

    private double viewDepth(Renderable3D renderable, Camera camera) {
        BoundingBox bounds = renderable.bounds();
        Vector3 min = bounds.min();
        Vector3 max = bounds.max();
        double x = ((double)min.x() + max.x()) * 0.5;
        double y = ((double)min.y() + max.y()) * 0.5;
        double z = ((double)min.z() + max.z()) * 0.5;
        renderable.worldTransform().copyValues(transformValues, 0);
        Vector3 eye = camera.position();
        Vector3 direction = camera.direction();
        // Subtract the eye from the translation before adding local offsets:
        // a float world-space center would erase them in large worlds.
        double relativeX = ((double)transformValues[12] - eye.x())
                + transformValues[0] * x + transformValues[4] * y + transformValues[8] * z;
        double relativeY = ((double)transformValues[13] - eye.y())
                + transformValues[1] * x + transformValues[5] * y + transformValues[9] * z;
        double relativeZ = ((double)transformValues[14] - eye.z())
                + transformValues[2] * x + transformValues[6] * y + transformValues[10] * z;
        double depth = relativeX * direction.x() + relativeY * direction.y() + relativeZ * direction.z();
        if (!Double.isFinite(depth)) {
            throw new FdxException("Blended renderable depth must be finite: " + renderable.material().id());
        }
        return depth;
    }

    private int compare(Renderable3D left, double leftDepth, Renderable3D right, double rightDepth) {
        if (left.material().alphaMode() == MaterialAlphaMode.BLEND
                && right.material().alphaMode() == MaterialAlphaMode.BLEND) {
            int depthOrder = Double.compare(rightDepth, leftDepth);
            if (depthOrder != 0) {
                return depthOrder;
            }
        }
        return stateComparator.compare(left, right);
    }

    private void merge(int left, int middle, int right) {
        int leftIndex = left;
        int rightIndex = middle;
        int output = left;
        while (leftIndex < middle && rightIndex < right) {
            Renderable3D leftValue = renderables.get(leftIndex);
            Renderable3D rightValue = renderables.get(rightIndex);
            double leftDepth = sortDepths[leftIndex];
            double rightDepth = sortDepths[rightIndex];
            if (compare(leftValue, leftDepth, rightValue, rightDepth) <= 0) {
                sortScratch[output] = leftValue;
                sortDepthScratch[output++] = leftDepth;
                leftIndex++;
            }
            else {
                sortScratch[output] = rightValue;
                sortDepthScratch[output++] = rightDepth;
                rightIndex++;
            }
        }
        while (leftIndex < middle) {
            sortScratch[output] = renderables.get(leftIndex);
            sortDepthScratch[output++] = sortDepths[leftIndex++];
        }
        while (rightIndex < right) {
            sortScratch[output] = renderables.get(rightIndex);
            sortDepthScratch[output++] = sortDepths[rightIndex++];
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
