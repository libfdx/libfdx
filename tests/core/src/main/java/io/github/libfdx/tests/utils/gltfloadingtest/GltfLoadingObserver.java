package io.github.libfdx.tests.utils.gltfloadingtest;

/** Optional platform assertions around the real managed glTF request. */
public interface GltfLoadingObserver {
    GltfLoadingObserver NONE = new GltfLoadingObserver() {};
    default void beforeRequest() {}
    default void frame(boolean modelReady) {}
    default void dispose() {}
}
