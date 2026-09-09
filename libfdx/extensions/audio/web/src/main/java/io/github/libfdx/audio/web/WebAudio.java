package io.github.libfdx.audio.web;

import io.github.libfdx.audio.PcmData;
import io.github.libfdx.audio.PooledAudio;
import io.github.libfdx.audio.BufferedMusic;
import io.github.libfdx.audio.MusicBuffering;
import io.github.libfdx.audio.PcmStream;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Float32Array;

/**
 * Browser playback. Output remains suspended until gesture activation succeeds.
 * Browser source nodes allocate at play/resume; Java voice state stays bounded.
 * Pause reconstructs a one-shot source at its saved position. All methods are
 * confined to the browser main application thread.
 */
public final class WebAudio extends PooledAudio {
    /** Logical provider identity. */
    public static final ProviderId ID = ProviderId.of("web_audio");
    private final JSObject engine;
    WebAudio(int maxVoices) { super(maxVoices); engine = open(maxVoices); }
    @Override public ProviderId providerId() { return ID; }
    /** Returns this live provider view. */
    @SuppressWarnings("unchecked") @Override public <T> T as() { checkLive(); return (T) this; }
    @Override protected void checkDevice() { }
    @Override protected Object upload(PcmData pcm) {
        JSObject buffer = createBuffer(engine, pcm.channels(), pcm.frames(), pcm.sampleRate());
        for (int c = 0; c < pcm.channels(); c++) {
            Float32Array data = channel(buffer, c);
            for (int f = 0; f < pcm.frames(); f++) data.set(f, pcm.sample(f, c) / 32768f);
        }
        return buffer;
    }
    @Override protected void release(Object resource) { /* Detached AudioBuffers are garbage-collected. */ }
    @Override protected void start(int slot, Object resource, float gain, float pitch, float pan, boolean loop, boolean paused) {
        try {
            configure(engine, slot, (JSObject) resource, pitch, loop);
            parametersSlot(slot, gain, pitch, pan);
            if (!paused) resumeSlot(slot);
        } catch (RuntimeException | Error error) { stopSlot(slot); throw error; }
    }
    @Override protected void stopSlot(int slot) { stop(engine, slot, true); }
    @Override protected void pauseSlot(int slot) { stop(engine, slot, false); }
    @Override protected void resumeSlot(int slot) { play(engine, slot); }
    @Override protected void parametersSlot(int slot, float gain, float pitch, float pan) { params(engine, slot, gain, pitch, pan); }
    @Override protected boolean finished(int slot) { return ended(engine, slot); }
    @Override protected boolean platformSuspended() { return suspended(engine); }
    @Override protected FdxFuture<Void> activate() {
        FdxFuture<Void> result = FdxFuture.pending();
        activate(engine, () -> result.complete(null), message -> result.completeExceptionally(new FdxException(message)));
        return result;
    }
    @Override protected void closeDevice() { close(engine); }
    @Override protected boolean supportsMusic() { return true; }
    @Override protected BufferedMusic openMusic(PcmStream source, MusicBuffering buffering) {
        return new BrowserMusic(source,buffering);
    }
    private final class BrowserMusic extends BufferedMusic {
        private final JSObject playback;
        BrowserMusic(PcmStream source, MusicBuffering buffering) {
            super(WebAudio.this,source,buffering);
            playback = WebMusicQueue.create(engine,buffering.buffers(),source.channels(),source.sampleRate());
        }
        @Override protected void enqueue(short[] samples,int frames) {
            JSObject buffer = createBuffer(engine,channels(),frames,sampleRate());
            for (int c = 0; c < channels(); c++) {
                Float32Array data = channel(buffer,c);
                for (int f = 0; f < frames; f++) { data.set(f,samples[f * channels() + c] / 32768f); }
            }
            WebMusicQueue.enqueue(playback,buffer);
        }
        @Override protected int processed() { return WebMusicQueue.processed(playback); }
        @Override protected int sampleOffset() { return WebMusicQueue.offset(playback); }
        @Override protected void startPlayback() { WebMusicQueue.play(playback); }
        @Override protected void pausePlayback() { WebMusicQueue.pause(playback); }
        @Override protected void clearPlayback() { WebMusicQueue.clear(playback); }
        @Override protected void parameters(float gain,float pan) { WebMusicQueue.parameters(playback,gain,pan); }
        @Override protected void closePlayback() { WebMusicQueue.close(playback); }
    }
    @JSFunctor private interface Success extends JSObject { void run(); }
    @JSFunctor private interface Failure extends JSObject { void run(String message); }

