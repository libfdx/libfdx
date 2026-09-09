package io.github.libfdx.audio;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BufferedMusicTest {
    private static final MusicBuffering SMALL = new MusicBuffering(4, 3);

    @Test void boundedQueuePlaysWholeTrackAndSeekWhileStoppedRetainsItsPosition() {
        Device audio = new Device(); Input source = new Input(15, true);
        Output music = (Output) audio.createMusic(source, SMALL);
        music.play(); pump(audio, 10);
        assertEquals(12, music.queuedFrames()); assertEquals(3, source.reads);
        music.consume(6); audio.update(); assertEquals(6, music.positionFrames());
        music.consume(6); pump(audio, 3); music.consume(3); audio.update();
        assertEquals(MusicState.ENDED, music.state()); assertEquals(15, music.positionFrames());
        assertEquals(List.of(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14), music.heard);
        music.stop().seek(9).play(); pump(audio, 8); music.consume(6); audio.update();
        assertEquals(List.of(9,10,11,12,13,14), music.heard.subList(15,21));
        audio.dispose(); assertTrue(source.isDisposed()); assertTrue(music.isDisposed());
    }
    @Test void halfOpenLoopsHaveNoRepeatedBoundarySamplesAndSuspendPreservesPause() {
        Device audio = new Device(); Output music = (Output) audio.createMusic(new Input(20,true),SMALL);
        music.loop(3,8).play(); pump(audio,8);
        music.consume(7); pump(audio,4); music.consume(5); pump(audio,4);
        assertEquals(List.of(3,4,5,6,7,3,4,5,6,7,3,4), music.heard);
        music.pause(); long position = music.positionFrames();
        audio.suspend(); audio.resume().get(); pump(audio,3); music.consume(4);
        assertEquals(MusicState.PAUSED,music.state()); assertEquals(position,music.positionFrames());
        music.play(); audio.update(); assertEquals(MusicState.PLAYING,music.state());
        audio.suspend(); music.consume(4); assertEquals(position,music.positionFrames());
        audio.resume().get(); audio.update(); music.consume(1); audio.update();
        assertEquals(position+1,music.positionFrames()); audio.dispose();
    }
    @Test void pendingReadCannotPublishOldSamplesAfterSeekAndUnderrunsRecoverWithoutSkipping() {
        Device audio = new Device(); Input input = new Input(100,true); input.delayed = true;
        Output music = (Output) audio.createMusic(input,SMALL); music.play(); audio.update();
        music.seek(40); input.complete(); audio.update();
        assertEquals(0,music.queuedFrames()); input.complete(); audio.update();
        input.complete(); audio.update(); music.consume(8); audio.update();
        assertEquals(MusicState.BUFFERING,music.state()); assertEquals(1,music.underruns());
        assertEquals(48,music.positionFrames()); music.consume(20); audio.update();
        assertEquals(1,music.underruns());
        input.complete(); audio.update(); input.complete(); audio.update();
        music.consume(3); audio.update(); assertEquals(51,music.positionFrames());
        assertEquals(List.of(40,41,42,43,44,45,46,47,48,49,50),music.heard);
        audio.dispose(); input.complete(); assertTrue(input.isDisposed());
    }
    @Test void failureAndCapacityKeepOwnershipLocalAndSequentialRestartIsExplicit() {
        Device audio = new Device(); Input a = new Input(40,true), b = new Input(40,true);
        Output first = (Output)audio.createMusic(a,SMALL), second = (Output)audio.createMusic(b,SMALL);
        first.play(); second.play(); a.delayed = true; audio.update();
        a.pending.completeExceptionally(new FdxException("I/O failed")); pump(audio,8);
        assertEquals(MusicState.FAILED,first.state()); assertTrue(a.isDisposed());
        assertEquals(MusicState.PLAYING,second.state()); assertNotNull(first.failure());
        audio.createMusic(new Input(10,true),SMALL); audio.createMusic(new Input(10,true),SMALL);
        Input rejected = new Input(10,true);
        assertThrows(FdxException.class,()->audio.createMusic(rejected,SMALL)); assertFalse(rejected.isDisposed());
        first.dispose(); Input sequential = new Input(10,false);
        Music replacement = audio.createMusic(sequential,SMALL).play(); audio.update(); replacement.stop();
        assertThrows(FdxException.class,replacement::play);
        assertThrows(FdxException.class,()->replacement.seek(0));
        audio.dispose(); assertTrue(b.isDisposed()); assertTrue(sequential.isDisposed());
    }
    @Test void shortUnknownTrackStartsWithoutTwoBuffersAndTruncatedKnownInputFails() {
        Device audio = new Device(); Input unknown = new Input(3,false); unknown.unknown = true;
        Output music = (Output)audio.createMusic(unknown,SMALL); music.play(); pump(audio,8);
        assertEquals(MusicState.PLAYING,music.state()); music.consume(3); audio.update();
        assertEquals(MusicState.ENDED,music.state()); assertEquals(3,music.positionFrames());
        Input truncated = new Input(3,true); truncated.advertised = 20;
        Music failed = audio.createMusic(truncated,SMALL).play(); pump(audio,8);
        assertEquals(MusicState.FAILED,failed.state()); assertTrue(truncated.isDisposed()); audio.dispose();
    }
    @Test void mixerMultipliesBusAndMasterGainWithoutChangingPitchOrTouchingRecycledVoices() {
        Device audio = new Device(); AudioMixer mixer = new AudioMixer(audio);
        Sound sound = audio.createSound(new PcmData(1,8000,new short[]{1}));
        mixer.masterGain(.5f).busGain(AudioBus.SFX,.4f).busGain(AudioBus.UI,.8f);
        long voice = mixer.play(AudioBus.SFX,sound,.5f,2,-.7f,true);
        assertArrayEquals(new float[]{.1f,2,-.7f},audio.voiceParameters,0.00001f);
        assertEquals(Audio.NO_VOICE,mixer.play(AudioBus.UI,sound,1,1,0,false));
        audio.stop(voice); long replacement = audio.play(sound,.9f,1,0,false);
        mixer.masterGain(.25f);
        assertArrayEquals(new float[]{.9f,1,0},audio.voiceParameters,0.00001f);
        assertEquals(VoiceState.PLAYING,audio.state(replacement)); audio.stop(replacement);
        mixer.play(AudioBus.UI,sound,.5f,1,0,true);
        assertEquals(.1f,audio.voiceParameters[0],0.00001f);
        mixer.dispose(); assertFalse(sound.isDisposed()); assertEquals(0,audio.activeVoices()); audio.dispose();
    }
    @Test void mixerCrossfadeIsCadenceIndependentAndBorrowsBothTracks() {
        Device audio = new Device(); AudioMixer mixer = new AudioMixer(audio);
        Output a = (Output)audio.createMusic(new Input(100,true),SMALL);
        Output b = (Output)audio.createMusic(new Input(100,true),SMALL);
        mixer.music(a,.8f).music(b,.4f).masterGain(.5f).busGain(AudioBus.MUSIC,.5f);
        a.play(); mixer.crossfade(a,b,2); pump(audio,5);
        assertEquals(.2f,a.outputGain,0.00001f); assertEquals(0,b.outputGain);
        for(int i=0;i<60;i++) mixer.update(1.0/60);
        assertEquals(.1f,a.outputGain,0.00001f); assertEquals(.05f,b.outputGain,0.00001f);
        mixer.update(1);
        assertEquals(MusicState.PAUSED,a.state()); assertEquals(0,a.outputGain);
        assertEquals(.1f,b.outputGain,0.00001f); assertFalse(a.isDisposed());
        mixer.crossfade(b,a,0); assertEquals(MusicState.PAUSED,b.state());
        assertEquals(.2f,a.outputGain,0.00001f);
        mixer.dispose(); assertFalse(a.isDisposed()); assertFalse(b.isDisposed());
        assertEquals(MusicState.STOPPED,a.state()); audio.dispose();
    }
    private static void pump(Device audio,int count) { for(int i=0;i<count;i++) audio.update(); }
    private static final class Input implements PcmStream {
        final int length; final boolean seekable; long advertised; boolean unknown, delayed, disposed;
        int reads, count, destinationOffset; long offset; short[] destination; FdxFuture<Integer> pending;
        Input(int length,boolean seekable) { this.length=length; advertised=length; this.seekable=seekable; }
        @Override public int channels() { return 1; }
        @Override public int sampleRate() { return 8000; }
        @Override public long frames() { return unknown ? -1 : advertised; }
        @Override public int maxReadFrames() { return 4; }
        @Override public boolean isSeekable() { return seekable; }
        @Override public FdxFuture<Integer> read(long offset,short[] destination,int start,int count) {
            assertFalse(disposed); assertTrue(pending==null || pending.isDone());
            this.offset=offset; this.destination=destination; destinationOffset=start; this.count=count; reads++;
            pending=FdxFuture.pending(); FdxFuture<Integer> result=pending; if(!delayed) complete(); return result;
        }
        void complete() {
            if(disposed || pending==null || pending.isDone()) return;
            int actual=(int)Math.min(count,Math.max(0,length-offset));
            for(int i=0;i<actual;i++) destination[destinationOffset+i]=(short)(offset+i);
            pending.complete(actual==0 ? -1 : actual);
        }
        @Override public void update() { }
        @Override public void dispose() {
            disposed=true;
            if(pending!=null && !pending.isDone()) pending.completeExceptionally(new FdxException("Closed"));
        }
        @Override public boolean isDisposed() { return disposed; }
    }
    private static final class Output extends BufferedMusic {
        final ArrayDeque<short[]> queue=new ArrayDeque<>(); final List<Integer> heard=new ArrayList<>();
        int offset, finished; boolean playing; float outputGain = 1;
        Output(Device owner,PcmStream source,MusicBuffering buffering) { super(owner,source,buffering); }
        void consume(int frames) {
            while(playing && frames-- > 0 && !queue.isEmpty()) {
                short[] block=queue.peek(); heard.add((int)block[offset++]);
                if(offset==block.length) { queue.remove(); offset=0; finished++; }
            }
            if(queue.isEmpty()) playing=false;
        }
        @Override protected void enqueue(short[] samples,int frames) { queue.add(java.util.Arrays.copyOf(samples,frames)); }
        @Override protected int processed() { int result=finished; finished=0; return result; }
        @Override protected int sampleOffset() { return offset; }
        @Override protected void startPlayback() { playing=true; }
        @Override protected void pausePlayback() { playing=false; }
        @Override protected void clearPlayback() { queue.clear(); offset=finished=0; playing=false; }
        @Override protected void parameters(float gain,float pan) { outputGain = gain; }
        @Override protected void closePlayback() { clearPlayback(); }
    }
    private static final class Device extends PooledAudio {
        final float[] voiceParameters = new float[3];
        Device() { super(1); }
        @Override protected boolean supportsMusic() { return true; }
        @Override protected BufferedMusic openMusic(PcmStream source,MusicBuffering buffering) { return new Output(this,source,buffering); }
        @Override public ProviderId providerId() { return ProviderId.of("music_test"); }
        @Override public <T> T as() { throw new UnsupportedOperationException(); }
        @Override protected void checkDevice() { }
        @Override protected Object upload(PcmData pcm) { return pcm; }
        @Override protected void release(Object resource) { }
        @Override protected void start(int slot,Object resource,float gain,float pitch,float pan,boolean loop,boolean paused) {
            parametersSlot(slot,gain,pitch,pan);
        }
        @Override protected void stopSlot(int slot) { }
        @Override protected void pauseSlot(int slot) { }
        @Override protected void resumeSlot(int slot) { }
        @Override protected void parametersSlot(int slot,float gain,float pitch,float pan) {
            voiceParameters[0]=gain; voiceParameters[1]=pitch; voiceParameters[2]=pan;
        }
        @Override protected boolean finished(int slot) { return false; }
        @Override protected FdxFuture<Void> activate() { return FdxFuture.completed(null); }
        @Override protected boolean platformSuspended() { return false; }
        @Override protected void closeDevice() { }
    }
}
