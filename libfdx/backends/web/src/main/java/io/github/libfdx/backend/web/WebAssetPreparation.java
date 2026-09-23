package io.github.libfdx.backend.web;

import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.assets.loaders.ImageDecoder;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.TextureMipmaps;
import io.github.libfdx.graphics.TextureMipmapPreparer;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.concurrent.CancellationException;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.typedarrays.Uint8Array;

/** Image/mipmap worker shared automatically by default loaders within each asset manager. Explicit
 * instances remain application-owned: call on the browser application thread and dispose after
 * borrowing asset managers. The worker retains at most eight jobs / 64 MiB of input, running one
 * at a time. Manager-owned defaults use cooperative overflow; explicit instances reject saturation.
 * Sources are borrowed unchanged until completion; transfers copy them before detaching
 * the message buffer. Completion runs on the browser event loop (managed loaders marshal through
 * update). Missing Worker/OffscreenCanvas support, startup failure, or a crashed worker selects
 * cooperative fallback for accepted jobs. Invalid input fails its future.
 * Disposal terminates the worker and cancels accepted jobs; late replies cannot publish results.
 * Raw PNG and mip filtering use the same Java implementation as the main application.
 */
public final class WebAssetPreparation implements ImageDecoder, TextureMipmapPreparer, Disposable {
    private static final int CHUNK = 65536, MAX_INPUT = 64 * 1024 * 1024;
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private final IdentityHashMap<Job, Boolean> overflow = new IdentityHashMap<>();
    private final boolean cooperativeOverflow;
    private WebWorkerConnection worker;
    private Job active;
    private boolean ready, fallback, disposed;
    private int nextId, retainedBytes, completedJobs, fallbackJobs;

    public WebAssetPreparation() { this(null, false, true); }

    /** Custom script URL relative to the document; its lifetime remains the caller's responsibility. */
    public WebAssetPreparation(String workerUrl) {
        this(workerUrl, false, false);
    }

    /** Borrows the manager's lazy shared worker, or returns null when the context cannot own CPU
     * resources. Excess jobs use cooperative preparation instead of failing on worker saturation. */
    public static WebAssetPreparation forManager(AssetLoadContext context) {
        return context.preparationResource(WebAssetPreparation.class,
                () -> new WebAssetPreparation(null, true, true));
    }

    private WebAssetPreparation(String workerUrl, boolean cooperativeOverflow, boolean bundled) {
        this.cooperativeOverflow = cooperativeOverflow;
        if (!bundled && (workerUrl == null || workerUrl.isEmpty())) throw new FdxException("Worker URL is required");
        if (imageWorkersAvailable()) worker = bundled
                ? WebWorkerConnection.bundled("assets", 10000, this::receive, this::unavailable)
                : WebWorkerConnection.open(workerUrl, 10000, this::receive, this::unavailable);
        if (worker == null) fallback = true;
    }

    /** Completed worker jobs, excluding cooperative fallback. Useful for validating actual offload. */
    public int completedWorkerJobs() { return completedJobs; }
    public int fallbackJobs() { return fallbackJobs; }

