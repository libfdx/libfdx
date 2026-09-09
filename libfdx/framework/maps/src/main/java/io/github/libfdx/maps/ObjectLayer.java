package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;

/** Immutable object membership in editor order, with a precomputed stable optional top-down draw order. */
public final class ObjectLayer extends MapLayer {
    private final MapObject[] objects;
    private final int[] drawOrder;
    public ObjectLayer(MapObject[] objects, boolean topDown) {
        if (objects == null) { throw new FdxException("Objects cannot be null"); }
        this.objects = objects.clone();
        drawOrder = new int[objects.length];
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] == null) { throw new FdxException("Object cannot be null"); }
            drawOrder[i] = i;
        }
        if (topDown) {
            // Sort setup-only indices, retaining editor order for equal Y.
            Integer[] order = new Integer[objects.length];
            for (int i = 0; i < order.length; i++) { order[i] = i; }
            java.util.Arrays.sort(order, (a, b) -> Float.compare(objects[b].y(), objects[a].y()));
            for (int i = 0; i < order.length; i++) { drawOrder[i] = order[i]; }
        }
    }
    public int objectCount() { return objects.length; }
    public MapObject object(int index) { return objects[index]; }
    public MapObject drawObject(int index) { return objects[drawOrder[index]]; }
}
