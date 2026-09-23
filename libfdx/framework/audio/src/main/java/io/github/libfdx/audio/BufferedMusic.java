package io.github.libfdx.audio;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;

/**
 * Provider support for a fixed buffer queue. Provider hooks run on the application
 * thread and must copy submitted samples. One decoded block is pending at a time;
 * seeks discard old generations only after their read completes. Native/browser
 * buffering is bounded by the supplied configuration.
 */
public abstract class BufferedMusic implements Music {
    private final PooledAudio owner;
    private final PcmStream source;
    private final int block;
    private final int channels, sampleRate;
    private final long frames;
    private final boolean seekable;
    private final long[] starts;
    private final int[] lengths;
    private final short[] decoded;
    private FdxFuture<Integer> read;
    private long generation, readGeneration, readOffset, nextFrame, position, loopStart, loopEnd;
    private int head, count, queuedFrames, requested, underruns;
    private float gain = 1, pan;
    private boolean desired, paused, looping, eof, started, starved, consumed, disposed;
    private MusicState state = MusicState.STOPPED;
    private Throwable failure;

    /** Source ownership transfers only when the enclosing provider factory returns successfully. */
    protected BufferedMusic(PooledAudio owner, PcmStream source, MusicBuffering buffering) {
        this.owner = owner; this.source = source; block = buffering.framesPerBuffer();
        channels = source.channels(); sampleRate = source.sampleRate(); frames = source.frames();
        seekable = source.isSeekable();
        starts = new long[buffering.buffers()]; lengths = new int[buffering.buffers()];
        decoded = new short[block * source.channels()]; loopEnd = source.frames();
    }
    /** Copies one block to the native queue; throws before leaving a partial queue change. */
    protected abstract void enqueue(short[] samples, int frames);
    /** Removes and returns the number of completely played blocks. */
    protected abstract int processed();
    /** Current offset inside the oldest unprocessed block, in frames. */
    protected abstract int sampleOffset();
    protected abstract void startPlayback();
    protected abstract void pausePlayback();
    protected abstract void clearPlayback();
    protected abstract void parameters(float gain, float pan);
    protected abstract void closePlayback();

    private void check() {
        owner.checkLive();
        if (disposed) { throw new FdxException("Music is disposed"); }
        if (failure != null) { throw new FdxException("Music playback failed", failure); }
    }
    @Override
    public final Audio audio() { return owner; }
    @Override
    public final Music play() {
        check();
        if ((state == MusicState.ENDED || state == MusicState.STOPPED) && consumed && !seekable) {
            throw new FdxException("Consumed sequential music requires a new source");
        }
        if (state == MusicState.ENDED) {
            rewind(looping ? loopStart : 0);
        }
        if (desired && !paused) { return this; }
        desired = true; paused = false;
        state = owner.isSuspended() ? MusicState.PAUSED : MusicState.BUFFERING; return this;
    }
    @Override
    public final Music pause() {
        check(); pump(owner.isSuspended());
        if (desired) { paused = true; pausePlayback(); state = MusicState.PAUSED; }
        return this;
    }
    @Override
    public final Music stop() {
        check(); rewind(0); desired = false; paused = false; state = MusicState.STOPPED; return this;
    }
    @Override
    public final Music seek(long frame) {
        check();
        if (!seekable) { throw new FdxException("Music source cannot seek"); }
        if (frame < 0 || frames() >= 0 && frame > frames()) { throw new IllegalArgumentException("Seek frame outside track"); }
        rewind(frame); state = desired ? paused ? MusicState.PAUSED : MusicState.BUFFERING : MusicState.STOPPED;
        return this;
    }
    @Override
    public final Music loop(long start, long end) {
        check();
        if (!seekable) { throw new FdxException("Looping requires a seekable music source"); }
        if (start < 0 || end <= start || frames() >= 0 && end > frames()) { throw new IllegalArgumentException("Invalid loop interval"); }
        long current = positionFrames(); loopStart = start; loopEnd = end; looping = true;
        rewind(current < start || current >= end ? start : current); return this;
    }
    @Override
    public final Music looping(boolean enabled) {
        check();
        if (enabled) { return loop(loopStart, loopEnd); }
        if (looping) { long current = positionFrames(); looping = false; rewind(current); }
        return this;
    }
    private void rewind(long frame) {
        generation++; clearPlayback(); head = count = queuedFrames = 0;
        nextFrame = position = frame; eof = false; started = starved = false;
        state = desired ? paused || owner.isSuspended() ? MusicState.PAUSED : MusicState.BUFFERING : MusicState.STOPPED;
    }
    @Override
    public final Music gain(float gain) {
        check(); if (!Float.isFinite(gain) || gain < 0 || gain > 1) { throw new IllegalArgumentException("Gain must be [0,1]"); }
        parameters(gain,pan); this.gain = gain; return this;
    }
    @Override
    public final Music pan(float pan) {
        check(); if (!Float.isFinite(pan) || pan < -1 || pan > 1) { throw new IllegalArgumentException("Pan must be [-1,1]"); }
        parameters(gain,pan); this.pan = pan; return this;
    }
    @Override
    public final float gain() { return gain; }
    @Override
    public final float pan() { return pan; }
    @Override
    public final int channels() { return channels; }
    @Override
    public final int sampleRate() { return sampleRate; }
    @Override
    public final long frames() { return frames; }
    @Override
    public final boolean isSeekable() { return seekable; }
    @Override
    public final long positionFrames() {
        if (!disposed && failure == null) { owner.checkLive(); pump(owner.isSuspended()); }
        return position;
    }
    @Override
    public final int queuedFrames() { return queuedFrames; }
    @Override
    public final int underruns() { return underruns; }
    @Override
    public final MusicState state() { return disposed ? MusicState.DISPOSED : state; }
    @Override
    public final Throwable failure() { return failure; }

