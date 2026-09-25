package io.github.libfdx.graphics.meshoptimizer;

import java.util.Arrays;

/** Resumable cache-frontier traversal, channel compaction and first-use vertex ordering. */
final class LodMeshOrderingTask {
    private static final double[] CACHE_SCORE=cacheScores();
    private final MeshLodData source;
    private final int[] input;
    private final boolean cacheOrder, fetch;
    private int[] indices, remaining, offsets, ends, adjacency, remap;
    private int[] origins;
    private boolean[] emitted, locked;
    private final int[] cache=new int[16];
    private double[] valenceScore;
    private final float[][] channels=new float[MeshLodData.CHANNEL_COUNT][];
    private int phase, cursor, count, scan, channel, maxValence;
    private MeshLodData result;
    LodMeshOrderingTask(MeshLodData source,int[] indices,boolean cache,boolean fetch) {
        this.source=source; input=indices; cacheOrder=cache; this.fetch=fetch;
        Arrays.fill(this.cache,-1);
    }
    boolean step(int budget) {
        if(budget<1) throw new IllegalArgumentException("Positive work budget required");
        while(budget-- > 0 && result==null) switch(phase) {
            case 0 -> { remap=new int[source.vertexCount()]; phase++; return false; }
            case 1 -> { if(cursor<remap.length) remap[cursor++]=-1; else { next(); if(!cacheOrder) { indices=input; phase=12; } } }
            case 2 -> { remaining=new int[source.vertexCount()]; phase++; return false; }
            case 3 -> { offsets=new int[source.vertexCount()+1]; phase++; return false; }
            case 4 -> { ends=new int[source.vertexCount()]; phase++; return false; }
            case 5 -> { adjacency=new int[input.length]; phase++; return false; }
            case 6 -> { emitted=new boolean[input.length/3]; phase++; return false; }
            case 7 -> { indices=new int[input.length]; phase++; return false; }
            case 8 -> {
                if(cursor<input.length) { int v=input[cursor++]; maxValence=Math.max(maxValence,++remaining[v]); }
                else next();
            }
            case 9 -> { if(cursor<remaining.length) { offsets[cursor+1]=offsets[cursor]+remaining[cursor]; ends[cursor]=offsets[cursor]; cursor++; } else next(); }
            case 10 -> {
                if(cursor<input.length) { adjacency[ends[input[cursor]]++]=cursor/3; cursor++; }
                else { valenceScore=new double[maxValence+1]; next(); return false; }
            }
            case 11 -> {
                if(cursor<valenceScore.length) { valenceScore[cursor]=cursor==0?0:2/Math.sqrt(cursor); cursor++; }
                else { next(); phase=20; }
            }
            case 20 -> { if(cursor<indices.length) emit(); else { remaining=null;offsets=null;ends=null;adjacency=null;emitted=null;valenceScore=null; phase=12;cursor=0; } }
            case 12 -> {
                if(cursor<indices.length) { int v=indices[cursor++]; if(fetch) { if(remap[v]<0) remap[v]=count++; } else remap[v]=0; }
                else next();
            }
            case 13 -> { if(!fetch && cursor<remap.length) { if(remap[cursor]>=0) remap[cursor]=count++; cursor++; } else next(); }
            case 14 -> {
                if(source.locked==null) { next(); continue; }
                if(locked==null) { locked=new boolean[count]; return false; }
                if(cursor<remap.length) { if(remap[cursor]>=0) locked[remap[cursor]]=source.vertexLocked(cursor); cursor++; }
                else next();
            }
            case 15 -> {
                if(channel==channels.length) { next(); continue; }
                float[] values=source.channel(channel); if(values==null) { channel++; continue; }
                int width=MeshLodData.components(channel);
                if(channels[channel]==null) { channels[channel]=new float[count*width]; return false; }
                if(cursor<remap.length) { if(remap[cursor]>=0) System.arraycopy(values,cursor*width,channels[channel],remap[cursor]*width,width); cursor++; }
                else { channel++;cursor=0; }
            }
            case 16 -> {
                if(indices==input) { indices=new int[input.length]; return false; }
                if(cursor<indices.length) { indices[cursor]=remap[cacheOrder?indices[cursor]:input[cursor]]; cursor++; }
                else next();
            }
            case 17 -> {
                if (source.deformation()!=null) {
                    if (origins==null) { origins=new int[count];return false; }
                    if (cursor<remap.length) { if (remap[cursor]>=0) origins[remap[cursor]]=source.sourceVertex(cursor);cursor++;continue; }
                }
                result=MeshLodData.owned(channels,indices,locked,true,source.deformation(),origins);
            }
            default -> throw new IllegalStateException("Unexpected ordering phase");
        }
        return result!=null;
    }
    private void emit() {
        int best=-1;double score=-1;
        for(int v:cache) if(v>=0) {
            int write=offsets[v];
            for(int i=offsets[v];i<ends[v];i++) {
                int face=adjacency[i]; if(emitted[face]) continue;
                adjacency[write++]=face;double value=0;
                for(int k=0;k<3;k++) { int n=input[face*3+k]; value+=valenceScore[remaining[n]];
                    for(int c=0;c<cache.length;c++) if(cache[c]==n) { value+=CACHE_SCORE[c]; break; } }
                if(value>score || value==score && face<best) { score=value;best=face; }
            }
            ends[v]=write;
        }
        if(best<0) { while(emitted[scan]) scan++;best=scan; }
        emitted[best]=true;
        for(int k=0;k<3;k++) { int v=indices[cursor++]=input[best*3+k];remaining[v]--;int slot=cache.length-1;
            for(int c=0;c<cache.length;c++) if(cache[c]==v) { slot=c;break; }
            for(int c=slot;c>0;c--) cache[c]=cache[c-1];cache[0]=v; }
    }
    private void next() { phase++;cursor=0; }
    MeshLodData result() { if(result==null) throw new IllegalStateException("Ordering incomplete");return result; }
    private static double[] cacheScores() { double[] scores=new double[16]; for(int c=0;c<scores.length;c++) scores[c]=c<3?.75:Math.pow(1-(c-3)/13.0,1.5); return scores; }
}
