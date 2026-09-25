package io.github.libfdx.backend.web;

import io.github.libfdx.graphics.meshoptimizer.ModelLodJob;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int8Array;

/** Browser-event-loop state for one worker. Request/result messages own independent buffers. */
final class WebModelLodSlot {
    private WebWorkerConnection worker;
    private ModelLodJob job;
    private boolean ready;
    private int id;

    boolean available() { return job == null; }
    void submit(ModelLodJob next) {
        job = next;
        id++;
        try {
            if(worker == null) worker = WebWorkerConnection.bundledSource(WebModelLodSource.source(),
                    "lod", 15000, this::receive, this::failed);
            if(worker == null) failed();
            else if(ready) send();
        } catch(RuntimeException | Error error) { fail(error); }
    }
    void update() {
        if(job != null && job.isCancelled()) {
            stop();
            job.fail(new java.util.concurrent.CancellationException("LOD worker cancelled"));
            job = null;
        }
    }
    private void send() {
        try {
            byte[] request = job.workerRequest();
            Int8Array bytes = Int8Array.create(request.length);
            bytes.set(request);
            worker.post(message(id, bytes), bytes.getBuffer());
        } catch(RuntimeException | Error error) { fail(error); }
    }
    private void receive(JSObject message) {
        if(isReady(message)) { ready = true; if(job != null) send(); return; }
        if(job == null || messageId(message) != id) { failed(); return; }
        String error = error(message);
        if(error != null) { fail(new IllegalStateException("LOD worker: " + error)); return; }
        ModelLodJob complete = job;
        job = null;
        try {
            if(complete.isCancelled()) { complete.fail(new java.util.concurrent.CancellationException("LOD worker cancelled")); return; }
            Int8Array bytes = bytes(message);
            complete.completeWorkerResponse(bytes.copyToJavaArray());
        } catch(RuntimeException | Error failure) { complete.fail(failure); stop(); }
    }
    private void failed() { fail(new IllegalStateException("LOD Web Worker unavailable or failed; enable blob workers in the page policy")); }
    private void fail(Throwable error) {
        stop();
        if(job != null) { job.fail(error); job = null; }
    }
    private void stop() { if(worker != null) worker.terminate(); worker = null; ready = false; }
    void dispose() {
        if(job != null) { job.cancel(); job.fail(new java.util.concurrent.CancellationException("LOD executor disposed")); job = null; }
        stop();
    }
    @JSBody(params = {"id", "bytes"}, script = "return {id:id,bytes:bytes.buffer};")
    private static native JSObject message(int id, Int8Array bytes);
    @JSBody(params = "m", script = "return !!m.ready;")
    private static native boolean isReady(JSObject m);
    @JSBody(params = "m", script = "return m.id;")
    private static native int messageId(JSObject m);
    @JSBody(params = "m", script = "return m.error||null;")
    private static native String error(JSObject m);
    @JSBody(params = "m", script = "return new Int8Array(m.bytes);")
    private static native Int8Array bytes(JSObject m);
}