    @Override
    public FdxFuture<ImageData> decodeAsync(String path, byte[] bytes) {
        FdxFuture<ImageData> result = FdxFuture.pending();
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_INPUT) {
            result.completeExceptionally(new FdxException("Invalid encoded image size")); return result;
        }
        submit(new Job(++nextId, path, bytes, null, 0, 0, false, false, result, null));
        return result;
    }

    @Override
    public FdxFuture<ByteBuffer[]> prepare(ByteBuffer source, int width, int height,
            boolean srgb, boolean alphaWeighted) {
        FdxFuture<ByteBuffer[]> result = FdxFuture.pending();
        long pixels = (long) width * height;
        if (width < 1 || height < 1 || pixels > MAX_INPUT / 4 || source == null || source.remaining() < pixels * 4) {
            result.completeExceptionally(new FdxException("Invalid mipmap input range")); return result;
        }
        submit(new Job(++nextId, null, null, source.slice().limit((int) pixels * 4), width, height,
                srgb, alphaWeighted, null, result));
        return result;
    }

    private void submit(Job job) {
        if (disposed) { job.fail(new FdxException("Asset worker is disposed")); return; }
        if (queue.size() + (active == null ? 0 : 1) >= 8 || job.size() > MAX_INPUT - retainedBytes) {
            if (cooperativeOverflow) { overflow.put(job, Boolean.TRUE); runFallback(job); }
            else job.fail(new FdxException("Asset worker queue is full"));
            return;
        }
        retainedBytes += job.size(); queue.addLast(job); pump();
    }

    private void pump() {
        if (disposed || active != null || !ready && !fallback) return;
        active = queue.pollFirst();
        if (active == null) return;
        Job job = active;
        if (fallback) { runFallback(job); return; }
        try {
            Int8Array transfer = Int8Array.create(job.size());
            copyInput(job, transfer, 0);
        } catch (RuntimeException | Error error) { finish(job, error); }
    }

    private void copyInput(Job job, Int8Array transfer, int offset) {
        nextFrame(() -> {
            if (disposed || active != job || fallback) return;
            try {
                long deadline = System.nanoTime() + 1_000_000L;
                int position = offset;
                do {
                    int end = Math.min(position + CHUNK, job.size());
                    if (job.encoded != null) {
                        byte[] part = java.util.Arrays.copyOfRange(job.encoded, position, end);
                        Int8Array bytes = Int8Array.create(part.length); bytes.set(part);
                        copyRange(bytes, transfer, 0, part.length, position);
                    } else {
                        ByteBuffer part = job.source.duplicate().position(position).limit(end).slice();
                        copyRange(Uint8Array.fromJavaBuffer(part), transfer, 0, end - position, position);
                    }
                    position = end;
                } while (position < job.size() && System.nanoTime() < deadline);
                if (position == job.size()) worker.post(message(job.id, job.encoded != null, transfer,
                        job.width, job.height, job.srgb, job.alphaWeighted), transfer.getBuffer());
                else copyInput(job, transfer, position);
            } catch (RuntimeException | Error error) { finish(job, error); }
        });
    }

    private void receive(JSObject message) {
        if (disposed) return;
        if (isReady(message)) { ready = true; pump(); return; }
        Job job = active;
        if (job == null || messageId(message) != job.id) { unavailable(); return; }
        String error = messageError(message);
        if (error != null) { finish(job, new FdxException("Asset worker: " + error)); return; }
        try {
            int width = messageWidth(message), height = messageHeight(message);
            if (width < 1 || height < 1 || (long) width * height > MAX_INPUT / 4
                    || job.encoded == null && (width != job.width || height != job.height))
                throw new FdxException("Invalid worker image extent");
            int count = job.encoded != null ? 1 : TextureMipmaps.levelCount(width, height);
            if (levelCount(message) != count) throw new FdxException("Invalid worker mip count");
            ByteBuffer[] levels = new ByteBuffer[count];
            for (int i = 0, w = width, h = height; i < count; i++, w = Math.max(1,w/2), h = Math.max(1,h/2)) {
                if (level(message,i).getLength() != w*h*4) throw new FdxException("Invalid worker pixel extent");
                levels[i] = ByteBuffer.allocateDirect(w*h*4);
            }
            copyOutput(job, message, levels, width, height, 0, 0);
        } catch (RuntimeException | Error failure) { finish(job, failure); }
    }

    private void copyOutput(Job job, JSObject message, ByteBuffer[] levels, int width, int height, int index, int offset) {
        nextFrame(() -> {
            if (disposed || active != job || fallback) return;
            try {
                long deadline = System.nanoTime() + 1_000_000L;
                int i = index, position = offset;
                do {
                    int end = Math.min(position + CHUNK, levels[i].capacity());
                    copyRange(level(message,i), Uint8Array.fromJavaBuffer(levels[i]), position, end, position);
                    position = end;
                    if (position == levels[i].capacity()) { i++; position = 0; }
                } while (i < levels.length && System.nanoTime() < deadline);
                if (i == levels.length) {
                    completedJobs++; release(job);
                    try { job.complete(width, height, levels); } finally { pump(); }
                } else copyOutput(job, message, levels, width, height, i, position);
            } catch (RuntimeException | Error failure) {
                if (active != job) throw failure; // A completed future's listener failed.
                finish(job, failure);
            }
        });
    }

    private void unavailable() {
        if (disposed || fallback) return;
        if (worker != null) worker.terminate();
        worker = null; fallback = true;
        if (active != null) runFallback(active); else pump();
    }

    private void runFallback(Job job) {
        fallbackJobs++;
        if (job.encoded != null) {
            ImageAssetLoader.decodeAsync(job.path, job.encoded).onSuccess(image -> {
                if (!pendingFallback(job)) return;
                release(job);
                try { job.image.complete(image); } finally { pump(); }
            }).onFailure(error -> finish(job,error));
        } else {
            try { fallbackStep(job, TextureMipmaps.prepareRgba8(job.source,job.width,job.height,job.srgb,job.alphaWeighted)); }
            catch (RuntimeException | Error failure) { finish(job,failure); }
        }
    }

    private void fallbackStep(Job job, TextureMipmaps.Rgba8Preparation preparation) {
        nextFrame(() -> {
            if (!pendingFallback(job)) return;
            try {
                long deadline = System.nanoTime() + 1_000_000L;
                do {
                    if (preparation.step(4096)) {
                        release(job);
                        try { job.mips.complete(preparation.result()); } finally { pump(); }
                        return;
                    }
                } while (System.nanoTime() < deadline);
                fallbackStep(job,preparation);
            } catch (RuntimeException | Error failure) {
                if (!pendingFallback(job)) throw failure;
                finish(job,failure);
            }
        });
    }

    private boolean pendingFallback(Job job) { return !disposed && (active == job || overflow.containsKey(job)); }
    private void release(Job job) {
        if (active == job) { active = null; retainedBytes -= job.size(); }
        else overflow.remove(job);
    }
    private void finish(Job job, Throwable failure) {
        if (!pendingFallback(job)) return;
        release(job); try { job.fail(failure); } finally { pump(); }
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (worker != null) worker.terminate();
        worker = null;
        if (active != null) { queue.addFirst(active); active = null; }
        queue.addAll(overflow.keySet()); overflow.clear();
        retainedBytes = 0;
        Throwable first = null;
        while (!queue.isEmpty()) {
            try { queue.removeFirst().fail(new CancellationException("Asset worker disposed")); }
            catch (RuntimeException | Error failure) { if (first == null) first = failure; else first.addSuppressed(failure); }
        }
        if (first instanceof Error error) throw error;
        if (first instanceof RuntimeException error) throw error;
    }
    @Override
    public boolean isDisposed() { return disposed; }

    private record Job(int id, String path, byte[] encoded, ByteBuffer source, int width, int height,
            boolean srgb, boolean alphaWeighted, FdxFuture<ImageData> image, FdxFuture<ByteBuffer[]> mips) {
        int size() { return encoded != null ? encoded.length : source.remaining(); }
        void fail(Throwable failure) { if (image != null) image.completeExceptionally(failure); else mips.completeExceptionally(failure); }
        void complete(int width, int height, ByteBuffer[] levels) {
            if (image != null) image.complete(new ImageData(width,height,levels[0])); else mips.complete(levels);
        }
    }

    @JSFunctor
    private interface Step extends JSObject { void run(); }
    @JSBody(params="step",script="requestAnimationFrame(function(){step();});")
    private static native void nextFrame(Step step);
    @JSBody(script="return typeof OffscreenCanvas === 'function' && typeof createImageBitmap === 'function';")
    private static native boolean imageWorkersAvailable();
    @JSBody(params={"id","decode","bytes","width","height","srgb","alphaWeighted"},script="""
        return {id:id,decode:decode,bytes:bytes.buffer,width:width,height:height,srgb:srgb,alphaWeighted:alphaWeighted};
        """)
    private static native JSObject message(int id,boolean decode,Int8Array bytes,int width,int height,boolean srgb,boolean alphaWeighted);
    @JSBody(params={"source","target","start","end","offset"},script="target.set(source.subarray(start,end),offset);")
    private static native void copyRange(JSObject source,JSObject target,int start,int end,int offset);
    @JSBody(params="m",script="return !!m.ready;")
    private static native boolean isReady(JSObject m);
    @JSBody(params="m",script="return m.id;")
    private static native int messageId(JSObject m);
    @JSBody(params="m",script="return m.error||null;")
    private static native String messageError(JSObject m);
    @JSBody(params="m",script="return m.width;")
    private static native int messageWidth(JSObject m);
    @JSBody(params="m",script="return m.height;")
    private static native int messageHeight(JSObject m);
    @JSBody(params="m",script="return m.levels.length;")
    private static native int levelCount(JSObject m);
    @JSBody(params={"m","i"},script="return new Int8Array(m.levels[i]);")
    private static native Int8Array level(JSObject m,int i);
}
