package io.github.libfdx.audio.loaders;

import io.github.libfdx.assets.AssetExecutor;
import io.github.libfdx.audio.PcmStream;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileDataSource;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

final class WavStreamTest {
    @Test
    void multiGigabyteTrackOpensWithOnlyHeadersAndDecodesBoundedSeekedFrames() {
        SyntheticFile file=new SyntheticFile(1_000_000_000L,2,16,true);
        PcmStream stream=WavStream.open(file,64,null).get();
        assertEquals(44,file.bytesRead); assertEquals(1_000_000_000L,stream.frames());
        short[] samples=new short[128];
        assertEquals(64,stream.read(700_000_000L,samples,0,64).get());
        for(int frame=0;frame<64;frame++) for(int channel=0;channel<2;channel++) {
            assertEquals(SyntheticFile.sample(700_000_000L+frame,channel),samples[frame*2+channel]);
        }
        assertEquals(2,stream.read(999_999_998L,samples,0,64).get());
        assertEquals(-1,stream.read(1_000_000_000L,samples,0,1).get());
        assertEquals(1,stream.read(0,samples,0,1).get()); assertEquals(0,samples[0]);
        assertTrue(file.bytesRead<400); assertTrue(file.maxRequested<=128);
        stream.dispose(); assertTrue(file.disposed);
    }

    @Test
    void partialPendingReadsComposeWithoutBlockingAndHonorSequentialOffsets() {
        SyntheticFile file=new SyntheticFile(7,1,8,false); file.delay=true; file.partial=3;
        FdxFuture<PcmStream> opening=WavStream.open(file,4,null);
        assertFalse(opening.isDone());
        for(int i=0;i<100 && !opening.isDone();i++) { file.complete(); }
        PcmStream stream=opening.get(); assertFalse(stream.isSeekable());
        short[] samples=new short[8]; Arrays.fill(samples,(short)77);
        FdxFuture<Integer> read=stream.read(0,samples,2,4);
        assertFalse(read.isDone());
        for(int i=0;i<100 && !read.isDone();i++) { file.complete(); stream.update(); }
        assertEquals(4,read.get()); assertEquals(77,samples[0]); assertEquals(77,samples[6]);
        for(int i=0;i<4;i++) { assertEquals((short)((((i*37+128)&255)-128)<<8),samples[2+i]); }
        assertTrue(stream.read(0,samples,0,1).isFailed());
        read=stream.read(4,samples,0,4);
        for(int i=0;i<100 && !read.isDone();i++) { file.complete(); stream.update(); }
        assertEquals(3,read.get()); // Includes the odd data-chunk pad in file input.
        assertEquals(file.length(),file.position);
        stream.dispose();
    }

    @Test
    void fullWorkerQueueRetriesAndClosingQueuedOrPendingReadsPreventsLateWrites() {
        SyntheticFile file=new SyntheticFile(20,2,16,true);
        ManualExecutor executor=new ManualExecutor();
        PcmStream stream=WavStream.open(file,8,executor).get();
        short[] samples=new short[16]; Arrays.fill(samples,(short)91);
        FdxFuture<Integer> read=stream.read(0,samples,0,8);
        assertFalse(read.isDone()); assertNull(executor.task);
        stream.update(); assertFalse(read.isDone());
        executor.accept=true; stream.update(); assertNotNull(executor.task);
        executor.run(); assertEquals(8,read.get());
        Arrays.fill(samples,(short)91);
        read=stream.read(8,samples,0,8); stream.dispose();
        assertTrue(read.isFailed()); executor.run();
        for(short sample:samples) { assertEquals(91,sample); }
        assertTrue(stream.read(0,samples,0,1).isFailed());

        file=new SyntheticFile(20,2,16,true); stream=WavStream.open(file,8,null).get();
        file.delay=true; read=stream.read(0,samples,0,8); stream.dispose(); file.complete();
        assertTrue(read.isFailed()); for(short sample:samples) { assertEquals(91,sample); }
    }

