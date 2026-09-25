package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.math.Matrix4;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** CPU geometry preparation shared by procedural/runtime and file-import adapters. */
public final class MeshLodGeometry {
    private MeshLodGeometry() { }
    /** Concatenates compatible vertex layouts. Caller must group compatible materials and rigid motion. */
    public static MeshLodData combine(MeshLodData... meshes) {
        if (meshes == null || meshes.length == 0) throw new IllegalArgumentException("Meshes required");
        int vertices=0,indices=0;
        for (MeshLodData mesh : meshes) {
            if (mesh.deformation()!=null) throw new IllegalArgumentException("Static combining cannot flatten skin/morph deformation");
            vertices=Math.addExact(vertices,mesh.vertexCount()); indices=Math.addExact(indices,mesh.indices().length);
        }
        float[][] channels=new float[MeshLodData.CHANNEL_COUNT][];
        for (int c=0;c<channels.length;c++) {
            boolean present=meshes[0].channel(c)!=null;
            for (MeshLodData mesh : meshes) if ((mesh.channel(c)!=null)!=present) throw new IllegalArgumentException("Incompatible vertex layouts");
            if (present) channels[c]=new float[Math.multiplyExact(vertices,MeshLodData.components(c))];
        }
        int[] output=new int[indices]; int vertexOffset=0,indexOffset=0;
        for (MeshLodData mesh : meshes) {
            for (int c=0;c<channels.length;c++) if (channels[c]!=null) System.arraycopy(mesh.channel(c),0,channels[c],vertexOffset*MeshLodData.components(c),mesh.channel(c).length);
            for (int index : mesh.indices()) output[indexOffset++]=index+vertexOffset;
            vertexOffset+=mesh.vertexCount();
        }
        boolean[] locked=null;
        for(MeshLodData mesh:meshes) if(mesh.locked!=null) { locked=new boolean[vertices];break; }
        if(locked!=null) { int offset=0;for(MeshLodData mesh:meshes) { if(mesh.locked!=null) System.arraycopy(mesh.locked,0,locked,offset,mesh.vertexCount());offset+=mesh.vertexCount(); } }
        return MeshLodData.owned(channels,output,locked,false);
    }
    /** Affine transform, inverse-transpose normals and mirrored winding/tangent handedness. */
    public static MeshLodData transform(MeshLodData source,Matrix4 transform) {
        if (source.deformation()!=null) throw new IllegalArgumentException("Static transforms cannot discard skin/morph coordinate mappings");
        float[] m=new float[16]; transform.copyValues(m,0);
        for (float value : m) if (!Float.isFinite(value)) throw new IllegalArgumentException("Finite transform required");
        if (m[3]!=0 || m[7]!=0 || m[11]!=0 || m[15]!=1) throw new IllegalArgumentException("Affine transform required");
        double a=m[0],b=m[4],c=m[8],d=m[1],e=m[5],f=m[9],g=m[2],h=m[6],i=m[10];
        double det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if (Math.abs(det)<1e-20) throw new IllegalArgumentException("Cannot merge a singular transform");
        double[] normal={(e*i-f*h)/det,(f*g-d*i)/det,(d*h-e*g)/det,
                (c*h-b*i)/det,(a*i-c*g)/det,(b*g-a*h)/det,
                (b*f-c*e)/det,(c*d-a*f)/det,(a*e-b*d)/det};
        float[][] channels=new float[MeshLodData.CHANNEL_COUNT][];
        for (int channel=0;channel<channels.length;channel++) if (source.channel(channel)!=null) channels[channel]=source.channel(channel).clone();
        float[] p=channels[0],n=channels[MeshLodData.NORMAL],t=channels[MeshLodData.TANGENT];
        for (int v=0;v<source.vertexCount();v++) {
            double x=p[v*3],y=p[v*3+1],z=p[v*3+2];
            p[v*3]=(float)(a*x+b*y+c*z+m[12]); p[v*3+1]=(float)(d*x+e*y+f*z+m[13]); p[v*3+2]=(float)(g*x+h*y+i*z+m[14]);
            if (n!=null) {
                x=n[v*3]; y=n[v*3+1]; z=n[v*3+2];
                unit(n,v*3,normal[0]*x+normal[1]*y+normal[2]*z,normal[3]*x+normal[4]*y+normal[5]*z,normal[6]*x+normal[7]*y+normal[8]*z);
            }
            if (t!=null) {
                x=t[v*4]; y=t[v*4+1]; z=t[v*4+2];
                double tx=a*x+b*y+c*z,ty=d*x+e*y+f*z,tz=g*x+h*y+i*z;
                if (n!=null) { double dot=tx*n[v*3]+ty*n[v*3+1]+tz*n[v*3+2]; tx-=dot*n[v*3]; ty-=dot*n[v*3+1]; tz-=dot*n[v*3+2]; }
                unit(t,v*4,tx,ty,tz); if (det<0) t[v*4+3]=-t[v*4+3];
            }
        }
        int[] indices=source.indices().clone();
        if (det<0) for (int v=0;v<indices.length;v+=3) { int temp=indices[v+1]; indices[v+1]=indices[v+2]; indices[v+2]=temp; }
        MeshLodData result=new MeshLodData(channels,indices);
        return source.locked==null?result:result.withLocks(source.locked.clone());
    }
    /** Full-detail indexing/cache/fetch pass, without geometric simplification. */
    public static MeshLodData optimize(MeshLodData source) {
        LodMeshWeldTask weld=new LodMeshWeldTask(Objects.requireNonNull(source));
        while(!weld.step(4096)) { }
        MeshLodData compact=weld.result();
        return LodMeshOrdering.compact(compact,compact.indices(),true,true);
    }
    /**
     * Prepares independently simplified primitives in one rigid coordinate frame. Exact shared
     * positions are locked on every participating primitive, including material/partition seams.
     * Does not join topology, repair mismatched boundaries or compare unrelated node frames.
     * Returns read-only geometry in input order; immutable channel storage may be shared with
     * previously compacted inputs. Input data is never modified.
     * This blocking helper is intended for import workers; model preparation uses cooperative jobs.
     */
    public static MeshLodData[] protectSharedVertices(MeshLodData... meshes) {
        return protectSharedVertices(() -> false,meshes);
    }
    /** Same operation with cancellation checks between CPU preparation steps. */
    public static MeshLodData[] protectSharedVertices(BooleanSupplier cancelled,MeshLodData... meshes) {
        Objects.requireNonNull(cancelled);Objects.requireNonNull(meshes);
        MeshLodData[] result=new MeshLodData[meshes.length];int[] members=new int[meshes.length];
        for(int i=0;i<meshes.length;i++) {
            LodMeshWeldTask weld=new LodMeshWeldTask(Objects.requireNonNull(meshes[i]));
            do { LodMeshPacking.check(cancelled); } while(!weld.step(4096));
            result[i]=weld.result();members[i]=i;
        }
        LodSharedVerticesTask shared=new LodSharedVerticesTask(result,meshes.length>1?List.of(members):List.of());
        do { LodMeshPacking.check(cancelled); } while(!shared.step(4096));
        return result;
    }
    private static void unit(float[] data,int offset,double x,double y,double z) {
        double length=Math.sqrt(x*x+y*y+z*z);
        if (length>0) { data[offset]=(float)(x/length); data[offset+1]=(float)(y/length); data[offset+2]=(float)(z/length); }
    }
}
