package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;

/**
 * Provides the default implementation of a model instance.
 *
 * @author xpenatan
 */
public final class DefaultModelInstance implements ModelInstance {
    private final Model model;
    private final Matrix4 transform = new Matrix4();
    private final Array<Renderable3D> renderables = new Array<Renderable3D>();
    private final Array<InstanceNode> rootNodes = new Array<InstanceNode>();
    private final Array<InstanceNode> instanceNodes = new Array<InstanceNode>();
    private final Array<SkinningPalette> skinningPalettes = new Array<SkinningPalette>();
    private final ObjectMap<String, InstanceNode> nodesById = new ObjectMap<String, InstanceNode>();

    /**
     * Creates a default model instance.
     *
     * @param model the model
     */
    public DefaultModelInstance(Model model) {
        if (model == null) {
            throw new FdxException("ModelInstance model cannot be null");
        }
        this.model = model;
        buildInstanceNodes();
    }

    /**
     * Sets the transform and returns this default model instance.
     *
     * @param transform the transform
     * @return this default model instance for chaining
     */
    public DefaultModelInstance transform(Matrix4 transform) {
        this.transform.set(transform != null ? transform : Matrix4.IDENTITY);
        updateWorldTransforms();
        return this;
    }

    /**
     * Sets an instance-local node transform and returns this instance.
     *
     * @param nodeId the node identifier
     * @param localTransform the local transform
     * @return this default model instance for chaining
     */
    public DefaultModelInstance nodeTransform(String nodeId, Matrix4 localTransform) {
        InstanceNode node = node(nodeId);
        node.localTransform.set(localTransform != null ? localTransform : Matrix4.IDENTITY);
        updateWorldTransforms();
        return this;
    }

    /**
     * Copies an instance-local node transform.
     *
     * @param nodeId the node identifier
     * @param out the output transform
     * @return the output transform
     */
    public Matrix4 copyNodeTransform(String nodeId, Matrix4 out) {
        if (out == null) {
            throw new FdxException("Model node transform output cannot be null");
        }
        return out.set(node(nodeId).localTransform);
    }

    /**
     * Copies an instance-local model-space node transform.
     *
     * @param nodeId the node identifier
     * @param out the output transform
     * @return the output transform
     */
    public Matrix4 copyNodeModelTransform(String nodeId, Matrix4 out) {
        if (out == null) {
            throw new FdxException("Model node transform output cannot be null");
        }
        return out.set(node(nodeId).modelTransform);
    }

    /**
     * Copies an instance-local world-space node transform.
     *
     * @param nodeId the node identifier
     * @param out the output transform
     * @return the output transform
     */
    public Matrix4 copyNodeWorldTransform(String nodeId, Matrix4 out) {
        if (out == null) {
            throw new FdxException("Model node transform output cannot be null");
        }
        return out.set(node(nodeId).worldTransform);
    }

    /**
     * Returns an instance-local node transform copy.
     *
     * @param nodeId the node identifier
     * @return the local node transform
     */
    public Matrix4 nodeTransform(String nodeId) {
        return copyNodeTransform(nodeId, new Matrix4());
    }

    /**
     * Returns an instance-local model-space node transform copy.
     *
     * @param nodeId the node identifier
     * @return the model-space node transform
     */
    public Matrix4 nodeModelTransform(String nodeId) {
        return copyNodeModelTransform(nodeId, new Matrix4());
    }

    /**
     * Returns an instance-local node world transform copy.
     *
     * @param nodeId the node identifier
     * @return the world node transform
     */
    public Matrix4 nodeWorldTransform(String nodeId) {
        return copyNodeWorldTransform(nodeId, new Matrix4());
    }

    /**
     * Resets instance-local node transforms from the shared model.
     *
     * @return this default model instance for chaining
     */
    public DefaultModelInstance resetNodeTransforms() {
        for (int i = 0; i < instanceNodes.size(); i++) {
            InstanceNode node = instanceNodes.get(i);
            node.localTransform.set(node.source.localTransform());
        }
        updateWorldTransforms();
        return this;
    }

