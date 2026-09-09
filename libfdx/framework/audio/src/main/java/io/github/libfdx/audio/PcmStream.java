package io.github.libfdx.audio;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;

/**
 * Owned bounded PCM16 input. Samples are signed, interleaved mono/stereo; offsets
 * and counts use sample frames except destinationSampleOffset, which indexes shorts.
 * One read may be pending. The destination is borrowed until its future completes;
 * failures may partially modify it. Reads return frames, -1 at EOF, or 0 for count=0.
 *
 * <p>Read/update calls belong to the application thread. Providers may complete on
 * a worker; consumers must poll or queue completion before touching audio resources.
 * Call update each application iteration to retry bounded worker submission or
 * advance cooperative I/O. It must never wait for a pending future. Dispose stops
 * future work and prevents destination writes after it returns. It may wait for
 * a current bounded synchronous read/decode; never call it from a mixer callback.</p>
 */
public interface PcmStream extends Disposable {
    int channels();
    int sampleRate();
    /** Total frames, or -1 when unknown. Metadata remains readable after disposal. */
    long frames();
    int maxReadFrames();
    boolean isSeekable();
    /** Sequential streams require the next unread frame; seekable streams accept arbitrary frames. */
    FdxFuture<Integer> read(long frameOffset, short[] destination, int destinationSampleOffset, int frameCount);
    void update();
}
