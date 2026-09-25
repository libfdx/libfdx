package io.github.libfdx.graphics.meshoptimizer;

/** Exact, channel-preserving compaction before allocating expensive quadric state. */
final class LodMeshWeldTask {
    private final MeshLodData source;
    private LodVertexIndex lookup;
    private boolean[] used, locked;
    private int[] map, references, indices, origins;
    private final float[][] channels = new float[MeshLodData.CHANNEL_COUNT][];
    private int phase, cursor, count, channel;
    private MeshLodData result;
    LodMeshWeldTask(MeshLodData source) { this.source=source; if(source.compact) result=source; }
    boolean step(int budget) {
        if(budget<1) throw new IllegalArgumentException("Positive work budget required");
        while(budget-- > 0 && result==null) switch(phase) {
            case 0 -> { used=new boolean[source.vertexCount()]; phase++; return false; }
            case 1 -> { map=new int[source.vertexCount()]; phase++; return false; }
            case 2 -> { references=new int[source.vertexCount()]; phase++; return false; }
            case 3 -> { indices=new int[source.indices().length]; phase++; return false; }
            case 4 -> { lookup=new LodVertexIndex(source,false); phase++; return false; }
            case 5 -> { if(cursor<indices.length) used[source.indices()[cursor++]]=true; else next(); }
            case 6 -> {
                if(cursor<used.length) {
                    int v=cursor++; if(!used[v]) continue;
                    int representative=lookup.add(v);
                    if(representative==v) { map[v]=count; references[count++]=v; }
                    else map[v]=map[representative];
                } else { lookup=null; used=null; next(); }
            }
            case 7 -> { if(cursor<indices.length) { indices[cursor]=map[source.indices()[cursor]]; cursor++; } else next(); }
            case 8 -> {
                if(source.locked==null) { next(); continue; }
                if(locked==null) { locked=new boolean[count]; return false; }
                if(cursor<source.indices().length) { int v=source.indices()[cursor++]; locked[map[v]] |= source.vertexLocked(v); }
                else next();
            }
            default -> {
                if (source.deformation()!=null && origins==null) { origins=new int[count];return false; }
                if (channel==channels.length && origins!=null && cursor<count) { origins[cursor]=source.sourceVertex(references[cursor]);cursor++;continue; }
                if(channel==channels.length) { result=MeshLodData.owned(channels,indices,locked,true,source.deformation(),origins); break; }
                float[] input=source.channel(channel);
                if(input==null) { channel++; continue; }
                int width=MeshLodData.components(channel);
                if(channels[channel]==null) { channels[channel]=new float[count*width]; return false; }
                if(cursor<count) { System.arraycopy(input,references[cursor]*width,channels[channel],cursor*width,width); cursor++; }
                else { cursor=0; channel++; }
            }
        }
        return result!=null;
    }
    private void next() { phase++; cursor=0; }
    MeshLodData result() { if(result==null) throw new IllegalStateException("Welding incomplete"); return result; }
}
