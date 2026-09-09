package io.github.libfdx.backend.web;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileDataSource;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int8Array;

/** Browser-event-loop source. HTTP ranges never fall back to downloading the entire resource. */
final class WebFileDataSource implements FileDataSource {
    private final String path;
    private final int maximum;
    private final byte[] memory;
    private final Int8Array cached;
    private long length;
    private boolean disposed;
    private FdxFuture<Integer> pending;
    private JSObject request;

    WebFileDataSource(String path, int maximum, byte[] memory, Int8Array cached) {
        this.path = path; this.maximum = maximum; this.memory = memory; this.cached = cached;
        length = memory != null ? memory.length : cached != null ? cached.getLength() : -1;
    }
    @Override public long length() { return length; }
    @Override public boolean isSeekable() { return true; }
    @Override public int maxReadBytes() { return maximum; }

    @Override public FdxFuture<Integer> read(long offset, byte[] destination, int start, int count) {
        try {
            FileDataSource.validate(offset, destination, start, count, maximum);
            if (disposed) { throw new FdxException("File input is disposed: " + path); }
            if (pending != null) { throw new FdxException("A file read is already pending: " + path); }
            if (offset > 9_007_199_254_740_991L - count) { throw new FdxException("Offset exceeds browser integer precision"); }
            if (count == 0) { return FdxFuture.completed(0); }
            if (length >= 0 && offset >= length) { return FdxFuture.completed(-1); }
            if (memory != null || cached != null) {
                int actual = (int)Math.min(count, length - offset);
                if (memory != null) { System.arraycopy(memory, (int)offset, destination, start, actual); }
                else { for (int i = 0; i < actual; i++) { destination[start + i] = cached.get((int)offset + i); } }
                return FdxFuture.completed(actual);
            }
            FdxFuture<Integer> result = FdxFuture.pending(); pending = result;
            try {
                request = readRange(path, offset, count, (bytes, total) -> {
                    if (pending != result) { return; }
                    pending = null; request = null;
                    try {
                        if (length >= 0 && length != (long)total) { throw new FdxException("File size changed while open: " + path); }
                        length = (long)total;
                        int actual = bytes == null ? -1 : bytes.getLength();
                        if (bytes != null) {
                            for (int i = 0; i < actual; i++) { destination[start + i] = bytes.get(i); }
                        }
                        result.complete(actual);
                    } catch (RuntimeException | Error error) { result.completeExceptionally(error); }
                }, message -> {
                    if (pending != result) { return; }
                    pending = null; request = null;
                    result.completeExceptionally(new FdxException(path + ": " + message));
                });
            } catch (RuntimeException | Error error) {
                pending = null; request = null; result.completeExceptionally(error);
            }
            return result;
        } catch (RuntimeException | Error error) { return FdxFuture.failed(error); }
    }
    @Override public void dispose() {
        if (disposed) { return; }
        disposed = true;
        FdxFuture<Integer> current = pending; pending = null;
        if (request != null) { abort(request); request = null; }
        if (current != null) { current.completeExceptionally(new FdxException("File input closed during read: " + path)); }
    }
    @Override public boolean isDisposed() { return disposed; }
    @JSFunctor private interface Success extends JSObject { void accept(Int8Array bytes, double totalLength); }
    @JSFunctor private interface Failure extends JSObject { void accept(String error); }
    @JSBody(params = "controller", script = "controller.abort();")
    private static native void abort(JSObject controller);

    @JSBody(params = {"path", "offset", "count", "ok", "fail"}, script = """
        var controller = new AbortController();
        var rangeEnd = offset + count - 1;
        fetch('assets/' + path, {headers:{Range:'bytes=' + offset + '-' + rangeEnd},
                                signal:controller.signal, cache:'no-store'}).then(function(response) {
            var range = response.headers.get('Content-Range') || '';
            var empty = /^bytes \\*\\/(\\d+)$/.exec(range);
            if (response.status === 416 && empty && Number(empty[1]) === 0 && offset === 0) {
                if (response.body) response.body.cancel(); ok(null, 0); return;
            }
            var match = /^bytes (\\d+)-(\\d+)\\/(\\d+)$/.exec(range);
            var encoding = response.headers.get('Content-Encoding');
            if (response.status !== 206 || !match || Number(match[1]) !== offset ||
                    Number(match[2]) < offset || Number(match[2]) >= offset + count ||
                    Number(match[3]) <= Number(match[2]) || !Number.isSafeInteger(Number(match[3])) ||
                    (encoding && encoding !== 'identity')) {
                if (response.body) response.body.cancel();
                throw new Error('Server must supply a valid, uncompressed HTTP byte range (206)');
            }
            var expected = Number(match[2]) - offset + 1, total = Number(match[3]);
            var output = new Int8Array(expected), written = 0, reader = response.body.getReader();
            function next() {
                return reader.read().then(function(part) {
                    if (part.done) {
                        if (written !== expected) throw new Error('Truncated HTTP range');
                        ok(output, total); return;
                    }
                    if (written + part.value.length > expected) {
                        reader.cancel(); throw new Error('HTTP range exceeded requested bound');
                    }
                    output.set(part.value, written); written += part.value.length;
                    return next();
                });
            }
            return next();
        }).catch(function(error) { fail(String(error)); });
        return controller;
        """)
    private static native JSObject readRange(String path, double offset, int count, Success ok, Failure fail);
}
