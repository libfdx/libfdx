package io.github.libfdx.files;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;

/**
 * Owned bounded file input, independent of whole-file loading. One read may be in
 * flight per source. Callers retain the destination but must not access its requested
 * range until completion; failed reads may partially modify it. A read returns 1..count
 * bytes, -1 at EOF, or 0 for count=0. Short reads are legal. No implicit whole-file cache.
 *
 * <p>Offsets and lengths use bytes. Seekable sources accept arbitrary offsets;
 * sequential sources require the offset immediately following their last successful
 * read (initially zero). Length is a snapshot, or -1 when unavailable. Content must
 * remain unchanged while open; live reload opens a new source.</p>
 *
 * <p>Futures may complete inline. A synchronous provider can block the calling thread;
 * use a worker outside rendering. Async callbacks run on the provider's completion
 * thread and must be queued before touching application resources. Dispose is
 * idempotent, prevents future reads, and permits destination reuse when it returns.
 * It may wait for an in-progress synchronous read; asynchronous providers cancel it.</p>
 */
public interface FileDataSource extends Disposable {
    long length();
    boolean isSeekable();
    int maxReadBytes();
    FdxFuture<Integer> read(long offset, byte[] destination, int destinationOffset, int count);

    /** Shared range validation, before touching destination storage or I/O. */
    static void validate(long offset, byte[] destination, int start, int count, int maximum) {
        if (offset < 0 || destination == null || start < 0 || count < 0 || count > maximum
                || start > destination.length - count || offset > Long.MAX_VALUE - count) {
            throw new FdxException("Invalid bounded file read range");
        }
    }
    /** Configured bounds are 1 byte through 16 MiB per request. */
    static void validateLimit(int maximum) {
        if (maximum < 1 || maximum > 16 * 1024 * 1024) { throw new FdxException("Read limit must be 1..16777216 bytes"); }
    }
}