    /**
     * Returns whether this instance has a node id.
     *
     * @param nodeId the node identifier
     * @return true if the node exists; false otherwise
     */
    public boolean hasNode(String nodeId) {
        String key = trimNodeId(nodeId);
        return key != null && nodesById.containsKey(key);
    }

    private void buildInstanceNodes() {
        renderables.clear();
        rootNodes.clear();
        instanceNodes.clear();
        skinningPalettes.clear();
        nodesById.clear();
        ArrayView<ModelNode> nodes = model.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            rootNodes.add(copyNode(nodes.get(i)));
        }
        updateWorldTransforms();
    }

    private InstanceNode copyNode(ModelNode source) {
        InstanceNode node = new InstanceNode(source);
        instanceNodes.add(node);
        String id = trimNodeId(source.id());
        if (id != null && !nodesById.containsKey(id)) {
            nodesById.put(id, node);
        }
        ArrayView<ModelNodePart> parts = source.parts();
        for (int i = 0; i < parts.size(); i++) {
            ModelNodePart part = parts.get(i);
            SkinningPalette palette = part.skin() != null ? skinningPalette(part.skin()) : null;
            renderables.add(new Renderable3D(part.meshPart(), part.material(), node.worldTransform,
                    part.meshPart().mesh().bounds(), palette));
        }
        ArrayView<ModelNode> children = source.children();
        for (int i = 0; i < children.size(); i++) {
            node.children.add(copyNode(children.get(i)));
        }
        return node;
    }

    private void updateWorldTransforms() {
        for (int i = 0; i < rootNodes.size(); i++) {
            updateModelTransform(rootNodes.get(i), Matrix4.IDENTITY);
        }
        for (int i = 0; i < skinningPalettes.size(); i++) {
            skinningPalettes.get(i).update(this);
        }
    }

    private void updateModelTransform(InstanceNode node, Matrix4 parentModelTransform) {
        node.modelTransform.setToMul(parentModelTransform, node.localTransform);
        node.worldTransform.setToMul(transform, node.modelTransform);
        for (int i = 0; i < node.children.size(); i++) {
            updateModelTransform(node.children.get(i), node.modelTransform);
        }
    }

    private InstanceNode node(String nodeId) {
        String key = trimNodeId(nodeId);
        if (key == null) {
            throw new FdxException("Model node id cannot be empty");
        }
        InstanceNode node = nodesById.get(key);
        if (node == null) {
            throw new FdxException("Model node not found: " + key);
        }
        return node;
    }

    private static String trimNodeId(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        String trimmed = nodeId.trim();
        return trimmed.length() > 0 ? trimmed : null;
    }

    private SkinningPalette skinningPalette(Skin skin) {
        for (int i = 0; i < skinningPalettes.size(); i++) {
            SkinningPalette palette = skinningPalettes.get(i);
            if (palette.skin() == skin) {
                return palette;
            }
        }
        SkinningPalette palette = new SkinningPalette(skin);
        skinningPalettes.add(palette);
        return palette;
    }

    /**
     * Returns the model.
     *
     * @return the model
     */
    @Override
    public Model model() {
        return model;
    }

    /**
     * Returns the transform.
     *
     * @return the transform
     */
    @Override
    public Matrix4 transform() {
        return transform;
    }

    /**
     * Runs the collect renderables step.
     *
     * @param queue the queue
     */
    @Override
    public void collectRenderables(RenderQueue3D queue) {
        if (queue == null) {
            throw new FdxException("RenderQueue3D cannot be null");
        }
        for (int i = 0; i < renderables.size(); i++) {
            queue.add(renderables.get(i));
        }
    }

    private static final class InstanceNode {
        private final ModelNode source;
        private final Matrix4 localTransform;
        private final Matrix4 modelTransform = new Matrix4();
        private final Matrix4 worldTransform = new Matrix4();
        private final Array<InstanceNode> children = new Array<InstanceNode>();

        InstanceNode(ModelNode source) {
            this.source = source;
            localTransform = new Matrix4(source.localTransform());
        }
    }
}
