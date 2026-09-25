package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.g3d.lod.ModelLodSynchronizer;

/** Validates generated hierarchy/skin mappings and copies instance pose/material state, not playback. */
final class LodGeneratedPoseSynchronizer implements ModelLodSynchronizer {
    private final CpuMorphModelAnimator[] morphs;
    private final float[] a=new float[16],b=new float[16];
    LodGeneratedPoseSynchronizer(CpuMorphModelAnimator[] morphs) { this.morphs=morphs; }
    @Override public boolean synchronize(ModelInstance source,ModelInstance target,int level) {
        if (!(source instanceof DefaultModelInstance base) || !(target instanceof DefaultModelInstance candidate)
                || source.model().isDisposed() || target.model().isDisposed()
                || source.model().nodes().size()!=target.model().nodes().size()) return false;
        for (int n=0;n<source.model().nodes().size();n++)
            if (!compatible(source.model().nodes().get(n),target.model().nodes().get(n))) return false;
        if (!candidate.copyPoseFrom(base)) return false;
        for (int n=0;n<source.model().nodes().size();n++) materials(base,candidate,source.model().nodes().get(n),target.model().nodes().get(n));
        if (morphs!=null) morphs[level-1].update();
        return true;
    }
    private boolean compatible(ModelNode source,ModelNode target) {
        if (!source.id().equals(target.id()) || source.children().size()!=target.children().size()) return false;
        for (int i=0;i<target.parts().size();i++) {
            ModelNodePart part=target.parts().get(i),original=original(source,part);
            if (original==null || original.morphTargetCount()!=part.morphTargetCount()
                    || original.meshPart().mesh().isDisposed() || part.meshPart().mesh().isDisposed() || !skin(original.skin(),part.skin())) return false;
        }
        for (int n=0;n<source.children().size();n++) if (!compatible(source.children().get(n),target.children().get(n))) return false;
        return true;
    }
    private ModelNodePart original(ModelNode node,ModelNodePart target) {
        for (int i=0;i<node.parts().size();i++) {
            ModelNodePart source=node.parts().get(i);
            if (source.meshPart().id().equals(target.meshPart().id()) && source.material()==target.material()) return source;
        }
        return null;
    }
    private boolean skin(Skin source,Skin target) {
        if (source==null || target==null) return source==target;
        if (source.skeleton().bones().size()!=target.skeleton().bones().size()) return false;
        for (int i=0;i<source.skeleton().bones().size();i++) {
            Bone left=source.skeleton().bones().get(i),right=target.skeleton().bones().get(i);
            if (!left.id().equals(right.id()) || left.parentIndex()!=right.parentIndex()) return false;
            left.inverseBindTransform().copyValues(a,0);right.inverseBindTransform().copyValues(b,0);
            for (int k=0;k<16;k++) if (Float.floatToIntBits(a[k])!=Float.floatToIntBits(b[k])) return false;
        }
        return true;
    }
    private void materials(DefaultModelInstance base,DefaultModelInstance candidate,ModelNode source,ModelNode target) {
        for (int t=0;t<target.parts().size();t++) {
            ModelNodePart original=original(source,target.parts().get(t));
            for (int s=0;s<source.parts().size();s++) if (source.parts().get(s)==original) {
                candidate.nodeMaterial(target.id(),t,base.nodeMaterial(source.id(),s));break;
            }
        }
        for (int n=0;n<source.children().size();n++) materials(base,candidate,source.children().get(n),target.children().get(n));
    }
}
