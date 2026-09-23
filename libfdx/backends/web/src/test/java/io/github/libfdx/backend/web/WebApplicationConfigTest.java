package io.github.libfdx.backend.web;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.application.ApplicationAdapter;
import org.junit.jupiter.api.Test;

final class WebApplicationConfigTest {
    @Test
    void deferredPathsAreCopiedAndMatchExactFilesOrDirectoryPrefixes() {
        String[] paths={"./assets/music/", "levels/next.tmj"};
        WebApplicationConfig config=new WebApplicationConfig().deferAssets(paths);
        paths[0]="changed";
        String[] selected=config.deferredAssets();
        assertTrue(WebAssetPreloader.isDeferred("music/track.wav",selected));
        assertTrue(WebAssetPreloader.isDeferred("levels/next.tmj",selected));
        org.junit.jupiter.api.Assertions.assertFalse(WebAssetPreloader.isDeferred("music-box.wav",selected));
        org.junit.jupiter.api.Assertions.assertFalse(WebAssetPreloader.isDeferred("levels/next.tmj.bak",selected));
        selected[0]="changed";
        org.junit.jupiter.api.Assertions.assertEquals("music/",config.deferredAssets()[0]);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->config.deferAssets("../other"));
    }
    @Test
    void acceptsAStandardApplicationListenerForPreloadRendering() {
        ApplicationAdapter listener = new ApplicationAdapter();

        WebApplicationConfig config = new WebApplicationConfig().preloadApplication(listener);

        assertSame(listener, config.applicationPreloadListener());
        assertTrue(config.preloadApplicationListener() == null);
    }

    @Test
    void contextAwarePreloadListenerReplacesAStandardListener() {
        ApplicationAdapter applicationListener = new ApplicationAdapter();
        WebPreloadApplicationListener webListener = context -> {
        };
        WebApplicationConfig config = new WebApplicationConfig()
                .preloadApplication(applicationListener)
                .preloadApplicationListener(webListener);

        assertSame(webListener, config.preloadApplicationListener());
        assertTrue(config.applicationPreloadListener() == null);
    }
}
