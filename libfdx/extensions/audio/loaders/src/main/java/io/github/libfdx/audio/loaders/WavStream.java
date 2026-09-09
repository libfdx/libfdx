package io.github.libfdx.audio.loaders;

import io.github.libfdx.assets.AssetExecutor;
import io.github.libfdx.audio.PcmStream;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileDataSource;
import io.github.libfdx.files.FileHandle;
import static io.github.libfdx.audio.loaders.WavFormat.*;

/**
 * Bounded PCM WAV decoding. RIFF headers are limited to 64 KiB/4096 chunks, fmt must
 * precede data, and data must be the final chunk. PCM8/16 mono/stereo share the sound
 * decoder's validation. No decoded track cache is created. Worker submission retries
 * through update when full; callbacks never touch a native audio resource.
 */
public final class WavStream implements PcmStream {
    private final FileDataSource source;
    private final WavFormat format;
    private final long dataOffset, frames;
    private final int maximum;
    private final AssetExecutor executor;
    private final byte[] encoded;
    private final Object lock = new Object();
    private boolean disposed, submitted;
    private Throwable failure;
    private long nextFrame;
    private Read pending;

    private WavStream(FileDataSource source, WavFormat format, long offset, long frames,
            int maximum, AssetExecutor executor) {
        this.source = source; this.format = format; dataOffset = offset; this.frames = frames;
        this.maximum = maximum; this.executor = executor;
        encoded = new byte[maximum * format.frameBytes() + 1];
    }

    /**
     * Validates arguments, then adopts source, closing it if opening fails. A successful
     * result owns it. Parsing may perform synchronous I/O on the caller: start this via
     * AssetLoadContext.asyncFuture or a worker. The optional executor is borrowed for
     * subsequent reads and must outlive the stream. Null uses cooperative reads on update.
     */
    public static FdxFuture<PcmStream> open(FileDataSource source, int maxFrames, AssetExecutor executor) {
        if (source == null || source.isDisposed() || maxFrames < 1 || maxFrames > 65536) {
            throw new IllegalArgumentException("Expected live input and 1..65536 frames per decode block");
        }
        Headers headers = new Headers(source, maxFrames, executor);
        headers.pump(); return headers.result;
    }
    /** Opens bounded file input and composes its pending future. Same scheduling rules as open. */
    public static FdxFuture<PcmStream> openFile(FileHandle file, int maxFrames, AssetExecutor executor) {
        if (file == null || maxFrames < 1 || maxFrames > 65536) {
            throw new IllegalArgumentException("File and 1..65536 frames required");
        }
        FdxFuture<PcmStream> result = FdxFuture.pending();
        file.openRead().onSuccess(source -> {
            FdxFuture<PcmStream> opened;
            try { opened = open(source,maxFrames,executor); }
            catch (RuntimeException | Error error) {
                try { source.dispose(); } catch (RuntimeException | Error close) { if (close != error) { error.addSuppressed(close); } }
                result.completeExceptionally(error); return;
            }
            opened.onSuccess(result::complete).onFailure(result::completeExceptionally);
        }).onFailure(result::completeExceptionally);
        return result;
    }
    @Override public int channels() { return format.channels(); }
    @Override public int sampleRate() { return format.sampleRate(); }
    @Override public long frames() { return frames; }
    @Override public int maxReadFrames() { return maximum; }
    @Override public boolean isSeekable() { return source.isSeekable(); }

    @Override public FdxFuture<Integer> read(long offset, short[] destination, int start, int count) {
        Read request;
        synchronized (lock) {
            if (disposed) { return FdxFuture.failed(new FdxException("WAV stream is disposed")); }
            if (failure != null) { return FdxFuture.failed(failure); }
            if (pending != null) { return FdxFuture.failed(new FdxException("WAV read is already pending")); }
            if (offset < 0 || destination == null || start < 0 || count < 0 || count > maximum
                    || (long)start + (long)count * channels() > destination.length
                    || !isSeekable() && offset != nextFrame) {
                return FdxFuture.failed(new FdxException("Invalid PCM frame range or sequential offset"));
            }
            if (count == 0) { return FdxFuture.completed(0); }
            if (offset >= frames) { return FdxFuture.completed(-1); }
            request = new Read(offset, destination, start, (int)Math.min(count, frames - offset));
            pending = request;
        }
        update(); return request.result;
    }

