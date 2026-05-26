package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Matrix4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModelNode {
    private final String id;
    private Matrix4 localTransform = Matrix4.IDENTITY;
    private final ArrayList<ModelNodePart> parts = new ArrayList<ModelNodePart>();
    private final ArrayList<ModelNode> children = new ArrayList<ModelNode>();

    public ModelNode(String id) {
        this.id = id != null ? id : "";
    }

    public ModelNode localTransform(Matrix4 localTransform) {
        this.localTransform = localTransform != null ? localTransform : Matrix4.IDENTITY;
        return this;
    }

    public ModelNode addPart(ModelNodePart part) {
        if (part != null) {
            parts.add(part);
        }
        return this;
    }

    public ModelNode addChild(ModelNode child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    public String id() {
        return id;
    }

    public Matrix4 localTransform() {
        return localTransform;
    }

    public List<ModelNodePart> parts() {
        return Collections.unmodifiableList(parts);
    }

    public List<ModelNode> children() {
        return Collections.unmodifiableList(children);
    }
}