    /** Called by the root even while suspended, so pending I/O can complete without playing. */
    final void pump(boolean rootPaused) {
        if (disposed || failure != null) { return; }
        try {
            source.update();
            int finished = processed();
            if (finished < 0 || finished > count) { throw new FdxException("Invalid processed music buffer count"); }
            for (int i = 0; i < finished; i++) {
                position = starts[head] + lengths[head]; queuedFrames -= lengths[head];
                head = (head + 1) % starts.length; count--;
            }
            if (count > 0) { position = starts[head] + Math.max(0,Math.min(lengths[head],sampleOffset())); }
            if (started && !eof && count == 0 && desired && !paused && !rootPaused && !starved) {
                underruns++; starved = true;
            }
            if (read != null && read.isDone()) {
                FdxFuture<Integer> completed = read; read = null;
                int actual = completed.get();
                if (readGeneration == generation) {
                    if (actual == -1) {
                        if (looping || frames >= 0) { throw new FdxException("PCM stream ended before its declared boundary"); }
                        eof = true;
                    }
                    else {
                        if (actual <= 0 || actual > requested) { throw new FdxException("Invalid PCM stream frame count"); }
                        enqueue(decoded,actual);
                        int tail = (head + count) % starts.length;
                        starts[tail] = readOffset; lengths[tail] = actual; count++; queuedFrames += actual;
                        nextFrame = Math.addExact(readOffset,actual);
                        if (looping && nextFrame == loopEnd) { nextFrame = loopStart; }
                        else if (!looping && frames() >= 0 && nextFrame == frames()) { eof = true; }
                    }
                }
            }
            if (desired && !eof && read == null && count < starts.length) {
                long end = looping ? loopEnd : frames();
                if (looping && (nextFrame < loopStart || nextFrame >= loopEnd)) { nextFrame = loopStart; }
                requested = end < 0 ? block : (int)Math.min(block,end - nextFrame);
                if (requested <= 0) { eof = true; }
                else {
                    readGeneration = generation; readOffset = nextFrame; consumed = true;
                    read = source.read(readOffset,decoded,0,requested);
                    if (read == null) { throw new FdxException("PCM stream returned no future"); }
                }
            }
            if (!desired) { return; }
            if (paused || rootPaused) { pausePlayback(); state = MusicState.PAUSED; }
            else if (count == 0 && eof && read == null) { desired = false; state = MusicState.ENDED; }
            else if (count >= 2 || count > 0 && (eof || started && !starved)) {
                startPlayback(); started = true; starved = false; state = MusicState.PLAYING;
            } else { state = MusicState.BUFFERING; }
        } catch (RuntimeException | Error error) {
            failure = error; state = MusicState.FAILED; desired = false;
            try { clearPlayback(); } catch (RuntimeException | Error clear) { if (clear != error) { error.addSuppressed(clear); } }
            try { source.dispose(); } catch (RuntimeException | Error close) { if (close != error) { error.addSuppressed(close); } }
            head = count = queuedFrames = 0;
        }
    }
    @Override
    public final void dispose() { if (!disposed) { owner.checkLive(); release(); } }
    final void release() {
        if (disposed) { return; }
        disposed = true; owner.detach(this);
        Throwable error = null;
        try { closePlayback(); } catch (RuntimeException | Error failure) { error = failure; }
        try { source.dispose(); } catch (RuntimeException | Error failure) {
            if (error == null) { error = failure; } else if (error != failure) { error.addSuppressed(failure); }
        }
        read = null; count = queuedFrames = 0;
        if (error instanceof RuntimeException runtime) { throw runtime; }
        if (error instanceof Error fatal) { throw fatal; }
    }
    @Override
    public final boolean isDisposed() { return disposed; }
}
