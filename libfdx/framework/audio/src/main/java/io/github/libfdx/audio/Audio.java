package io.github.libfdx.audio;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderHandle;

/**
 * Optional backend-owned playback service. All operations, including sound disposal,
 * belong to the application thread. Dispose application-owned sounds/music before
 * backend shutdown. Shutdown invalidates every remaining sound, music, and voice.
 * No user callbacks run on a mixer thread.
 */
public interface Audio extends Disposable, ProviderHandle {
    /** Rejected/stopped voice sentinel; no allocation or silent voice stealing occurs. */
    long NO_VOICE = 0L;

    /** Creates an application-owned device sound by copying immutable decoded PCM. */
    Sound createSound(PcmData pcm);

    /**
     * Creates owned streamed playback and transfers source ownership on success only.
     * The caller retains source after failure. Streams use a separate bounded pool
     * from sound voices; unsupported providers and exhausted capacity fail explicitly.
     */
    default Music createMusic(PcmStream source, MusicBuffering buffering) {
        throw new UnsupportedOperationException("Streamed music is unsupported by this audio provider");
    }
    default Music createMusic(PcmStream source) { return createMusic(source, MusicBuffering.DEFAULT); }
    /** Fixed number of simultaneously owned music streams; zero means unsupported. */
    default int maxMusicStreams() { return 0; }

    /**
     * Starts a voice, or returns {@link #NO_VOICE} when all slots are occupied.
     * The sound is borrowed until stop, completion, or sound disposal. Gain is [0,1],
     * pitch is [0.25,4], and pan is [-1,1]. Mono uses equal-power panning; stereo
     * uses balance (attenuate the opposite channel). Looping repeats the whole sound.
     * Handles are scoped to this service and never reused. Invalid parameters,
     * disposed sounds, and sounds from another resource domain fail before playback.
     */
    long play(Sound sound, float gain, float pitch, float pan, boolean loop);

    /** Starts one centered, non-looping voice at its original rate. */
    default long play(Sound sound) { return play(sound, 1, 1, 0, false); }

    /** Stops and releases a slot. Returns false for stale, foreign, or zero handles. */
    boolean stop(long voice);
    /** Pauses a voice while retaining its slot and playback position. */
    boolean pause(long voice);
    /** Resumes a voice; service suspension still takes precedence. */
    boolean resume(long voice);
    /** Changes gain, pitch, and pan without restarting playback. */
    boolean parameters(long voice, float gain, float pitch, float pan);
    /** Polls completion; stale, foreign, and zero handles are STOPPED. */
    VoiceState state(long voice);
    /** Polls device/completion state. Backends call this each application-loop iteration. */
    void update();
    /** Number of occupied slots, including paused voices; polls completed voices first. */
    int activeVoices();
    /** Fixed maximum simultaneous voices. */
    int maxVoices();
    /** Pauses all voices without losing their individual pause states. */
    void suspend();
    /**
     * Requests output activation/resume. Browser callers invoke this inside a user
     * gesture. Completion runs on the application event loop; a pending activation
     * does not mean sound is audible. A later suspend/dispose supersedes this request.
     */
    FdxFuture<Void> resume();
    /** True while explicitly suspended or waiting for platform activation. */
    boolean isSuspended();
}
