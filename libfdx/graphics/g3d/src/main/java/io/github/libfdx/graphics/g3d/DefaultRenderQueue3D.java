package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

    @Override
    public void clear() {
        renderables.clear();
    }

    @Override
    public void add(Renderable3D renderable) {
        if (renderable != null) {
            renderables.add(renderable);
        }
    }

    @Override
    public int size() {
        return renderables.size();
    }

    @Override
    public Renderable3D get(int index) {
        return renderables.get(index);
    }

    @Override
    public void sort(Camera camera) {
        Collections.sort(renderables, stateComparator);
    }

    @Override
    public List<Renderable3D> renderables() {
        return Collections.unmodifiableList(renderables);
    }
}