    @Test
    void malformedOrTruncatedHeadersCloseInputAndRuntimeTruncationFailsCleanly() {
        SyntheticFile invalid=new SyntheticFile(20,2,16,true); invalid.header[0]='X';
        assertTrue(WavStream.open(invalid,8,null).isFailed()); assertTrue(invalid.disposed);
        invalid=new SyntheticFile(20,2,16,true); invalid.header[22]=3;
        assertTrue(WavStream.open(invalid,8,null).isFailed()); assertTrue(invalid.disposed);
        SyntheticFile file=new SyntheticFile(20,2,16,true);
        PcmStream stream=WavStream.open(file,8,null).get(); file.truncate=48;
        FdxFuture<Integer> read=stream.read(0,new short[16],0,8);
        assertTrue(read.isFailed()); assertTrue(file.disposed);
        stream.dispose();
    }

    private static final class ManualExecutor implements AssetExecutor {
        boolean accept; Runnable task;
        @Override
        public boolean submit(Runnable runnable) { if(!accept || task!=null) return false; task=runnable; return true; }
        void run() { Runnable run=task; task=null; if(run!=null) run.run(); }
        @Override
        public void dispose() { }
        @Override
        public boolean isDisposed() { return false; }
    }
    /** Virtual PCM payload makes track size independent of actual test memory/disk use. */
    private static final class SyntheticFile implements FileDataSource {
        final byte[] header=new byte[44];
        final long size;
        final int channels,bits,frameBytes;
        final boolean seekable;
        boolean disposed,delay;
        int partial=128,maxRequested;
        long position,bytesRead,truncate=Long.MAX_VALUE;
        FdxFuture<Integer> pending; long offset; byte[] target; int start,count;
        SyntheticFile(long frames,int channels,int bits,boolean seekable) {
            this.channels=channels; this.bits=bits; this.seekable=seekable; frameBytes=channels*(bits/8);
            long data=frames*frameBytes; size=44+data+(data&1);
            tag(0,"RIFF"); number(4,size-8,4); tag(8,"WAVE"); tag(12,"fmt "); number(16,16,4);
            number(20,1,2); number(22,channels,2); number(24,48000,4); number(28,48000*frameBytes,4);
            number(32,frameBytes,2); number(34,bits,2); tag(36,"data"); number(40,data,4);
        }
        static short sample(long frame,int channel) { return (short)(frame*17+channel*1000); }
        void tag(int at,String text) { for(int i=0;i<4;i++) header[at+i]=(byte)text.charAt(i); }
        void number(int at,long value,int bytes) { for(int i=0;i<bytes;i++) header[at+i]=(byte)(value>>>(i*8)); }
        @Override
        public long length() { return size; }
        @Override
        public boolean isSeekable() { return seekable; }
        @Override
        public int maxReadBytes() { return 128; }
        @Override
        public FdxFuture<Integer> read(long offset,byte[] target,int start,int count) {
            FileDataSource.validate(offset,target,start,count,128);
            if(disposed || !seekable && offset!=position || pending!=null) return FdxFuture.failed(new FdxException("Invalid synthetic input state"));
            maxRequested=Math.max(maxRequested,count); this.offset=offset; this.target=target; this.start=start; this.count=count;
            FdxFuture<Integer> result=FdxFuture.pending(); pending=result;
            if(!delay) complete(); return result;
        }
        void complete() {
            if(pending==null) return;
            FdxFuture<Integer> result=pending; pending=null;
            if(disposed) { result.completeExceptionally(new FdxException("closed")); return; }
            int actual=(int)Math.min(Math.min(partial,count),Math.max(0,Math.min(size,truncate)-offset));
            for(int i=0;i<actual;i++) {
                long index=offset+i;
                if(index<44) target[start+i]=header[(int)index];
                else {
                    long frame=(index-44)/frameBytes;
                    int lane=(int)((index-44)%frameBytes);
                    target[start+i]=bits==8?(byte)((frame*37+128)&255)
                            :(byte)(sample(frame,lane/2)>>>((lane&1)*8));
                }
            }
            position=offset+actual; bytesRead+=actual;
            result.complete(actual==0&&count>0?-1:actual);
        }
        @Override
        public void dispose() {
            disposed=true;
            if(pending!=null) { FdxFuture<Integer> result=pending; pending=null; result.completeExceptionally(new FdxException("closed")); }
        }
        @Override
        public boolean isDisposed() { return disposed; }
    }
}
