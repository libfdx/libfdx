package io.github.libfdx.audio.loaders;

import io.github.libfdx.assets.*;
import io.github.libfdx.audio.*;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.files.FileDataSource;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AudioAssetLoadersTest {
    @Test
    void musicOpeningFinalizationCancellationAndFailureCloseExactlyOneOwnedInput() {
        // Cancellation before opening, after delivery but before creation, failed creation, and successful scope release.
        for (int phase=0; phase<4; phase++) {
            int selected=phase; int[] closed={0}, created={0}; PcmStream[] adopted={null};
            byte[] wav=WavDecoderTest.wav(1,16,new byte[]{0,-128,0,127});
            FileDataSource source=proxy(FileDataSource.class,(name,args)->switch(name) {
                case "isDisposed" -> closed[0]!=0;
                case "dispose" -> { closed[0]++; yield null; }
                case "isSeekable" -> true;
                case "length" -> (long)wav.length;
                case "maxReadBytes" -> 4096;
                case "read" -> {
                    int offset=((Long)args[0]).intValue(); int count=Math.min((int)args[3],wav.length-offset);
                    System.arraycopy(wav,offset,(byte[])args[1],(int)args[2],count); yield FdxFuture.completed(count==0 ? -1 : count);
                }
                default -> throw new AssertionError(name);
            });
            FdxFuture<FileDataSource> opening=FdxFuture.pending();
            FileHandle file=proxy(FileHandle.class,(name,args)->opening);
            FileSystem files=proxy(FileSystem.class,(name,args)->file);
            Music music=proxy(Music.class,(name,args)->switch(name) {
                case "dispose" -> { adopted[0].dispose(); yield null; }
                case "isDisposed" -> adopted[0].isDisposed();
                default -> throw new AssertionError(name);
            });
            Thread owner=Thread.currentThread();
            Audio audio=proxy(Audio.class,(name,args)->switch(name) {
                case "isDisposed" -> false;
                case "maxMusicStreams" -> 4;
                case "createMusic" -> {
                    assertSame(owner,Thread.currentThread()); created[0]++;
                    if(selected==2) throw new IllegalStateException("Native creation failed");
                    adopted[0]=(PcmStream)args[0]; yield music;
                }
                default -> throw new AssertionError(name);
            });
            DefaultAssetManager assets=new DefaultAssetManager(files);
            AudioAssetLoaders.registerMusic(assets,audio,new MusicBuffering(4,3),null);
            AssetLease<Music> lease=assets.acquire(AssetDescriptor.of("music.wav",Music.class));
            assets.update(1,Long.MAX_VALUE);
            if(phase==0) lease.dispose();
            opening.complete(source);
            assertEquals(0,created[0]);
            if(phase==1) { assets.update(1,Long.MAX_VALUE); lease.dispose(); }
            for(int i=0;i<20;i++) assets.update(1,Long.MAX_VALUE);
            if(phase==3) { assertSame(music,lease.asset()); assertEquals(0,closed[0]); lease.dispose(); }
            if(phase==2) assertTrue(lease.future().isFailed());
            assets.dispose(); assertEquals(1,closed[0],"phase "+phase); assertEquals(phase<2 ? 0 : 1,created[0]);
        }
    }
    @Test
    void asyncPcmDependencyPrecedesBudgetedDeviceUploadAndScopesShareIt() {
        FdxFuture<byte[]> bytes = FdxFuture.pending();
        Thread appThread = Thread.currentThread();
        int[] uploads = {0}, releases = {0};
        FileHandle file = proxy(FileHandle.class, (name, args) -> bytes);
        FileSystem files = proxy(FileSystem.class, (name, args) -> file);
        Sound sound = proxy(Sound.class, (name, args) -> switch (name) {
            case "dispose" -> { releases[0]++; yield null; }
            case "isDisposed" -> releases[0] != 0;
            default -> throw new AssertionError(name);
        });
        Audio audio = proxy(Audio.class, (name, args) -> switch (name) {
            case "isDisposed" -> false;
            case "createSound" -> {
                assertSame(appThread, Thread.currentThread());
                assertEquals(-32768, ((PcmData) args[0]).sample(0, 0));
                uploads[0]++; yield sound;
            }
            default -> throw new AssertionError(name);
        });
        DefaultAssetManager assets = new DefaultAssetManager(files);
        AudioAssetLoaders.register(assets, audio);
        try {
            AssetScope a = assets.createScope(), b = assets.createScope();
            AssetLease<Sound> first = a.load(AssetDescriptor.of("tone.wav", Sound.class));
            AssetLease<Sound> second = b.load(AssetDescriptor.of("tone.wav", Sound.class));
            assertFalse(assets.update()); assertEquals(0, uploads[0]);
            bytes.complete(WavDecoderTest.wav(1, 16, new byte[] {0, -128}));
            assertEquals(0, uploads[0]);
            for (int i = 0; i < 100 && !assets.update(1, Long.MAX_VALUE); i++) assertEquals(1, assets.lastUpdateTaskCount());
            assertTrue(first.isLoaded()); assertSame(first.asset(), second.asset()); assertEquals(1, uploads[0]);
            a.dispose(); assertEquals(0, releases[0]); assertNotNull(assets.find("tone.wav", PcmData.class));
            b.dispose(); assertEquals(1, releases[0]); assertNull(assets.find("tone.wav", PcmData.class));
        } finally { assets.dispose(); }
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> call.run(method.getName(), args));
    }
    private interface Call { Object run(String name, Object[] args); }
}
