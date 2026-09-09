package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Vector3;

/**
 * Borrows a shared model and owns its node pose, materials and skinning palettes. GPU-skinned
 * parts with retained source influences get conservative bounds updated with each pose.
 * Instances do not own model meshes; CPU animation binds separately owned copies explicitly.
 * Apply pose/material/bounds changes before queuing this instance for drawing.
 *
 * @author xpenatan
 */
public final class DefaultModelInstance implements ModelInstance {
    private final Model model;
    private final Matrix4 transform = new Matrix4();
    private final Array<InstancePart> instanceParts = new Array<InstancePart>();
    private final Array<InstanceNode> rootNodes = new Array<InstanceNode>();
    private final Array<InstanceNode> instanceNodes = new Array<InstanceNode>();
    private final Array<SkinningPalette> skinningPalettes = new Array<SkinningPalette>();
    private final ObjectMap<String, InstanceNode> nodesById = new ObjectMap<String, InstanceNode>();
    private final float[] appliedTransformValues = new float[Matrix4.VALUE_COUNT];
    private final float[] currentTransformValues = new float[Matrix4.VALUE_COUNT];
    private Object cpuSkinningOwner;

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
     * Overrides one node part's material for this model instance.
     *
     * <p>The material is borrowed and is not disposed by this instance. Change
     * instance materials only when renderables from this instance are not
     * already queued in an active batch.</p>
     *
     * @param nodeId the node identifier
     * @param partIndex the zero-based node part index
     * @param material the replacement material
     * @return this default model instance for chaining
     */
    public DefaultModelInstance nodeMaterial(String nodeId, int partIndex,
            Material material) {
        if (material == null) {
            throw new FdxException("Model node material cannot be null");
        }
        part(nodeId, partIndex).renderable.material(material);
        return this;
    }

    /**
     * Returns one instance-local node part material.
     *
     * @param nodeId the node identifier
     * @param partIndex the zero-based node part index
     * @return the current instance-local material
     */
    public Material nodeMaterial(String nodeId, int partIndex) {
        return part(nodeId, partIndex).renderable.material();
    }

    /** Overrides borrowed conservative bounds for one node part, after deformation and before
     * its renderable world transform. Null disables frustum rejection. Keep custom bounds current
     * through all scene/shadow submissions; custom shader displacement may need wider bounds.
     * Does not change transparent sorting bounds. */
    public DefaultModelInstance nodeCullingBounds(String nodeId,int partIndex,BoundingBox bounds) {
        InstancePart part=part(nodeId,partIndex);
        part.cullingOverride=true; part.renderable.cullingBounds(bounds); return this;
    }

    /** Returns the borrowed current culling bounds, or null for always visible. */
    public BoundingBox nodeCullingBounds(String nodeId,int partIndex) {
        return part(nodeId,partIndex).renderable.cullingBounds();
    }

    /** Restores automatic current-pose bounds, static bounds, or the always-visible fallback. */
    public DefaultModelInstance resetNodeCullingBounds(String nodeId,int partIndex) {
        InstancePart part=part(nodeId,partIndex); part.cullingOverride=false;
        boolean deformed=part.source.meshPart().mesh().hasPbrSkinning() || cpuSkinningOwner != null;
        part.renderable.cullingBounds(part.animatedBounds != null && deformed ? part.animatedBounds
                : part.source.skin() == null ? part.renderable.bounds() : null);
        return this;
    }

