package io.github.libfdx.audio;

import io.github.libfdx.core.Disposable;

/**
 * Application-owned streamed playback in one audio domain. Owns its PCM source;
 * controls/disposal belong to the application thread. The backend pumps buffers
 * from Audio.update. Buffering/underruns freeze the media clock; missed audio is
 * not treated as played. No user callbacks run on the native mixer.
 */
public interface Music extends Disposable {
    /** Borrowed owning audio domain, retained for identity checks after disposal. */
    Audio audio();
    /** Starts or resumes playback. Restarting an ended/stopped consumed source requires seek support. */
    Music play();
    Music pause();
    /** Drops queued audio and resets position. A consumed sequential source cannot subsequently restart. */
    Music stop();
    /** Flushes queued audio and seeks to an absolute frame; requires seek support. */
    Music seek(long frame);
    /** Enables the half-open loop interval [start,end), in frames; requires seek support. */
    Music loop(long start, long end);
    /** Enables the configured interval (whole track by default), or disables looping. */
    Music looping(boolean enabled);
    Music gain(float gain);
    Music pan(float pan);
    float gain();
    float pan();
    int channels();
    int sampleRate();
    long frames();
    /** Whether this track supports absolute seeks and loops; retained after disposal. */
    boolean isSeekable();
    long positionFrames();
    int queuedFrames();
    int underruns();
    MusicState state();
    /** Failure retained for diagnosis, or null. Failure affects this stream, not other voices. */
    Throwable failure();
}
