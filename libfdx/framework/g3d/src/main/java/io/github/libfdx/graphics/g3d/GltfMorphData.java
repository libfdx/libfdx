package io.github.libfdx.graphics.g3d;

import io.github.libfdx.json.JsonValue;

/** Checked glTF morph metadata and expansion into the loader's triangle-corner domain. */
final class GltfMorphData {
    static final String[] SEMANTICS = {"POSITION", "NORMAL", "TANGENT"};
    static int count(JsonValue root, int mesh) {
        if (mesh < 0) return 0;
        JsonValue primitives=root.require("meshes").require(mesh).require("primitives");
        int count=-1;
        for (int p=0;p<primitives.size();p++) {
            JsonValue primitive=primitives.require(p);
            JsonValue targets=primitive.get("targets"); int next=targets == null ? 0 : targets.size();
            if (count >= 0 && next != count) throw new IllegalArgumentException("glTF mesh primitives have different morph target counts");
            count=next;
        }
        if (count > 4096) throw new IllegalArgumentException("glTF morph target count exceeds supported limit");
        return Math.max(0,count);
    }
    static float[] weights(JsonValue root, JsonValue node, int mesh) {
        int count=count(root,mesh);
        JsonValue values=node == null ? null : node.get("weights");
        if (values == null && mesh >= 0) values=root.require("meshes").require(mesh).get("weights");
        float[] weights=new float[count];
        if (values != null) {
            if (!values.isArray() || values.size() != count || count == 0) throw new IllegalArgumentException("glTF morph weight count mismatch");
            for (int i=0;i<count;i++) {
                weights[i]=values.require(i).floatValue();
                if (!Float.isFinite(weights[i])) throw new IllegalArgumentException("glTF morph weights must be finite");
            }
        }
        return weights;
    }
    static void validate(JsonValue primitive) {
        JsonValue targets=primitive.get("targets"); if (targets == null) return;
        JsonValue attributes=primitive.require("attributes");
        for (int t=0;t<targets.size();t++) {
            JsonValue target=targets.require(t);
            int recognized=0;
            for (String name : SEMANTICS) if (target.get(name) != null) {
                recognized++;
                if (attributes.get(name) == null) throw new IllegalArgumentException("glTF morph target requires base " + name);
            }
            if (recognized != target.size()) throw new IllegalArgumentException("Morph targets support POSITION, NORMAL and TANGENT deltas");
        }
    }
    static MorphTarget[] read(GltfAccessors accessors, JsonValue mesh, JsonValue primitive, float[] basePositions,
            float[] baseNormals, int[] references) {
        JsonValue targets=primitive.get("targets"); if (targets == null) return new MorphTarget[0];
        MorphTarget[] result=new MorphTarget[targets.size()];
        JsonValue extras=mesh.get("extras"), names=extras==null?null:extras.get("targetNames");
        JsonValue defaults=mesh.get("weights");
        for (int t=0;t<result.length;t++) {
            float[][] values=new float[3][];
            for (int c=0;c<3;c++) {
                JsonValue id=targets.require(t).get(SEMANTICS[c]); if (id == null) continue;
                float[] source=accessors.attribute(id.intValue(),"MORPH_"+SEMANTICS[c]);
                if (source.length != basePositions.length) throw new IllegalArgumentException("Morph accessor vertex count mismatch");
                values[c]=new float[references.length*3];
                for (int v=0;v<references.length;v++) System.arraycopy(source,references[v]*3,values[c],v*3,3);
            }
            if (baseNormals == null && values[0] != null) values[1]=flatNormalDeltas(basePositions,references,values[0]);
            String name=names!=null && names.isArray() && names.size()==result.length ? names.require(t).stringValue() : "target-"+t;
            float weight=defaults==null?0:defaults.require(t).floatValue();
            result[t]=new MorphTarget(name,weight,values[0],values[1],values[2]);
        }
        return result;
    }
    private static float[] flatNormalDeltas(float[] base,int[] refs,float[] delta) {
        float[] output=new float[delta.length]; double[] normals=new double[6];
        for (int f=0;f<refs.length;f+=3) {
            for (int pose=0;pose<2;pose++) {
                double ax=base[refs[f]*3]+pose*delta[f*3],ay=base[refs[f]*3+1]+pose*delta[f*3+1],az=base[refs[f]*3+2]+pose*delta[f*3+2];
                double ux=base[refs[f+1]*3]+pose*delta[(f+1)*3]-ax,uy=base[refs[f+1]*3+1]+pose*delta[(f+1)*3+1]-ay,uz=base[refs[f+1]*3+2]+pose*delta[(f+1)*3+2]-az;
                double vx=base[refs[f+2]*3]+pose*delta[(f+2)*3]-ax,vy=base[refs[f+2]*3+1]+pose*delta[(f+2)*3+1]-ay,vz=base[refs[f+2]*3+2]+pose*delta[(f+2)*3+2]-az;
                double x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx,len=Math.sqrt(x*x+y*y+z*z);
                normals[pose*3]=len > 1e-20 ? x/len : 0; normals[pose*3+1]=len > 1e-20 ? y/len : 0; normals[pose*3+2]=len > 1e-20 ? z/len : 1;
            }
            for (int v=0;v<3;v++) for (int c=0;c<3;c++) output[(f+v)*3+c]=(float)(normals[3+c]-normals[c]);
        }
        return output;
    }
}