    /**
     * Restores one node part's material from the shared model.
     *
     * @param nodeId the node identifier
     * @param partIndex the zero-based node part index
     * @return this default model instance for chaining
     */
    public DefaultModelInstance resetNodeMaterial(String nodeId,
            int partIndex) {
        InstancePart part = part(nodeId, partIndex);
        part.renderable.material(part.source.material());
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
        synchronizeMutableTransform();
        return out.set(node(nodeId).worldTransform);
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
     * Restores every instance-local material from the shared model.
     *
     * @return this default model instance for chaining
     */
    public DefaultModelInstance resetNodeMaterials() {
        for (int i = 0; i < instanceNodes.size(); i++) {
            InstanceNode node = instanceNodes.get(i);
            for (int j = 0; j < node.parts.size(); j++) {
                InstancePart part = node.parts.get(j);
                part.renderable.material(part.source.material());
            }
        }
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
        instanceParts.clear();
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
        InstanceNode node = new InstanceNode(source, instanceNodes.size());
        instanceNodes.add(node);
        String id = trimNodeId(source.id());
        if (id != null && !nodesById.containsKey(id)) {
            nodesById.put(id, node);
        }
        ArrayView<ModelNodePart> parts = source.parts();
        for (int i = 0; i < parts.size(); i++) {
            ModelNodePart part = parts.get(i);
            SkinningPalette palette = part.skin() != null ? skinningPalette(part.skin()) : null;
            BoundingBox animatedBounds=palette != null && part.skinBounds() != null
                    ? new BoundingBox(new Vector3(),new Vector3()) : null;
            boolean gpuSkinning=part.meshPart().mesh().hasPbrSkinning();
            Renderable3D renderable = new Renderable3D(part.meshPart(),
                    // Palettes already include the model-space joint hierarchy. Applying the mesh
                    // node again double-transforms skins parented beneath translated/scaled nodes.
                    part.material(), palette == null ? node.worldTransform : transform,
                    animatedBounds != null && gpuSkinning ? animatedBounds : part.meshPart().mesh().bounds(), palette);
            if (animatedBounds != null && gpuSkinning) renderable.cullingBounds(animatedBounds);
            InstancePart instancePart=new InstancePart(part, renderable, animatedBounds);
            node.parts.add(instancePart);
            instanceParts.add(instancePart);
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
        for (int i=0;i<instanceNodes.size();i++) {
            Array<InstancePart> parts=instanceNodes.get(i).parts;
            for (int j=0;j<parts.size();j++) {
                InstancePart part=parts.get(j);
                if (part.animatedBounds != null) part.source.skinBounds().update(part.renderable.skinningPalette(),part.animatedBounds);
            }
        }
        transform.copyValues(appliedTransformValues, 0);
    }

    private void synchronizeMutableTransform() {
        transform.copyValues(currentTransformValues, 0);
        for (int i = 0; i < Matrix4.VALUE_COUNT; i++) {
            if (Float.floatToRawIntBits(currentTransformValues[i])
                    != Float.floatToRawIntBits(appliedTransformValues[i])) {
                updateWorldTransforms();
                return;
            }
        }
    }

    int animationNodeCount() { return instanceNodes.size(); }
    int animationNodeIndex(String id) { return node(id).index; }
    Matrix4 animationDefault(int index) { return instanceNodes.get(index).source.localTransform(); }
    Matrix4 animationLocal(int index) { return instanceNodes.get(index).localTransform; }
    void applyAnimationTransforms() { updateWorldTransforms(); }

    void claimCpuSkinning(Object owner) {
        if (cpuSkinningOwner != null) throw new FdxException("This instance already has a CPU skinning animator");
        cpuSkinningOwner=owner;
    }
    void releaseCpuSkinning(Object owner) { if (cpuSkinningOwner == owner) cpuSkinningOwner=null; }
    int skinningPartCount() { return instanceParts.size(); }
    ModelNodePart skinningSourcePart(int index) { return instanceParts.get(index).source; }
    Renderable3D skinningRenderable(int index) { return instanceParts.get(index).renderable; }
    BoundingBox skinningBounds(int index) { return instanceParts.get(index).animatedBounds; }
    boolean skinningCullingOverride(int index) { return instanceParts.get(index).cullingOverride; }
    void skinningRenderable(int index,Renderable3D renderable) { instanceParts.get(index).renderable=renderable; }

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

    private InstancePart part(String nodeId, int partIndex) {
        InstanceNode node = node(nodeId);
        if (partIndex < 0 || partIndex >= node.parts.size()) {
            throw new FdxException("Model node part index is out of range: "
                    + node.source.id() + "[" + partIndex + "]");
        }
        return node.parts.get(partIndex);
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
     * Returns the mutable transform. Direct mutations are synchronized before
     * world transforms are queried or renderables are collected.
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
        synchronizeMutableTransform();
        for (int i = 0; i < instanceParts.size(); i++) {
            queue.add(instanceParts.get(i).renderable);
        }
    }

    private static final class InstanceNode {
        private final int index;
        private final ModelNode source;
        private final Matrix4 localTransform;
        private final Matrix4 modelTransform = new Matrix4();
        private final Matrix4 worldTransform = new Matrix4();
        private final Array<InstancePart> parts = new Array<InstancePart>();
        private final Array<InstanceNode> children = new Array<InstanceNode>();

        InstanceNode(ModelNode source, int index) {
            this.index = index;
            this.source = source;
            localTransform = new Matrix4(source.localTransform());
        }
    }

    private static final class InstancePart {
        private final ModelNodePart source;
        private Renderable3D renderable;
        private final BoundingBox animatedBounds;
        private boolean cullingOverride;

        InstancePart(ModelNodePart source, Renderable3D renderable, BoundingBox animatedBounds) {
            this.source = source;
            this.renderable = renderable;
            this.animatedBounds = animatedBounds;
        }
    }
}
