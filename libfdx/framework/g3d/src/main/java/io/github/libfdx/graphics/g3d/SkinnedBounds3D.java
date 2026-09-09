package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.BoundingBox;

/** Prepared conservative bounds for normalized, nonnegative four-weight linear skinning.
 * Copies a bind-position box for each influencing joint; retains no source arrays or GPU resources.
 * Preparation scans vertices once. Updating transforms joint boxes, without per-vertex work or
 * allocation. The immutable preparation can be shared; each instance owns its palette/output bounds.
 * Reprepare after source geometry/influences change. Custom vertex displacement needs wider bounds. */
public final class SkinnedBounds3D {
    private final int jointCount;
    private final float[] boxes;
    private final int[] used;

    public SkinnedBounds3D(float[] positions,int[] joints,float[] weights,int jointCount) {
        if (jointCount < 1 || jointCount > 65_536 || positions == null || positions.length == 0 || positions.length%3 != 0
                || joints == null || weights == null || joints.length != (long)positions.length/3*4 || weights.length != joints.length)
            throw new FdxException("Skinned bounds need positions, four influences per vertex and a bounded joint count");
        this.jointCount=jointCount;
        boxes=new float[(jointCount+1)*6]; boolean[] present=new boolean[jointCount+1];
        for (int i=0;i<boxes.length;i+=6) {
            boxes[i]=boxes[i+1]=boxes[i+2]=Float.POSITIVE_INFINITY;
            boxes[i+3]=boxes[i+4]=boxes[i+5]=Float.NEGATIVE_INFINITY;
        }
        for (int v=0;v<positions.length/3;v++) {
            float x=positions[v*3],y=positions[v*3+1],z=positions[v*3+2];
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) throw new FdxException("Skinned positions must be finite");
            double sum=0;
            for (int i=0;i<4;i++) {
                float weight=weights[v*4+i];
                if (!Float.isFinite(weight) || weight < 0) throw new FdxException("Skin weights must be finite and nonnegative");
                sum+=weight;
                if (weight == 0) continue;
                int joint=joints[v*4+i];
                if (joint < 0 || joint >= jointCount) throw new FdxException("Skin joint index outside palette");
                include(joint,x,y,z); present[joint]=true;
            }
            if (sum == 0) { include(jointCount,x,y,z); present[jointCount]=true; }
        }
        int count=0; for (boolean value : present) if (value) count++;
        used=new int[count]; int cursor=0;
        for (int i=0;i<present.length;i++) if (present[i]) used[cursor++]=i;
    }

    private void include(int joint,float x,float y,float z) {
        int i=joint*6;
        boxes[i]=Math.min(boxes[i],x); boxes[i+1]=Math.min(boxes[i+1],y); boxes[i+2]=Math.min(boxes[i+2],z);
        boxes[i+3]=Math.max(boxes[i+3],x); boxes[i+4]=Math.max(boxes[i+4],y); boxes[i+5]=Math.max(boxes[i+5],z);
    }

    /** Writes conservative model-space bounds before the instance transform. Output must contain
     * independently mutable min/max vectors. Rejects nonfinite/nonaffine palette data or overflow.
     * Zero-weight vertices use their untransformed bind positions. */
    public BoundingBox update(SkinningPalette palette,BoundingBox out) {
        if (palette == null || palette.size() != jointCount || out == null)
            throw new FdxException("Skinned bounds palette size/output mismatch");
        float[] matrices=palette.valuesUnsafe();
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX;
        double maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for (int joint : used) {
            int b=joint*6;
            double cx=((double)boxes[b]+boxes[b+3])*.5,cy=((double)boxes[b+1]+boxes[b+4])*.5,cz=((double)boxes[b+2]+boxes[b+5])*.5;
            double hx=((double)boxes[b+3]-boxes[b])*.5,hy=((double)boxes[b+4]-boxes[b+1])*.5,hz=((double)boxes[b+5]-boxes[b+2])*.5;
            double x=cx,y=cy,z=cz,ex=hx,ey=hy,ez=hz;
            double magnitudeX=Math.abs(cx),magnitudeY=Math.abs(cy),magnitudeZ=Math.abs(cz);
            if (joint != jointCount) {
                int m=joint*16;
                for (int i=0;i<16;i++) if (!Float.isFinite(matrices[m+i])) throw new FdxException("Skin palette must be finite");
                if (matrices[m+3] != 0 || matrices[m+7] != 0 || matrices[m+11] != 0 || matrices[m+15] != 1)
                    throw new FdxException("Skin palette must be affine");
                x=matrices[m]*cx+matrices[m+4]*cy+matrices[m+8]*cz+matrices[m+12];
                y=matrices[m+1]*cx+matrices[m+5]*cy+matrices[m+9]*cz+matrices[m+13];
                z=matrices[m+2]*cx+matrices[m+6]*cy+matrices[m+10]*cz+matrices[m+14];
                magnitudeX=Math.abs(matrices[m]*cx)+Math.abs(matrices[m+4]*cy)+Math.abs(matrices[m+8]*cz)+Math.abs(matrices[m+12]);
                magnitudeY=Math.abs(matrices[m+1]*cx)+Math.abs(matrices[m+5]*cy)+Math.abs(matrices[m+9]*cz)+Math.abs(matrices[m+13]);
                magnitudeZ=Math.abs(matrices[m+2]*cx)+Math.abs(matrices[m+6]*cy)+Math.abs(matrices[m+10]*cz)+Math.abs(matrices[m+14]);
                ex=Math.abs(matrices[m])*hx+Math.abs(matrices[m+4])*hy+Math.abs(matrices[m+8])*hz;
                ey=Math.abs(matrices[m+1])*hx+Math.abs(matrices[m+5])*hy+Math.abs(matrices[m+9])*hz;
                ez=Math.abs(matrices[m+2])*hx+Math.abs(matrices[m+6])*hy+Math.abs(matrices[m+10])*hz;
            }
            // Covers float matrix/weight evaluation at the renderer boundary, including cancellation.
            double marginX=Math.max(1e-5,(magnitudeX+ex)*2e-6),marginY=Math.max(1e-5,(magnitudeY+ey)*2e-6),marginZ=Math.max(1e-5,(magnitudeZ+ez)*2e-6);
            minX=Math.min(minX,x-ex-marginX); maxX=Math.max(maxX,x+ex+marginX);
            minY=Math.min(minY,y-ey-marginY); maxY=Math.max(maxY,y+ey+marginY);
            minZ=Math.min(minZ,z-ez-marginZ); maxZ=Math.max(maxZ,z+ez+marginZ);
        }
        if (!Float.isFinite((float)minX) || !Float.isFinite((float)minY) || !Float.isFinite((float)minZ)
                || !Float.isFinite((float)maxX) || !Float.isFinite((float)maxY) || !Float.isFinite((float)maxZ))
            throw new FdxException("Animated bounds overflow");
        out.min().set((float)minX,(float)minY,(float)minZ); out.max().set((float)maxX,(float)maxY,(float)maxZ);
        return out;
    }
}