    @Override public void update() {
        Read request;
        synchronized (lock) {
            if (disposed || pending == null || submitted) { return; }
            request = pending; submitted = true;
        }
        try {
            if (executor == null) { request.run(); }
            else if (!executor.submit(request)) {
                synchronized (lock) { if (pending == request) { submitted = false; } }
            }
        } catch (RuntimeException | Error error) { request.fail(error); }
    }
    @Override public void dispose() {
        Read request;
        synchronized (lock) {
            if (disposed) { return; }
            disposed = true; request = pending; pending = null;
        }
        try { source.dispose(); }
        finally {
            if (request != null) { request.result.completeExceptionally(new FdxException("WAV stream closed during read")); }
        }
    }
    @Override public boolean isDisposed() { synchronized (lock) { return disposed; } }

    private final class Read implements Runnable {
        final FdxFuture<Integer> result = FdxFuture.pending();
        final long offset;
        final short[] destination;
        final int start, count, bytes;
        int acquired;
        Read(long offset, short[] destination, int start, int count) {
            this.offset = offset; this.destination = destination; this.start = start; this.count = count;
            int padding = offset + count == frames ? (int)(frames * format.frameBytes() & 1) : 0;
            bytes = count * format.frameBytes() + padding;
        }
        @Override public void run() {
            while (true) {
                synchronized (lock) { if (disposed || pending != this) { return; } }
                int accepted;
                try {
                    FdxFuture<Integer> read = source.read(dataOffset + offset * format.frameBytes() + acquired, encoded, acquired,
                            Math.min(bytes - acquired, source.maxReadBytes()));
                    if (read == null) { throw invalid("Input returned no future"); }
                    if (read.isDone()) { accepted = accept(read.get()); }
                    else { read.onSuccess(this::onRead).onFailure(this::fail); return; }
                } catch (RuntimeException | Error error) { fail(error); return; }
                if (accepted > 0) { result.complete(count); return; }
                if (accepted < 0) { return; }
            }
        }
        private void onRead(int actual) {
            int accepted;
            try { accepted = accept(actual); }
            catch (RuntimeException | Error error) { fail(error); return; }
            if (accepted > 0) { result.complete(count); }
            else if (accepted == 0) { synchronized (lock) { if (pending == this) { submitted = false; } } }
        }
        private int accept(int actual) {
            synchronized (lock) {
                if (disposed || pending != this) { return -1; }
                if (actual <= 0 || actual > Math.min(bytes - acquired, source.maxReadBytes())) { throw invalid("Truncated or invalid PCM read"); }
                acquired += actual;
                if (acquired < bytes) { return 0; }
                int samples = count * channels();
                for (int i = 0; i < samples; i++) {
                    destination[start + i] = format.bits() == 8 ? (short)(((encoded[i] & 255) - 128) << 8)
                            : (short)u16(encoded, i * 2);
                }
                nextFrame = offset + count; pending = null; submitted = false;
            }
            return 1;
        }
        void fail(Throwable error) {
            synchronized (lock) {
                if (disposed || pending != this) { return; }
                failure = error; pending = null; submitted = false;
            }
            try { source.dispose(); }
            catch (RuntimeException | Error close) { if (close != error) { error.addSuppressed(close); } }
            result.completeExceptionally(error);
        }
    }

