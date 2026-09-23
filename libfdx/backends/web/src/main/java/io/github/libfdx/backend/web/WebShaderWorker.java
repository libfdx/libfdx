package io.github.libfdx.backend.web;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.internal.NativeRuntimeShaderResultEnvelope;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** Backend-owned, lazy Tint worker. Accessed only from the browser event loop.
 * Source and the existing FDXR envelope cross the boundary; no Java closures or GPU handles do.
 * Up to 32 jobs / four million UTF-16 input characters remain retained through result decoding.
 * Infrastructure failure selects owner-executor fallback; invalid shader diagnostics do not.
 */
final class WebShaderWorker {
    private static final int MAX_CHARS = 4 * 1024 * 1024;
    private final WebRuntimeShaderCompiler compiler;
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private final ArrayList<Job> pending = new ArrayList<>();
    private WebWorkerConnection worker;
    private Job active;
    private boolean started, ready, fallback, disposed;
    private int nextId, retainedChars;

    WebShaderWorker(WebRuntimeShaderCompiler compiler) { this.compiler = compiler; }

    FdxFuture<RuntimeShaderCompileResult> compile(RuntimeShaderCompileRequest request, Consumer<Runnable> execute) {
        if (request == null || execute == null) throw new NullPointerException("Shader request/executor");
        long size = (long) request.source().length() + request.entryPoint().length()
                + request.glslProfile().length() + request.glslEsProfile().length();
        if (disposed || pending.size() >= 32 || size > MAX_CHARS - retainedChars)
            return FdxFuture.failed(new FdxException(disposed ? "Shader worker is disposed" : "Shader worker queue is full"));
        Job job = new Job(++nextId, request, execute, (int) size);
        queue.addLast(job); pending.add(job); retainedChars += job.size;
        if (!started) {
            started = true;
            worker = WebWorkerConnection.bundled("shader", 15000, this::receive, this::unavailable);
            if (worker == null) fallback = true;
        }
        pump();
        return job.future;
    }

    private void pump() {
        if (disposed || active != null || !ready && !fallback) return;
        active = queue.pollFirst();
        if (active == null) return;
        if (fallback) runFallback(active);
        else {
            try {
                var r = active.request;
                worker.post(message(active.id, r.source(), WebRuntimeShaderCompiler.nativeTarget(r.target()),
                        WebRuntimeShaderCompiler.nativeStage(r.stage()), r.entryPoint(), r.glslProfile(), r.glslEsProfile()));
            } catch (RuntimeException | Error error) { unavailable(); }
        }
    }

    private void receive(JSObject message) {
        if (disposed || fallback) return;
        if (startupError(message)) { unavailable(); return; }
        if (ready(message)) { ready = true; pump(); return; }
        Job job = active;
        if (job == null || id(message) != job.id || job.completing) { unavailable(); return; }
        String error = error(message), encoded = result(message);
        if (error == null && (encoded == null || encoded.isEmpty())) { unavailable(); return; }
        complete(job, encoded, error);
    }

    private void runFallback(Job job) {
        if (job.completing) return;
        job.completing = true;
        active = null;
        try {
            job.execute.accept(() -> complete(job, null, null));
        } catch (Throwable failure) {
            if (job.future.isDone()) throw failure;
            release(job);
            job.future.completeExceptionally(failure);
        } finally { pump(); }
    }

    private void complete(Job job, String encoded, String error) {
        if (disposed || job.future.isDone()) return;
        RuntimeShaderCompileResult result = null;
        Throwable failure = null;
        try {
            if (error != null) throw new FdxException("Shader worker: " + error);
            result = encoded == null ? compiler.compile(job.request)
                    : NativeRuntimeShaderResultEnvelope.decodeBase64(encoded);
        } catch (Throwable caught) { failure = caught; }
        release(job);
        try {
            if (failure != null) job.future.completeExceptionally(failure);
            else job.future.complete(result);
        } finally { pump(); }
    }

    private void release(Job job) {
        if (active == job) active = null;
        pending.remove(job); retainedChars -= job.size;
    }

    private void unavailable() {
        if (disposed || fallback) return;
        fallback = true;
        if (worker != null) worker.terminate();
        worker = null;
        if (active != null) runFallback(active); else pump();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        if (worker != null) worker.terminate();
        worker = null;
        active = null;
        queue.clear(); queue.addAll(pending); pending.clear();
        retainedChars = 0;
        Throwable first = null;
        while (!queue.isEmpty()) {
            try { queue.removeFirst().future.completeExceptionally(new CancellationException("Shader backend disposed")); }
            catch (Throwable failure) { if (first == null) first = failure; }
        }
        if (first instanceof RuntimeException failure) throw failure;
        if (first instanceof Error failure) throw failure;
    }

    private static final class Job {
        final int id, size;
        final RuntimeShaderCompileRequest request;
        final Consumer<Runnable> execute;
        final FdxFuture<RuntimeShaderCompileResult> future = FdxFuture.pending();
        boolean completing;
        Job(int id, RuntimeShaderCompileRequest request, Consumer<Runnable> execute, int size) {
            this.id = id; this.request = request; this.execute = execute; this.size = size;
        }
    }

    @JSBody(params = {"id","source","target","stage","entry","glsl","es"}, script =
            "return {id:id,source:source,target:target,stage:stage,entry:entry,glsl:glsl,es:es};")
    private static native JSObject message(int id, String source, int target, int stage, String entry, String glsl, String es);
    @JSBody(params = "m", script = "return m.ready === true;") private static native boolean ready(JSObject m);
    @JSBody(params = "m", script = "return !!m.startupError;") private static native boolean startupError(JSObject m);
    @JSBody(params = "m", script = "return m.id | 0;") private static native int id(JSObject m);
    @JSBody(params = "m", script = "return m.error || null;") private static native String error(JSObject m);
    @JSBody(params = "m", script = "return m.result || null;") private static native String result(JSObject m);
}
