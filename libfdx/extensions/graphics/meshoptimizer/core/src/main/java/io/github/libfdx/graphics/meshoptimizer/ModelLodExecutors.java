package io.github.libfdx.graphics.meshoptimizer;

/** Platform default factory. Desktop/Android use JVM workers; the web backend binds this hook
 * to Web Workers at compile time. The caller owns the returned service. No process-global pool. */
public final class ModelLodExecutors {
    private ModelLodExecutors() { }
    public static ModelLodExecutor create(int workers) {
        return new ThreadedModelLodExecutor(workers);
    }
}
