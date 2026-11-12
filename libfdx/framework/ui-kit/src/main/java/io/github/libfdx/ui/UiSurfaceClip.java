package io.github.libfdx.ui;

/** Reused scanline mask for a drawable and any shaped clipping ancestors. */
final class UiSurfaceClip {
    private UiRoot root;
    private UiNode node;
    private UiShape surface = UiShape.rectangle();
    private UiRect bounds;
    private boolean ancestors;
    private UiNode boundary;
    float left;
    float right;

    void node(UiRoot root, UiNode node) {
        this.root = root;
        this.node = node;
        ancestors = false;
        boundary = null;
        for (UiNode ancestor = node; ancestor != null; ancestor = ancestor.parent()) {
            ancestors |= ancestor.modifier().clipsToBounds() && !root.shapeFor(ancestor).rectangular();
            if (root.isOverlayNode(ancestor)) {
                boundary = ancestor.parent();
                break;
            }
        }
    }

    void surface(UiShape shape, UiRect bounds) {
        this.surface = shape;
        this.bounds = bounds;
    }

    void clearSurface() {
        surface = UiShape.rectangle();
        bounds = null;
    }

    boolean active() {
        return ancestors || !surface.rectangular();
    }

    void span(float y) {
        left = Float.NEGATIVE_INFINITY;
        right = Float.POSITIVE_INFINITY;
        if (!surface.rectangular()) {
            left = surface.left(bounds, y);
            right = surface.right(bounds, y);
            if (y < bounds.y() || y > bounds.bottom()) right = left;
        }
        if (!ancestors) return;
        float worldY = node.renderY(y);
        for (UiNode ancestor = node; ancestor != boundary; ancestor = ancestor.parent()) {
            if (!ancestor.modifier().clipsToBounds()) continue;
            UiShape shape = root.shapeFor(ancestor);
            if (shape.rectangular()) continue;
            UiRect rect = ancestor.bounds();
            float localY = ancestor.localY(worldY);
            if (!Float.isFinite(localY) || localY < rect.y() || localY > rect.bottom()) {
                right = left;
                return;
            }
            left = Math.max(left, node.localX(ancestor.renderX(shape.left(rect, localY))));
            right = Math.min(right, node.localX(ancestor.renderX(shape.right(rect, localY))));
        }
    }

}
