package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Matrix4;

import io.github.libfdx.core.FdxException;

import java.util.ArrayList;
import java.util.List;

public final class DefaultModelInstance implements ModelInstance {
    private final Model model;
    private Matrix4 transform = Matrix4.IDENTITY;
    private final ArrayList<Renderable3D> renderables = new ArrayList<Renderable3D>();

    public DefaultModelInstance(Model model) {
        if (model == null) {
            throw new FdxException("ModelInstance model cannot be null");
        }
        this.model = model;
        rebuildRenderables();
    }

    public DefaultModelInstance transform(Matrix4 transform) {
        this.transform = transform != null ? transform : Matrix4.IDENTITY;
        rebuildRenderables();
        return this;
    }

    private void rebuildRenderables() {
        renderables.clear();
        List<ModelNode> nodes = model.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            collectNode(nodes.get(i), transform);
        }
    }

    private void collectNode(ModelNode node, Matrix4 parentTransform) {
        Matrix4 worldTransform = parentTransform.multiply(node.localTransform());
        List<ModelNodePart> parts = node.parts();
        for (int i = 0; i < parts.size(); i++) {
            ModelNodePart part = parts.get(i);
            renderables.add(new Renderable3D(part.meshPart(), part.material(), worldTransform,
                    part.meshPart().mesh().bounds()));
        }
        List<ModelNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            collectNode(children.get(i), worldTransform);
        }
    }

    @Override
    public Model model() {
        return model;
    }

    @Override
    public Matrix4 transform() {
        return transform;
    }

    @Override
    public void collectRenderables(RenderQueue3D queue) {
        if (queue == null) {
            throw new FdxException("RenderQueue3D cannot be null");
        }
        for (int i = 0; i < renderables.size(); i++) {
            queue.add(renderables.get(i));
        }
    }
}
