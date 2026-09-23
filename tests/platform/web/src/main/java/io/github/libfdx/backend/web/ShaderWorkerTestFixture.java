package io.github.libfdx.backend.web;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.runtime.core.shader.*;
import io.github.libfdx.runtime.core.shader.internal.NativeRuntimeShaderResultEnvelope;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import org.teavm.jso.JSBody;

/** Test infrastructure in the backend package to exercise its non-public lifecycle. */
public final class ShaderWorkerTestFixture {
    private static final String SOURCE = """
            @vertex fn vs() -> @builtin(position) vec4<f32> { return vec4<f32>(0.0, 0.0, 0.0, 1.0); }
            @fragment fn fs() -> @location(0) vec4<f32> { return vec4<f32>(1.0); }
            """;
    private final WebRuntimeShaderCompiler compiler = new WebRuntimeShaderCompiler();
    private final WebRuntimeShaderCompiler crashing = new WebRuntimeShaderCompiler();
    private final WebRuntimeShaderCompiler missing = new WebRuntimeShaderCompiler();
    private final WebRuntimeShaderCompiler blocked = new WebRuntimeShaderCompiler();
    private final WebRuntimeShaderCompiler noBlob = new WebRuntimeShaderCompiler();
    private final ArrayDeque<Runnable> work = new ArrayDeque<>();
    private final ArrayList<FdxFuture<RuntimeShaderCompileResult>> results = new ArrayList<>();
    private final ArrayList<RuntimeShaderCompileResult> reference = new ArrayList<>();
    private FdxFuture<RuntimeShaderCompileResult> crashed, unavailable, rejected, blobFailed;
    private WebWorkerConnection silent, borrowed;
    private String borrowedUrl;
    private boolean timedOut, borrowedReady;
    private boolean passed;

    public void start() {
        watchBlobUrls();
        watchWorker();
        check(compiler.compileAsync(RuntimeShaderCompileRequest.builder(" ".repeat(4 * 1024 * 1024),
                RuntimeShaderCompileTarget.WGPU_WGSL).build(), work::add).isFailed(), "Input size bound did not reject");
        add(RuntimeShaderCompileRequest.builder(SOURCE, RuntimeShaderCompileTarget.WGPU_WGSL).build());
        add(RuntimeShaderCompileRequest.builder(SOURCE, RuntimeShaderCompileTarget.WEBGPU_WGSL).build());
        add(RuntimeShaderCompileRequest.builder(SOURCE, RuntimeShaderCompileTarget.WEBGL_GLSL_ES)
                .stage(RuntimeShaderCompileStage.VERTEX).entryPoint("vs").build());
        add(RuntimeShaderCompileRequest.builder(SOURCE, RuntimeShaderCompileTarget.WEBGL_GLSL_ES)
                .stage(RuntimeShaderCompileStage.FRAGMENT).entryPoint("fs").build());
        add(RuntimeShaderCompileRequest.builder("invalid wgsl", RuntimeShaderCompileTarget.WGPU_WGSL).build());
        check(results.stream().noneMatch(FdxFuture::isDone), "Worker submission completed synchronously");

        var request = RuntimeShaderCompileRequest.builder(SOURCE, RuntimeShaderCompileTarget.WGPU_WGSL).build();
        crashWorker();
        try { crashed = crashing.compileAsync(request, work::add); }
        finally { restoreWorker(); }
        disableWorker();
        try { unavailable = missing.compileAsync(request, work::add); }
        finally { restoreWorker(); }
        check(!unavailable.isDone(), "Fallback bypassed loading executor");

        blockWorker();
        try { rejected = blocked.compileAsync(request, work::add); }
        finally { restoreWorker(); }
        blockBlob();
        try { blobFailed = noBlob.compileAsync(request, work::add); }
        finally { restoreBlob(); }
        check(!rejected.isDone() && !blobFailed.isDone(), "Blocked Blob fallback bypassed executor");

        silenceWorker();
        try { silent = WebWorkerConnection.bundled("assets", 30, message -> {
            throw new IllegalStateException("Silent worker replied");
        }, () -> { timedOut = true; silent.terminate(); }); }
        finally { restoreWorker(); }
        check(silent != null, "Silent worker did not start");
        borrowedUrl = customUrl();
        borrowed = WebWorkerConnection.open(borrowedUrl, 15000, message -> borrowedReady = true,
                () -> { throw new IllegalStateException("Custom worker failed"); });

        var cancelled = new WebRuntimeShaderCompiler();
        ArrayList<FdxFuture<RuntimeShaderCompileResult>> accepted = new ArrayList<>();
        for (int i = 0; i < 32; i++) accepted.add(cancelled.compileAsync(request, work::add));
        check(cancelled.compileAsync(request, work::add).isFailed(), "Queue bound did not reject");
        cancelled.dispose(); cancelled.dispose();
        for (var future : accepted) check(future.isFailed(), "Shutdown did not fail pending work");
        check(cancelled.compileAsync(request, work::add).isFailed(), "Disposed compiler accepted work");
    }

    private void add(RuntimeShaderCompileRequest request) {
        reference.add(compiler.compile(request));
        results.add(compiler.compileAsync(request, work::add));
    }

