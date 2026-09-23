package io.github.libfdx.testsupport.graphics;

/** Optional platform checks; the executable scenario owns all actual asset requests. */
public interface ConcurrentGltfObserver {
    ConcurrentGltfObserver NONE = new ConcurrentGltfObserver() { };
    default void beforeRequests() { }
    default void modelReady(int index) { }
    default void frame(boolean allReady) { }
    default void loadingComplete(long maxAssetNanos, long maxShaderNanos, long maxRenderNanos, long maxFrameGapNanos) { }
    default void dispose() { }
}