    /** Iterative for already-completed reads; pending reads resume on their completion thread. */
    private static final class Headers {
        final FileDataSource source;
        final int maximum;
        final AssetExecutor executor;
        final byte[] buffer;
        final FdxFuture<PcmStream> result = FdxFuture.pending();
        FdxFuture<Integer> pending;
        long offset, riffEnd, nextChunk, skip;
        int needed = 12, filled, phase, chunks;
        WavFormat format;
        Headers(FileDataSource source, int maximum, AssetExecutor executor) {
            this.source = source; this.maximum = maximum; this.executor = executor;
            buffer = new byte[Math.max(18, Math.min(16384, source.maxReadBytes()))];
        }
        void pump() {
            while (!result.isDone()) {
                PcmStream ready = null;
                try {
                    if (pending != null) {
                        if (!pending.isDone()) { return; }
                        int actual = pending.get(); pending = null;
                        if (actual <= 0 || actual > Math.min(needed - filled, source.maxReadBytes())) { throw invalid("Truncated header"); }
                        filled += actual;
                        if (filled == needed) { ready = parse(); }
                    }
                    if (ready == null) {
                        pending = source.read(offset + filled, buffer, filled, Math.min(needed - filled, source.maxReadBytes()));
                        if (pending == null) { throw invalid("Input returned no future"); }
                    }
                } catch (RuntimeException | Error error) { fail(error); return; }
                if (ready != null) { result.complete(ready); return; }
                if (!pending.isDone()) {
                    FdxFuture<Integer> wait = pending;
                    wait.onSuccess(ignored -> pump()).onFailure(this::fail); return;
                }
            }
        }
        PcmStream parse() {
            if (phase == 0) {
                if (!tag(buffer,0,"RIFF") || !tag(buffer,8,"WAVE")) { throw invalid("Expected RIFF/WAVE"); }
                riffEnd = u32(buffer,4) + 8;
                if (riffEnd < 12 || source.length() >= 0 && source.length() != riffEnd) { throw invalid("RIFF length mismatch"); }
                prepare(1,12,8);
            } else if (phase == 1) {
                if (++chunks > 4096 || offset + 8 > riffEnd || offset + 8 > 65536) { throw invalid("Header limit exceeded"); }
                long size = u32(buffer,4); nextChunk = offset + 8 + size + (size & 1);
                if (nextChunk > riffEnd) { throw invalid("Chunk exceeds RIFF length"); }
                if (tag(buffer,0,"data")) {
                    if (format == null || size == 0 || size % format.frameBytes() != 0 || nextChunk != riffEnd) {
                        throw invalid("Streaming requires fmt before a nonempty, frame-aligned final data chunk");
                    }
                    return new WavStream(source,format,offset + 8,size / format.frameBytes(),maximum,executor);
                }
                if (nextChunk + 8 > 65536) { throw invalid("Header limit exceeded"); }
                if (tag(buffer,0,"fmt ")) {
                    if (format != null || size != 16 && size != 18) { throw invalid("Duplicate or unsupported fmt chunk"); }
                    prepare(2,offset + 8,(int)size);
                } else if (source.isSeekable() || size == 0) { prepare(1,nextChunk,8); }
                else { skip = size + (size & 1); prepare(3,offset + 8,(int)Math.min(skip,buffer.length)); }
            } else if (phase == 2) {
                format = WavFormat.read(buffer,0,needed); prepare(1,nextChunk,8);
            } else {
                skip -= needed;
                if (skip == 0) { prepare(1,nextChunk,8); }
                else { prepare(3,offset + needed,(int)Math.min(skip,buffer.length)); }
            }
            return null;
        }
        void prepare(int phase,long offset,int count) { this.phase = phase; this.offset = offset; needed = count; filled = 0; }
        void fail(Throwable error) {
            if (result.isDone()) { return; }
            try { source.dispose(); }
            catch (RuntimeException | Error close) { if (close != error) { error.addSuppressed(close); } }
            result.completeExceptionally(error);
        }
    }
}
