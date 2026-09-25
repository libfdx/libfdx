package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Vector3;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

/** Bounded CPU packing; even retained-channel staging yields through Mesh preparation. */
final class LodMeshPackingTask {
    private final MeshLodData source;
    private final BooleanSupplier cancelled;
    private final boolean split;
    private final ArrayList<LodPreparedMesh> chunks=new ArrayList<>();
    private int[] map,references;
    private short[] indices;
    private float[][] channels;
    private Mesh.PositionColor3DPreparation preparation;
    private LodDeformationPackingTask deformation;
    private int phase,cursor,start,end,vertices,count,channel;
    private float minX,minY,minZ,maxX,maxY,maxZ;
    private LodPreparedMesh[] result;
    LodMeshPackingTask(MeshLodData source,BooleanSupplier cancelled) {
        this.source=source;this.cancelled=cancelled;split=source.vertexCount()>65536;
    }
    boolean step(int budget) {
        if(budget<1) throw new IllegalArgumentException("Positive work budget required");
        LodMeshPacking.check(cancelled);
        while(budget-- > 0 && result==null) switch(phase) {
            case 0 -> {
                if(split) { map=new int[source.vertexCount()]; phase++; }
                else { vertices=source.vertexCount();count=source.indices().length;end=count;phase=4; }
                return false;
            }
            case 1 -> { if(cursor<map.length) map[cursor++]=-1;else { cursor=0;phase++; } }
            case 2 -> { references=new int[65536];phase++;return false; }
            case 3 -> {
                if(end==source.indices().length || vertices>65533) { phase++;cursor=0;continue; }
                for(int k=0;k<3;k++) { int v=source.indices()[end++];if(map[v]<0) { map[v]=vertices;references[vertices++]=v; }count++; }
            }
            case 4 -> { indices=new short[count];channels=new float[MeshLodData.CHANNEL_COUNT][];phase++;return false; }
            case 5 -> {
                if(cursor<count) { int v=source.indices()[start+cursor];indices[cursor++]=(short)(split?map[v]:v); }
                else { phase++;cursor=0; }
            }
            case 6 -> {
                if(channel==channels.length) {
                    phase=7;cursor=0;minX=minY=minZ=Float.POSITIVE_INFINITY;maxX=maxY=maxZ=Float.NEGATIVE_INFINITY;
                    if (source.deformation()!=null) { deformation=new LodDeformationPackingTask(source,split ? references : null,vertices);phase=10; }
                    continue;
                }
                float[] input=source.channel(channel);if(input==null) { channel++;continue; }
                if(!split) { channels[channel++]=input;continue; }
                int width=MeshLodData.components(channel);
                if(channels[channel]==null) { channels[channel]=new float[vertices*width];return false; }
                if(cursor<vertices) { System.arraycopy(input,references[cursor]*width,channels[channel],cursor*width,width);cursor++; }
                else { cursor=0;channel++; }
            }
            case 7 -> {
                if(cursor<vertices) {
                    int p=cursor++*3;float[] positions=channels[0];
                    minX=Math.min(minX,positions[p]);maxX=Math.max(maxX,positions[p]);
                    minY=Math.min(minY,positions[p+1]);maxY=Math.max(maxY,positions[p+1]);
                    minZ=Math.min(minZ,positions[p+2]);maxZ=Math.max(maxZ,positions[p+2]);
                } else {
                    preparation=Mesh.preparePositionColor3D(channels[0],channels[1],channels[2],channels[3],channels[4],channels[7],channels[8],channels[9],channels[10],
                            deformation==null ? null : deformation.joints(),deformation==null ? null : deformation.weights(),
                            new BoundingBox(new Vector3(minX,minY,minZ),new Vector3(maxX,maxY,maxZ)),true,channels[5],channels[6]);phase++;return false;
                }
            }
            case 8 -> {
                if(!preparation.step(budget+1)) return false;
                chunks.add(deformation==null ? new LodPreparedMesh(preparation,indices,vertices) : new LodPreparedMesh(preparation,indices,vertices,deformation.targets()));
                preparation=null;channels=null;indices=null;deformation=null;
                if(end==source.indices().length) result=chunks.toArray(new LodPreparedMesh[0]);
                else { phase++;cursor=0; }
                return result!=null;
            }
            case 9 -> { if(cursor<vertices) map[references[cursor++]]=-1;else { start=end;vertices=count=cursor=channel=0;phase=3; } }
            case 10 -> { if (deformation.step(budget+1)) phase=7;return false; }
            default -> throw new IllegalStateException("Unexpected packing phase");
        }
        return result!=null;
    }
    LodPreparedMesh[] result() { if(result==null) throw new IllegalStateException("Packing incomplete");return result; }
}
