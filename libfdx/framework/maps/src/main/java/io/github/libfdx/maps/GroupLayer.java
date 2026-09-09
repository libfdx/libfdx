package io.github.libfdx.maps;

import io.github.libfdx.core.FdxException;

/**
 * Ordered layer group with mutable metadata and fixed child membership. Children are
 * borrowed, the array is copied, and at most 32 nested groups are accepted. Visibility,
 * opacity, offsets and parallax compose during rendering without mutating children.
 */
public final class GroupLayer extends MapLayer {
    private final MapLayer[] children;
    private final int depth;

    public GroupLayer(MapLayer... children) {
        if (children == null) { throw new FdxException("Group children cannot be null"); }
        this.children = children.clone();
        int depth = 1;
        for (MapLayer child : this.children) {
            if (child == null) { throw new FdxException("Group child cannot be null"); }
            if (child instanceof GroupLayer group) { depth = Math.max(depth, 1 + group.depth); }
        }
        if (depth > 32) { throw new FdxException("Layer groups exceed maximum depth 32"); }
        this.depth = depth;
    }

    public int layerCount() { return children.length; }
    public MapLayer layer(int index) { return children[index]; }
}
