package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.BoundingBox;
import java.util.Arrays;

/** Conservative interval bounds per joint and target; frame work scales with joints/targets,
 * not vertices. The union contains normalized positive-weight linear skin blends. */
final class MorphBounds3D {
    private final int bones,targets;
    private final double[] extremes;
    private final float[] matrices;
    private final MorphTarget[] morphs;
    MorphBounds3D(ModelNodePart part) {
        bones=part.skin()==null?0:part.skin().skeleton().bones().size();targets=part.morphTargetCount();
        morphs=part.morphTargets();matrices=new float[bones*16];
        extremes=new double[Math.multiplyExact(Math.multiplyExact(bones+1,targets+1),6)];
        for(int i=0;i<extremes.length;i+=6) { Arrays.fill(extremes,i,i+3,Double.POSITIVE_INFINITY);Arrays.fill(extremes,i+3,i+6,Double.NEGATIVE_INFINITY); }
        float[] positions=part.meshPart().mesh().sourcePositions(),weights=part.weights();int[] joints=part.joints();
        for(int v=0;v<positions.length/3;v++) {
            boolean weighted=false;
            if(bones>0) for(int j=0;j<4;j++) if(weights[v*4+j]>0) {
                add(joints[v*4+j],v,positions);weighted=true;
            }
            if(!weighted) add(bones,v,positions);
        }
    }
    private void add(int group,int vertex,float[] positions) {
        if(group<0 || group>bones) throw new IllegalArgumentException("Morph bounds joint outside palette");
        for(int t=0;t<=targets;t++) for(int c=0;c<3;c++) {
            double value=t==0?positions[vertex*3+c]:morphs[t-1].delta(0,vertex,c);
            int at=(group*(targets+1)+t)*6+c;extremes[at]=Math.min(extremes[at],value);extremes[at+3]=Math.max(extremes[at+3],value);
        }
    }
    void update(SkinningPalette palette,float[] weights,BoundingBox out) {
        if(bones>0) palette.copyValues(matrices);
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int group=0;group<=bones;group++) {
            int at=group*(targets+1)*6;if(!Double.isFinite(extremes[at]))continue;
            double x0=extremes[at],y0=extremes[at+1],z0=extremes[at+2],x1=extremes[at+3],y1=extremes[at+4],z1=extremes[at+5];
            for(int t=0;t<targets;t++) {
                double w=weights[t];int lo=at+(t+1)*6+(w<0?3:0),hi=at+(t+1)*6+(w<0?0:3);
                x0+=w*extremes[lo];y0+=w*extremes[lo+1];z0+=w*extremes[lo+2];
                x1+=w*extremes[hi];y1+=w*extremes[hi+1];z1+=w*extremes[hi+2];
            }
            for(int c=0;c<8;c++) {
                double x=(c&1)==0?x0:x1,y=(c&2)==0?y0:y1,z=(c&4)==0?z0:z1,px=x,py=y,pz=z;
                if(group<bones) {
                    int m=group*16;px=matrices[m]*x+matrices[m+4]*y+matrices[m+8]*z+matrices[m+12];
                    py=matrices[m+1]*x+matrices[m+5]*y+matrices[m+9]*z+matrices[m+13];pz=matrices[m+2]*x+matrices[m+6]*y+matrices[m+10]*z+matrices[m+14];
                }
                minX=Math.min(minX,px);minY=Math.min(minY,py);minZ=Math.min(minZ,pz);maxX=Math.max(maxX,px);maxY=Math.max(maxY,py);maxZ=Math.max(maxZ,pz);
            }
        }
        double magnitude=Math.max(Math.max(Math.abs(minX),Math.abs(maxX)),Math.max(Math.max(Math.abs(minY),Math.abs(maxY)),Math.max(Math.abs(minZ),Math.abs(maxZ))));
        double margin=Math.max(1e-5,magnitude*2e-6);
        out.min().set((float)(minX-margin),(float)(minY-margin),(float)(minZ-margin));
        out.max().set((float)(maxX+margin),(float)(maxY+margin),(float)(maxZ+margin));
    }
}
