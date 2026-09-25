package io.github.libfdx.graphics.g3d.lod;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.ObjectSet;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelInstance;
import io.github.libfdx.graphics.g3d.ModelNode;
import io.github.libfdx.math.Matrix4;

/** Default safety guard; custom pose/material mappings opt into an explicit synchronizer. */
final class LodStaticState {
    private final DefaultModelInstance instance;
    private final Model model;
    private final ModelNode[] roots;
    private final Array<LodNodeState> nodes = new Array<>();
    private final Array<Material> materialIds = new Array<>();
    private final Array<LodMaterialState> materials = new Array<>();
    private final Matrix4 scratch = new Matrix4();
    private final float[] values = new float[16];
    private boolean supported = true;

    LodStaticState(ModelInstance source) {
        instance = source instanceof DefaultModelInstance value ? value : null;
        model = source.model();
        roots = model == null ? new ModelNode[0] : new ModelNode[model.nodes().size()];
        if (model != null) {
            for (int i = 0; i < roots.length; i++) roots[i] = model.nodes().get(i);
            collect(model.nodes(), new ObjectSet<>());
        }
    }

    private void collect(ArrayView<ModelNode> source, ObjectSet<String> ids) {
        for (int i = 0; i < source.size(); i++) {
            ModelNode node = source.get(i);
            String id = node.id().trim();
            if (id.isEmpty() || !ids.add(id)) supported = false;
            nodes.add(new LodNodeState(node));
            for (int j = 0; j < node.parts().size(); j++) {
                if (node.parts().get(j).morphTargetCount()>0) supported=false;
                Material material = node.parts().get(j).material();
                if (!materialIds.contains(material, true)) {
                    materialIds.add(material); materials.add(new LodMaterialState(material));
                }
            }
            collect(node.children(), ids);
        }
    }

    boolean unchanged() {
        return unchanged(true,null);
    }

    boolean unchanged(boolean checkMaterials, java.util.function.BiPredicate<Material,Material> replacement) {
        if (!supported || instance == null || model == null || instance.model() != model
                || model.animations().notEmpty() || model.skins().notEmpty()
                || model.nodes().size() != roots.length) return false;
        for (int i = 0; i < roots.length; i++) if (model.nodes().get(i) != roots[i]) return false;
        for (int i = 0; i < nodes.size(); i++) if (!nodes.get(i).unchanged(instance, scratch, values,replacement)) return false;
        for (int i = 0; i < materials.size(); i++) {
            Material material = materialIds.get(i);
            if (material.shaderProvider() != null || material.shaderBinding() != null
                    || checkMaterials && !materials.get(i).unchanged()) return false;
        }
        return true;
    }
}
