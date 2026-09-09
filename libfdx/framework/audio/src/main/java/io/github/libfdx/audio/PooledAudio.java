package io.github.libfdx.audio;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;

/**
 * Provider support for bounded voices and resource ownership. Hooks execute only on
 * the application thread. Providers retain native slot storage and must undo partially
 * failed uploads/starts. Voice controls reuse Java storage. Music allocates read
 * futures at block boundaries; each stream owns a fixed decoded/provider queue.
 */
public abstract class PooledAudio implements Audio {
    private static int nextDomain;
    private final long domain = allocateDomain();
    private final Voice[] voices;
    private long serial;
    private long activationRevision;
    private boolean suspended;
    private boolean disposed;
    private DeviceSound sounds;
    private final BufferedMusic[] music = new BufferedMusic[4];

    /** Providers opt in when their stream implementation is available. */
    protected boolean supportsMusic() { return false; }
    /** Allocates native stream state; source remains caller-owned if setup throws. */
    protected BufferedMusic openMusic(PcmStream source, MusicBuffering buffering) {
        throw new UnsupportedOperationException("Streamed music is unsupported by this provider");
    }
    @Override public final int maxMusicStreams() { return supportsMusic() ? music.length : 0; }
    @Override public final Music createMusic(PcmStream source, MusicBuffering buffering) {
        checkLive();
        if (source == null || source.isDisposed() || buffering == null || source.channels() < 1 || source.channels() > 2
                || source.sampleRate() < 8000 || source.sampleRate() > 192000 || source.frames() == 0 || source.frames() < -1
                || source.maxReadFrames() < buffering.framesPerBuffer()) {
            throw new IllegalArgumentException("Invalid PCM stream or unsupported buffer size");
        }
        if (!supportsMusic()) { throw new UnsupportedOperationException("Streamed music is unsupported by this provider"); }
        for (int i = 0; i < music.length; i++) {
            if (music[i] == null) {
                BufferedMusic created = openMusic(source,buffering);
                if (created == null) { throw new FdxException("Audio provider returned no music stream"); }
                music[i] = created; return created;
            }
        }
        throw new FdxException("Music stream capacity exhausted: " + music.length);
    }
    final void detach(BufferedMusic stream) {
        for (int i = 0; i < music.length; i++) { if (music[i] == stream) { music[i] = null; return; } }
    }

    /** Allocates all Java voice state once; capacity is 1–256. */
    protected PooledAudio(int capacity) {
        if (capacity < 1 || capacity > 256) throw new IllegalArgumentException("Voice capacity must be 1–256");
        voices = new Voice[capacity];
        for (int i = 0; i < capacity; i++) voices[i] = new Voice();
    }
    private static synchronized long allocateDomain() {
        if (nextDomain == 0x7fffff) throw new FdxException("Audio domain identifiers exhausted");
        return (long) ++nextDomain << 40;
    }
    /** Provider checks thread affinity and device failure here. */
    protected abstract void checkDevice();
    /** Copies PCM into an owned provider resource; cleans up on failure. */
    protected abstract Object upload(PcmData pcm);
    /** Releases an upload after its voices have stopped. */
    protected abstract void release(Object resource);
    /** Configures and starts a free native slot (or retains it paused). */
    protected abstract void start(int slot, Object resource, float gain, float pitch, float pan,
            boolean loop, boolean paused);
    /** Stops and detaches a native slot, including a naturally completed slot. */
    protected abstract void stopSlot(int slot);
    /** Pauses a native slot. */
    protected abstract void pauseSlot(int slot);
    /** Resumes a native slot without rewinding. */
    protected abstract void resumeSlot(int slot);
    /** Updates native gain/rate/pan. */
    protected abstract void parametersSlot(int slot, float gain, float pitch, float pan);
    /** True only on natural completion, not while paused or awaiting activation. */
    protected abstract boolean finished(int slot);
    /** Requests platform activation; callbacks must run on the application event loop. */
    protected abstract FdxFuture<Void> activate();
    /** Reports platform suspension without changing per-voice state. */
    protected abstract boolean platformSuspended();
    /** Releases the native service after all sounds and voices are invalidated. */
    protected abstract void closeDevice();

