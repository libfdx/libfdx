package io.github.libfdx.graphics.meshoptimizer;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded JVM adapter for desktop and Android. At most one retained job per configured worker;
 * cancelled running jobs keep their slot until acknowledged. Never joins the application thread. */
final class ThreadedModelLodExecutor implements ModelLodExecutor {
    private final ThreadPoolExecutor pool;
    private final ArrayList<ModelLodJob> jobs = new ArrayList<>();
    private final int workers;
    private boolean disposed;

    ThreadedModelLodExecutor(int workers) {
        if(workers < 1) throw new IllegalArgumentException("LOD workers must be positive");
        this.workers = workers;
        AtomicInteger ids = new AtomicInteger();
        pool = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(workers), task -> {
                    Thread thread = new Thread(task, "libfdx-lod-" + ids.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override public int workers() { return workers; }
    @Override public void update() { jobs.removeIf(ModelLodJob::isFinished); }
    @Override public boolean hasCapacity() { update(); return !disposed && jobs.size() < workers; }
    @Override public ModelLodJob trySubmit(ModelLodInput input, ModelLodSettings settings) {
        if(disposed) throw new IllegalStateException("LOD executor is disposed");
        if(!hasCapacity()) return null;
        ModelLodJob job = new ModelLodJob(input, settings);
        jobs.add(job);
        try { pool.execute(job::execute); }
        catch(RuntimeException failure) { jobs.remove(job); job.cancel(); throw failure; }
        return job;
    }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if(disposed) return;
        disposed = true;
        for(ModelLodJob job : jobs) job.cancel();
        jobs.clear();
        pool.shutdown();
    }
}
