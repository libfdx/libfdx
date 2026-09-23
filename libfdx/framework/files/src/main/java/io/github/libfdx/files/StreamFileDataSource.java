package io.github.libfdx.files;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import java.io.InputStream;

/** Synchronous bounded stream adapter. Its private lock never guards application callbacks. */
final class StreamFileDataSource implements FileDataSource {
    private final FdxTask<InputStream> opener;
    private final long length;
    private final int maximum;
    private InputStream stream;
    private long position;
    private boolean disposed;

    StreamFileDataSource(InputStream stream, FdxTask<InputStream> opener, long length, int maximum) {
        this.stream = stream; this.opener = opener; this.length = length; this.maximum = maximum;
    }
    @Override
    public long length() { return length; }
    @Override
    public boolean isSeekable() { return opener != null; }
    @Override
    public int maxReadBytes() { return maximum; }

    @Override
    public synchronized FdxFuture<Integer> read(long offset, byte[] destination, int start, int count) {
        try {
            FileDataSource.validate(offset, destination, start, count, maximum);
            if (disposed) { throw new FdxException("File input is disposed"); }
            if (opener == null && offset != position) { throw new FdxException("Input supports sequential reads only"); }
            if (count == 0) { return FdxFuture.completed(0); }
            if (length >= 0 && offset >= length) { return FdxFuture.completed(-1); }
            if (offset != position) {
                if (offset < position) {
                    InputStream replacement = opener.run();
                    if (replacement == null) { throw new FdxException("File could not be reopened"); }
                    try { stream.close(); }
                    catch (Throwable error) { replacement.close(); throw error; }
                    stream = replacement; position = 0;
                }
                while (position < offset) {
                    long skipped = stream.skip(offset - position);
                    if (skipped > 0) { position += skipped; }
                    else if (stream.read() < 0) { return FdxFuture.completed(-1); }
                    else { position++; }
                }
            }
            int read = stream.read(destination, start, count);
            if (read == 0) {
                int value = stream.read();
                if (value < 0) { read = -1; }
                else { destination[start] = (byte)value; read = 1; }
            }
            if (read > 0) { position += read; }
            return FdxFuture.completed(read);
        } catch (Throwable error) { return FdxFuture.failed(error); }
    }
    @Override
    public synchronized void dispose() {
        if (disposed) { return; }
        disposed = true;
        try { stream.close(); }
        catch (Exception error) { throw new FdxException("Could not close file input", error); }
        finally { stream = null; }
    }
    @Override
    public synchronized boolean isDisposed() { return disposed; }
}
