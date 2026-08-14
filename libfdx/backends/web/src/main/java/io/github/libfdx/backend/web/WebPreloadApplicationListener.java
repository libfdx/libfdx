package io.github.libfdx.backend.web;

/**
 * Defines the context-aware web startup preloading screen lifecycle.
 *
 * <p>The backend submits the first rendered frame before it starts heavyweight asset and native-runtime loading.
 * The supplied context and its UI Kit root are borrowed for this lifecycle and are disposed by the backend after
 * {@link #dispose(WebPreloadContext)} returns.</p>
 *
 * @author xpenatan
 */
public interface WebPreloadApplicationListener {
    /**
     * Returns a no-op preloading listener.
     *
     * @return a no-op listener
     */
    static WebPreloadApplicationListener none() {
        return WebNoopPreloadApplicationListener.INSTANCE;
    }

    /**
     * Runs the create step.
     *
     * @param context the preload context
     */
    default void create(WebPreloadContext context) {
    }

    /**
     * Handles a size change.
     *
     * @param context the preload context
     * @param width the logical canvas width
     * @param height the logical canvas height
     */
    default void resize(WebPreloadContext context, int width, int height) {
    }

    /**
     * Renders one preloading frame.
     *
     * @param context the preload context
     */
    void render(WebPreloadContext context);

    /**
     * Releases resources held by this listener.
     *
     * @param context the preload context
     */
    default void dispose(WebPreloadContext context) {
    }
}