    /** Fails clearly after shutdown, before provider operations. */
    protected final void checkLive() {
        if (disposed) throw new FdxException("Audio is disposed");
        checkDevice();
    }
    @Override public final Sound createSound(PcmData pcm) {
        checkLive();
        if (pcm == null) throw new IllegalArgumentException("PCM cannot be null");
        DeviceSound sound = new DeviceSound(pcm, upload(pcm));
        sound.next = sounds;
        if (sounds != null) sounds.previous = sound;
        sounds = sound;
        return sound;
    }
    @Override public final long play(Sound sound, float gain, float pitch, float pan, boolean loop) {
        checkLive();
        validate(gain, pitch, pan);
        if (!(sound instanceof DeviceSound) || ((DeviceSound) sound).owner() != this || sound.isDisposed()) {
            throw new FdxException("Sound is disposed or belongs to another audio resource domain");
        }
        update();
        for (int i = 0; i < voices.length; i++) {
            Voice voice = voices[i];
            if (voice.sound != null) continue;
            if (serial == 0xffffffffL) throw new FdxException("Audio voice identifiers exhausted");
            start(i, ((DeviceSound) sound).resource, gain, pitch, pan, loop, suspended);
            voice.sound = (DeviceSound) sound;
            voice.paused = false;
            voice.handle = domain | (++serial << 8) | i;
            return voice.handle;
        }
        return NO_VOICE;
    }
    private int slot(long handle) {
        int index = (int) (handle & 255);
        return handle != 0 && index < voices.length && voices[index].handle == handle
                && voices[index].sound != null ? index : -1;
    }
    private int liveSlot(long handle) {
        int index = slot(handle);
        if (index >= 0 && !voices[index].paused && !suspended && !platformSuspended() && finished(index)) {
            stopAt(index); return -1;
        }
        return index;
    }
    private void stopAt(int index) {
        Voice voice = voices[index];
        voice.handle = 0;
        voice.sound = null;
        voice.paused = false;
        stopSlot(index);
    }
    @Override public final boolean stop(long handle) {
        checkLive();
        int index = slot(handle);
        if (index < 0) return false;
        stopAt(index);
        return true;
    }
    @Override public final boolean pause(long handle) {
        checkLive();
        int index = liveSlot(handle);
        if (index < 0) return false;
        if (!voices[index].paused && !suspended) pauseSlot(index);
        voices[index].paused = true;
        return true;
    }
    @Override public final boolean resume(long handle) {
        checkLive();
        int index = liveSlot(handle);
        if (index < 0) return false;
        if (voices[index].paused && !suspended) resumeSlot(index);
        voices[index].paused = false;
        return true;
    }
    @Override public final boolean parameters(long handle, float gain, float pitch, float pan) {
        checkLive();
        validate(gain, pitch, pan);
        int index = liveSlot(handle);
        if (index < 0) return false;
        parametersSlot(index, gain, pitch, pan);
        return true;
    }
    @Override public final VoiceState state(long handle) {
        checkLive();
        int index = liveSlot(handle);
        return index < 0 ? VoiceState.STOPPED : voices[index].paused || isSuspended()
                ? VoiceState.PAUSED : VoiceState.PLAYING;
    }
    @Override public final void update() {
        checkLive();
        boolean paused = suspended || platformSuspended();
        if (!paused) {
            for (int i = 0; i < voices.length; i++) {
                if (voices[i].sound != null && !voices[i].paused && finished(i)) stopAt(i);
            }
        }
        for (BufferedMusic stream : music) { if (stream != null) { stream.pump(paused); } }
    }
    @Override public final int activeVoices() {
        update();
        int count = 0;
        for (int i = 0; i < voices.length; i++) if (voices[i].sound != null) count++;
        return count;
    }
    @Override public final int maxVoices() { return voices.length; }
    @Override public final void suspend() {
        checkLive();
        activationRevision++;
        update();
        if (suspended) return;
        suspended = true;
        for (int i = 0; i < voices.length; i++) {
            if (voices[i].sound != null && !voices[i].paused) pauseSlot(i);
        }
        for (BufferedMusic stream : music) { if (stream != null) { stream.pump(true); } }
    }
    @Override public final FdxFuture<Void> resume() {
        checkLive();
        long revision = ++activationRevision;
        FdxFuture<Void> result = FdxFuture.pending();
        activate().onSuccess(ignored -> {
            try {
                if (disposed || revision != activationRevision) throw new FdxException("Audio activation superseded");
                if (suspended) {
                    for (int i = 0; i < voices.length; i++) {
                        if (voices[i].sound != null && !voices[i].paused) resumeSlot(i);
                    }
                    suspended = false;
                }
            } catch (RuntimeException | Error error) {
                result.completeExceptionally(error);
                return;
            }
            result.complete(null);
        }).onFailure(result::completeExceptionally);
        return result;
    }
    @Override public final boolean isSuspended() { checkLive(); return suspended || platformSuspended(); }
    @Override public final boolean isDisposed() { return disposed; }
    @Override public final void dispose() {
        if (disposed) return;
        checkDevice();
        Throwable failure = null;
        for (BufferedMusic stream : music) {
            if (stream != null) {
                try { stream.release(); } catch (RuntimeException | Error error) { failure = append(failure,error); }
            }
        }
        while (sounds != null) {
            try { sounds.releaseSound(); }
            catch (RuntimeException | Error error) { failure = append(failure, error); }
        }
        disposed = true;
        activationRevision++;
        try { closeDevice(); }
        catch (RuntimeException | Error error) { failure = append(failure, error); }
        rethrow(failure);
    }
    private static void validate(float gain, float pitch, float pan) {
        if (!Float.isFinite(gain) || gain < 0 || gain > 1 || !Float.isFinite(pitch)
                || pitch < 0.25f || pitch > 4 || !Float.isFinite(pan) || pan < -1 || pan > 1) {
            throw new IllegalArgumentException("Expected gain [0,1], pitch [0.25,4], pan [-1,1]");
        }
    }
    private static Throwable append(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }
    private static void rethrow(Throwable error) {
        if (error instanceof RuntimeException) throw (RuntimeException) error;
        if (error instanceof Error) throw (Error) error;
    }
    private static final class Voice {
        private DeviceSound sound;
        private long handle;
        private boolean paused;
    }
    private final class DeviceSound implements Sound {
        private Object resource;
        private DeviceSound previous, next;
        private final double duration;
        private final int channels, sampleRate;
        DeviceSound(PcmData pcm, Object resource) {
            this.resource = resource;
            duration = pcm.durationSeconds(); channels = pcm.channels(); sampleRate = pcm.sampleRate();
        }
        PooledAudio owner() { return PooledAudio.this; }
        @Override public double durationSeconds() { return duration; }
        @Override public int channels() { return channels; }
        @Override public int sampleRate() { return sampleRate; }
        @Override public ProviderId providerId() { return PooledAudio.this.providerId(); }
        @Override public <T> T as() {
            checkLive();
            if (isDisposed()) throw new FdxException("Sound is disposed");
            throw new FdxException("This sound has no public provider-specific view");
        }
        @Override public boolean isDisposed() { return resource == null; }
        @Override public void dispose() {
            if (isDisposed()) return;
            checkLive();
            releaseSound();
        }
        private void releaseSound() {
            Object released = resource;
            resource = null;
            if (previous == null) sounds = next; else previous.next = next;
            if (next != null) next.previous = previous;
            previous = next = null;
            Throwable failure = null;
            for (int i = 0; i < voices.length; i++) {
                if (voices[i].sound == this) {
                    try { stopAt(i); }
                    catch (RuntimeException | Error error) { failure = append(failure, error); }
                }
            }
            try { release(released); }
            catch (RuntimeException | Error error) { failure = append(failure, error); }
            rethrow(failure);
        }
    }
}