    public boolean update() {
        // Deliberately leave all caller executors paused until the worker has serviced every job.
        // A cancelled/paused loading scope must not retain the shared worker's active slot.
        if (workerResults() < 5) return false;
        long deadline = System.nanoTime() + 2_000_000L;
        while (!work.isEmpty() && System.nanoTime() < deadline) work.removeFirst().run();
        if (passed) return true;
        if (!timedOut || !borrowedReady || !rejected.isDone() || !blobFailed.isDone()
                || !crashed.isDone() || !unavailable.isDone() || results.stream().anyMatch(r -> !r.isDone())) return false;
        for (int i = 0; i < results.size(); i++) {
            var expected = reference.get(i);
            var actual = results.get(i).get();
            check(actual.success() == expected.success(), "Worker status differs at " + i);
            if (expected.success()) check(Arrays.equals(NativeRuntimeShaderResultEnvelope.encode(expected),
                    NativeRuntimeShaderResultEnvelope.encode(actual)), "Worker result/reflection differs at " + i);
            else {
                check(actual.diagnostics().length == expected.diagnostics().length, "Diagnostic count differs");
                for (int j = 0; j < actual.diagnostics().length; j++) check(actual.diagnostics()[j].message()
                        .equals(expected.diagnostics()[j].message()), "Worker diagnostics differ");
            }
        }
        check(!results.get(4).get().success(), "Invalid WGSL compiled successfully");
        check(crashed.get().success() && unavailable.get().success(), "Worker infrastructure fallback failed");
        check(rejected.get().success() && blobFailed.get().success(), "Blocked Blob worker fallback failed");
        borrowed.terminate(); borrowed.terminate();
        check(onlyBorrowedUrlRemains(borrowedUrl), "Owned URL leaked, revoked twice, or custom URL was revoked");
        revokeCustomUrl(borrowedUrl);
        check(allUrlsReleased(), "Worker Blob URLs were retained after startup/failure/disposal");
        check(workerResults() == 5, "Expected five actual worker replies, got " + workerResults());
        passed = true;
        System.out.println("WEB_SHADER_WORKER_PASS worker=5 results=exact diagnostics=exact crash=1 unavailable=1 bounds=1 disposal=1 blobCleanup=1 blocked=1 timeout=1 customUrlOwnership=1");
        return true;
    }

    public void dispose() {
        compiler.dispose(); crashing.dispose(); missing.dispose(); blocked.dispose(); noBlob.dispose();
        check(passed, "Shader worker test ended before completion");
    }

    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }

    @JSBody(script = """
            const original=globalThis.Worker;
            globalThis.libfdxShaderTestReplies=0;
            globalThis.Worker=function(url,options) {
                const w=new original(url,options);
                if(String(url).indexOf('#libfdx-shader')>=0) w.addEventListener('message',function(e) {
                    if(e.data.result) globalThis.libfdxShaderTestReplies++;
                });
                return w;
            };
            globalThis.Worker.prototype=original.prototype;
            """)
    private static native void watchWorker();
    @JSBody(script = "return globalThis.libfdxShaderTestReplies;")
    private static native int workerResults();
    @JSBody(script = """
            const original=globalThis.Worker;
            globalThis.libfdxTestWorker=original;
            globalThis.Worker=function() {
                return new original("data:text/javascript,postMessage({ready:true});onmessage=function(){throw new Error('injected shader crash');}");
            };
            """)
    private static native void crashWorker();
    @JSBody(script = """
            const original=globalThis.Worker;
            globalThis.libfdxTestWorker=original;
            globalThis.Worker=function() {return new original('data:text/javascript,onmessage=function(){};');};
            """)
    private static native void silenceWorker();
    @JSBody(script = "globalThis.libfdxTestWorker=globalThis.Worker; globalThis.Worker=function(){throw new DOMException('Injected CSP rejection','SecurityError');};")
    private static native void blockWorker();
    @JSBody(script = "globalThis.libfdxTestCreateUrl=URL.createObjectURL; URL.createObjectURL=function(){throw new Error('Injected Blob failure');};")
    private static native void blockBlob();
    @JSBody(script = "URL.createObjectURL=globalThis.libfdxTestCreateUrl; delete globalThis.libfdxTestCreateUrl;")
    private static native void restoreBlob();
    @JSBody(script = """
            const create=URL.createObjectURL, revoke=URL.revokeObjectURL;
            const probe=globalThis.libfdxBlobProbe={live:new Set(),created:0,revoked:0,duplicates:0};
            URL.createObjectURL=function(blob) {
                const url=create.call(URL,blob); probe.live.add(url); probe.created++; return url;
            };
            URL.revokeObjectURL=function(url) {
                if(!probe.live.delete(url))probe.duplicates++;
                probe.revoked++; revoke.call(URL,url);
            };
            """)
    private static native void watchBlobUrls();
    @JSBody(script = "return URL.createObjectURL(new Blob(['postMessage({ready:true});'],{type:'text/javascript'}));")
    private static native String customUrl();
    @JSBody(params = "url", script = "const p=globalThis.libfdxBlobProbe; return p.live.size===1 && p.live.has(url) && p.duplicates===0;")
    private static native boolean onlyBorrowedUrlRemains(String url);
    @JSBody(params = "url", script = "URL.revokeObjectURL(url);")
    private static native void revokeCustomUrl(String url);
    @JSBody(script = "const p=globalThis.libfdxBlobProbe; return p.created>=6 && p.live.size===0 && p.created===p.revoked && p.duplicates===0;")
    private static native boolean allUrlsReleased();
    @JSBody(script = "globalThis.libfdxTestWorker=globalThis.Worker; globalThis.Worker=undefined;")
    private static native void disableWorker();
    @JSBody(script = "globalThis.Worker=globalThis.libfdxTestWorker; delete globalThis.libfdxTestWorker;")
    private static native void restoreWorker();
}
