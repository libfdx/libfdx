package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.*;
import io.github.libfdx.core.*;
import io.github.libfdx.files.FileDataSource;
import io.github.libfdx.files.FileHandle;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Bounded reads and asynchronous file composition through the real asset update queue. */
public final class FileStreamingTest extends GraphicsParityTest {
    private final AssetExecutor executor;
    private DefaultAssetManager assets;
    private AssetLease<Probe> lease;
    private Probe probe;
    private boolean reported;
    private Thread owner;

    public FileStreamingTest(long frames) { this(frames,null); }
    /** Owns the optional worker executor, closed after the asset manager. */
    public FileStreamingTest(long frames, AssetExecutor executor) { super(frames); this.executor=executor; }

    @Override public void create(Fdx fdx) {
        initialize(fdx,"FileStreamingTest"); owner=Thread.currentThread();
        assets=new DefaultAssetManager(fdx.files(),executor);
        assets.registerLoader(Probe.class,new AssetLoader<Probe>() {
            @Override public Class<Probe> type() { return Probe.class; }
            @Override public FdxFuture<Probe> load(AssetLoadContext context,AssetDescriptor<Probe> descriptor) {
                FdxFuture<Probe> result=FdxFuture.pending();
                FileHandle file=context.files().internal(descriptor.path());
                context.asyncFuture(() -> file.openRead(4096)).onSuccess(source -> {
                    Probe opened=new Probe(source); result.onFailure(error -> opened.dispose());
                    read(context,result,opened,0);
                }).onFailure(result::completeExceptionally);
                return result;
            }
        });
        lease=assets.acquire(AssetDescriptor.of("streaming/bytes.bin",Probe.class)); markCreated();
    }

    private void read(AssetLoadContext context,FdxFuture<Probe> result,Probe probe,int phase) {
        try {
            if(Thread.currentThread()!=owner) { throw new FdxException("File preparation callback escaped update thread"); }
            if(probe.source.length()!=65536 || !probe.source.isSeekable()) { throw new FdxException("Unexpected source metadata"); }
            if(phase==4) {
                FileHandle file=context.files().internal("streaming/message.txt");
                context.asyncFuture(() -> file.readString(StandardCharsets.UTF_8))
                        .onSuccess(text -> {
                            try {
                                if(!text.equals("libfdx asynchronous text\n")) { throw new FdxException("Async text mismatch"); }
                                checkEmpty(context,result,probe);
                            } catch(RuntimeException | Error error) { result.completeExceptionally(error); }
                        }).onFailure(result::completeExceptionally);
                return;
            }
            long offset=phase==0?0:phase==1?65529:phase==2?123:65536;
            int count=phase==0?4096:phase==1?8:phase==2?17:1;
            int expected=phase==1?7:phase==3?-1:count;
            context.asyncFuture(() -> probe.source.read(offset,probe.buffer,0,count)).onSuccess(actual -> {
                try {
                    if(actual!=expected) { throw new FdxException("Unexpected range count: "+actual+" phase="+phase); }
                    for(int i=0;i<actual;i++) {
                        if((probe.buffer[i]&255)!=(((offset+i)*31+7)&255)) { throw new FdxException("Range data mismatch"); }
                    }
                    read(context,result,probe,phase+1);
                } catch(RuntimeException | Error error) { result.completeExceptionally(error); }
            }).onFailure(result::completeExceptionally);
        } catch(RuntimeException | Error error) { result.completeExceptionally(error); }
    }

    private void checkEmpty(AssetLoadContext context,FdxFuture<Probe> result,Probe probe) {
        FileHandle file=context.files().internal("streaming/empty.bin");
        context.asyncFuture(() -> file.openRead(32)).onSuccess(empty -> {
            try {
                context.asyncFuture(() -> empty.read(0,probe.buffer,0,1)).onSuccess(actual -> {
                    try {
                        if(empty.length()!=0 || actual!=-1) { throw new FdxException("Empty input mismatch"); }
                        checkCancellation(context,result,probe);
                    } catch(RuntimeException | Error error) { result.completeExceptionally(error); }
                    finally { empty.dispose(); }
                }).onFailure(error -> { empty.dispose(); result.completeExceptionally(error); });
            } catch(RuntimeException | Error error) { empty.dispose(); result.completeExceptionally(error); }
        }).onFailure(result::completeExceptionally);
    }
    private void checkCancellation(AssetLoadContext context,FdxFuture<Probe> result,Probe probe) {
        FileHandle file=context.files().internal("streaming/bytes.bin");
        context.asyncFuture(() -> file.openRead(32)).onSuccess(cancel -> {
            try {
                Arrays.fill(probe.cancelBuffer,(byte)91);
                FdxFuture<Integer> pending=cancel.read(300,probe.cancelBuffer,0,32);
                probe.cancelledPending=!pending.isDone(); cancel.dispose();
                if(probe.cancelledPending && !pending.isFailed()) { throw new FdxException("Close did not cancel pending read"); }
                result.complete(probe);
            } catch(RuntimeException | Error error) { cancel.dispose(); result.completeExceptionally(error); }
        }).onFailure(result::completeExceptionally);
    }

    @Override public void render() {
        assets.update(2,1_000_000);
        if(assets.lastUpdateTaskCount()>2) { throw new FdxException("Read completion exceeded task budget"); }
        if(lease.future().isFailed()) { lease.future().get(); }
        if(lease.isLoaded()) {
            probe=lease.asset();
            if(probe.cancelledPending) {
                for(byte value:probe.cancelBuffer) { if(value!=91) { throw new FdxException("Disposed read wrote to reused destination"); } }
            }
            if(!reported) {
                logger.info("FileStreamingTest complete: boundedReads=4096,seek=true,eof=true,text=true,empty=true,asyncCancellation="
                        +probe.cancelledPending+",workerExecutor="+(executor!=null)); reported=true;
            }
        }
        graphics.clear(.03f,reported?.5f:.08f,.14f,1); finishFrame();
    }
    @Override public void dispose() {
        boolean failed=lease!=null && lease.future().isFailed();
        dispose(assets); dispose(executor);
        if(failed) { return; } // Preserve the originating read failure during backend shutdown.
        if(requiresCompletion() && (!reported || probe==null || !probe.source.isDisposed())) {
            throw new FdxException("File streaming fixture did not finish and close its source");
        }
        verifyDisposed();
    }
    private static final class Probe implements Disposable {
        final FileDataSource source;
        final byte[] buffer=new byte[4096], cancelBuffer=new byte[32];
        boolean cancelledPending;
        Probe(FileDataSource source) { this.source=source; }
        @Override public void dispose() { source.dispose(); }
        @Override public boolean isDisposed() { return source.isDisposed(); }
    }
}