    @JSBody(params = "count", script = """
        if (!globalThis.AudioContext) throw new Error('Web Audio is unavailable');
        var ctx = new AudioContext(), slots = [];
        try {
            for (var i = 0; i < count; i++) {
                var split = ctx.createChannelSplitter(2), merge = ctx.createChannelMerger(2);
                var left = ctx.createGain(), right = ctx.createGain();
                split.connect(left, 0); split.connect(right, 1);
                left.connect(merge, 0, 0); right.connect(merge, 0, 1); merge.connect(ctx.destination);
                slots.push({split:split, merge:merge, left:left, right:right, buffer:null, source:null, offset:0, time:0, rate:1, loop:false});
            }
        } catch (error) { ctx.close(); throw error; }
        return {ctx:ctx, slots:slots};
        """)
    private static native JSObject open(int count);
    @JSBody(params = "e", script = """
        if (e.ctx.state === 'closed') throw new Error('Browser audio context is closed');
        return e.ctx.state !== 'running';
        """)
    private static native boolean suspended(JSObject e);
    @JSBody(params = {"e", "ok", "fail"}, script = "e.ctx.resume().then(function() { ok(); }, function(error) { fail(String(error)); });")
    private static native void activate(JSObject e, Success ok, Failure fail);
    @JSBody(params = {"e", "channels", "frames", "rate"}, script = "return e.ctx.createBuffer(channels, frames, rate);")
    private static native JSObject createBuffer(JSObject e, int channels, int frames, int rate);
    @JSBody(params = {"buffer", "index"}, script = "return buffer.getChannelData(index);")
    private static native Float32Array channel(JSObject buffer, int index);
    @JSBody(params = {"e", "index", "buffer", "pitch", "loop"}, script = """
        if (e.ctx.state === 'closed') throw new Error('Browser audio context is closed');
        var s = e.slots[index]; s.buffer = buffer; s.offset = 0; s.rate = pitch; s.loop = loop;
        """)
    private static native void configure(JSObject e, int index, JSObject buffer, float pitch, boolean loop);
    @JSBody(params = {"e", "index"}, script = """
        var s = e.slots[index];
        if (s.source || !s.buffer) return;
        var source = e.ctx.createBufferSource(); source.buffer = s.buffer;
        source.playbackRate.value = s.rate; source.loop = s.loop;
        if (s.buffer.numberOfChannels === 1) {
            source.connect(s.left); source.connect(s.right);
        } else source.connect(s.split);
        s.time = e.ctx.currentTime;
        var offset = s.loop ? s.offset % s.buffer.duration : Math.min(s.offset, s.buffer.duration);
        source.start(0, offset); s.source = source;
        """)
    private static native void play(JSObject e, int index);
    @JSBody(params = {"e", "index", "release"}, script = """
        var s = e.slots[index];
        if (s.source) {
            s.offset += (e.ctx.currentTime - s.time) * s.rate;
            s.source.stop(); s.source.disconnect(); s.source = null;
        }
        if (release) { s.buffer = null; s.offset = 0; }
        """)
    private static native void stop(JSObject e, int index, boolean release);
    @JSBody(params = {"e", "index", "gain", "pitch", "pan"}, script = """
        var s = e.slots[index], stereo = s.buffer.numberOfChannels === 2;
        if (s.source) {
            s.offset += (e.ctx.currentTime - s.time) * s.rate; s.time = e.ctx.currentTime;
            s.source.playbackRate.value = pitch;
        }
        s.rate = pitch;
        s.left.gain.value = gain * (stereo ? Math.min(1, 1-pan) : Math.cos((pan+1)*Math.PI/4));
        s.right.gain.value = gain * (stereo ? Math.min(1, 1+pan) : Math.sin((pan+1)*Math.PI/4));
        """)
    private static native void params(JSObject e, int index, float gain, float pitch, float pan);
    @JSBody(params = {"e", "index"}, script = """
        var s = e.slots[index];
        return s.source !== null && !s.loop && s.offset + (e.ctx.currentTime-s.time)*s.rate >= s.buffer.duration;
        """)
    private static native boolean ended(JSObject e, int index);
    @JSBody(params = "e", script = """
        for (var i = 0; i < e.slots.length; i++) {
            var s = e.slots[i];
            if (s.source) { s.source.stop(); s.source.disconnect(); }
            s.split.disconnect(); s.left.disconnect(); s.right.disconnect(); s.merge.disconnect();
            s.buffer = s.source = null;
        }
        if (e.ctx.state !== 'closed') e.ctx.close().catch(function(error) { console.error('Audio close failed', error); });
        """)
    private static native void close(JSObject e);
}
