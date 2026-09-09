package io.github.libfdx.audio.openal;

import io.github.libfdx.audio.*;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

final class OpenALMusicTest {
    @Test void nativeStreamRemainsBoundedSeeksLoopsAndRecoversFromAnUnderrun() {
        OpenALAudio audio=OpenALAudio.loopback(32,48000);
        try {
            Tone input=new Tone(); Music music=audio.createMusic(input,new MusicBuffering(1024,4));
            music.loop(4800,14400).gain(.5f).pan(-1).play(); pump(audio,8);
            assertEquals(4096,music.queuedFrames()); assertEquals(4,input.reads);
            short[] block=new short[1024]; double energy=0;
            for(int i=0;i<50;i++) {
                audio.renderSamples(block,512); energy+=rms(block,0,0);
                assertTrue(rms(block,1,0)<5,"Hard-left music leaked right");
                assertTrue(music.queuedFrames()<=4096); assertNull(music.failure());
            }
            assertTrue(energy/50>1500); assertEquals(0,music.underruns());
            long position=music.positionFrames();
            assertTrue(position>=4800 && position<14400);
            music.pause(); audio.renderSamples(new short[4096],2048);
            audio.renderSamples(block,512); assertTrue(rms(block,0,0)<5);
            assertEquals(position,music.positionFrames());
            music.looping(false).seek(24000).pan(1).play(); pump(audio,8);
            audio.renderSamples(block,512); assertEquals(24512,music.positionFrames());
            assertTrue(rms(block,1,256)>1500);
            // A long render intentionally consumes more than the bounded queue.
            audio.renderSamples(new short[24000],12000);
            assertEquals(1,music.underruns()); long stalled=music.positionFrames();
            pump(audio,8); audio.renderSamples(block,512);
            assertEquals(stalled+512,music.positionFrames());
            assertTrue(rms(block,1,256)>1500); assertNull(music.failure());
            music.dispose(); assertTrue(input.isDisposed());
        } finally { audio.dispose(); }
    }
    @Test void twoMusicStreamsAndThirtyTwoEffectsShareOutputWithoutSharingCapacity() {
        OpenALAudio audio=OpenALAudio.loopback(32,48000);
        try {
            short[] pcm=new short[480]; java.util.Arrays.fill(pcm,(short)1000);
            Sound effect=audio.createSound(new PcmData(1,48000,pcm));
            for(int i=0;i<32;i++) assertNotEquals(0,audio.play(effect,.01f,1,0,true));
            Music a=audio.createMusic(new Tone(),new MusicBuffering(1024,4)).gain(.2f).pan(-1).play();
            Music b=audio.createMusic(new Tone(),new MusicBuffering(1024,4)).gain(.1f).pan(1).play();
            pump(audio,8); assertEquals(0,audio.play(effect));
            short[] block=new short[1024]; audio.renderSamples(block,512);
            assertTrue(rms(block,0,100)>rms(block,1,100)*1.6);
            audio.suspend(); audio.renderSamples(new short[4096],2048); audio.renderSamples(block,512);
            assertTrue(rms(block,0,0)<5); assertTrue(rms(block,1,0)<5);
            audio.resume().get(); audio.update(); audio.renderSamples(block,512);
            assertEquals(32,audio.activeVoices()); assertEquals(MusicState.PLAYING,a.state());
            assertEquals(MusicState.PLAYING,b.state()); assertTrue(rms(block,0,100)>500);
        } finally { audio.dispose(); }
    }
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void musicHardPanRemainsIsolatedWithHeadphoneVirtualization(int channels) {
        OpenALAudio audio = OpenALAudio.loopback(1, 48000);
        try {
            OpenALTestOutput.enableHeadphones();
            Music music = audio.createMusic(new Tone(channels), new MusicBuffering(1024, 4));
            music.gain(.5f).pan(-1).play();
            pump(audio, 8);
            short[] output = new short[2048];
            for (int i = 0; i < 2; i++) audio.renderSamples(output, 1024);
            assertTrue(rms(output, 0, 256) > 1000);
            assertTrue(rms(output, 1, 256) < 5, "Hard-left headphone music leaked");
            music.pan(1);
            for (int i = 0; i < 2; i++) audio.renderSamples(output, 1024);
            assertTrue(rms(output, 1, 256) > 1000);
            assertTrue(rms(output, 0, 256) < 5, "Hard-right headphone music leaked");
            music.dispose();
        } finally { audio.dispose(); }
    }
    private static void pump(OpenALAudio audio,int count) { for(int i=0;i<count;i++) audio.update(); }
    private static double rms(short[] pcm,int channel,int skip) {
        double sum=0; int count=0;
        for(int i=skip*2+channel;i<pcm.length;i+=2) { sum+=(double)pcm[i]*pcm[i]; count++; }
        return Math.sqrt(sum/count);
    }
    private static final class Tone implements PcmStream {
        int reads; boolean disposed;
        private final int channelCount;
        Tone() { this(1); }
        Tone(int channels) { channelCount = channels; }
        @Override public int channels() { return channelCount; }
        @Override public int sampleRate() { return 48000; }
        @Override public long frames() { return 480000; }
        @Override public int maxReadFrames() { return 4096; }
        @Override public boolean isSeekable() { return true; }
        @Override public FdxFuture<Integer> read(long offset,short[] destination,int start,int frames) {
            if(disposed) return FdxFuture.failed(new FdxException("Closed"));
            reads++; int actual=(int)Math.min(frames,frames()-offset);
            for(int i=0;i<actual;i++) {
                for(int channel=0;channel<channelCount;channel++) {
                    destination[start+i*channelCount+channel]=(short)(Math.sin((offset+i)*Math.PI*880/48000)*12000);
                }
            }
            return FdxFuture.completed(actual==0 ? -1 : actual);
        }
        @Override public void update() { }
        @Override public void dispose() { disposed=true; }
        @Override public boolean isDisposed() { return disposed; }
    }
}
