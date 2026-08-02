package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.math.Matrix4;

/**
 * Represents a model node.
 *
 * @author xpenatan
 */
public final class ModelNode {
    private final String id;
    private Matrix4 localTransform = Matrix4.IDENTITY;
    private final Array<ModelNodePart> parts = new Array<ModelNodePart>();
    private final Array<ModelNode> children = new Array<ModelNode>();
    private final ArrayView<ModelNodePart> readOnlyParts = parts.view();
    private final ArrayView<ModelNode> readOnlyChildren = children.view();

    /**
     * Creates a model node.
     *
     * @param id the identifier
     */
    public ModelNode(String id) {
        this.id = id != null ? id : "";
    }

    /**
     * Sets the local transform and returns this model node.
     *
     * @param localTransform the local transform
     * @return this model node for chaining
     */
    public ModelNode localTransform(Matrix4 localTransform) {
        this.localTransform = localTransform != null ? localTransform : Matrix4.IDENTITY;
        return this;
    }

    /**
     * Adds the part.
     *
     * @param part the part
     * @return this model node for chaining
     */
    public ModelNode addPart(ModelNodePart part) {
        if (part != null) {
            parts.add(part);
        }
        return this;
    }

    /**
     * Adds the child.
     *
     * @param child the child
     * @return this model node for chaining
     */
    public ModelNode addChild(ModelNode child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * Returns the local transform.
     *
     * @return the local transform
     */
    public Matrix4 localTransform() {
        return localTransform;
    }

    /**
     * Returns the parts.
     *
     * @return the parts
     */
    public ArrayView<ModelNodePart> parts() {
        return readOnlyParts;
    }

    /**
     * Returns the children.
     *
     * @return the children
     */
    public ArrayView<ModelNode> children() {
        return readOnlyChildren;
    }
}
