package io.github.libfdx.audio;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;

/**
 * Application-owned gain routing with fixed bookkeeping bounded by the borrowed
 * audio service's capacities. All methods run on its application thread. Sounds
 * and registered music are borrowed; dispose stops managed playback without
 * disposing those assets. Direct controls remain available through their handles.
 * Call update(elapsedSeconds) to advance fades; the backend still pumps audio.
 */
public final class AudioMixer implements Disposable {
    private final Audio audio;
    private final long[] voices;
    private final AudioBus[] routes;
    private final float[] voiceGains, pitches, pans;
    private final Music[] tracks;
    private final float[] trackGains, weights;
    private final float[] buses = {1,1,1};
    private float master = 1;
    private int from = -1, to = -1;
    private float fromWeight, toWeight;
    private double duration, elapsed;
    private boolean disposed;

    public AudioMixer(Audio audio) {
        if (audio == null || audio.isDisposed()) { throw new IllegalArgumentException("Live audio required"); }
        this.audio = audio;
        int capacity = audio.maxVoices(); voices = new long[capacity]; routes = new AudioBus[capacity];
        voiceGains = new float[capacity]; pitches = new float[capacity]; pans = new float[capacity];
        tracks = new Music[audio.maxMusicStreams()]; trackGains = new float[tracks.length]; weights = new float[tracks.length];
    }
    private void check() {
        if (disposed || audio.isDisposed()) { throw new FdxException("Mixer or its audio service is disposed"); }
    }
    private static void gainValue(float gain) {
        if (!Float.isFinite(gain) || gain < 0 || gain > 1) { throw new IllegalArgumentException("Gain must be [0,1]"); }
    }
    public float masterGain() { return master; }
    public AudioMixer masterGain(float gain) { check(); gainValue(gain); master = gain; apply(); return this; }
    public float busGain(AudioBus bus) { return buses[bus.ordinal()]; }
    public AudioMixer busGain(AudioBus bus,float gain) {
        check(); gainValue(gain); buses[bus.ordinal()] = gain; apply(); return this;
    }
    /** Starts a managed voice; saturation returns NO_VOICE without stealing. */
    public long play(AudioBus bus,Sound sound,float gain,float pitch,float pan,boolean loop) {
        check(); gainValue(gain);
        if (bus == null) { throw new IllegalArgumentException("Bus required"); }
        reap();
        for (int i = 0; i < voices.length; i++) {
            if (voices[i] != Audio.NO_VOICE) { continue; }
            long voice = audio.play(sound,gain * master * buses[bus.ordinal()],pitch,pan,loop);
            if (voice != Audio.NO_VOICE) {
                voices[i] = voice; routes[i] = bus; voiceGains[i] = gain; pitches[i] = pitch; pans[i] = pan;
            }
            return voice;
        }
        return Audio.NO_VOICE;
    }
    /** Borrows a music instance in this domain and records its independent base gain. */
    public AudioMixer music(Music music,float gain) {
        check(); gainValue(gain);
        if (music == null || music.isDisposed() || music.failure() != null || music.audio() != audio) {
            throw new IllegalArgumentException("Live music in the mixer's audio domain required");
        }
        reap(); int index = index(music);
        if (index < 0) { for (int i = 0; i < tracks.length; i++) { if (tracks[i] == null) { index = i; break; } } }
        if (index < 0) { throw new FdxException("Mixer music capacity exhausted"); }
        if (tracks[index] == null) { weights[index] = 1; }
        tracks[index] = music; trackGains[index] = gain; applyTrack(index); return this;
    }
    /** Stops tracking a music instance, leaving its current playback and gain intact. */
    public void remove(Music music) {
        check(); int index = index(music);
        if (index < 0) { return; }
        cancelFade(index); tracks[index] = null;
    }
    /**
     * Starts/resumes both registered tracks and linearly fades their gain weights.
     * Caller time controls progress, independently of rendering cadence. At the end,
     * the outgoing track is paused; both remain borrowed. One fade is active at a time;
     * a replacement starts at the current weights. Zero seconds completes immediately.
     */
    public void crossfade(Music outgoing,Music incoming,double seconds) {
        check();
        if (!Double.isFinite(seconds) || seconds < 0 || outgoing == incoming) { throw new IllegalArgumentException("Invalid crossfade"); }
        reap(); int a = index(outgoing), b = index(incoming);
        if (a < 0 || b < 0) { throw new IllegalArgumentException("Register both tracks before crossfading"); }
        boolean replacing = from >= 0;
        fromWeight = weights[a]; toWeight = replacing ? weights[b] : 0;
        from = a; to = b; duration = seconds; elapsed = 0; weights[b] = toWeight;
        applyTrack(b); outgoing.play(); incoming.play(); update(0);
    }
    /** Finite nonnegative application elapsed time. No per-update Java storage is allocated. */
    public void update(double seconds) {
        check();
        if (!Double.isFinite(seconds) || seconds < 0) { throw new IllegalArgumentException("Invalid elapsed seconds"); }
        reap();
        if (from < 0) { return; }
        elapsed = Math.min(duration,elapsed + seconds);
        float t = duration == 0 ? 1 : (float)(elapsed / duration);
        weights[from] = fromWeight * (1-t); weights[to] = toWeight + (1-toWeight)*t;
        applyTrack(from); applyTrack(to);
        if (t == 1) { tracks[from].pause(); from = to = -1; }
    }
    private int index(Music music) {
        if (music != null) { for (int i = 0; i < tracks.length; i++) { if (tracks[i] == music) { return i; } } }
        return -1;
    }
    private void cancelFade(int index) { if (index == from || index == to) { from = to = -1; } }
    private void reap() {
        for (int i = 0; i < voices.length; i++) {
            if (voices[i] != Audio.NO_VOICE && audio.state(voices[i]) == VoiceState.STOPPED) { voices[i] = Audio.NO_VOICE; }
        }
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i] != null && (tracks[i].isDisposed() || tracks[i].failure() != null)) {
                cancelFade(i); tracks[i] = null;
            }
        }
    }
    private void applyTrack(int index) {
        tracks[index].gain(trackGains[index] * weights[index] * master * buses[AudioBus.MUSIC.ordinal()]);
    }
    private void apply() {
        reap();
        for (int i = 0; i < voices.length; i++) {
            if (voices[i] != Audio.NO_VOICE) {
                if (!audio.parameters(voices[i],voiceGains[i] * master * buses[routes[i].ordinal()],pitches[i],pans[i])) {
                    voices[i] = Audio.NO_VOICE;
                }
            }
        }
        for (int i = 0; i < tracks.length; i++) { if (tracks[i] != null) { applyTrack(i); } }
    }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) { return; }
        disposed = true; Throwable error = null;
        if (!audio.isDisposed()) {
            for (long voice : voices) {
                if (voice == Audio.NO_VOICE) { continue; }
                try { audio.stop(voice); } catch (RuntimeException | Error next) { error = append(error,next); }
            }
            for (Music track : tracks) {
                if (track == null || track.isDisposed() || track.failure() != null) { continue; }
                try { track.stop(); } catch (RuntimeException | Error next) { error = append(error,next); }
            }
        }
        java.util.Arrays.fill(tracks,null); java.util.Arrays.fill(voices,Audio.NO_VOICE);
        if (error instanceof RuntimeException runtime) { throw runtime; }
        if (error instanceof Error fatal) { throw fatal; }
    }
    private static Throwable append(Throwable first,Throwable next) {
        if (first == null) { return next; } if (first != next) { first.addSuppressed(next); } return first;
    }
}
