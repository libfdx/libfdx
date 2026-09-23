package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUCallbackMode;
import com.github.xpenatan.webgpu.WGPUCreatePipelineAsyncStatus;
import com.github.xpenatan.webgpu.WGPUCreateRenderPipelineAsyncCallback;
import com.github.xpenatan.webgpu.WGPURenderPipeline;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Native Dawn callback draining, including after the application context has closed.
 * Each pending caller retains the resource domain until its future settles. Callbacks only
 * record results; wrappers are destroyed after processEvents has returned. */
final class WGPUDawnPipelines implements AutoCloseable {
    private final WGPUContext context;
    private final WGPUResourceDomain domain;
    private final ScheduledThreadPoolExecutor events;
    private final ArrayList<Result> pending = new ArrayList<>();
    private boolean scheduled;
    private volatile boolean closed;

    WGPUDawnPipelines(WGPUContext context) {
        this.context = context;
        domain = context.resourceDomain();
        events = new ScheduledThreadPoolExecutor(1, work -> {
            Thread thread = new Thread(work, "libfdx-dawn-events");
            thread.setDaemon(true);
            return thread;
        });
    }

    // Submission and descriptor construction stay on a CPU worker, outside the event lock.
    FdxFuture<WGPURenderPipelineHandle> submit(WGPUGraphicsDevice.RenderPipelineInputs inputs,
            WGPUShaderModuleHandle module) {
        Result result = new Result(inputs, module);
        try {
            context.nativeDevice().createRenderPipelineAsync(inputs.descriptor,
                    WGPUCallbackMode.AllowProcessEvents, result);
        } catch (RuntimeException | Error error) {
            result.dispose();
            throw error;
        }
        synchronized (domain) {
            // Even a close racing submission must drain this callback, never destroy it early.
            pending.add(result);
            if (!scheduled) {
                scheduled = true;
                events.execute(this::poll);
            }
        }
        return result.future;
    }

    private void poll() {
        ArrayList<Result> completed = new ArrayList<>();
        synchronized (domain) {
            context.nativeInstance().processEvents();
            for (int i = pending.size() - 1; i >= 0; i--) {
                Result result = pending.get(i);
                if (result.called) {
                    pending.remove(i);
                    // Other context event pumps use this same lock. No callback can still
                    // be executing when its native/FFM callback storage is freed.
                    result.dispose();
                    completed.add(result);
                }
            }
            if (pending.isEmpty()) scheduled = false;
            else events.schedule(this::poll, 1, TimeUnit.MILLISECONDS);
        }
        // Settlement may release the final device/instance reference. All event access and
        // callback retirement above must finish first, and application publication stays on owner.
        for (Result result : completed) result.complete();
    }

    @Override
    public void close() {
        synchronized (domain) { closed = true; }
        // The CPU workers may still be returning from submission. Shutdown is performed only
        // after they and every pending callback have retired (see drained()).
    }

    void drained() {
        synchronized (domain) {
            if (closed && pending.isEmpty()) events.shutdown();
        }
    }

    private final class Result extends WGPUCreateRenderPipelineAsyncCallback {
        final FdxFuture<WGPURenderPipelineHandle> future = FdxFuture.pending();
        final WGPUGraphicsDevice.RenderPipelineInputs inputs;
        final WGPUShaderModuleHandle module;
        WGPUCreatePipelineAsyncStatus status;
        WGPURenderPipeline pipeline;
        String message;
        volatile boolean called;

        Result(WGPUGraphicsDevice.RenderPipelineInputs inputs, WGPUShaderModuleHandle module) {
            this.inputs = inputs;
            this.module = module;
        }

        @Override
        protected void onCallback(WGPUCreatePipelineAsyncStatus status,
                WGPURenderPipeline pipeline, String message) {
            this.status = status;
            this.pipeline = pipeline;
            this.message = message;
            called = true;
        }

        void complete() {
            WGPURenderPipelineHandle output = null;
            Throwable failure = null;
            try {
                if (closed || domain.isClosed()) throw new CancellationException("Dawn preparation closed");
                if (status != WGPUCreatePipelineAsyncStatus.Success || !pipeline.isValid())
                    throw new FdxException("Dawn render pipeline failed (" + status + "): " + message);
                output = inputs.takePipeline(pipeline);
                pipeline = null;
            } catch (Throwable error) { failure = error; }
            WGPUCleanup cleanup = new WGPUCleanup();
            if (pipeline != null) {
                if (pipeline.isValid()) cleanup.run(pipeline::release);
                cleanup.run(pipeline::dispose);
            }
            cleanup.run(inputs::close);
            cleanup.run(module::dispose);
            try { cleanup.throwIfFailed(); }
            catch (Throwable error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
            if (failure == null) future.complete(output);
            else {
                if (output != null) output.dispose();
                future.completeExceptionally(failure);
            }
        }
    }
}
