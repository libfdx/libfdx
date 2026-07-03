package io.github.libfdx.backend.web;

/**
 * Provides a no-op web preload listener.
 *
 * @author xpenatan
 */
final class WebNoopPreloadApplicationListener implements WebPreloadApplicationListener {
    static final WebNoopPreloadApplicationListener INSTANCE = new WebNoopPreloadApplicationListener();

    private WebNoopPreloadApplicationListener() {
    }

    /**
     * Renders one preloading frame.
     *
     * @param context the preload context
     */
    @Override
    public void render(WebPreloadContext context) {
    }
}
