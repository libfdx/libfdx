package io.github.libfdx.graphics.meshoptimizer;

import java.util.Objects;
import java.util.concurrent.CancellationException;

/** Asynchronous CPU preparation. Poll/take/cancel on the application owner; executors call
 * execute or complete/fail off-thread. Completion is safely published. No application callbacks
 * or graphics operations run on workers. Keep the source alive until generated LODs are disposed;
 * after cancel returns the worker only retains detached CPU data, so the source may be released. */
public final class ModelLodJob {
    private ModelLodInput owner;
    private final ModelLodInput detached;
    private final ModelLodSettings settings;
    private volatile boolean cancelled;
    private volatile boolean finished;
    private PreparedModelLods result;
    private Throwable failure;
    private boolean taken;

    public ModelLodJob(ModelLodInput input, ModelLodSettings settings) {
        owner = Objects.requireNonNull(input);
        detached = input.detached();
        this.settings = Objects.requireNonNull(settings);
    }

    public ModelLodSettings settings() { return settings; }
    public boolean isCancelled() { return cancelled; }
    /** Physical worker completion, including acknowledgement of cancellation. */
    public boolean isFinished() { return finished; }
    public boolean isDone() { return cancelled || finished; }

    /** Owner-thread cancellation; no waiting for a worker and no GPU resources to release. */
    public synchronized void cancel() { cancelled = true; owner = null; result = null; }

    /** CPU worker entry point. Must never be executed inline on the application thread. */
    public void execute() {
        try { complete(new ModelLodOptimizer().prepare(detached, settings, () -> cancelled)); }
        catch(Throwable error) { fail(error); }
    }

    /** Transport entry point; consumes a completed, detached CPU result. */
    public synchronized void complete(PreparedModelLods prepared) {
        if(finished) return;
        if(!cancelled) result = Objects.requireNonNull(prepared);
        finished = true;
    }

    /** Transport entry point. Failure is delivered when the owner takes the result. */
    public synchronized void fail(Throwable error) {
        if(finished) return;
        failure = Objects.requireNonNull(error);
        finished = true;
    }

    /** Encodes only detached CPU data for a worker transport. Call before handing off execution. */
    public byte[] workerRequest() { return ModelLodWorkerCodec.request(detached, settings); }

    /** Restores worker output without running the optimizer or vertex packer on the owner. */
    public void completeWorkerResponse(byte[] response) {
        if(cancelled) { fail(new CancellationException("LOD job cancelled")); return; }
        complete(ModelLodWorkerCodec.response(detached, settings, response));
    }

    /** Takes CPU preparation once on the graphics owner. Does not upload or block. */
    public synchronized PreparedModelLods take() {
        if(cancelled) throw new CancellationException("LOD job cancelled");
        if(!finished) throw new IllegalStateException("LOD preparation is pending");
        if(taken) throw new IllegalStateException("LOD preparation already consumed");
        taken = true;
        if(failure instanceof RuntimeException error) { owner = null; throw error; }
        if(failure instanceof Error error) { owner = null; throw error; }
        if(failure != null) { owner = null; throw new IllegalStateException("LOD worker failed", failure); }
        PreparedModelLods prepared = new PreparedModelLods(owner, settings, result.meshes, result.reports);
        owner = null;
        result = null;
        return prepared;
    }
}
