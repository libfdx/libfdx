package io.github.libfdx.graphics.g3d.lod;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.ModelNode;
import io.github.libfdx.graphics.g3d.ModelNodePart;
import io.github.libfdx.math.Matrix4;

/** Snapshot of a node's identity, topology and default pose; root motion remains unrestricted. */
final class LodNodeState {
    private final ModelNode node;
    private final String id;
    private final ModelNode[] children;
    private final ModelNodePart[] parts;
    private final float[] local = new float[16];
    private boolean supported = true;

    LodNodeState(ModelNode node) {
        this.node = node;
        id = node.id().trim();
        children = new ModelNode[node.children().size()];
        parts = new ModelNodePart[node.parts().size()];
        node.localTransform().copyValues(local, 0);
        for (int i = 0; i < children.length; i++) children[i] = node.children().get(i);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = node.parts().get(i);
            if (parts[i].skin() != null || parts[i].meshPart().mesh().hasPbrSkinning()) supported = false;
        }
    }

    boolean unchanged(DefaultModelInstance instance, Matrix4 scratch, float[] values,
            java.util.function.BiPredicate<io.github.libfdx.graphics.g3d.Material,io.github.libfdx.graphics.g3d.Material> replacement) {
        if (!supported || id.isEmpty() || !instance.hasNode(id)
                || node.children().size() != children.length || node.parts().size() != parts.length) return false;
        for (int i = 0; i < children.length; i++) if (node.children().get(i) != children[i]) return false;
        node.localTransform().copyValues(values, 0);
        if (!same(values)) return false;
        instance.copyNodeTransform(id, scratch).copyValues(values, 0);
        if (!same(values)) return false;
        try {
            for (int i = 0; i < parts.length; i++) {
                if (node.parts().get(i) != parts[i]) return false;
                var actual = instance.nodeMaterial(id,i);
                if (actual != parts[i].material() && (replacement == null || !replacement.test(parts[i].material(),actual))) return false;
            }
        } catch (FdxException incompatible) {
            // The model may have gained parts after this instance was constructed.
            // Its fixed instance topology cannot represent this snapshot.
            supported = false;
            return false;
        }
        return true;
    }

    private boolean same(float[] values) {
        for (int i = 0; i < 16; i++) if (!Float.isFinite(values[i]) || values[i] != local[i]) return false;
        return true;
    }
}
