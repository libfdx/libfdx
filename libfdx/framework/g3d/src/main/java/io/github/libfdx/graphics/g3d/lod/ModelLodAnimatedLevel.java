package io.github.libfdx.graphics.g3d.lod;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g3d.*;

/** Per-instance animation owner for a separately loaded LOD asset. Borrows both models/instances;
 * owns only candidate CPU morph buffers. Requires identical ordered node/primitive/material slots,
 * rest transforms, morph defaults and skin mappings. Geometry may differ. A mismatch fails at
 * construction before modifying either instance. One controller drives the base; synchronize()
 * copies its pose/materials and deforms the candidate, without advancing time/events. Construct,
 * synchronize and dispose on the graphics owner, outside queued draws. Shared model metadata must
 * remain immutable for this owner's lifetime. Dispose before asset leases. */
public final class ModelLodAnimatedLevel implements Disposable {
    private final DefaultModelInstance base, candidate;
    private final CpuMorphModelAnimator morph;
    private boolean disposed;

    public ModelLodAnimatedLevel(GraphicsContext graphics, DefaultModelInstance base, DefaultModelInstance candidate) {
        if(base==null || candidate==null || base==candidate || base.model().isDisposed() || candidate.model().isDisposed()
                || base.model().nodes().size()!=candidate.model().nodes().size())
            throw new IllegalArgumentException("Animated LOD requires live compatible instances");
        this.base=base;this.candidate=candidate;
        for(int n=0;n<base.model().nodes().size();n++) validate(base.model().nodes().get(n),candidate.model().nodes().get(n));
        morph=new CpuMorphModelAnimator(graphics,candidate);
    }

    /** Returns false for disposed source geometry or changed hierarchy. No playback state is owned. */
    public boolean synchronize() {
        if(disposed || base.model().isDisposed() || candidate.model().isDisposed() || !candidate.copyPoseFrom(base)) return false;
        for(int n=0;n<base.model().nodes().size();n++) {
            if(!materials(base.model().nodes().get(n),candidate.model().nodes().get(n))) return false;
        }
        candidate.transform().set(base.transform());
        morph.update();
        return true;
    }

    private static void validate(ModelNode a, ModelNode b) {
        if(!a.id().equals(b.id()) || a.children().size()!=b.children().size() || a.parts().size()!=b.parts().size()
                || !java.util.Arrays.equals(a.localTransform().values(),b.localTransform().values())
                || !java.util.Arrays.equals(a.morphWeights(),b.morphWeights())) mismatch();
        for(int p=0;p<a.parts().size();p++) {
            ModelNodePart left=a.parts().get(p),right=b.parts().get(p);
            if(left.morphTargetCount()!=right.morphTargetCount() || !left.material().id().equals(right.material().id())
                    || left.meshPart().primitiveTopology()!=right.meshPart().primitiveTopology()) mismatch();
            Skin l=left.skin(),r=right.skin();
            if((l==null)!=(r==null)) mismatch();
            if(l!=null) {
                if(l.skeleton().bones().size()!=r.skeleton().bones().size()) mismatch();
                for(int j=0;j<l.skeleton().bones().size();j++) {
                    Bone x=l.skeleton().bones().get(j),y=r.skeleton().bones().get(j);
                    if(!x.id().equals(y.id()) || x.parentIndex()!=y.parentIndex()
                            || !java.util.Arrays.equals(x.inverseBindTransform().values(),y.inverseBindTransform().values())) mismatch();
                }
            }
        }
        for(int c=0;c<a.children().size();c++) validate(a.children().get(c),b.children().get(c));
    }
    private boolean materials(ModelNode a,ModelNode b) {
        if(a.parts().size()!=b.parts().size() || a.children().size()!=b.children().size()) return false;
        for(int p=0;p<a.parts().size();p++) {
            if(a.parts().get(p).meshPart().mesh().isDisposed() || b.parts().get(p).meshPart().mesh().isDisposed()) return false;
            candidate.nodeMaterial(b.id(),p,base.nodeMaterial(a.id(),p));
        }
        for(int c=0;c<a.children().size();c++) if(!materials(a.children().get(c),b.children().get(c))) return false;
        return true;
    }
    private static void mismatch() { throw new IllegalArgumentException("Animated LOD hierarchy, rest pose, material slots or skin mapping differs from base"); }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() { if(disposed)return;disposed=true;morph.dispose(); }
}
